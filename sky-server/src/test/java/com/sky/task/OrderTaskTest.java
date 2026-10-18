package com.sky.task;

import com.sky.entity.Orders;
import com.sky.enumeration.OrderAction;
import com.sky.mapper.OrderMapper;
import com.sky.service.impl.OrderStatusUpdater;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务的单元测试。
 *
 * <p>重点不是「有没有调用更新」，而是「同一批里有一单状态已被并发改掉时，
 * 后面的订单还能不能继续处理」。这曾经是真 bug：原实现无条件 update，
 * 用户卡在第 15 分钟付款，订单会被改回已取消。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单定时任务")
class OrderTaskTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderStatusUpdater orderStatusUpdater;

    @InjectMocks
    private OrderTask orderTask;

    @Test
    @DisplayName("超时取消：第一单刚支付（条件更新失败）时跳过，第二单照常取消")
    void timeoutTaskSkipsOrderPaidAtLastMoment() {
        Orders justPaid = Orders.builder().id(1L).status(Orders.PENDING_PAYMENT).build();
        Orders timeout = Orders.builder().id(2L).status(Orders.PENDING_PAYMENT).build();
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any()))
                .thenReturn(Arrays.asList(justPaid, timeout));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.PENDING_PAYMENT), eq(OrderAction.TIMEOUT_CANCEL)))
                .thenReturn(false, true);

        assertDoesNotThrow(() -> orderTask.processTimeOutOrder());

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderStatusUpdater, times(2))
                .change(captor.capture(), eq(Orders.PENDING_PAYMENT), eq(OrderAction.TIMEOUT_CANCEL));

        List<Long> ids = captor.getAllValues().stream().map(Orders::getId).collect(Collectors.toList());
        assertEquals(Arrays.asList(1L, 2L), ids, "一单失败不能中断整批");
    }

    @Test
    @DisplayName("超时取消：没有订单时什么也不做")
    void timeoutTaskWithNoOrders() {
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.PENDING_PAYMENT), any()))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> orderTask.processTimeOutOrder());
        verify(orderStatusUpdater, never()).change(any(), any(), any());
    }

    @Test
    @DisplayName("自动完成：走 AUTO_COMPLETE，用条件更新而不是无条件 update")
    void deliveryTaskUsesConditionalUpdate() {
        Orders order = Orders.builder().id(3L).status(Orders.DELIVERY_IN_PROGRESS).build();
        when(orderMapper.getByStatusAndOrderTime(eq(Orders.DELIVERY_IN_PROGRESS), any()))
                .thenReturn(Collections.singletonList(order));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.DELIVERY_IN_PROGRESS), eq(OrderAction.AUTO_COMPLETE)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderTask.processDeliveryOrder());

        verify(orderStatusUpdater)
                .change(any(Orders.class), eq(Orders.DELIVERY_IN_PROGRESS), eq(OrderAction.AUTO_COMPLETE));
    }
}
