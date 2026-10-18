-- ============================================================
-- 性能验证用种子数据（独立库，不碰业务库 sky_take_out）
--
-- 目的：验证 orders 表在 (status, order_time) 上的索引
--       对定时任务两条查询的影响。
--
-- 用法：
--   mysql --defaults-extra-file=<凭据文件> --default-character-set=utf8mb4 < perf/seed_perf_data.sql
--
-- 数据设计（20 万单）：
--   - 状态分布贴近真实外卖：已完成 60%、已取消 15%、派送中 10%、
--     已接单 6%、待接单 6%、待付款 3%
--   - 待付款订单绝大多数落在最近 15 分钟内（会被超时任务捞走的是极少数）
--   - 派送中订单绝大多数落在最近 60 分钟内
--   - 其余状态的下单时间均匀撒在最近 90 天
--   这样两条定时任务的查询返回行数很小，符合生产实际；
--   而没有索引时，MySQL 仍必须扫完整张表才能找到这几行。
-- ============================================================

DROP DATABASE IF EXISTS sky_take_out_perf;
CREATE DATABASE sky_take_out_perf DEFAULT CHARSET = utf8mb3 COLLATE = utf8mb3_bin;
USE sky_take_out_perf;

-- 表结构直接复制业务库（索引也会一起复制；当前只有主键，正好用来做"加索引前"的基线）
CREATE TABLE orders LIKE sky_take_out.orders;
CREATE TABLE order_detail LIKE sky_take_out.order_detail;

-- 用 6 张 0~9 的小表交叉连接生成 1~200000 的序号，比存储过程逐行插入快得多
-- 注意：这里不能用 TEMPORARY 表 —— MySQL 不允许在同一条语句里多次引用同一张临时表
CREATE TABLE digits (d INT);
INSERT INTO digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

INSERT INTO orders
    (number, status, user_id, address_book_id, order_time,
     pay_method, pay_status, amount, delivery_status, tableware_status)
SELECT
    CONCAT('PERF', LPAD(t.n, 10, '0')),
    t.st,
    1 + (t.n % 5000),
    1 + (t.n % 5000),
    CASE
        -- 极少数"漏网"的待付款/派送中订单：时间已经超过阈值，会被定时任务捞到
        WHEN t.st IN (1, 4) AND t.n % 97 = 0
            THEN DATE_SUB(NOW(), INTERVAL (120 + t.n % 600) MINUTE)
        -- 待付款订单：绝大多数在最近 15 分钟内
        WHEN t.st = 1 THEN DATE_SUB(NOW(), INTERVAL (t.n % 14) MINUTE)
        -- 派送中订单：绝大多数在最近 60 分钟内
        WHEN t.st = 4 THEN DATE_SUB(NOW(), INTERVAL (t.n % 59) MINUTE)
        -- 其余：90 天内均匀分布
        ELSE DATE_SUB(NOW(), INTERVAL (t.n % 7776000) SECOND)
    END,
    1,
    IF(t.st = 1, 0, 1),
    ROUND(20 + (t.n % 10000) / 100, 2),
    1,
    1
FROM (
    SELECT n,
           CASE
               WHEN n % 100 < 3  THEN 1
               WHEN n % 100 < 9  THEN 2
               WHEN n % 100 < 15 THEN 3
               WHEN n % 100 < 25 THEN 4
               WHEN n % 100 < 85 THEN 5
               ELSE 6
           END AS st
    FROM (
        SELECT a.d * 100000 + b.d * 10000 + c.d * 1000
             + d.d * 100 + e.d * 10 + f.d AS n
        FROM digits a, digits b, digits c, digits d, digits e, digits f
        WHERE a.d * 100000 + b.d * 10000 + c.d * 1000
            + d.d * 100 + e.d * 10 + f.d BETWEEN 1 AND 200000
    ) s
) t;

SELECT 'orders 行数' AS 指标, COUNT(*) AS 值 FROM orders;
SELECT status AS 状态, COUNT(*) AS 行数 FROM orders GROUP BY status ORDER BY status;

-- 辅助表用完即删
DROP TABLE digits;
