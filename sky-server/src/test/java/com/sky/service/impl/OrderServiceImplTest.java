package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.enumeration.OrderAction;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.vo.OrderSubmitVO;
import com.sky.websocket.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    private DishMapper dishMapper;
    @Mock
    private SetmealMapper setmealMapper;
    @Mock
    private WebSocketServer webSocketServer;
    @Mock
    private OrderStatusUpdater orderStatusUpdater;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        // 下单 / 支付都会从 ThreadLocal 取当前用户 id
        BaseContext.setCurrentId(1L);
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

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

    // ---------- 下单 ----------

    @Test
    @DisplayName("下单：金额由服务端按当前价重算，客户端传来的 amount 被忽略")
    void submitOrderComputesAmountServerSide() {
        when(addressBookMapper.getById(1L)).thenReturn(addressBook());
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(cartWithDish(10L, "10.00", 2)));
        when(dishMapper.getById(10L))
                .thenReturn(dish(10L, "宫保鸡丁", "10.00", StatusConstant.ENABLE));
        stubInsertBackfillId();

        OrderSubmitVO vo = orderService.submitOrder(submitDto(3));

        // 客户端在 DTO 里传的是 0.01；服务端应重算为 10.00 × 2 + 打包费 3 = 23.00
        ArgumentCaptor<Orders> orderCaptor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(orderCaptor.capture());
        assertEquals(new BigDecimal("23.00"), orderCaptor.getValue().getAmount());
        assertEquals(new BigDecimal("23.00"), vo.getOrderAmount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<OrderDetail>> detailCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderDetailMapper).insertBatch(detailCaptor.capture());
        OrderDetail detail = detailCaptor.getValue().get(0);
        assertEquals(99L, detail.getOrderId());
        assertEquals("宫保鸡丁", detail.getName(), "名称应刷新为数据库当前值");
        assertEquals(new BigDecimal("10.00"), detail.getAmount());
        assertEquals(Integer.valueOf(2), detail.getNumber());

        verify(shoppingCartMapper).deleteByUserId(1L);
    }

    @Test
    @DisplayName("下单：购物车价格与数据库不一致时拒绝，不落订单")
    void submitOrderRejectsPriceChange() {
        when(addressBookMapper.getById(1L)).thenReturn(addressBook());
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(cartWithDish(10L, "10.00", 1)));
        when(dishMapper.getById(10L))
                .thenReturn(dish(10L, "宫保鸡丁", "12.00", StatusConstant.ENABLE));

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(submitDto(0)));

        verify(orderMapper, never()).insert(any(Orders.class));
        verify(orderDetailMapper, never()).insertBatch(any());
        verify(shoppingCartMapper, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("下单：菜品已停售时拒绝")
    void submitOrderRejectsDisabledDish() {
        when(addressBookMapper.getById(1L)).thenReturn(addressBook());
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(cartWithDish(10L, "10.00", 1)));
        when(dishMapper.getById(10L))
                .thenReturn(dish(10L, "宫保鸡丁", "10.00", StatusConstant.DISABLE));

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(submitDto(0)));
        verify(orderMapper, never()).insert(any(Orders.class));
    }

    @Test
    @DisplayName("下单：套餐已停售时拒绝")
    void submitOrderRejectsDisabledSetmeal() {
        when(addressBookMapper.getById(1L)).thenReturn(addressBook());
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(cartWithSetmeal(20L, "20.00", 1)));
        when(setmealMapper.getById(20L))
                .thenReturn(Setmeal.builder().id(20L).name("双人套餐")
                        .price(new BigDecimal("20.00")).status(StatusConstant.DISABLE).build());

        assertThrows(OrderBusinessException.class, () -> orderService.submitOrder(submitDto(0)));
        verify(orderMapper, never()).insert(any(Orders.class));
    }

    @Test
    @DisplayName("下单：套餐正常时按套餐当前价计算")
    void submitOrderSetmealUsesCurrentPrice() {
        when(addressBookMapper.getById(1L)).thenReturn(addressBook());
        when(shoppingCartMapper.list(any(ShoppingCart.class)))
                .thenReturn(Collections.singletonList(cartWithSetmeal(20L, "20.00", 1)));
        when(setmealMapper.getById(20L))
                .thenReturn(Setmeal.builder().id(20L).name("双人套餐")
                        .price(new BigDecimal("20.00")).status(StatusConstant.ENABLE).build());
        stubInsertBackfillId();

        orderService.submitOrder(submitDto(2));

        ArgumentCaptor<Orders> captor = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).insert(captor.capture());
        assertEquals(new BigDecimal("22.00"), captor.getValue().getAmount(), "20.00 × 1 + 2");
    }

    // ---------- 测试数据 ----------

    private static AddressBook addressBook() {
        return AddressBook.builder()
                .id(1L).userId(1L).consignee("张三").phone("13800000000").detail("某地址")
                .build();
    }

    private static OrdersSubmitDTO submitDto(int packAmount) {
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(1L);
        dto.setPayMethod(1);
        dto.setPackAmount(packAmount);
        dto.setTablewareNumber(1);
        dto.setDeliveryStatus(1);
        dto.setTablewareStatus(1);
        dto.setAmount(new BigDecimal("0.01"));   // 客户端故意传一个假金额
        return dto;
    }

    private static ShoppingCart cartWithDish(Long dishId, String amount, int number) {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(100L);
        cart.setUserId(1L);
        cart.setDishId(dishId);
        cart.setName("旧菜名");
        cart.setImage("old.png");
        cart.setAmount(new BigDecimal(amount));
        cart.setNumber(number);
        return cart;
    }

    private static ShoppingCart cartWithSetmeal(Long setmealId, String amount, int number) {
        ShoppingCart cart = new ShoppingCart();
        cart.setId(101L);
        cart.setUserId(1L);
        cart.setSetmealId(setmealId);
        cart.setName("旧套餐名");
        cart.setImage("old.png");
        cart.setAmount(new BigDecimal(amount));
        cart.setNumber(number);
        return cart;
    }

    private static Dish dish(Long id, String name, String price, Integer status) {
        return Dish.builder().id(id).name(name).image("new.png")
                .price(new BigDecimal(price)).status(status).build();
    }

    /** 模拟 useGeneratedKeys 回填主键 */
    private void stubInsertBackfillId() {
        doAnswer(invocation -> {
            Orders order = invocation.getArgument(0);
            order.setId(99L);
            return null;
        }).when(orderMapper).insert(any(Orders.class));
    }

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
