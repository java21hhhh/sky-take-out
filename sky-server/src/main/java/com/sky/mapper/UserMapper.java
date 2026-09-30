package com.sky.mapper;

import com.sky.dto.UserCountDTO;
import com.sky.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper {

    /**
     * 根据 openid 查询用户
     *
     * @param openid 微信用户唯一标识
     * @return 用户对象，不存在返回 null
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 新增用户（首次微信登录自动注册）
     * 主键 id 通过数据库自增回填到 user.id
     *
     * @param user 用户对象
     */
    @Insert("insert into user (openid, name, phone, sex, id_number, avatar, create_time) " +
            "values (#{openid}, #{name}, #{phone}, #{sex}, #{idNumber}, #{avatar}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);

    /**
     * 按注册日期分组，统计区间内每天的新增用户数
     * <p>
     * 只返回「有人注册」的日期，没人注册的日期不会出现在结果里，
     * 调用方要自己按日期列表补 0（见 ReportServiceImpl#getUserStatistics）
     *
     * @param begin        起始日期（含），当天 00:00:00
     * @param endExclusive 结束日期（不含），传「截止日期 + 1 天」
     */
    @Select("select date(create_time) as statDate, count(*) as userCount from user " +
            "where create_time >= #{begin} and create_time < #{endExclusive} " +
            "group by date(create_time) order by date(create_time)")
    List<UserCountDTO> countNewUserByDate(@Param("begin") LocalDate begin,
                                          @Param("endExclusive") LocalDate endExclusive);

    /**
     * 统计在指定日期之前注册的用户总数，作为统计区间的「期初总数」
     *
     * @param begin 起始日期（不含）
     */
    @Select("select count(*) from user where create_time < #{begin}")
    Integer countRegisteredBefore(@Param("begin") LocalDate begin);

    /**
     * 动态条件统计用户数量（工作台「今日数据」里的新增用户数）
     * <p>
     * SQL 在 UserMapper.xml 里，可传 begin / end
     *
     * @param map 查询条件
     * @return 用户数量
     */
    Integer countByMap(Map map);

}
