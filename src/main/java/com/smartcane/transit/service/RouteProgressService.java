package com.smartcane.transit.service;

import com.smartcane.transit.dto.request.ArrivalCheckRequest;
import com.smartcane.transit.dto.request.ProgressUpdateEnvelope;
import com.smartcane.transit.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteProgressService {

    private final ProgressCoordinator coordinator;

    public ArrivalCheckResponse checkWalkStep(Itinerary itin, ArrivalCheckRequest req) {
        return coordinator.checkWalkStep(itin, req);
    }

    public ArrivalCheckResponse checkTransitLeg(Itinerary itin, ArrivalCheckRequest req) {
        return coordinator.checkTransitLeg(itin, req);
    }

    public GuidanceResponse updateProgress(String tripId, ProgressUpdateEnvelope envelope) {
        return coordinator.updateProgress(tripId, envelope);
    }
}
