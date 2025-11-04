// dto/response/CurrencyInfo.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrencyInfo(
        String symbol,
        String currency,
        String currencyCode
) {}
