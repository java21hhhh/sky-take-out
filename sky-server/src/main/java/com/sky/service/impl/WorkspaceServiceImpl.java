package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.entity.Orders;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 统计指定区间的运营数据
     * <p>
     * 口径：营业额和有效订单数都只算「已完成」的订单；完成率 = 有效订单数 / 总订单数；
     * 客单价 = 营业额 / 有效订单数。分母为 0 时一律返回 0.0，避免除 0
     *
     * @param begin 起始时间（含）
     * @param end   截止时间（含）
     */
    @Override
    public BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end) {
        //1. 总订单数：只按时间过滤，不带状态条件
        Map<String, Object> countMap = new HashMap<>();
        countMap.put("begin", begin);
        countMap.put("end", end);
        Integer totalOrderCount = orderMapper.countByMap(countMap);

        //2. 营业额 + 有效订单数：在同一个 map 上补一个 status 就够了，两个查询只差这一个条件
        Map<String, Object> completedMap = new HashMap<>();
        completedMap.put("begin", begin);
        completedMap.put("end", end);
        completedMap.put("status", Orders.COMPLETED);
        // 当天没有已完成订单时 sum() 返回 NULL，SQL 里已用 ifnull 兜成 0，不会是 null
        Double turnover = orderMapper.sumByMap(completedMap);
        Integer validOrderCount = orderMapper.countByMap(completedMap);

        //3. 新增用户：单独用一个 map，不要复用上面那个 ——
        //   它带着 status，而 user 表根本没有 status 列，复用了只是「侥幸能跑」，读代码的人会一头雾水
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("begin", begin);
        userMap.put("end", end);
        Integer newUsers = userMapper.countByMap(userMap);

        //4. 完成率和客单价：分子分母都得判，totalOrderCount 为 0 时算完成率会除 0
        Double orderCompletionRate = 0.0;
        Double unitPrice = 0.0;
        if (totalOrderCount != 0 && validOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
            unitPrice = turnover / validOrderCount;
        }

        return BusinessDataVO.builder()
                .turnover(turnover)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .newUsers(newUsers)
                .build();
    }

    /**
     * 订单总览：工作台这个卡片看的是「今天」
     */
    @Override
    public OrderOverViewVO getOrderOverView() {
        LocalDate today = LocalDate.now();
        // 时间条件只放一次，后面反复改 status 复用同一个 map
        Map<String, Object> map = new HashMap<>();
        map.put("begin", LocalDateTime.of(today, LocalTime.MIN));
        map.put("end", LocalDateTime.of(today, LocalTime.MAX));

        map.put("status", Orders.TO_BE_CONFIRMED);
        Integer waitingOrders = orderMapper.countByMap(map);

        map.put("status", Orders.CONFIRMED);
        Integer deliveredOrders = orderMapper.countByMap(map);

        map.put("status", Orders.COMPLETED);
        Integer completedOrders = orderMapper.countByMap(map);

        map.put("status", Orders.CANCELLED);
        Integer cancelledOrders = orderMapper.countByMap(map);

        // 全部订单：把 status 去掉（键还在、值是 null，XML 里的 <if test="status != null"> 会跳过它）
        map.put("status", null);
        Integer allOrders = orderMapper.countByMap(map);

        return OrderOverViewVO.builder()
                .waitingOrders(waitingOrders)
                .deliveredOrders(deliveredOrders)
                .completedOrders(completedOrders)
                .cancelledOrders(cancelledOrders)
                .allOrders(allOrders)
                .build();
    }

    @Override
    public DishOverViewVO getDishOverView() {
        Map<String, Object> map = new HashMap<>();

        map.put("status", StatusConstant.ENABLE);
        Integer sold = dishMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = dishMapper.countByMap(map);

        return DishOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }

    @Override
    public SetmealOverViewVO getSetmealOverView() {
        Map<String, Object> map = new HashMap<>();

        map.put("status", StatusConstant.ENABLE);
        Integer sold = setmealMapper.countByMap(map);

        map.put("status", StatusConstant.DISABLE);
        Integer discontinued = setmealMapper.countByMap(map);

        return SetmealOverViewVO.builder()
                .sold(sold)
                .discontinued(discontinued)
                .build();
    }
}
