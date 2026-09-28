package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;

public interface OrderService {



    /**
     * 用户下单
     *
     * @param ordersSubmitDTO 下单参数
     * @return 下单结果（订单id、订单号、金额、下单时间）
     */
    OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO 支付参数
     * @return 预支付交易单参数
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO);

    /**
     * 支付成功，修改订单状态
     * <p>
     * 当前由 payment 直接调用（模拟支付）；将来接入真实微信支付后，
     * 改由支付回调 /notify/paySuccess 调用即可，无需改动本方法。
     *
     * @param outTradeNo 商户订单号
     */
    void paySuccess(String outTradeNo);

    /**
     * 用户端历史订单分页查询
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   订单状态，可为 null
     * @return 分页结果
     */
    PageResult pageQuery4User(int pageNum, int pageSize, Integer status);

    /**
     * 查询订单详情
     *
     * @param id 订单id
     * @return 订单及其明细
     */
    OrderVO details(Long id);

    /**
     * 用户取消订单
     *
     * @param id 订单id
     */
    void userCancelById(Long id);

    /**
     * 再来一单：把该订单的菜品重新加回购物车
     *
     * @param id 订单id
     */
    void repetition(Long id);

    //商家端取消
    void cancel(OrdersCancelDTO ordersCancelDTO) ;
    //统计各种状态的订单
    OrderStatisticsVO statistics();

    void complete(Long id);

    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    void confirm(OrdersConfirmDTO ordersConfirmDTO);

    void delivery(Long id);

    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    void reminder(Long id);
}
