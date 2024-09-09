package com.sky.task;


import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 自定义定时任务类
 */
@Component //需要交给Spring容器去管理
@Slf4j
public class MyTask {

    /**
     * 定时任务 每隔5秒触发一次
     *    定时任务具体处理的业务逻辑需要写在这个方法里面
     * @Scheduled(cron = "0/5 * * * * ?")：通过此注解指定方法什么时候触发，
     *     从第0秒开始每隔5秒开始触发。
     * 方法返回值类型是void，方法名任意。
     */
    //@Scheduled(cron = "0/5 * * * * ?")
    public void executeTask(){
        log.info("定时任务开始执行：{}",new Date());
    }
}

