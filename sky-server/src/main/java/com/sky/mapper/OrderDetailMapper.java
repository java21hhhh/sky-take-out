package com.sky.mapper;


import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {

    /**
     * 批量插入订单明细
     * <p>
     * 显式写 @Param：XML 里的 &lt;foreach collection="orderDetails"&gt; 才不依赖
     * "编译保留参数名 + MyBatis 自动包装单集合参数"这两个隐含条件。
     *
     * @param orderDetails 订单明细列表
     */
    void insertBatch(@Param("orderDetails") List<OrderDetail> orderDetails);

    /**
     * 根据订单id查询订单明细
     *
     * @param orderId 订单id
     * @return 订单明细列表
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

}
