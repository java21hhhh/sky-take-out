package com.sky.controller.admin;


import com.sky.constant.RedisConstant;
import com.sky.constant.StatusConstant;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/admin/shop")
@RestController("adminShopController")
@Slf4j
public class ShopController {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    //设置店铺的营业状态
    @PutMapping("/{status}")
    public Result setStatus(@PathVariable Integer status){
        log.info("设置营业状态：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "已打烊");
        redisTemplate.opsForValue().set(RedisConstant.SHOP_STATUS, status);
        return Result.success();
    }

    //获取店铺的营业状态(Redis 中未设置过则默认 DISABLE=0 已打烊,避免返回 null 触发 NPE)
    @GetMapping("/status")
    public Result<Integer> getStatus(){
        Object value = redisTemplate.opsForValue().get(RedisConstant.SHOP_STATUS);
        Integer status = value instanceof Integer ? (Integer) value : StatusConstant.DISABLE;
        log.info("获取营业状态：{}", StatusConstant.ENABLE.equals(status) ? "营业中" : "已打烊");
        return Result.success(status);
    }
}
