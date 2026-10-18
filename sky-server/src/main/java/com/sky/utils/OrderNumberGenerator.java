package com.sky.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 订单号生成器。
 *
 * <p>格式：<b>13 位毫秒时间戳 + 3 位毫秒内序列 + 2 位机器号</b>，共 18 位数字。
 *
 * <p>为什么不用 {@code System.currentTimeMillis()}：同一毫秒内的两个下单请求
 * 会拿到同一个订单号。压测或秒杀场景下这不是理论问题。
 *
 * <p>为什么不用 UUID：订单号要给用户看、要能按时间排序，18 位数字比 32 位十六进制可读。
 *
 * <p>为什么不用数据库自增：订单号是业务编号，直接暴露订单量不合适。
 *
 * <p><b>限制（先明说）</b>：
 * <ul>
 *   <li>单机每毫秒最多 1000 单，用完会等到下一毫秒；</li>
 *   <li>多实例部署时，每个实例的 {@code sky.order.worker-id} 必须不同（取值 0~99）；</li>
 *   <li>时钟回拨直接抛异常 —— 宁可下单失败，也不生成可能重复的号。</li>
 * </ul>
 *
 * <p>数据库侧还有 {@code uk_order_number} 唯一索引兜底：生成器真出错时，
 * 插入会失败而不是静默写入两笔同号订单。
 */
@Component
public class OrderNumberGenerator {

    /** 每毫秒最多 1000 个序号（3 位） */
    private static final int MAX_SEQUENCE = 1000;

    /** 机器号，0~99；单实例保持默认 0 即可 */
    @Value("${sky.order.worker-id:0}")
    private long workerId;

    private long lastTimestamp = -1L;
    private int sequence = 0;

    /**
     * 生成下一个订单号。方法整体加锁 —— 这是单实例内的临界区，
     * 生成 18 位字符串的成本远低于一次数据库写入，不值得为它做无锁化。
     */
    public synchronized String next() {
        if (workerId < 0 || workerId > 99) {
            throw new IllegalStateException("sky.order.worker-id 必须在 0~99 之间");
        }

        long now = System.currentTimeMillis();
        if (now < lastTimestamp) {
            throw new IllegalStateException("系统时钟回拨，拒绝生成订单号");
        }

        if (now == lastTimestamp) {
            sequence++;
            if (sequence >= MAX_SEQUENCE) {
                // 当前毫秒的序列用完了，等到下一毫秒再发号
                while (now <= lastTimestamp) {
                    now = System.currentTimeMillis();
                }
                sequence = 0;
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;

        // Locale.ROOT：避免某些区域设置把数字格式化成非 ASCII 字符
        return String.format(Locale.ROOT, "%013d%03d%02d", now, sequence, workerId);
    }
}
