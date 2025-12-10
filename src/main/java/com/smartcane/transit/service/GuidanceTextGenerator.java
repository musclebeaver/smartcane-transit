package com.smartcane.transit.service;

import com.smartcane.transit.config.GuidanceProperties;
import com.smartcane.transit.dto.response.ArrivalCheckResponse;
import com.smartcane.transit.dto.response.SkTransitRootDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GuidanceTextGenerator {

    private final GuidanceProperties props;

    private static long roundMeters(double m) {
        if (Double.isNaN(m) || Double.isInfinite(m)) return 0L;
        return Math.round(m);
    }

    private Double parseNextStepMeters(String nextInstruction) {
        if (nextInstruction == null) return null;
        if (!nextInstruction.startsWith("NEXT_STEP:")) return null;
        try {
            String num = nextInstruction.substring("NEXT_STEP:".length()).trim();
            return Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String from(ArrivalCheckResponse arrival,
                       TripState state,
                       SkTransitRootDto.ItineraryDto itinerary,
                       SkTransitRootDto.LegDto currentLeg) {

        double remRaw = arrival.remainingMeters();
        double remain = (Double.isNaN(remRaw) || Double.isInfinite(remRaw) || remRaw < 0) ? 9999.0 : remRaw;
        long remM = roundMeters(remain);

        String rawMode = (currentLeg.mode() != null) ? currentLeg.mode() : "WALK";
        String mode = rawMode.toUpperCase();
        String phase = (state.getPhase() != null) ? state.getPhase() : "";

        Integer stopsLeft = arrival.stopsLeft(); // 이제 정상적으로 값이 들어옵니다.

        boolean isLastLeg = false;
        if (itinerary != null && itinerary.legs() != null && !itinerary.legs().isEmpty()) {
            int lastIdx = itinerary.legs().size() - 1;
            isLastLeg = state.getLegIndex() >= lastIdx;
        }

        String startName = (currentLeg.start() != null) ? currentLeg.start().name() : null;
        String endName   = (currentLeg.end()   != null) ? currentLeg.end().name()   : null;

        // ✅ [신규] 노선명 가져오기 (예: "간선:매월26")
        String routeName = (currentLeg.route() != null) ? currentLeg.route() : null;

        // -------------------------------
        // 0-1) WALK + 경로 이탈
        // -------------------------------
        boolean offRoute = arrival.offRoute();
        if ("WALK".equals(mode) && offRoute) {
            return "경로를 벗어났습니다. 조금 전 안내된 보행 경로 쪽으로 방향을 다시 잡아 주세요.";
        }

        // -------------------------------
        // 1) "도착" 판정
        // -------------------------------
        if (arrival.arrived()) {
            if ("WALK".equals(mode) && isLastLeg) {
                return "최종 목적지에 도착했습니다. 주변을 천천히 확인해 주세요.";
            }
            if ("WALK".equals(mode)) {
                return "도착 지점에 도달했습니다. 주변을 확인하시고 다음 대중교통 승강장을 찾아 이동해 주세요.";
            }
            if ("BUS".equals(mode) || "SUBWAY".equals(mode)) {
                return switch (phase) {
                    case "ONBOARD" -> "하차 지점에 도착했습니다. 천천히 내리신 후, 안전한 곳으로 이동해 주세요.";
                    case "TRANSFER" -> "환승 지점에 도착했습니다. 안내 표지판을 따라 이동해 주세요.";
                    case "ARRIVED" -> "최종 목적지에 도착했습니다.";
                    default -> "도착 지점에 도달했습니다.";
                };
            }
            return "도착 지점에 도달했습니다.";
        }

        // -------------------------------
        // 2) WALK 구간
        // -------------------------------
        if ("WALK".equals(mode)) {
            String stepDesc = arrival.currentInstruction();
            Double nextStepDistRaw = parseNextStepMeters(arrival.nextInstruction());
            Long nextStepM = null;
            if (nextStepDistRaw != null && !Double.isNaN(nextStepDistRaw)) {
                nextStepM = Math.max(0L, roundMeters(nextStepDistRaw));
            }

            Integer stepIdx = state.getStepIndex();
            Integer lastSpokenStepIdx = state.getLastSpokenStepIndex();
            boolean isNewStep = (stepIdx != null && !stepIdx.equals(lastSpokenStepIdx));

            if (isNewStep && stepDesc != null && !stepDesc.isBlank()) {
                state.setLastSpokenStepIndex(stepIdx);
                if (nextStepM != null) {
                    return String.format("%s. 다음 안내까지 약 %d미터 남았습니다.", stepDesc, nextStepM);
                } else {
                    return String.format("%s. 안내된 경로를 따라 계속 이동해 주세요.", stepDesc);
                }
            }
            if (nextStepM != null) {
                if (nextStepM <= 0) return "잠시 후 다음 안내가 있습니다. 속도를 줄이고 주변을 잘 살펴 주세요.";
                return String.format("다음 안내까지 약 %d미터 남았습니다. 안내된 경로를 따라 계속 이동해 주세요.", nextStepM);
            }
            if (remM <= 30) return "잠시 후 다음 안내가 있습니다.";
            return "안내된 경로를 따라 계속 이동해 주세요.";
        }

        // -------------------------------
        // 3) BUS 구간 (수정됨)
        // -------------------------------
        if ("BUS".equals(mode)) {
            String segment = (startName != null && endName != null)
                    ? String.format("%s에서 %s 방향 버스 구간입니다. ", startName, endName)
                    : "버스 구간입니다. ";

            // 1. [대기 중] 노선명 안내 추가
            if (TripState.PHASE_WAITING_TRANSIT.equals(phase)) {
                if (routeName != null) {
                    // 예: "잠시 후 간선:매월26 버스가 도착할 예정입니다."
                    return String.format("정류장에서 잠시 기다려 주세요. 잠시 후 %s 버스가 도착할 예정입니다.", routeName);
                } else {
                    return "정류장에서 잠시 기다려 주세요. 버스가 도착하면 안내해 드리겠습니다.";
                }
            }

            // 2. [탑승 중] 남은 정거장 안내 (stopsLeft가 이제 정상적으로 들어옴)
            if (stopsLeft != null) {
                if (stopsLeft <= 0) {
                    return segment + "곧 하차 정류장입니다. 하차 벨을 누르고 내릴 준비를 해 주세요.";
                } else if (stopsLeft == 1) {
                    return segment + "다음 정류장에서 하차입니다. 이번 정류장을 지나면 하차 벨을 눌러주세요.";
                } else if (stopsLeft <= 3) {
                    return String.format("%s하차까지 %d정거장 남았습니다. 목적지가 가까워지고 있습니다.", segment, stopsLeft);
                } else {
                    return String.format("%s하차까지 약 %d정거장 남았습니다. 편안히 이동해 주세요.", segment, stopsLeft);
                }
            }

            // 3. [그 외] 데이터가 없을 때
            return switch (phase) {
                case TripState.PHASE_ONBOARD -> segment + "버스에 탑승 중입니다. 곧 정류장 안내를 시작합니다.";
                case TripState.PHASE_TRANSFER -> "환승 버스를 기다리는 구간입니다.";
                default -> segment + "정류장에서 버스를 기다려 주세요.";
            };
        }

        // 4) SUBWAY 구간 (기존 유지)
        if ("SUBWAY".equals(mode)) {
            // (지하철 로직은 기존과 동일하게 유지하거나 필요시 비슷하게 수정)
            return "지하철 구간입니다. 경로를 따라 이동해 주세요.";
        }

        return "경로를 따라 이동해 주세요.";
    }
}