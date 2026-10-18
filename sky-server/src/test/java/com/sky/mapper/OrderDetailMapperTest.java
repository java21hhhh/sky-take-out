package com.sky.mapper;

import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * OrderDetailMapper.getByOrderIds 的集成测试 —— 真实 MySQL。
 *
 * <p>单元测试里 mapper 是 mock 的，验证不了 {@code <foreach>} 拼出来的
 * {@code where order_id in (...)} 是否正确。这里插两个订单、三条明细，
 * 确认一次查询能把两个订单的明细都捞回来。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
@Tag("integration")
@DisplayName("订单明细批量查询 SQL（集成）")
class OrderDetailMapperTest {

    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private OrderMapper orderMapper;

    @Test
    @DisplayName("一次 in 查询返回多个订单的明细")
    void getByOrderIdsReturnsAll() {
        Long orderId1 = insertOrder();
        Long orderId2 = insertOrder();

        orderDetailMapper.insertBatch(Arrays.asList(
                detail(orderId1, "宫保鸡丁", 2, "10.00"),
                detail(orderId1, "米饭", 1, "2.00"),
                detail(orderId2, "鱼香肉丝", 1, "12.00")
        ));

        List<OrderDetail> details = orderDetailMapper.getByOrderIds(Arrays.asList(orderId1, orderId2));

        assertEquals(3, details.size());
        assertEquals(2L, details.stream().filter(d -> orderId1.equals(d.getOrderId())).count());
        assertEquals(1L, details.stream().filter(d -> orderId2.equals(d.getOrderId())).count());
    }

    private Long insertOrder() {
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
        assertNotNull(order.getId());
        return order.getId();
    }

    private static OrderDetail detail(Long orderId, String name, int number, String amount) {
        return OrderDetail.builder()
                .orderId(orderId)
                .name(name)
                .number(number)
                .amount(new BigDecimal(amount))
                .build();
    }
}
