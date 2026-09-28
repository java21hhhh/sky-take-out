package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.UserCountDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 营业额统计
     * <p>
     * 营业额 = 该日「已完成」订单的实收金额之和，未完成的订单不算收入
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = buildDateList(begin, end);

        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            Map map = new HashMap();
            map.put("status", Orders.COMPLETED);            // 5 已完成
            // 日界统一用 LocalTime.MIN/MAX，和订单统计保持一致：
            // 若 end 只写到 23:59:59，落在 23:59:59.xxx 的订单会算进订单统计却漏在营业额里，
            // 同一个页面上两张图就对不上了
            map.put("begin", LocalDateTime.of(date, LocalTime.MIN));  // 当天 00:00:00
            map.put("end", LocalDateTime.of(date, LocalTime.MAX));    // 当天 23:59:59.999999999
            // 当天没有已完成订单时 sum() 返回 NULL，SQL 里已用 ifnull 兜成 0，
            // 所以这里拿到的不会是 null，可以直接放进集合
            turnoverList.add(orderMapper.sumByMap(map));
        }

        return TurnoverReportVO.builder()
                .dateList(join(dateList))
                .turnoverList(join(turnoverList))
                .build();
    }

    /**
     * 用户统计：用户总量 + 每日新增用户
     * <p>
     * 统计口径：用户按「注册时间落在哪一天」算作那一天的新增用户；
     * 总用户量是累计值，第 i 天的总量 = 第 i-1 天的总量 + 第 i 天的新增。
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = buildDateList(begin, end);

        //1. 一次查出区间内「每天的新增用户数」，按注册日期分组
        //   注意这个查询只会返回「有人注册」的日期，没人注册的日期不在结果里
        Map<LocalDate, Integer> newUserMap = new HashMap<>();
        List<UserCountDTO> userCountList = userMapper.countNewUserByDate(begin, end.plusDays(1));
        for (UserCountDTO userCountDTO : userCountList) {
            newUserMap.put(userCountDTO.getStatDate(), userCountDTO.getUserCount());
        }

        //2. 期初总数：begin 之前就注册的用户，是第一天「总用户量」的基数
        //   总用户量没必要一天查一次数据库——搞清楚它是累计值，一次查询加累加就够了
        Integer totalUser = userMapper.countRegisteredBefore(begin);

        //3. 按日期列表逐天累加，得到每天的总用户量和新增用户数
        List<Integer> totalUserList = new ArrayList<>();
        List<Integer> newUserList = new ArrayList<>();
        for (LocalDate date : dateList) {
            Integer newUser = newUserMap.getOrDefault(date, 0); // 当天没人注册就是 0
            totalUser += newUser;
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }

        return UserReportVO.builder()
                .dateList(join(dateList))
                .totalUserList(join(totalUserList))
                .newUserList(join(newUserList))
                .build();
    }

    /**
     * 订单统计：订单总数、有效订单数、订单完成率 + 每日订单数
     * <p>
     * 订单总数含已取消的订单，有效订单只算已完成的（status=5），
     * 完成率 = 有效订单数 / 订单总数，没有订单时按 0 处理，避免除 0
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        // 用统一的 buildDateList：它按 isAfter 收尾，区间传反时返回空列表，
        // 不会像手写 while(!begin.equals(end)) 那样永远等不到 equals 而无限加日期
        List<LocalDate> dateList = buildDateList(begin, end);

        //存放每天的订单总数
        List<Integer> orderCountList = new ArrayList<>();
        //存放每天的有效订单数
        List<Integer> valiOrderCountList = new ArrayList<>();


        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Integer orderCount = getOrderCount(beginTime, endTime, null);
            Integer valiOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            valiOrderCountList.add(valiOrderCount);
        }
        Integer totalOrderCount =orderCountList.stream().reduce(0,Integer::sum);
        Integer valiOrderCount =valiOrderCountList.stream().reduce(0,Integer::sum);

        Double orderCompletionRate =0.0;
        if(totalOrderCount !=0){

            orderCompletionRate = valiOrderCount.doubleValue()/totalOrderCount;
        }
        return OrderReportVO.builder()
                .dateList(join(dateList))
                .orderCountList(join(orderCountList))
                .validOrderCountList(join(valiOrderCountList))
                .orderCompletionRate(orderCompletionRate)
                .validOrderCount(valiOrderCount)
                .totalOrderCount(totalOrderCount)
                .build();
    }

    /**
     * 销量排名 Top10：区间内「已完成」订单里销量最高的 10 个菜品
     * <p>
     * 返回的是两个逗号分隔字符串，前端 split 之后喂给 echarts
     *
     * @param begin 起始日期（含）
     * @param end   截止日期（含）
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime,endTime);

        List<String> names =salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList =join(names);


        List<Integer> number =salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList =join(number);

        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 导出最近 30 天的运营数据报表（Excel）
     * <p>
     * 前端调这个接口时**不传任何参数**（确认框里那句「最近30天运营数据」只是文案），
     * 所以日期范围在这里算：截止昨天往前 30 天，和数据统计页的默认区间口径一致
     *
     * @param response xlsx 二进制直接写进响应流，所以返回值是 void
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        //1. 概览数据 = 整个区间的汇总，复用工作台的统计逻辑，不再写一遍
        BusinessDataVO overview = workspaceService.getBusinessData(
                LocalDateTime.of(begin, LocalTime.MIN),
                LocalDateTime.of(end, LocalTime.MAX));

        InputStream in = this.getClass().getClassLoader()
                .getResourceAsStream("template/运营数据报表模板.xlsx");
        if (in == null) {
            throw new RuntimeException("找不到 Excel 模板：template/运营数据报表模板.xlsx");
        }

        try (InputStream template = in;
             XSSFWorkbook excel = new XSSFWorkbook(template)) {

            XSSFSheet sheet = excel.getSheet("Sheet1");

            //2. 时间范围。模板里 B2:G2 是合并出来的「时间」行
            sheet.getRow(1).getCell(1).setCellValue("时间：" + begin + "至" + end);

            //3. 概览区。⚠️ 坐标最容易踩的坑：OOXML 里行号/列号从 1 开始（r="4"、列 B），
            //   而 POI 的 getRow()/getCell() 都是 0 基 —— getRow(3) 才对应第 4 行，getCell(1) 才是 B 列。
            //   模板实际布局（下面写的是 1 基行号）：
            //     第2行  B2:G2 合并，填「时间：…至…」
            //     第3行  B3:G3 合并，是「概览数据」小节标题，不要往这里写
            //     第4行  B4=营业额  D4=订单完成率  F4=新增用户数 → 值填 C4/E4/G4
            //     第5行  B5=有效订单 D5=平均客单价              → 值填 C5/E5
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(overview.getTurnover());
            row.getCell(4).setCellValue(overview.getOrderCompletionRate());
            row.getCell(6).setCellValue(overview.getNewUsers());

            row = sheet.getRow(4);
            row.getCell(2).setCellValue(overview.getValidOrderCount());
            row.getCell(4).setCellValue(overview.getUnitPrice());

            //4. 明细区。第 6 行是「明细数据」小节标题，第 7 行是表头
            //   （日期/营业额/有效订单/订单完成率/平均客单价/新增用户数），数据从第 8 行开始。
            //   第 8 行对应 getRow(7)，往后 30 行正好到第 37 行，把模板恰好写满、不越界
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);
                BusinessDataVO daily = workspaceService.getBusinessData(
                        LocalDateTime.of(date, LocalTime.MIN),
                        LocalDateTime.of(date, LocalTime.MAX));

                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(daily.getTurnover());
                row.getCell(3).setCellValue(daily.getValidOrderCount());
                row.getCell(4).setCellValue(daily.getOrderCompletionRate());
                row.getCell(5).setCellValue(daily.getUnitPrice());
                row.getCell(6).setCellValue(daily.getNewUsers());
            }

            //5. 响应头。前端是 axios responseType:'blob' + 硬编码文件名下载，其实不看这两个头；
            //   设上之后，直接用浏览器打开这个 URL 也是一个正常的下载
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=" + URLEncoder.encode("运营数据统计报表.xlsx", "UTF-8"));

            excel.write(response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("导出运营数据报表失败", e);
        }
    }

    /**
     * 构造 [begin, end] 的连续日期列表（含首尾）
     * <p>
     * 用 isAfter 判断而不是 equals：万一前端传反了（begin > end），
     * 这里得到空列表直接返回空数据，不会死循环
     */
    private List<LocalDate> buildDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        LocalDate date = begin;
        while (!date.isAfter(end)) {
            dateList.add(date);
            date = date.plusDays(1);
        }
        return dateList;
    }

    /**
     * 把日期/数字列表拼成逗号分隔的字符串，前端拿到后自己按逗号切开喂给 echarts
     */
    private String join(List<?> list) {
        return list.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end,Integer status) {
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);

        return orderMapper.countByMap(map);
    }
}
