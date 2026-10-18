package com.sky.service.impl;

import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.Orders;
import com.sky.enumeration.OrderAction;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * OrderServiceImpl 的单元测试。
 *
 * <p>这里验证的是「每个业务动作都交给迁移表处理」以及「更新失败要报错」，
 * 不重复测迁移规则本身（那在 OrderActionTest 里）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单服务")
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private AddressBookMapper addressBookMapper;
    @Mock
    private ShoppingCartMapper shoppingCartMapper;
    @Mock
    private WebSocketServer webSocketServer;
    @Mock
    private OrderStatusUpdater orderStatusUpdater;

    @InjectMocks
    private OrderServiceImpl orderService;

    // ---------- 商家取消 ----------

    @Test
    @DisplayName("商家取消：已完成的订单被拒绝，不执行更新")
    void cancelCompletedRejected() {
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.COMPLETED));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.COMPLETED), eq(OrderAction.CANCEL_BY_ADMIN)))
                .thenReturn(false);

        OrdersCancelDTO dto = cancelDto(1L);

        assertThrows(OrderBusinessException.class, () -> orderService.cancel(dto));
        verify(orderStatusUpdater).change(any(Orders.class), eq(Orders.COMPLETED), eq(OrderAction.CANCEL_BY_ADMIN));
    }

    @Test
    @DisplayName("商家取消：待接单的订单可以取消")
    void cancelToBeConfirmed() {
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.TO_BE_CONFIRMED));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.CANCEL_BY_ADMIN)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.cancel(cancelDto(1L)));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderStatusUpdater).change(captor.capture(), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.CANCEL_BY_ADMIN));
        assertEquals(1L, captor.getValue().getId());
        assertEquals("商家取消", captor.getValue().getCancelReason());
    }

    @Test
    @DisplayName("商家取消：订单不存在时直接报错")
    void cancelNotFound() {
        OrdersCancelDTO dto = cancelDto(99L);

        assertThrows(OrderBusinessException.class, () -> orderService.cancel(dto));
        verifyNoInteractions(orderStatusUpdater);
    }

    // ---------- 其余状态流转的动作映射 ----------

    @Test
    @DisplayName("接单：交给 CONFIRM，只有待接单可以接")
    void confirmUsesConfirmAction() {
        OrdersConfirmDTO dto = new OrdersConfirmDTO();
        dto.setId(1L);
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.TO_BE_CONFIRMED));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.CONFIRM)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.confirm(dto));
        verify(orderStatusUpdater).change(any(Orders.class), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.CONFIRM));
    }

    @Test
    @DisplayName("拒单：交给 REJECT，只有待接单可以拒")
    void rejectionUsesRejectAction() {
        OrdersRejectionDTO dto = new OrdersRejectionDTO();
        dto.setId(1L);
        dto.setRejectionReason("没食材了");
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.TO_BE_CONFIRMED));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.REJECT)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.rejection(dto));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderStatusUpdater).change(captor.capture(), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.REJECT));
        assertEquals("没食材了", captor.getValue().getRejectionReason());
    }

    @Test
    @DisplayName("派送：交给 DELIVER，只有已接单可以派送")
    void deliveryUsesDeliverAction() {
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.CONFIRMED));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.CONFIRMED), eq(OrderAction.DELIVER)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.delivery(1L));
        verify(orderStatusUpdater).change(any(Orders.class), eq(Orders.CONFIRMED), eq(OrderAction.DELIVER));
    }

    @Test
    @DisplayName("完成：交给 COMPLETE，只有派送中可以完成")
    void completeUsesCompleteAction() {
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.DELIVERY_IN_PROGRESS));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.DELIVERY_IN_PROGRESS), eq(OrderAction.COMPLETE)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.complete(1L));
        verify(orderStatusUpdater).change(any(Orders.class), eq(Orders.DELIVERY_IN_PROGRESS), eq(OrderAction.COMPLETE));
    }

    // ---------- 用户取消 ----------

    @Test
    @DisplayName("用户取消：已付款的待接单订单，支付状态置为退款")
    void userCancelPaidOrderRefunds() {
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.TO_BE_CONFIRMED, Orders.PAID));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.CANCEL_BY_USER)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.userCancelById(1L));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderStatusUpdater).change(captor.capture(), eq(Orders.TO_BE_CONFIRMED), eq(OrderAction.CANCEL_BY_USER));
        assertEquals(Orders.REFUND, captor.getValue().getPayStatus());
    }

    @Test
    @DisplayName("用户取消：未付款的待付款订单，不写 pay_status")
    void userCancelUnpaidOrderDoesNotRefund() {
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.PENDING_PAYMENT, Orders.UN_PAID));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.PENDING_PAYMENT), eq(OrderAction.CANCEL_BY_USER)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.userCancelById(1L));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderStatusUpdater).change(captor.capture(), eq(Orders.PENDING_PAYMENT), eq(OrderAction.CANCEL_BY_USER));
        assertNull(captor.getValue().getPayStatus(), "没付过款就不该动支付状态");
    }

    @Test
    @DisplayName("用户取消：已接单的订单不能由用户取消")
    void userCancelConfirmedRejected() {
        when(orderMapper.getById(1L)).thenReturn(order(1L, Orders.CONFIRMED));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.CONFIRMED), eq(OrderAction.CANCEL_BY_USER)))
                .thenReturn(false);

        assertThrows(OrderBusinessException.class, () -> orderService.userCancelById(1L));
    }

    // ---------- 支付 ----------

    @Test
    @DisplayName("支付回调幂等：已支付的订单直接返回，不重复更新、不重复提醒")
    void paySuccessIsIdempotent() {
        when(orderMapper.getByNumberAndUserId(eq("N1"), any())).thenReturn(order(1L, Orders.TO_BE_CONFIRMED, Orders.PAID));

        assertDoesNotThrow(() -> orderService.paySuccess("N1"));

        verifyNoInteractions(orderStatusUpdater);
        verifyNoInteractions(webSocketServer);
    }

    @Test
    @DisplayName("支付成功：走 PAY 迁移，成功后才推来单提醒")
    void paySuccessUpdatesThenNotifies() {
        when(orderMapper.getByNumberAndUserId(eq("N1"), any())).thenReturn(order(1L, Orders.PENDING_PAYMENT, Orders.UN_PAID));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.PENDING_PAYMENT), eq(OrderAction.PAY)))
                .thenReturn(true);

        assertDoesNotThrow(() -> orderService.paySuccess("N1"));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderStatusUpdater).change(captor.capture(), eq(Orders.PENDING_PAYMENT), eq(OrderAction.PAY));
        assertEquals(Orders.PAID, captor.getValue().getPayStatus());
        verify(webSocketServer).sendToAllClient(anyString());
    }

    @Test
    @DisplayName("支付失败（订单已被取消）：报错且不推提醒")
    void paySuccessRejectedWhenStatusChanged() {
        when(orderMapper.getByNumberAndUserId(eq("N1"), any())).thenReturn(order(1L, Orders.CANCELLED, Orders.UN_PAID));
        when(orderStatusUpdater.change(any(Orders.class), eq(Orders.CANCELLED), eq(OrderAction.PAY)))
                .thenReturn(false);

        assertThrows(OrderBusinessException.class, () -> orderService.paySuccess("N1"));
        verify(webSocketServer, never()).sendToAllClient(anyString());
    }

    // ---------- 测试数据 ----------

    private static Orders order(Long id, Integer status) {
        return Orders.builder().id(id).status(status).build();
    }

    private static Orders order(Long id, Integer status, Integer payStatus) {
        return Orders.builder().id(id).status(status).payStatus(payStatus).build();
    }

    private static OrdersCancelDTO cancelDto(Long id) {
        OrdersCancelDTO dto = new OrdersCancelDTO();
        dto.setId(id);
        dto.setCancelReason("商家取消");
        return dto;
    }
}
