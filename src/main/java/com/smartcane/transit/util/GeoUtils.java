package com.smartcane.transit.util;

import java.util.ArrayList;
import java.util.List;
/**
 * 경로/거리 계산에 쓰이는 지오메트리 유틸 모음.
 * - 하버사인 거리(m) 계산
 * - "lon,lat lon,lat ..." 형태의 라인스트링 파싱
 * - 폴리라인 길이(m) 계산
 */
public final class GeoUtils {
    private GeoUtils() {}

    // 지구 반지름 (m)
    private static final double R = 6371000.0;
    /**
     * 하버사인(Haversine) 공식으로 두 좌표 간 거리(m)를 계산한다.
     */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2)*Math.sin(dLon/2);
        return 2*R*Math.asin(Math.sqrt(a));
    }

    /**
     * "lon,lat lon,lat ..." 형태의 라인스트링을 파싱하여
     * 리스트<double[]{lat, lon}> 로 변환한다.
     *  - 내부 계산 일관성을 위해 [lat, lon] 순서로 사용한다.
     */
    public static List<double[]> parseLineString(String line) {
        if (line == null || line.isBlank()) return List.of();
        String[] pairs = line.trim().split("\\s+");
        List<double[]> out = new ArrayList<>(pairs.length);
        for (String p : pairs) {
            String[] xy = p.split(",");
            if (xy.length == 2) {
                try {
                    double lon = Double.parseDouble(xy[0]);
                    double lat = Double.parseDouble(xy[1]);
                    out.add(new double[]{lat, lon});
                } catch (NumberFormatException ignored) {
                    // 좌표 파싱 실패 시 해당 포인트는 스킵
                }
            }
        }
        return out;
    }
    /**
     * 폴리라인(연속된 점들)의 총 길이(m)를 계산한다.
     */
    public static double polylineLength(List<double[]> pts) {
        double sum = 0;
        for (int i = 1; i < pts.size(); i++) {
            sum += haversine(pts.get(i-1)[0], pts.get(i-1)[1], pts.get(i)[0], pts.get(i)[1]);
        }
        return sum;
    }
}
