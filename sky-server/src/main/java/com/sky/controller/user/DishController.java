package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端-菜品浏览接口
 */
@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {

    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 根据分类id查询起售菜品(含口味)，带 Redis 缓存：key=dish_分类id
     *
     * @param categoryId 分类id
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询起售菜品")
    @SuppressWarnings("unchecked")
    public Result<List<DishVO>> list(Long categoryId) {
        log.info("C端根据分类id查询菜品，categoryId：{}", categoryId);

        //构造redis中的key，规则：dish_分类id
        String key = "dish_" + categoryId;

        //1. 先查redis缓存，命中直接返回，不查数据库
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Result.success((List<DishVO>) cached);
        }

        //2. 未命中：查询数据库（仅起售菜品），将结果写入redis
        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        List<DishVO> list = dishService.listWithFlavor(dish);
        redisTemplate.opsForValue().set(key, list);

        return Result.success(list);
    }
}
