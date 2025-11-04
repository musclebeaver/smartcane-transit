// dto/response/MetaData.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetaData(
        RequestParameters requestParameters,
        Plan plan
) {}
