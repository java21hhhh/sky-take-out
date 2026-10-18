# sky-take-out

餐饮外卖 SaaS 的**后端服务** —— 同时支撑**商家管理端**和**顾客点餐端**两套接口。

Spring Boot 2.7.3 单体应用，按职责拆成三个 Maven 模块（`sky-common` / `sky-pojo` / `sky-server`）。

---

## 一、业务范围

一共 17 个 Controller，按调用方分成两半：

| | 管理端 `/admin/**` | 用户端 `/user/**` |
|---|---|---|
| 认证 | JWT，请求头 `token` | JWT，请求头 `authentication` |
| 员工 | 登录、增删改查、启用禁用 | — |
| 分类 | 增删改查、启用禁用 | 按类型查 |
| 菜品 | 增删改查、起售停售、口味 | 按分类查 |
| 套餐 | 增删改查、起售停售 | 按分类查 |
| 订单 | 接单、拒单、派送、完成、取消 | 下单、支付、历史、再来一单 |
| 店铺 | 营业状态、营业中/打烊 | 查询营业状态 |
| 其它 | 工作台统计、运营报表（Excel 导出） | 微信登录、购物车、地址簿 |

管理端和用户端**各自一套 JWT 拦截器**，令牌密钥与请求头名都不同，互不通用。

---

## 二、技术栈

| 用途 | 选型 |
|---|---|
| 语言 / 框架 | Java 11 · Spring Boot 2.7.3 |
| 持久层 | MyBatis 2.2.0 · PageHelper 1.3.0（分页） · Druid 1.2.1（连接池） |
| 数据库 | MySQL 8 |
| 缓存 | Redis（菜品列表 `dish_*`、店铺营业状态 `SHOP_STATUS`） |
| 实时推送 | Spring WebSocket（来单提醒） |
| 认证 | JJWT 0.9.1 |
| 接口文档 | Knife4j 3.0.2（Swagger2） |
| 报表导出 | Apache POI 3.16 |
| 公共字段填充 | AspectJ 切面 `AutoFillAspect`（创建/更新时间、操作人） |

---

## 三、我改过的地方

> 下面几处是**在原课程实现之外做的**。写在这里是因为它们的价值不在"改了什么"，
> 而在"为什么原来的写法是错的、代价是什么"。

### 1. 派送中订单的定时任务：调度周期和判断阈值对不上

`sky-server/.../task/OrderTask.java` → `processDeliveryOrder()`

原来的 cron 是 `0 0 1 * * ?` —— **每天凌晨 1 点只跑一次**，但方法里的判断条件写的是
「派送超过 **60 分钟**仍未完成」。**这两个数字对不上**：

> 下午 13:30 下单、14:00 开始派送的单子，要等到**第二天凌晨 1 点**才会被标记成已完成。
> 中间十几个小时，订单状态一直是错的 —— 用户端和商家端都挂着一个假的「派送中」。

**改法**：cron 换成 `0 */5 * * * *`（每 5 分钟）。这样「超过 60 分钟」才真的是 60 分钟
（最坏情况滞后 5 分钟，而不是滞后一整天）。

**代价（明说）**：查询次数从每天 1 次变成每天 288 次。这是刻意的取舍 —— 订单状态错了是
两个端都看得见的体验问题，而这条查询本身可以做到很便宜。

**「便宜」是量过的，不是感觉**：为了让这 288 次查询真的便宜，给 `orders` 补了
`(status, order_time)` 联合索引。20 万单数据下实测，同一条查询从
`type=ALL`（扫 199092 行，**约 430 ms**）变成 `type=range`（扫 61 行，**约 9 ms**），
约 **45 倍**。代价是索引占 **5.52 MB**（表 23.1 MB，约 +24%），且每次写入
`status` / `order_time` 都要维护它。复现脚本见 `perf/seed_perf_data.sql`。

> 列顺序不是随便写的：`status` 是等值条件放前面，`order_time` 是范围条件放后面，
> 这样 MySQL 才能同时用上两列。反过来写成 `(order_time, status)`，第二列基本用不上。

**进一步**：真要在"扫表"和"实时"之间两全，正解是**延迟队列**（到点由消息驱动，
而不是靠轮询去发现）。本仓库没做，留作后续。

### 2. 菜品缓存清理：`KEYS` 换成 `SCAN`

`sky-server/.../controller/admin/DishController.java` → `cleanCache()` / `scanKeys()`

原来用的是 `redisTemplate.keys(pattern)`，它对应 Redis 的 **KEYS** 命令，有两个致命点：

- **O(N)** —— 要遍历整个 keyspace 才凑齐匹配项，key 越多越慢；
- **阻塞** —— Redis 执行命令是**单线程**的，KEYS 跑的时候别的命令全部排队。
  线上几十万个 key 时，一句 `KEYS dish_*` 就能把整个 Redis 卡住好几秒，
  所有依赖 Redis 的接口跟着一起超时。

**改法**：改用 `SCAN` 游标分批遍历（每批 `count(200)`），把一次长阻塞拆成很多次短查询。

**代价（明说）**：SCAN **不保证一次扫完** —— 遍历期间被新增/删除的 key 可能漏掉、也可能重复返回。
对"清缓存"这个场景无所谓（缓存本来就是清掉让它重建的）；但拿去干统计就得自己处理重复和漏读。

### 3. 订单状态：6 个方法各写各的，收敛成一张迁移表 + 条件更新

`sky-common/.../enumeration/OrderAction.java`、`sky-server/.../service/impl/OrderStatusUpdater.java`

原来的状态判断散在 6 个方法里，写法各不相同：

| 位置 | 原来的写法 | 问题 |
|---|---|---|
| `complete()` | `DELIVERY_IN_PROGRESS.equals(status)` | 只挡了一个方向 |
| `userCancelById()` | `status > TO_BE_CONFIRMED` | 依赖状态码的数字大小 |
| `cancel()`（管理员） | **完全不判断** | 已完成的订单也能被「取消」 |
| `processTimeOutOrder()` | 先查后 `update`，更新无条件 | 用户卡在第 15 分钟付款，订单会被改回「已取消」 |

**改法一：把规则收进一张表。** `OrderAction` 枚举声明每个动作允许从哪些状态出发、落到哪个状态：

| 动作 | 允许的起始状态 | 目标 |
|---|---|---|
| 支付 | 待付款 | 待接单 |
| 接单 / 拒单 | 待接单 | 已接单 / 已取消 |
| 派送 | 已接单 | 派送中 |
| 完成 | 派送中 | 已完成 |
| 管理员取消 | 待付款 / 待接单 / 已接单 / 派送中 | 已取消 |
| 用户取消 | 待付款 / 待接单 | 已取消 |

**改法二：更新 SQL 带上状态条件。** 光有表还不够 —— 两个请求可以同时读到「待付款」，
都通过校验，然后各自更新。所以状态更新必须写成：

```sql
update orders set status = ?, ... where id = ? and status = ?
```

影响行数为 0，说明「检查」和「更新」之间状态已经被别人改掉了。
**状态机管业务规则，条件更新管并发，两者缺一不可。**

**结果**：`OrderStatusUpdater` 成为状态迁移的唯一入口，6 处各写各的判断全部删除；
定时任务遇到刚支付的订单会跳过并记日志，不再覆盖。

### 4. 测试：从「仓库里没有测试」到 29 + 2

先修了一个隐藏问题：`.gitignore` 里有 `*Test.java` 和 `**/test/` 两行（课程模板留下的），
**所有测试文件都被挡在版本控制外** —— 写了也提交不上去。

然后按层补：

| 测试 | 数量 | 验证什么 |
|---|---|---|
| `OrderActionTest` | 9 | 迁移表：哪些状态允许哪些动作 |
| `OrderStatusUpdaterTest` | 4 | 规则不允许时不碰数据库；条件更新 0 行时返回失败 |
| `OrderServiceImplTest` | 13 | 每个动作交给正确的 action；更新失败报错；支付回调幂等 |
| `OrderTaskTest` | 3 | 同一批里一单并发失败，不能中断整批 |
| `OrderMapperTest`（集成） | 2 | 真实 MySQL：`WHERE status = ?` 不匹配时影响 0 行 |

单元测试不依赖 MySQL / Redis，`mvn test` 直接跑；集成测试打了 `@Tag("integration")`，
默认排除，需要时：

```bash
mvn test -Dsky.test.excludedGroups=
```

---

## 四、怎么跑

### 前置

- JDK 11、Maven
- MySQL 8（本地 3306）
- Redis（本地 6379，未设 `requirepass` 可留空密码）

### 1. 配置

真实配置 `application-dev.yml` **不在仓库里**（含数据库密码、OSS 密钥、微信 AppSecret，
已被 `.gitignore` 排除）。用模板复制一份再填：

```bash
cp sky-server/src/main/resources/application-dev.yml.example \
   sky-server/src/main/resources/application-dev.yml
```

然后把模板里所有 `<...>` 占位符替换成真实值。

### 2. 数据库

建库脚本在 **`sql/sky_take_out.sql`** —— 包含表结构和业务种子数据
（员工 / 分类 / 菜品 / 口味 / 套餐），**不含**用户、订单、购物车、地址簿等个人数据。
直接导入即可：

```bash
mysql -u root -p < sql/sky_take_out.sql
```

脚本会创建 `sky_take_out` 库并切换过去；表结构部分带 `DROP TABLE IF EXISTS`，
重复导入会先清空再重建，**别对着有真实数据的库跑**。

### 3. 启动

**要两步** —— `sky-server` 依赖另外两个模块的 artifact，全新克隆时它们还不在本地仓库里：

```bash
mvn -DskipTests install           # 先把 sky-common / sky-pojo 装进本地仓库
mvn -pl sky-server spring-boot:run
```

> ⚠️ 常见坑：写成 `mvn -pl sky-server -am spring-boot:run` 会**失败** ——
> `-am` 把父工程也拉进 reactor，`spring-boot:run` 会在根 pom 上报
> `Unable to find a suitable main class`。

- 服务端口 **8080**
- 接口文档：<http://localhost:8080/doc.html>（Knife4j）
- 管理端默认账号见数据库 `employee` 表

### 4. 前端

**小程序前端不在本仓库内** —— 这是一个纯后端仓库。

---

## 五、目录结构

```
sky-take-out
├── sky-common/            工具与常量（JWT、OSS、异常、上下文）
│   └── .../constant  context  enumeration  exception  json  properties  result  utils
├── sky-pojo/              数据模型（DTO / VO / Entity）
├── sky-server/            业务主体
    └── src/main/java/com/sky/
        ├── SkyApplication.java     启动类（@EnableScheduling）
        ├── controller/admin/       管理端接口 ×9
        ├── controller/user/        用户端接口 ×8
        ├── service/impl/           业务实现
        ├── mapper/                 MyBatis 接口（XML 在 resources/mapper/）
        ├── interceptor/            两套 JWT 拦截器
        ├── aspect/                 AutoFillAspect 公共字段填充
        ├── task/                   定时任务（超时订单 / 派送中订单）
        ├── websocket/              来单提醒
        └── config/                 Redis / OSS / WebMvc / WebSocket
├── perf/                  性能验证种子数据（独立库，不影响业务库）
└── sql/                   建库脚本（结构 + 种子数据）
```

---

## 六、这个仓库的边界（先说清楚，免得你 clone 下来踩坑）

- **项目来源**：本仓库源自**苍穹外卖课程项目**的后端实现，包名 `com.sky` 与模块名沿用了课程约定。
  第三节点名的改动是课程之外的。
- **测试覆盖有限** —— 29 个单元测试 + 2 个集成测试，集中在订单状态与定时任务；
  员工、分类、套餐、报表等模块仍无测试（集成测试需要本地 MySQL / Redis）。
- **支付是打桩的** —— `OrderServiceImpl.payment()` 是**模拟支付**（代码里有明确注释），
  没有接入真实微信支付：没有商户号，也没有处理微信的异步回调 `notify`。
  将来接真实支付时，把 `paySuccess(...)` 那一行挪到回调里即可。
- **建库 SQL 已补、没有前端** —— 建库脚本在 `sql/sky_take_out.sql`，小程序前端不在本仓库。

---

## 七、运行验证（2026-10-17）

在本机实测跑通（不是只编译）：

- 环境：**JDK 11**（Temurin 11.0.32）+ MySQL 8.0 + Redis 7
- 构建与启动：`mvn -DskipTests install` → `mvn -pl sky-server spring-boot:run`
- 启动耗时约 **9 秒**；`/doc.html` 返回 **200**
- `POST /admin/employee/login`（`admin` / `123456`）返回 `code=1` 并签发 JWT
- `GET /user/shop/status` 返回 `{"code":1,"data":1}`，说明 MySQL 与 Redis 都连通
- ⚠️ 项目是 Spring Boot 2.7.3 / Java 11，**在 JDK 21 上未验证**。
- 测试：`mvn test` 跑 29 个单元测试（不依赖 MySQL / Redis）；集成测试默认排除。

### 索引效果实测（2026-10-18）

用 `perf/seed_perf_data.sql` 在独立库 `sky_take_out_perf` 造 20 万单，
测两条定时任务实际执行的 SQL（`WHERE status = ? AND order_time <= ?`）：

| | 加索引前 | 加索引后 |
|---|---|---|
| 执行计划 | `type=ALL`，`rows=199092` | `type=range`，`key=idx_status_order_time` |
| 实际返回行数 | 61 / 206 | 61 / 206 |
| 服务端耗时 | 430 – 500 ms | 2 – 12 ms |
| 索引体积 | — | 5.52 MB（表 23.1 MB） |

数字取自 MySQL `SHOW PROFILES`（服务端执行时间，不含客户端进程启动）。
首次执行含冷页读取会明显更慢，表里取的是稳定值。

---

## 许可

本项目仅用于学习与个人作品展示。课程原始素材版权归原作者所有。
