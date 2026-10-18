package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.enumeration.OrderAction;
import com.sky.mapper.OrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderStatusUpdater 的单元测试。
 *
 * <p>这里验证的是「规则校验 + 条件更新」这两步的配合：
 * 规则不允许时连 SQL 都不该发；规则允许但影响 0 行，必须返回失败。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单状态更新器")
class OrderStatusUpdaterTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderStatusUpdater orderStatusUpdater;

    @Test
    @DisplayName("规则不允许：直接返回 false，完全不碰数据库")
    void disallowedByRule() {
        Orders update = Orders.builder().id(1L).build();

        assertFalse(orderStatusUpdater.change(update, Orders.COMPLETED, OrderAction.CONFIRM));

        verifyNoInteractions(orderMapper);
        assertNull(update.getStatus(), "校验失败时不应写入目标状态");
    }

    @Test
    @DisplayName("规则允许且更新到行：写入目标状态并返回 true")
    void allowedAndApplied() {
        Orders update = Orders.builder().id(1L).build();
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1);

        assertTrue(orderStatusUpdater.change(update, Orders.PENDING_PAYMENT, OrderAction.PAY));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).updateStatus(captor.capture(), eq(Orders.PENDING_PAYMENT));
        assertEquals(Orders.TO_BE_CONFIRMED, captor.getValue().getStatus());
        assertEquals(1L, captor.getValue().getId());
    }

    @Test
    @DisplayName("并发：条件更新影响 0 行，说明状态已被别人改掉，返回 false")
    void concurrentlyChanged() {
        Orders update = Orders.builder().id(1L).build();
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(0);

        assertFalse(orderStatusUpdater.change(update, Orders.PENDING_PAYMENT, OrderAction.PAY));
    }

    @Test
    @DisplayName("缺少订单 id 属于编程错误，直接抛 IllegalArgumentException")
    void missingId() {
        assertThrows(IllegalArgumentException.class,
                () -> orderStatusUpdater.change(new Orders(), Orders.PENDING_PAYMENT, OrderAction.PAY));
        verifyNoInteractions(orderMapper);
    }
}
