package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

import java.time.LocalDate;

/**
 * 数据统计相关接口
 */
@RestController("adminReportController")
@RequestMapping("/admin/report")
@Slf4j
public class ReportController {

    @Autowired
    private ReportService reportService;

    /**
     * 营业额统计
     * 请求示例：/admin/report/turnoverStatistics?begin=2026-08-29&end=2026-09-27
     *
     * @param begin 起始日期，格式 yyyy-MM-dd
     * @param end   截止日期，格式 yyyy-MM-dd
     */
    @GetMapping("/turnoverStatistics")
    public Result<TurnoverReportVO> turnoverStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("营业额统计：{} ~ {}", begin, end);
        return Result.success(reportService.getTurnoverStatistics(begin, end));
    }

    /**
     * 用户统计：用户总量 + 每日新增用户
     * 请求示例：/admin/report/userStatistics?begin=2026-08-29&end=2026-09-27
     *
     * @param begin 起始日期，格式 yyyy-MM-dd
     * @param end   截止日期，格式 yyyy-MM-dd
     */
    @GetMapping("/userStatistics")
    public Result<UserReportVO> userStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("用户统计：{} ~ {}", begin, end);
        return Result.success(reportService.getUserStatistics(begin, end));
    }
    /**
     * 订单统计：订单总数、有效订单数、订单完成率 + 每日订单数
     * 请求示例：/admin/report/ordersStatistics?begin=2026-09-21&end=2026-09-27
     *
     * @param begin 起始日期，格式 yyyy-MM-dd
     * @param end   截止日期，格式 yyyy-MM-dd
     */
    @GetMapping("/ordersStatistics")
    public Result<OrderReportVO> ordersStatistics(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("订单统计：{} ~ {}", begin, end);
        return Result.success(reportService.getOrdersStatistics(begin, end));
    }

    /**
     * 销量排名 Top10
     * 请求示例：/admin/report/top10?begin=2026-09-21&end=2026-09-27
     *
     * @param begin 起始日期，格式 yyyy-MM-dd
     * @param end   截止日期，格式 yyyy-MM-dd
     */
    @GetMapping("/top10")
    public Result<SalesTop10ReportVO> top10(
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end){
        log.info("销量排名top10:{},{}",begin,end);
        return Result.success(reportService.getSalesTop10(begin,end));
    }

    /**
     * 导出最近 30 天的运营数据报表
     * 请求示例：/admin/report/export（前端不传任何参数，日期范围由后端算）
     * <p>
     * 返回的是 xlsx 二进制流，不包 Result
     */
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
        log.info("导出运营数据报表");
        reportService.exportBusinessData(response);
    }

}
