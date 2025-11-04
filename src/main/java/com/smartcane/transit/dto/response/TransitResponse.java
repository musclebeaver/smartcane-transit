// dto/response/TransitResponse.java
package com.smartcane.transit.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransitResponse(MetaData metaData) {}
