package com.sky.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单号生成器的单元测试。
 *
 * <p>核心只有一件事：<b>任何情况下都不能发出重复号</b>。
 * 单线程连发、多线程并发、机器号编码、时钟回拨，各测一条。
 */
@DisplayName("订单号生成器")
class OrderNumberGeneratorTest {

    @Test
    @DisplayName("连续生成 10000 个号不重复，且都是 18 位数字")
    void sequentialNumbersAreUnique() {
        OrderNumberGenerator generator = new OrderNumberGenerator();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < 10_000; i++) {
            String number = generator.next();
            assertEquals(18, number.length(), "订单号长度应为 18");
            assertTrue(number.matches("\\d{18}"), "订单号应为纯数字：" + number);
            assertTrue(seen.add(number), "出现重复订单号：" + number);
        }
    }

    @Test
    @DisplayName("多线程并发生成也不重复")
    void concurrentNumbersAreUnique() throws Exception {
        OrderNumberGenerator generator = new OrderNumberGenerator();
        int threads = 8;
        int perThread = 500;
        Set<String> seen = ConcurrentHashMap.newKeySet();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        seen.add(generator.next());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发生成未在 30 秒内完成");
        pool.shutdown();

        assertEquals(threads * perThread, seen.size(), "并发下出现了重复订单号");
    }

    @Test
    @DisplayName("机器号编码在最后两位")
    void workerIdIsEncoded() {
        OrderNumberGenerator generator = new OrderNumberGenerator();
        ReflectionTestUtils.setField(generator, "workerId", 7L);

        assertTrue(generator.next().endsWith("07"));
    }

    @Test
    @DisplayName("机器号越界直接拒绝")
    void invalidWorkerIdRejected() {
        OrderNumberGenerator generator = new OrderNumberGenerator();
        ReflectionTestUtils.setField(generator, "workerId", 100L);

        assertThrows(IllegalStateException.class, generator::next);
    }

    @Test
    @DisplayName("时钟回拨时拒绝发号，而不是发出可能重复的号")
    void clockRollbackRejected() {
        OrderNumberGenerator generator = new OrderNumberGenerator();
        ReflectionTestUtils.setField(generator, "lastTimestamp",
                System.currentTimeMillis() + 60_000);

        assertThrows(IllegalStateException.class, generator::next);
    }
}
