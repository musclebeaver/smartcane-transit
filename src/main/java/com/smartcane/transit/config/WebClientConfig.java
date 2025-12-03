// config/WebClientConfig.java
package com.smartcane.transit.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(SKTransitProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient skTransitWebClient(SKTransitProperties props) {
        log.info("SK Transit baseUrl = {}", props.getBaseUrl());
        if (props.getAppKey() == null) {
            log.error("SK Transit appKey 가 null 입니다! application.yml 설정 확인 필요");
        } else {
            log.info("SK Transit appKey prefix = {}****", props.getAppKey().substring(0, 4));
        }

        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("accept", "application/json")
                .defaultHeader("content-type", "application/json")
                .defaultHeader("appKey", props.getAppKey())
                .exchangeStrategies(
                        ExchangeStrategies.builder()
                                .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                                .build()
                )
                .build();
    }
}
