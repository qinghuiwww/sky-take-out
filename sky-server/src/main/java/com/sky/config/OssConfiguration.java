package com.sky.config;

import com.sky.properties.AliOssProperties;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，用于创建AliOssUtil对象
 */
@Configuration
@Slf4j
public class OssConfiguration {
    /*在这个配置类中，定义了一个名为aliOssUtil的@Bean方法，用于创建一个AliOssUtil对象。*/
    @Bean
    /*@ConditionalOnMissingBean注解表示当不存在名为aliOssUtil的bean时，才会创建该bean。
      这意味着如果已经有其他地方定义了名为aliOssUtil的bean，那么这个方法将不会执行。*/
    @ConditionalOnMissingBean
    /*在方法体中，通过依赖注入的方式获取AliOssProperties对象，并使用它的属性值来创建AliOssUtil对象。*/
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties){
        log.info("开始创建阿里云文件上传工具类对象：{}",aliOssProperties);
        return new AliOssUtil(aliOssProperties.getEndpoint(),
                aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(),
                aliOssProperties.getBucketName());
    }

}
