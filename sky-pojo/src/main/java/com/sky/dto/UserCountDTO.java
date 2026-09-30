package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 按日期分组统计的数量（目前用于用户统计里的「每日新增用户数」）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCountDTO implements Serializable {

    //统计日期
    private LocalDate statDate;

    //该日期的用户数量
    private Integer userCount;
}
