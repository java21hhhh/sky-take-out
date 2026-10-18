package com.sky.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.sky.enumeration.OrderStatus.CANCELLED;
import static com.sky.enumeration.OrderStatus.COMPLETED;
import static com.sky.enumeration.OrderStatus.CONFIRMED;
import static com.sky.enumeration.OrderStatus.DELIVERY_IN_PROGRESS;
import static com.sky.enumeration.OrderStatus.PENDING_PAYMENT;
import static com.sky.enumeration.OrderStatus.TO_BE_CONFIRMED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态迁移表的单元测试。
 *
 * <p>这些用例的意义不是「覆盖率」，而是把业务规则钉死：
 * 以后谁改迁移表，只要碰坏了规则，这里立刻红。
 */
@DisplayName("订单状态迁移表")
class OrderActionTest {

    private static int code(OrderStatus status) {
        return status.getCode();
    }

    @Test
    @DisplayName("支付：只能从待付款出发，落到待接单")
    void pay() {
        assertTrue(OrderAction.PAY.canApply(code(PENDING_PAYMENT)));
        assertFalse(OrderAction.PAY.canApply(code(TO_BE_CONFIRMED)), "已付款的订单不能重复支付");
        assertFalse(OrderAction.PAY.canApply(code(CANCELLED)), "已取消的订单不能支付");
        assertEquals(code(TO_BE_CONFIRMED), OrderAction.PAY.targetCode());
    }

    @Test
    @DisplayName("接单 / 拒单：只能从待接单出发")
    void confirmAndReject() {
        assertTrue(OrderAction.CONFIRM.canApply(code(TO_BE_CONFIRMED)));
        assertTrue(OrderAction.REJECT.canApply(code(TO_BE_CONFIRMED)));

        assertFalse(OrderAction.CONFIRM.canApply(code(PENDING_PAYMENT)), "未付款不能接单");
        assertFalse(OrderAction.CONFIRM.canApply(code(CONFIRMED)), "不能重复接单");
        assertFalse(OrderAction.REJECT.canApply(code(CONFIRMED)), "已接单不能再拒单");

        assertEquals(code(CONFIRMED), OrderAction.CONFIRM.targetCode());
        assertEquals(code(CANCELLED), OrderAction.REJECT.targetCode());
    }

    @Test
    @DisplayName("派送：只能从已接单出发")
    void deliver() {
        assertTrue(OrderAction.DELIVER.canApply(code(CONFIRMED)));
        assertFalse(OrderAction.DELIVER.canApply(code(TO_BE_CONFIRMED)), "待接单必须先接单才能派送");
        assertFalse(OrderAction.DELIVER.canApply(code(DELIVERY_IN_PROGRESS)), "不能重复派送");
        assertEquals(code(DELIVERY_IN_PROGRESS), OrderAction.DELIVER.targetCode());
    }

    @Test
    @DisplayName("完成：只能从派送中出发")
    void complete() {
        assertTrue(OrderAction.COMPLETE.canApply(code(DELIVERY_IN_PROGRESS)));
        assertTrue(OrderAction.AUTO_COMPLETE.canApply(code(DELIVERY_IN_PROGRESS)));

        assertFalse(OrderAction.COMPLETE.canApply(code(CONFIRMED)), "已接单还没派送，不能完成");
        assertFalse(OrderAction.COMPLETE.canApply(code(COMPLETED)), "不能重复完成");
        assertEquals(code(COMPLETED), OrderAction.COMPLETE.targetCode());
        assertEquals(code(COMPLETED), OrderAction.AUTO_COMPLETE.targetCode());
    }

    @Test
    @DisplayName("管理员取消：待付款到派送中都可取消，已完成 / 已取消不可")
    void adminCancel() {
        assertTrue(OrderAction.CANCEL_BY_ADMIN.canApply(code(PENDING_PAYMENT)));
        assertTrue(OrderAction.CANCEL_BY_ADMIN.canApply(code(TO_BE_CONFIRMED)));
        assertTrue(OrderAction.CANCEL_BY_ADMIN.canApply(code(CONFIRMED)));
        assertTrue(OrderAction.CANCEL_BY_ADMIN.canApply(code(DELIVERY_IN_PROGRESS)));

        assertFalse(OrderAction.CANCEL_BY_ADMIN.canApply(code(COMPLETED)), "已完成的订单不能再取消");
        assertFalse(OrderAction.CANCEL_BY_ADMIN.canApply(code(CANCELLED)), "不能重复取消");
        assertEquals(code(CANCELLED), OrderAction.CANCEL_BY_ADMIN.targetCode());
    }

    @Test
    @DisplayName("用户取消：只有待付款和待接单可取消")
    void userCancel() {
        assertTrue(OrderAction.CANCEL_BY_USER.canApply(code(PENDING_PAYMENT)));
        assertTrue(OrderAction.CANCEL_BY_USER.canApply(code(TO_BE_CONFIRMED)));

        assertFalse(OrderAction.CANCEL_BY_USER.canApply(code(CONFIRMED)), "商家已接单后应由商家处理");
        assertFalse(OrderAction.CANCEL_BY_USER.canApply(code(DELIVERY_IN_PROGRESS)));
        assertFalse(OrderAction.CANCEL_BY_USER.canApply(code(COMPLETED)));
        assertFalse(OrderAction.CANCEL_BY_USER.canApply(code(CANCELLED)));
    }

    @Test
    @DisplayName("超时取消：只处理待付款")
    void timeoutCancel() {
        assertTrue(OrderAction.TIMEOUT_CANCEL.canApply(code(PENDING_PAYMENT)));
        assertFalse(OrderAction.TIMEOUT_CANCEL.canApply(code(TO_BE_CONFIRMED)), "已付款的订单不能被超时任务取消");
        assertFalse(OrderAction.TIMEOUT_CANCEL.canApply(code(CANCELLED)));
    }

    @Test
    @DisplayName("null 和不认识的状态码，一律拒绝所有动作")
    void unknownStatusRejected() {
        for (OrderAction action : OrderAction.values()) {
            assertFalse(action.canApply(null), action + " 不应接受 null 状态");
            assertFalse(action.canApply(0), action + " 不应接受未定义的状态码 0");
            assertFalse(action.canApply(99), action + " 不应接受未定义的状态码 99");
        }
    }

    @Test
    @DisplayName("状态码与 Orders 常量一一对应")
    void statusCodes() {
        assertEquals(1, code(PENDING_PAYMENT));
        assertEquals(2, code(TO_BE_CONFIRMED));
        assertEquals(3, code(CONFIRMED));
        assertEquals(4, code(DELIVERY_IN_PROGRESS));
        assertEquals(5, code(COMPLETED));
        assertEquals(6, code(CANCELLED));

        assertEquals(PENDING_PAYMENT, OrderStatus.of(1));
        assertEquals(CANCELLED, OrderStatus.of(6));
        assertEquals(null, OrderStatus.of(null));
        assertEquals(null, OrderStatus.of(99));
    }
}
