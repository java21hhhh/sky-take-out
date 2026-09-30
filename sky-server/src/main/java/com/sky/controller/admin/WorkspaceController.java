package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 工作台相关接口
 * <p>
 * 前端页面（管理端 dashboard）创建时并发请求这 4 个接口，都不带参数，
 * 所以「今天」「今日」这类时间范围一律由后端自己算
 */
@RestController("adminWorkspaceController")
@RequestMapping("/admin/workspace")
@Slf4j
public class WorkspaceController {

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 今日运营数据：营业额、有效订单、订单完成率、平均客单价、新增用户
     * 请求示例：/admin/workspace/businessData
     */
    @GetMapping("/businessData")
    public Result<BusinessDataVO> businessData() {
        LocalDate today = LocalDate.now();
        LocalDateTime begin = LocalDateTime.of(today, LocalTime.MIN);
        LocalDateTime end = LocalDateTime.of(today, LocalTime.MAX);
        log.info("工作台今日数据：{}", today);
        return Result.success(workspaceService.getBusinessData(begin, end));
    }

    /**
     * 订单管理：待接单、待派送、已完成、已取消、全部订单
     */
    @GetMapping("/overviewOrders")
    public Result<OrderOverViewVO> overviewOrders() {
        log.info("工作台订单总览");
        return Result.success(workspaceService.getOrderOverView());
    }

    /**
     * 菜品总览：已启售、已停售
     */
    @GetMapping("/overviewDishes")
    public Result<DishOverViewVO> overviewDishes() {
        log.info("工作台菜品总览");
        return Result.success(workspaceService.getDishOverView());
    }

    /**
     * 套餐总览：已启售、已停售
     * <p>
     * 路径是 overviewSetmeals（小写 m），必须和前端调用保持一致
     */
    @GetMapping("/overviewSetmeals")
    public Result<SetmealOverViewVO> overviewSetmeals() {
        log.info("工作台套餐总览");
        return Result.success(workspaceService.getSetmealOverView());
    }
}
