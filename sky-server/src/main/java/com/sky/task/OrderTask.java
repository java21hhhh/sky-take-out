package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    @Scheduled(cron = "0 * * * * *")
    public void processTimeOutOrder() {
        log.info("定时处理超时订单：{}", LocalDateTime.now());

        LocalDateTime time =LocalDateTime.now().plusMinutes(-15);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT,time);

        if(!ordersList.isEmpty()){
            for(Orders orders:ordersList){
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }

    }
    /**
     * 处理派送中的订单：超过 60 分钟仍未完成的，自动置为已完成。
     *
     * <p>★ 这里改过一处 ★
     * 原来的 cron 是 {@code 0 0 1 * * ?} —— <b>每天凌晨 1 点只跑一次</b>。
     * 但下面的判断条件写的是「派送超过 60 分钟」。<b>这两个数字对不上：</b>
     * 下午 13:30 下单、14:00 开始派送的单子，要等到【第二天凌晨 1 点】
     * 才会被标记成已完成 —— 中间十几个小时，订单状态一直是错的，
     * 用户端和商家端都会看到一个"派送中"的假状态。
     *
     * <p>改成每 5 分钟跑一次之后，「超过 60 分钟」才真的是 60 分钟
     * （最坏情况滞后 5 分钟，而不是滞后一整天）。
     *
     * <p><b>代价明说：</b>查询次数从每天 1 次变成每天 288 次。
     * 这是刻意的取舍 —— 一条按 status + order_time 的查询很便宜，
     * 而订单状态错了是两个端都看得见的体验问题。
     * （真要在"扫表"和"实时"之间两全，正解是换成延迟队列，
     * 让"到点"由消息驱动、而不是靠轮询去"发现"。留作后续优化。）
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void processDeliveryOrder(){
        log.info("定时处理派送中订单：{}", LocalDateTime.now());

        LocalDateTime time =LocalDateTime.now().plusMinutes(-60);

        List<Orders> ordersList =orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS,time);

        if (!ordersList.isEmpty()){
            for(Orders orders:ordersList){
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }
}
