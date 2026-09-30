package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.vo.DishItemVO;

import java.util.List;

/**
 * 套餐浏览业务接口（C端）
 */
public interface SetmealService {

    /**
     * 条件查询套餐
     *
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐id查询包含的菜品选项
     *
     * @param id
     * @return
     */
    List<DishItemVO> getDishItemById(Long id);

    void addSetmealWithDish(SetmealDTO setmealDTO);

    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    SetmealDTO getByIdWithDish(Long id);

    void update(SetmealDTO setmealDTO);

    void deleteBatch(List<Long> ids);

    /**
     * 套餐起售、停售
     *
     * @param status 状态，1 起售 0 停售
     * @param id     套餐 id
     */
    void startOrStop(Integer status, Long id);
}
