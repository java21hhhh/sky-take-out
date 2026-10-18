package com.sky.mapper;

import com.sky.entity.Orders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * OrderMapper.updateStatus 的集成测试 —— 真实打到 MySQL。
 *
 * <p>单元测试里 mapper 是 mock 的，只能验证「调用参数对不对」，
 * 验证不了 SQL 本身。这里验证两件事：
 * <ol>
 *   <li>条件不匹配时影响 0 行（这是并发保护的基础）；</li>
 *   <li>条件匹配时真的把状态和附加字段写进去了。</li>
 * </ol>
 *
 * <p>类上有 {@code @Transactional}，测试结束自动回滚，不会污染业务库。
 * 依赖本地 MySQL / Redis，默认被 surefire 排除（@Tag("integration")）。
 * 手动运行：{@code mvn test -Dsky.test.excludedGroups=}
 */
// 必须用 RANDOM_PORT：WebSocketConfiguration 里的 ServerEndpointExporter 需要真实的
// Servlet 容器，默认的 MOCK 环境没有 ServerContainer，上下文会启动失败。
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Tag("integration")
@DisplayName("订单条件更新 SQL（集成）")
class OrderMapperTest {

    @Autowired
    private OrderMapper orderMapper;

    @Test
    @DisplayName("状态不匹配影响 0 行；匹配时更新 1 行并写入附加字段")
    void updateStatusOnlyWhenCurrentStatusMatches() {
        Long id = insertPendingPaymentOrder();

        // ① 期望「待接单」，但库里是「待付款」—— 一行都不该更新
        Orders wrong = Orders.builder()
                .id(id)
                .status(Orders.CONFIRMED)
                .build();
        assertEquals(0, orderMapper.updateStatus(wrong, Orders.TO_BE_CONFIRMED),
                "条件不匹配时必须影响 0 行");

        Orders unchanged = orderMapper.getById(id);
        assertEquals(Orders.PENDING_PAYMENT, unchanged.getStatus(), "条件不匹配时不能改状态");

        // ② 期望「待付款」，匹配 —— 更新 1 行，并同时写入支付状态和结账时间
        Orders right = Orders.builder()
                .id(id)
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();
        assertEquals(1, orderMapper.updateStatus(right, Orders.PENDING_PAYMENT),
                "条件匹配时必须更新 1 行");

        Orders updated = orderMapper.getById(id);
        assertEquals(Orders.TO_BE_CONFIRMED, updated.getStatus());
        assertEquals(Orders.PAID, updated.getPayStatus());
        assertNotNull(updated.getCheckoutTime(), "checkout_time 应随支付一起写入");
    }

    @Test
    @DisplayName("取消时能一次写入取消原因和取消时间")
    void updateStatusWritesCancelFields() {
        Long id = insertPendingPaymentOrder();

        LocalDateTime cancelTime = LocalDateTime.now();
        Orders cancel = Orders.builder()
                .id(id)
                .status(Orders.CANCELLED)
                .cancelReason("订单超时")
                .cancelTime(cancelTime)
                .build();

        assertEquals(1, orderMapper.updateStatus(cancel, Orders.PENDING_PAYMENT));

        Orders updated = orderMapper.getById(id);
        assertEquals(Orders.CANCELLED, updated.getStatus());
        assertEquals("订单超时", updated.getCancelReason());
        assertNotNull(updated.getCancelTime());
    }

    /**
     * 插一条「待付款」订单，返回自增主键。
     * 只填 NOT NULL 字段，避免测试数据依赖业务库里的用户 / 地址。
     */
    private Long insertPendingPaymentOrder() {
        Orders order = Orders.builder()
                .number("IT-" + System.nanoTime())
                .status(Orders.PENDING_PAYMENT)
                .userId(1L)
                .addressBookId(1L)
                .orderTime(LocalDateTime.now())
                .payMethod(1)
                .payStatus(Orders.UN_PAID)
                .amount(new BigDecimal("10.00"))
                .deliveryStatus(1)
                .tablewareStatus(1)
                .build();

        orderMapper.insert(order);

        assertNotNull(order.getId(), "insert 必须回填主键");
        return order.getId();
    }
}
