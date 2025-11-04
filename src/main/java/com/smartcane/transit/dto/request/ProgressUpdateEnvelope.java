package com.smartcane.transit.dto.request;

import com.smartcane.transit.dto.response.MetaData;

public record ProgressUpdateEnvelope(
        MetaData metaData,
        ProgressUpdateRequest progress
) {}
