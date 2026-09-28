package com.sky.service;

import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {

    /**
     * 营业额统计
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end);

    /**
     * 用户统计：用户总量 + 每日新增用户
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    UserReportVO getUserStatistics(LocalDate begin, LocalDate end);

    /**
     * 订单统计：订单总数、有效订单数、订单完成率 + 每日订单数
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end);

    /**
     * 销量排名 Top10
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end);

    /**
     * 导出最近 30 天的运营数据报表（Excel）
     * <p>
     * 日期范围由实现类自己算（前端不传参数），所以这里没有参数
     *
     * @param response xlsx 二进制直接写进响应流
     */
    void exportBusinessData(HttpServletResponse response);
}

