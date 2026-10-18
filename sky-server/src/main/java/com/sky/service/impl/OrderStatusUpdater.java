package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.enumeration.OrderAction;
import com.sky.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 订单状态迁移的唯一入口。
 *
 * <p>职责只有两件事，缺一不可：
 * <ol>
 *   <li><b>业务规则</b>：当前状态能不能执行这个动作（{@link OrderAction} 迁移表）；</li>
 *   <li><b>并发安全</b>：更新时带 {@code WHERE status = 期望状态}，影响 0 行就说明
 *       状态在「检查」和「更新」之间被别人改掉了。</li>
 * </ol>
 *
 * <p>只做业务规则校验是不够的 —— 两个请求可以同时读到「待付款」，
 * 都通过校验，然后各自更新。必须有条件更新做兜底。
 *
 * <p>本类不抛业务异常，只返回成功/失败：Service 层要报错，定时任务层只记日志跳过，
 * 两边的处理方式不同。
 */
@Component
public class OrderStatusUpdater {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 按迁移表校验并执行状态更新。
     *
     * @param update    要写入的字段，<b>必须带 id</b>；本方法会写入 status
     * @param fromStatus 调用方读到的当前状态
     * @param action     业务动作
     * @return true 表示迁移成功；false 表示规则不允许，或状态已被并发修改
     */
    public boolean change(Orders update, Integer fromStatus, OrderAction action) {
        if (update == null || update.getId() == null) {
            throw new IllegalArgumentException("订单 id 不能为空");
        }
        if (!action.canApply(fromStatus)) {
            return false;
        }
        update.setStatus(action.targetCode());
        return orderMapper.updateStatus(update, fromStatus) > 0;
    }
}
