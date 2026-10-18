package com.sky.enumeration;

/**
 * 订单状态。
 *
 * <p>与 {@code Orders} 里的常量一一对应。从本类开始，状态判断不再散落在各个
 * Service 方法里 —— 统一交给 {@link OrderAction} 的迁移表决定。
 */
public enum OrderStatus {

    PENDING_PAYMENT(1, "待付款"),
    TO_BE_CONFIRMED(2, "待接单"),
    CONFIRMED(3, "已接单"),
    DELIVERY_IN_PROGRESS(4, "派送中"),
    COMPLETED(5, "已完成"),
    CANCELLED(6, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按数据库里的状态码取枚举。
     *
     * <p>返回 {@code null} 而不是抛异常：状态码来自数据库，属于外部数据。
     * 遇到不认识的值时，让调用方按「任何迁移都不允许」处理，比抛 500 更安全。
     */
    public static OrderStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
