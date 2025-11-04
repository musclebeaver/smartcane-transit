package com.smartcane.transit.util;

import java.util.List;

import static com.smartcane.transit.util.GeoUtils.haversine;

/**
 * 폴리라인 최근접 스냅 (정밀)
 * - 각 선분에 현재 위치를 '수직 투영'하여 선분 내부/끝점 중 실제로 가장 가까운 지점을 찾음
 * - 모든 계산을 '미터 단위의 로컬 평면(ENU)'로 수행하여 5~10m 수준 정밀도를 확보
 * - 반환:
 *   - snappedMetersFromStart: 시작점부터 최근접점까지 누적거리(m)
 *   - distanceToPolyline: 현재 위치와 최근접점 사이 거리(m)
 *   - snappedLat/Lon: 최근접점 좌표 (디버깅/시각화용)
 */
public final class PolylineSnapper {
    private PolylineSnapper() {}

    public static final class SnapResult {
        public final double snappedMetersFromStart;
        public final double distanceToPolyline;
        public final double snappedLat;
        public final double snappedLon;

        public SnapResult(double metersFromStart, double dist, double lat, double lon) {
            this.snappedMetersFromStart = metersFromStart;
            this.distanceToPolyline = dist;
            this.snappedLat = lat;
            this.snappedLon = lon;
        }
    }

    public static SnapResult snapToPolyline(double lat, double lon, List<double[]> pts) {
        if (pts == null || pts.isEmpty()) {
            return new SnapResult(0, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN);
        }
        if (pts.size() == 1) {
            double d = haversine(lat, lon, pts.get(0)[0], pts.get(0)[1]);
            return new SnapResult(0, d, pts.get(0)[0], pts.get(0)[1]);
        }

        // 누적거리 테이블 (m)
        final int n = pts.size();
        double[] acc = new double[n];
        for (int i = 1; i < n; i++) {
            acc[i] = acc[i - 1] + haversine(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1]);
        }

        // 로컬 ENU 기준점: 첫 점
        double refLat = pts.get(0)[0];
        double refLon = pts.get(0)[1];

        // 현재 위치를 ENU로 변환
        double[] Penu = wgs84ToENU(lat, lon, refLat, refLon);

        double bestDist = Double.POSITIVE_INFINITY;
        double bestAcc  = 0;
        double bestLat  = Double.NaN;
        double bestLon  = Double.NaN;

        // 각 선분 [A -> B] 에 대해 수직 투영
        for (int i = 1; i < n; i++) {
            double[] A = pts.get(i - 1);
            double[] B = pts.get(i);

            double[] Aenu = wgs84ToENU(A[0], A[1], refLat, refLon);
            double[] Benu = wgs84ToENU(B[0], B[1], refLat, refLon);

            // 벡터 계산 (미터)
            double vx = Benu[0] - Aenu[0];
            double vy = Benu[1] - Aenu[1];
            double wx = Penu[0] - Aenu[0];
            double wy = Penu[1] - Aenu[1];

            double segLen2 = vx*vx + vy*vy;
            double t = (segLen2 == 0) ? 0 : ((wx*vx + wy*vy) / segLen2); // 투영 스칼라

            double projx, projy;
            if (t <= 0) {                // A 쪽
                projx = Aenu[0]; projy = Aenu[1];
            } else if (t >= 1) {         // B 쪽
                projx = Benu[0]; projy = Benu[1];
            } else {                     // 선분 내부
                projx = Aenu[0] + t*vx;
                projy = Aenu[1] + t*vy;
            }

            // 현재점과 투영점 사이의 실제 거리(미터)
            double dx = Penu[0] - projx;
            double dy = Penu[1] - projy;
            double dist = Math.hypot(dx, dy);

            if (dist < bestDist) {
                bestDist = dist;

                // 투영점의 위경도 복원
                double[] projLL = enuToWGS84(projx, projy, refLat, refLon);
                bestLat = projLL[0];
                bestLon = projLL[1];

                // A→투영점까지의 길이(미터)
                double along = Math.hypot(projx - Aenu[0], projy - Aenu[1]);

                // 시작점→A까지의 누적 + A→투영점
                bestAcc = acc[i - 1] + along;
            }
        }

        return new SnapResult(bestAcc, bestDist, bestLat, bestLon);
    }

    // --- WGS84 <-> 로컬 ENU 근사 변환 (고도=0 가정, 수십~수백 m 스케일에서 충분히 정확) ---
    // 위도/경도를 기준(refLat, refLon)에서 동(E), 북(N) 미터로 변환
    private static double[] wgs84ToENU(double lat, double lon, double refLat, double refLon) {
        // 위도 1도 ≈ 111,320 m, 경도 1도 ≈ 111,320 * cos(lat) m (근사)
        double mPerDegLat = 111_320.0;
        double mPerDegLon = 111_320.0 * Math.cos(Math.toRadians(refLat));
        double e = (lon - refLon) * mPerDegLon; // 동쪽(+)
        double n = (lat - refLat) * mPerDegLat; // 북쪽(+)
        return new double[]{e, n};
    }

    // 로컬 ENU(미터) → 위도/경도
    private static double[] enuToWGS84(double e, double n, double refLat, double refLon) {
        double mPerDegLat = 111_320.0;
        double mPerDegLon = 111_320.0 * Math.cos(Math.toRadians(refLat));
        double lat = refLat + (n / mPerDegLat);
        double lon = refLon + (e / mPerDegLon);
        return new double[]{lat, lon};
    }
}
