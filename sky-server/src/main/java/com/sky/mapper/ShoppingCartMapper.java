package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 批量插入购物车（「再来一单」把订单明细写回购物车时用）
     * <p>
     * 显式写 @Param：XML 里的 &lt;foreach collection="shoppingCarts"&gt; 才不依赖
     * "编译保留参数名 + MyBatis 自动包装单集合参数"这两个隐含条件。
     *
     * @param shoppingCarts 购物车条目列表
     */
    void insertBatch(@Param("shoppingCarts") List<ShoppingCart> shoppingCarts);

    @Update("update shopping_cart set number =#{number} where id =#{id}")
    void updateNumberById(ShoppingCart cart);
    @Insert("insert into shopping_cart " +
            "(name, image, user_id, dish_id, setmeal_id, dish_flavor, number, amount, create_time) " +
            "values (#{name}, #{image}, #{userId}, #{dishId}, #{setmealId}, " +
            "#{dishFlavor}, #{number}, #{amount}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    @Delete("delete from shopping_cart where user_id =#{userId}")
    void deleteByUserId(Long userId);

    @Delete("delete from shopping_cart where id = #{id}")
    void deleteById(Long id);

}
