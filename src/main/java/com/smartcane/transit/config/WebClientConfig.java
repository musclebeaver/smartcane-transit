// config/WebClientConfig.java
package com.smartcane.transit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(SKTransitProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient skTransitWebClient(SKTransitProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl()) // ✅ getter 사용
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
