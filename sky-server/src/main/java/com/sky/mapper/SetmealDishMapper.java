package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealDishMapper {
    /**
     * 批量插入套餐与菜品的关联关系
     * <p>
     * 显式写 @Param：XML 里的 &lt;foreach collection="setmealDishes"&gt; 才不依赖
     * "编译保留参数名 + MyBatis 自动包装单集合参数"这两个隐含条件。
     *
     * @param setmealDishes 套餐菜品关系列表
     */
    void insertBatch(@Param("setmealDishes") List<SetmealDish> setmealDishes);

    @Select("SELECT * FROM sky_take_out.setmeal_dish WHERE setmeal_id =#{setmealId}")
    List<SetmealDish> getBySetmealId(Long id);

    @Delete("delete from setmeal_dish where setmeal_id=#{setmealId}")
    void deleteBySetmealId(Long id);
}

