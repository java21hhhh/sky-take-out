package com.sky.service;

import com.sky.vo.BusinessDataVO;
import com.sky.vo.DishOverViewVO;
import com.sky.vo.OrderOverViewVO;
import com.sky.vo.SetmealOverViewVO;

import java.time.LocalDateTime;

public interface WorkspaceService {

    /**
     * 统计指定时间区间的运营数据：营业额、有效订单数、订单完成率、平均客单价、新增用户数
     * <p>
     * 导出 Excel 也复用这个方法（区间传 30 天），所以区间由调用方传入，
     * 而不是在这里写死「今天」
     *
     * @param begin 起始时间（含）
     * @param end   截止时间（含）
     */
    BusinessDataVO getBusinessData(LocalDateTime begin, LocalDateTime end);

    /**
     * 订单总览：今日各状态（待接单/待派送/已完成/已取消）订单数 + 全部订单数
     */
    OrderOverViewVO getOrderOverView();

    /**
     * 菜品总览：已启售 / 已停售数量
     */
    DishOverViewVO getDishOverView();

    /**
     * 套餐总览：已启售 / 已停售数量
     */
    SetmealOverViewVO getSetmealOverView();
}
