package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    /**
     * 插入订单数据
     * <p>
     * SQL 在 OrderMapper.xml 里，带 useGeneratedKeys 回填主键
     *
     * @param orders 订单
     */
    void insert(Orders orders);

    /**
     * 修改订单信息（只更新传入的非空字段）
     *
     * @param orders 订单
     */
    void update(Orders orders);

    /**
     * 分页条件查询，按下单时间倒序
     *
     * @param ordersPageQueryDTO 查询条件
     * @return 分页结果
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据id查询订单
     *
     * @param id 订单id
     * @return 订单
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据订单号和用户id查询订单
     * <p>
     * 显式写 @Param：不依赖"编译保留参数名"这个隐含条件
     *
     * @param orderNumber 订单号
     * @param userId      用户id
     * @return 订单
     */
    @Select("select * from orders where number = #{orderNumber} and user_id = #{userId}")
    Orders getByNumberAndUserId(@Param("orderNumber") String orderNumber, @Param("userId") Long userId);

    @Select("select count(id) from orders where status =#{status}")
    Integer countStatus(Integer toBeConfirmed);

    @Select("select * from orders where status =#{status} and order_time <=#{time}")
    List<Orders> getByStatusAndOrderTime(Integer status, LocalDateTime time);

    /**
     *通过动态条件统计营业额
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 通过动态条件统计订单数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);

    /**
     * 统计指定时间区间内销量排名前十的商品
     * <p>
     * 显式写 @Param：不依赖"编译保留参数名"这个隐含条件（XML 里用 #{begin}/#{end}）
     *
     * @param begin 起始时间（含）
     * @param end   截止时间（含）
     * @return 商品名 + 销量，按销量倒序，最多 10 条
     */
    List<GoodsSalesDTO> getSalesTop10(@Param("begin") LocalDateTime begin, @Param("end") LocalDateTime end);
 }
