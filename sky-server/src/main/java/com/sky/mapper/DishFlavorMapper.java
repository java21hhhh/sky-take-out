package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DishFlavorMapper {
    void insertBacth(List<DishFlavor> flavors);

    /**
     * 根据菜品id批量删除口味
     * @param dishIds
     */
    void deleteByDishIds(List<Long> dishIds);

    /**
     * 根据菜品id查询口味列表
     * @param dishId
     * @return
     */
    List<DishFlavor> getByDishId(Long dishId);
}
