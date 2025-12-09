package com.smartcane.transit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration // 👈 이렇게 하면 스프링이 자동으로 찾아서 빈으로 등록합니다.
@ConfigurationProperties(prefix = "public-data.bus")
public class PublicDataProperties {
    private String baseUrl;
    private String serviceKey;
}