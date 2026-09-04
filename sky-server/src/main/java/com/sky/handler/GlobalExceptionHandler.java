package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLFeatureNotSupportedException;
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

    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex){
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            // 从 "Duplicate entry 'xxx' for key 'yyy'" 中提取重复值，避免按逗号 split 越界
            String duplicateValue = message.replaceAll("(?s).*Duplicate entry '(.*?)' for key.*", "$1");
            return Result.error(duplicateValue + MessageConstant.ALREADY_EXIST);
        } else {
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }
    }
}
