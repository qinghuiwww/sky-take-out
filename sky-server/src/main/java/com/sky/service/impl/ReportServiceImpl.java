package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.ReportService;
import com.sky.vo.TurnoverReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;

    /**
     * 统计指定时间区间内的营业额数据：查询的是订单表，状态已经完成的订单
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnover(LocalDate begin, LocalDate end) {
        //1.日期：当前集合用于存放从begin到end范围内的每天的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)){
            //日期计算，获得指定日期后1天的日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //现在是list类型而vo类中的dateList要求是String类型，所以需要进行转化
        //把list集合的每个元素取出来并且以逗号分隔，最终拼成一个字符串
        String data = StringUtils.join(dateList, ",");


        //2.营业额：是和日期一一对应的，所以需要遍历获取每天的日期，然后查询数据库把每天的营业额查出来
        //         中间在以逗号隔开。
        //当前集合用于存放存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();
        for (LocalDate date : dateList) {
            //查询data日期对应的营业额数据，营业额是指：状态为“已完成”的订单金额合计
            //获取的date（每日）只含有年月日没有体现时分秒，而这个order_time下单时间是
            //      LocalDateTime类型，既有年月日又有时分秒，所以要查询date这一天的
            //      订单就需要来计算这一天的起始时间是从什么时刻开始（当天的0时0分0秒），
            //      这一天的结束时间是从什么时候结束（11:59:59，无限接近下一天的0时0分0秒）。
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);//获取当天的开始时间：年月日时分秒
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);//获取当天的结束时间：年月日时分秒（无限接近于下一天的0时0分0秒）

            //select sum(amount) from orders where order_time > ? and order_time < ? and status = 5;
            Map map = new HashMap();//封装sql所需要的参数为一个map集合
            map.put("begin",beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);//已完成
            Double turnover = orderMapper.sumByMap(map);//计算出来的营业额
            //假设这一天一个订单都没有，那么营业额应该是0.0才对，而这里查询出的来营业额
            //   为空显然不合理，所以如果返回为空的时候需要把它转化为0.0。
            turnover = turnover == null ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        //同样需要把集合类型转化为字符串类型并用逗号分隔
        String turnover = StringUtils.join(turnoverList, ",");


        //构建VO对象
        TurnoverReportVO trvo = TurnoverReportVO
                .builder()
                .dateList(data)
                .turnoverList(turnover)
                .build();

        return trvo;
    }


}

