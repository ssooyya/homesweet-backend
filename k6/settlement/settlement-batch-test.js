import http from "k6/http";
import {check, sleep} from "k6";

/**
 * 요구사항
 * 1분에 1만건 -> 5만건 -> 10만 -> ... 건의 주문이 들어오는 환경
 * 10초단위 배치 실행마다 해당 10초 구간에 새로 생성된 정산 대상 주문
 * 정산 생성 -> 취소 -> 일/주/월/연 집계를 10초 이내에 완료
 * 집계는 해당 배치에 생성된 settlement 데이터 대상
 * */

// 배치 시작 API 2초내
export const options = {
    vus: 1,           // 오직 1명만 배치 실행
    duration: "2m",   // 2분 동안 테스트
    thresholds: {
        http_req_failed: ["rate < 0.05"],
        http_req_duration: ["p(95) < 2000"], // 2초
    },
};

// JWT token
const token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMSIsImVtYWlsIjoiaHNrd29vbjdAZ21haWwuY29tIiwibmFtZSI6Iu2drOyImCIsInByb3ZpZGVyIjoiZ29vZ2xlIiwicm9sZSI6IlNFTExFUiIsImlhdCI6MTc2NDIwMzE3MSwiZXhwIjoxNzY0MjIxMTcxfQ.V3-SiHupKTVm1W3l4x3F4zh5NAdGNRsoWZA0BUbQuKKKw44lDBHBlH_ruPMqBHMucuMgwCSkzfBYVohXZwsFZA";
export default function () {

    const headers = {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
    };
    const batchRes = http.post(
        "http://localhost:8080/api/v1/settlement/batch/run",
        null,
        headers
    );

    check(batchRes, {
        "settlement batch started": (r) => r.status === 200,
    });
    console.log(`[${new Date().toISOString()}] duration: ${batchRes.timings.duration}ms`);
    sleep(10);
}
