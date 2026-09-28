package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import javax.servlet.http.HttpServletResponse;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     * @param
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 数据库约束异常（java.sql 原样抛出时走这里）
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        return handleConstraintViolation(ex.getMessage());
    }

    /**
     * 数据库约束异常（Spring 已翻译成 DataAccessException 时走这里，如字段超长）
     */
    @ExceptionHandler
    public Result exceptionHandler(DataIntegrityViolationException ex){
        return handleConstraintViolation(ex.getMessage());
    }

    /**
     * Spring MVC 自身的请求异常：请求方法不匹配、媒体类型不支持、参数缺失/类型错误、报文不可读。
     * <p>
     * 这类异常的语义是"请求本身写错了"，必须保留原始 4xx 状态码。若交给下面的兜底处理器，
     * 会被包装成 200 + "未知错误"，接口方法写错时前端完全看不出问题所在。
     * 状态码保留，响应体统一成 Result 格式，并只回简短文案——原始异常信息（如 Jackson 的整段
     * 解析报错）只写日志，避免被前端当成提示语弹给用户。
     */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public Result exceptionHandler(Exception ex, HttpServletResponse response) {
        log.warn("请求异常：{}", ex.getMessage());
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
            return Result.error(MessageConstant.REQUEST_METHOD_NOT_SUPPORTED);
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            response.setStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
            return Result.error(MessageConstant.REQUEST_MEDIA_TYPE_NOT_SUPPORTED);
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return Result.error(MessageConstant.REQUEST_PARAM_ERROR);
    }

    /**
     * 兜底：其余未预期异常统一包装成 Result 格式返回。
     * <p>
     * 否则会返回 Spring 原生错误体 {"timestamp":...,"status":500,...}，
     * 里面没有 code 字段，前端响应拦截器读 res.data.code 会拿到 undefined，只能提示"未知错误"。
     */
    @ExceptionHandler
    public Result exceptionHandler(Exception ex){
        log.error("系统异常：", ex);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 唯一约束冲突时，从 "Duplicate entry 'xxx' for key 'yyy'" 中提取信息并转成可读文案。
     * <p>
     * 不同表的唯一索引含义不同，按索引名区分——否则套餐重名会被报成"账号已存在"。
     */
    private Result handleConstraintViolation(String message) {
        if (message != null && message.contains("Duplicate entry")) {
            if (message.contains("idx_setmeal_name")) {
                return Result.error(MessageConstant.SETMEAL_NAME_EXIST);
            }
            String duplicateValue = message.replaceAll("(?s).*Duplicate entry '(.*?)' for key.*", "$1");
            return Result.error(duplicateValue + MessageConstant.ALREADY_EXIST);
        }
        log.error("数据库约束异常：{}", message);
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }
}
