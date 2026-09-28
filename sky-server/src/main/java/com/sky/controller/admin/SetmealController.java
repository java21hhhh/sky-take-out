package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/admin/setmeal")
@Api(tags = "套餐管理接口")
@Slf4j
public class SetmealController {

    private final SetmealService setmealService;

    public SetmealController(SetmealService setmealService) {
        this.setmealService = setmealService;
    }

    /**
     * 新增套餐
     * <p>
     * C 端 /user/setmeal/list 用 @Cacheable(cacheNames = "setmealCache", key = "#categoryId") 按分类缓存，
     * 新增套餐后必须清掉该分类的缓存，否则小程序看不到新套餐。
     * 注意 @CacheEvict 默认在方法正常返回后才执行，插入失败（事务回滚）时不会误清缓存。
     */
    @PostMapping
    @ApiOperation("新增套餐")
    @CacheEvict(cacheNames = "setmealCache", key = "#setmealDTO.categoryId")
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        setmealService.addSetmealWithDish(setmealDTO);
        return Result.success();
    }
    @GetMapping("/page")
    @ApiOperation("分页查询套餐")
    public Result<PageResult> page (SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("分页查询套餐：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询回显套餐")
    public Result<SetmealDTO> getById(@PathVariable Long id) {
        log.info("根据id查询回显套餐：{}",id);
        SetmealDTO setmealDTO = setmealService.getByIdWithDish(id);
        return Result.success(setmealDTO);
    }

    /**
     * 批量删除套餐
     * <p>
     * 删除会影响 C 端 /user/setmeal/list，同样清空整个 setmealCache（见 startOrStop 的说明）。
     */
    @DeleteMapping
    @ApiOperation("批量删除套餐")
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public Result delete(@RequestParam List<Long> ids) {
    log.info("批量删除套餐：{}",ids);
    setmealService.deleteBatch(ids);return Result.success();
    }

    /**
     * 修改套餐
     * <p>
     * 必须清空整个 setmealCache 而不是只清原分类：修改时可能把 categoryId 改到别的分类，
     * 只清新分类会漏掉旧分类那份缓存，小程序继续看到不该出现的套餐。
     */
    @PutMapping
    @ApiOperation("修改套餐")
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐：{}",setmealDTO);
        setmealService.update(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐起售、停售
     * <p>
     * 起售/停售会改变 C 端 /user/setmeal/list 的查询结果，而该接口按分类做了缓存，
     * 这里直接清空 setmealCache 的全部条目，避免小程序看到过期的状态。
     */

    @PostMapping("/status/{status}")
    @ApiOperation("套餐起售停售")
    @CacheEvict(cacheNames = "setmealCache", allEntries = true)
    public Result StartOrStop(@PathVariable Integer status,Long id) {
        log.info("套餐起售停售:{},{}",status,id);
        setmealService.startOrStop(status,id);
        return Result.success();
    }

}
