package com.smartcane.transit.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "public-data.bus")
public class PublicDataProperties {
    private String arrivalBaseUrl; // arrival-base-url 매핑
    private String stationBaseUrl; // station-base-url 매핑
    private String serviceKey;     // service-key 매핑
}