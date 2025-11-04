// dto/response/Fare.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Fare(Regular regular) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Regular(
            Integer totalFare,
            CurrencyInfo currency
    ) {}
}
