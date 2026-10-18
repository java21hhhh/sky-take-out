package com.sky.enumeration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 订单状态迁移表。
 *
 * <p>每一个业务动作在这里声明：允许从哪些状态出发、最终落到哪个状态。
 * 以前这些规则散在 6 个 Service 方法里，各写各的 ——
 * 有的用 {@code equals}，有的用数字大小比较，有的干脆不判断（管理员取消）。
 * 同一个业务规则有六种写法，就一定有漏的；现在只有一个出处。
 *
 * <p>注意：本类只负责「业务规则允不允许」。并发安全要靠更新 SQL 里的
 * {@code WHERE status = 期望状态}（检查完到更新之间，状态可能被别的请求改掉），
 * 两者缺一不可。
 */
public enum OrderAction {

    /** 支付：待付款 -> 待接单 */
    PAY(EnumSet.of(OrderStatus.PENDING_PAYMENT), OrderStatus.TO_BE_CONFIRMED),

    /** 接单：待接单 -> 已接单 */
    CONFIRM(EnumSet.of(OrderStatus.TO_BE_CONFIRMED), OrderStatus.CONFIRMED),

    /** 拒单：待接单 -> 已取消 */
    REJECT(EnumSet.of(OrderStatus.TO_BE_CONFIRMED), OrderStatus.CANCELLED),

    /** 派送：已接单 -> 派送中 */
    DELIVER(EnumSet.of(OrderStatus.CONFIRMED), OrderStatus.DELIVERY_IN_PROGRESS),

    /** 完成：派送中 -> 已完成 */
    COMPLETE(EnumSet.of(OrderStatus.DELIVERY_IN_PROGRESS), OrderStatus.COMPLETED),

    /** 自动完成（定时任务）：派送中 -> 已完成 */
    AUTO_COMPLETE(EnumSet.of(OrderStatus.DELIVERY_IN_PROGRESS), OrderStatus.COMPLETED),

    /** 管理员取消：待付款 / 待接单 / 已接单 / 派送中 -> 已取消（已完成、已取消不可再取消） */
    CANCEL_BY_ADMIN(EnumSet.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.TO_BE_CONFIRMED,
            OrderStatus.CONFIRMED,
            OrderStatus.DELIVERY_IN_PROGRESS
    ), OrderStatus.CANCELLED),

    /** 用户取消：待付款 / 待接单 -> 已取消 */
    CANCEL_BY_USER(EnumSet.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.TO_BE_CONFIRMED
    ), OrderStatus.CANCELLED),

    /** 超时取消（定时任务）：待付款 -> 已取消 */
    TIMEOUT_CANCEL(EnumSet.of(OrderStatus.PENDING_PAYMENT), OrderStatus.CANCELLED);

    private final Set<OrderStatus> allowedFrom;
    private final OrderStatus target;

    OrderAction(Set<OrderStatus> allowedFrom, OrderStatus target) {
        this.allowedFrom = Collections.unmodifiableSet(allowedFrom);
        this.target = target;
    }

    public Set<OrderStatus> getAllowedFrom() {
        return allowedFrom;
    }

    public OrderStatus getTarget() {
        return target;
    }

    /** 目标状态的数据库状态码，用于写回 orders.status */
    public int targetCode() {
        return target.getCode();
    }

    /**
     * 当前状态能否执行本动作。
     *
     * @param currentStatus 数据库里的订单状态码，可为 null
     * @return 允许返回 true；状态为 null 或不认识时一律 false
     */
    public boolean canApply(Integer currentStatus) {
        OrderStatus current = OrderStatus.of(currentStatus);
        return current != null && allowedFrom.contains(current);
    }
}
