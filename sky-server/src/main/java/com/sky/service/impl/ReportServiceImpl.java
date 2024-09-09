package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
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
    @Autowired
    private UserMapper userMapper;

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

    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //1.准备日期条件：和营业额功能相同，不在赘述。
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //同样需要把集合类型转化为字符串类型并用逗号分隔
        String data = StringUtils.join(dateList, ",");

        //2.准备每一天对应的用户数量：总用户数量和新增用户数量    查询的是用户表
        List<Integer> newUserList = new ArrayList<>(); //此集合保存新增用户数量
        List<Integer> totalUserList = new ArrayList<>(); //此集合保存总用户数量

        /**
         * 思路分析：
         * 当天新增用户数量：只需要根据注册时间计算出当天的起始时间和结束时间作为查询条件，
         *                就是当天新增的用户数量。
         * 当天总用户数量：比如统计截止到4月1号这一天总的用户数量，意味着注册时间只要是在4月1号
         *              之前（包含4月1号）这一天的数量就可以了。
         * 没必要写2个sql，只需要写一个动态sql（动态的去拼接这2个连接条件）兼容这2个sql就可以了。
         */
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);//起始时间 包含年月日时分秒
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);//结束时间

            //新增用户数量 select count(id) from user where create_time > ? and create_time < ?
            Integer newUser = getUserCount(beginTime, endTime);
            //总用户数量 select count(id) from user where  create_time < ?
            Integer totalUser = getUserCount(null, endTime);

            newUserList.add(newUser);//把查询到的数据添加到集合中保存
            totalUserList.add(totalUser);
        }

        //同样需要把集合类型转化为字符串类型并用逗号分隔
        String newUser = StringUtils.join(newUserList, ",");
        String totalUser = StringUtils.join(totalUserList, ",");

        //封装vo返回结果
        return UserReportVO.builder()
                .dateList(data)
                .newUserList(newUser)
                .totalUserList(totalUser)
                .build();
    }

    /**
     * 根据时间区间统计用户数量
     * @param beginTime
     * @param endTime
     * @return
     */
    private Integer getUserCount(LocalDateTime beginTime, LocalDateTime endTime) {
        //封装sql查询的条件为map集合，因为设计的mapper层传递的参数是使用map来封装的
        Map map = new HashMap();
        map.put("begin",beginTime);
        map.put("end", endTime);
        return userMapper.countByMap(map);
    }

    /**
     * 根据时间区间统计订单数量
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end){
        //1.准备日期条件：和营业额功能相同，不在赘述。
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);

        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //同样需要把集合类型转化为字符串类型并用逗号分隔
        String data = StringUtils.join(dateList, ",");

        //2.准备每一天对应的订单数量：订单总数  有效订单数
        //每天订单总数集合
        List<Integer> orderCountList = new ArrayList<>();
        //每天有效订单数集合
        List<Integer> validOrderCountList = new ArrayList<>();

        /**
         * 思路分析：查询的是订单表
         * 查询每天的总订单数：只需要根据下单时间计算出当天的起始时间和结束时间作为查询条件，
         *                 就是当天的总订单数。
         * 查询每天的有效订单数：根据下单时间计算出当天的起始时间和结束时间以及状态已完成（代表有效订单）的订单作为查询条件，
         *                   就是每天的有效订单数
         * 同样没必要写2个sql，因为这2个SQL的主体结构相同，只是查询的条件不同，所以没有必要写2个sql只需要写一个动态的sql即可。
         */
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);//起始时间 包含年月日时分秒
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);//结束时间
            //查询每天的总订单数 select count(id) from orders where order_time > ? and order_time < ?
            Integer orderCount = getOrderCount(beginTime, endTime, null);

            //查询每天的有效订单数 select count(id) from orders where order_time > ? and order_time < ? and status = ?
            Integer validOrderCount = getOrderCount(beginTime, endTime, Orders.COMPLETED);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        //同样需要把集合类型转化为字符串类型并用逗号分隔
        String orderCount1 = StringUtils.join(orderCountList, ",");//每天订单总数集合
        String validOrderCount1 = StringUtils.join(validOrderCountList, ",");//每天有效订单数集合

        /**
         * 3. 准备时间区间内的订单数：时间区间内的总订单数   时间区间内的总有效订单数
         * 思路分析：
         *    订单总数：整个这个时间区域内订单总的数量，根据这个时间去卡查询数据库可以查询出来。
         *    时间区间内的总有效订单数：也可以通过查询数据库查出来。
         *    实际上不查询数据库，总订单数和总有效订单数也能计算出来：
         *         因为上面2个集合中已经查询保存了，这个时间段之内每天的总订单数和总有效订单数，
         *         所以只需要分别遍历这2个集合，每天的订单总数加一起就是整个时间段总订单数，
         *         每天的有效订单数加起来就是整个时间段的总有效订单数。
         */
        //计算时间区域内的总订单数量
        //Integer totalOrderCounts = orderCountList.stream().reduce(Integer::sum).get();//方式一：简写方式
        Integer totalOrderCounts = 0;
        for (Integer integer : orderCountList) {  //方式二：普通for循环方式
            totalOrderCounts = totalOrderCounts+integer;
        }
        //计算时间区域内的总有效订单数量
        //Integer validOrderCounts = validOrderCountList.stream().reduce(Integer::sum).get();//方式一：简写方式
        Integer validOrderCounts = 0;
        for (Integer integer : validOrderCountList) { //方式二：普通for循环方式
            validOrderCounts = validOrderCounts+integer;
        }

        //4.订单完成率：  总有效订单数量/总订单数量=订单完成率
        Double orderCompletionRate = 0.0;  //订单完成率的初始值
        if(totalOrderCounts != 0){ //防止分母为0出现异常
            //总有效订单数量和总有效订单数量都是Integer类型，这里使用的是Double类型接收所以需要进行转化
            orderCompletionRate = validOrderCounts.doubleValue() / totalOrderCounts;
        }

        //构造vo对象
        return OrderReportVO.builder()
                .dateList(data)  //x轴日期数据
                .orderCountList(orderCount1) //y轴每天订单总数
                .validOrderCountList(validOrderCount1)//y轴每天有效订单总数
                .totalOrderCount(totalOrderCounts) //时间区域内总订单数
                .validOrderCount(validOrderCounts) //时间区域内总有效订单数
                .orderCompletionRate(orderCompletionRate) //订单完成率
                .build();

    }

    /**
     * 根据时间区间统计指定状态的订单数量
     * @param beginTime
     * @param endTime
     * @param status
     * @return
     */
    private Integer getOrderCount(LocalDateTime beginTime, LocalDateTime endTime, Integer status) {
        //封装sql查询的条件为map集合，因为设计的mapper层传递的参数是使用map来封装的
        Map map = new HashMap();
        map.put("status", status);
        map.put("begin",beginTime);
        map.put("end", endTime);
        return orderMapper.countByMap(map);
    }


}

