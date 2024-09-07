package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/**
 * 阿里云配置实体类
 */
@Component
/*请注意，为了使用@ConfigurationProperties注解，你需要在Spring Boot应用程序中启用配置绑定功能。
你可以通过在主应用程序类上添加@EnableConfigurationProperties注解来实现。*/
@ConfigurationProperties(prefix = "sky.alioss")
@Data
public class AliOssProperties {

    private String endpoint;//表示OSS服务的访问域名。
    private String accessKeyId;//表示访问OSS服务所需的Access Key ID。
    private String accessKeySecret;//表示访问OSS服务所需的Access Key Secret。
    private String bucketName;//表示要操作的存储桶名称。

}
