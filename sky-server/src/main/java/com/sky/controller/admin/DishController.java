package com.sky.controller.admin;

import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RequestMapping
@RestController
@Slf4j
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/admin/dish")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}", dishDTO);
        dishService.saveWithFlavor(dishDTO);

        //清理该分类下的菜品缓存
        cleanCache("dish_" + dishDTO.getCategoryId());
        return Result.success();
    }
    @GetMapping("/admin/dish/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据分类id查询菜品（新增套餐页面「选择菜品」用）
     * <p>
     * 注意路径必须写全：本类没有类级 @RequestMapping，若缺这个方法，
     * GET /admin/dish/list 会落到下面的 /admin/dish/{id} 上，把 "list" 当成 id 转 Long 而报错。
     */
    @GetMapping("/admin/dish/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("根据分类id查询菜品：{}", categoryId);
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE); //只查起售中的菜品
        List<DishVO> list = dishService.listWithFlavor(dish);
        return Result.success(list);
    }

    @DeleteMapping("/admin/dish")
    @ApiOperation("菜品删除")
    public Result delete(@RequestParam List<Long> ids) {
        log.info("菜品删除：{}", ids);
        dishService.deleteBatch(ids);

        //清理所有菜品缓存
        cleanCache("dish_*");
        return Result.success();
    }
@GetMapping("/admin/dish/{id}")
    @ApiOperation("根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("根据id查询菜品：{}",id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    @PutMapping("/admin/dish")
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        //清理所有菜品缓存
        cleanCache("dish_*");
        return Result.success();
    }

    @PostMapping("/admin/dish/status/{status}")
    @ApiOperation("启用禁用菜品")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("启用禁用菜品：{}, {}", status, id);
        dishService.startOrStop(status, id);

        //清理所有菜品缓存
        cleanCache("dish_*");
        return Result.success();
    }

    /**
     * 清理菜品缓存数据
     *
     * @param pattern 要匹配的 key 模式，如 dish_*（全部）或 dish_分类id（单个分类）
     */
    private void cleanCache(String pattern) {
        Set<String> keys = scanKeys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 按模式找出所有 key —— 用 SCAN 游标，而不是 KEYS。
     *
     * <p>★ 这里改过一处 ★
     * 原来用的是 {@code redisTemplate.keys(pattern)}，它对应 Redis 的 <b>KEYS</b> 命令。
     * KEYS 有两个致命点：
     * <ul>
     *   <li><b>O(N)</b> —— 要遍历整个 keyspace 才凑齐匹配项，key 越多越慢；</li>
     *   <li><b>阻塞</b> —— Redis 执行命令是<b>单线程</b>的，KEYS 跑的时候别的命令全部排队。
     *       线上几十上百万个 key，一句 {@code KEYS dish_*} 就能把整个 Redis 卡住好几秒，
     *       所有依赖 Redis 的接口跟着一起超时。</li>
     * </ul>
     * 所以 KEYS 在生产上属于明令禁止的命令（Redis 官方文档的原话就是：不要在生产环境使用）。
     *
     * <p><b>SCAN 好在哪：</b>游标式分批遍历 —— 每次只返回一小批（count 是"建议值"，
     * Redis 可能多给也可能少给），拿到游标下次接着扫。它把一次长阻塞
     * 拆成很多次短查询，Redis 有机会在批与批之间处理别的命令。
     *
     * <p><b>代价（面试会问）：</b>SCAN 不保证"一次扫完"——
     * 遍历期间被新增/删除的 key，可能漏掉、也可能重复返回。
     * 对"清缓存"这个场景无所谓（缓存本来就是清掉让它重建的）；
     * 但如果拿去干"统计"类的活，就得自己处理重复和漏读。
     */
    private Set<String> scanKeys(String pattern) {
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(200)          // 每批约 200 个（只是建议值，Redis 不保证）
                .build();

        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new HashSet<>();
            // Cursor 实现了 Closeable，用完必须关 —— 它背后挂着一个 redis 连接
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    // key 用的是 StringRedisSerializer（见 RedisConfiguration），
                    // 所以 byte[] 直接按 UTF-8 还原即可
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }
}




























