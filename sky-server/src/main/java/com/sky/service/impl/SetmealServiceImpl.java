package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 套餐浏览业务实现（C端）
 */
@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {

    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;


    /**
     * 条件查询套餐
     *
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        return setmealMapper.list(setmeal);
    }

    /**
     * 根据套餐id查询包含的菜品选项
     *
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

    /**
     *
     * @param setmealDTO 添加套餐
     */
    @Override
    @Transactional
    public void addSetmealWithDish(SetmealDTO setmealDTO) {


        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);

        // 前端未传 status 时必须补默认值：insert 显式写入 #{status}，传 null 会覆盖
        // 数据库列默认值 1，导致套餐状态为 NULL —— C 端只查 status=1，该套餐将永久不可见
        if (setmeal.getStatus() == null) {
            setmeal.setStatus(StatusConstant.ENABLE);
        }

        setmealMapper.addSetmeal(setmeal);

        Long id  = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(dish -> dish.setSetmealId(id));  // ← 这里能用
            setmealDishMapper.insertBatch(setmealDishes);
        }

    }

    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        int PageNum = setmealPageQueryDTO.getPage();
        int PageSize = setmealPageQueryDTO.getPageSize();

        PageHelper.startPage(PageNum, PageSize);
        Page<SetmealVO> page =setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     *
     * @param id 根据id查询回显套餐信息
     * @return
     */
    @Override
    public SetmealDTO getByIdWithDish(Long id) {

        Setmeal setmeal =setmealMapper.getById(id);
        if (setmeal == null) {
            // 抛业务异常，GlobalExceptionHandler 会原样把 msg 回给前端；
            // 若让它走到 null 上继续 copyProperties，只会被兜底成"未知错误"
            throw new BaseException(MessageConstant.SETMEAL_NOT_FOUND);
        }
        List<SetmealDish> setmealDishes =setmealDishMapper.getBySetmealId(id);
        SetmealDTO setmealDTO = new SetmealDTO();
        BeanUtils.copyProperties(setmeal,setmealDTO);
        setmealDTO.setSetmealDishes(setmealDishes);
        return setmealDTO;
    }


    @Override
    public void deleteBatch(List<Long> ids) {
     for (Long id : ids) {
         Setmeal setmeal = setmealMapper.getById(id);
         if(setmeal != null && setmeal.getStatus() ==StatusConstant.ENABLE){
             throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
         }
     }
     for (Long id : ids) {
         setmealMapper.deleteById(id);
         setmealDishMapper.deleteBySetmealId(id);
     }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SetmealDTO setmealDTO) {
        log.info("update 收到的 DTO = {}", setmealDTO);          // ← 打印 DTO
        log.info("DTO 的 id = {}", setmealDTO.getId());
        //1，将DTO拷到实体类
        Setmeal setmeal =new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        //2.更新套餐基本功能
        setmealMapper.update(setmeal);

        //3,拿到套餐id
        Long setmealId  = setmeal.getId();

        //4.删除套餐原有的菜品关联数据
        setmealDishMapper.deleteBySetmealId(setmealId);

        //5.插入新的菜品数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (setmealDishes != null && !setmealDishes.isEmpty()) {
            setmealDishes.forEach(dish -> dish.setSetmealId(setmealId));
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 套餐起售、停售
     * <p>
     * 只需按 id 更新 status 字段。SetmealMapper.xml 的 update 是动态 SQL，
     * 未赋值的字段不会进入 set 子句，因此这里只构造 id 和 status 两项即可，
     * 不会误伤套餐的其他字段。
     *
     * @param status 状态，1 起售 0 停售
     * @param id     套餐 id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();
        setmealMapper.update(setmeal);
    }


}
