import http from "k6/http";
import { check, sleep } from "k6";

const token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMSIsImVtYWlsIjoiaHNrd29vbjdAZ21haWwuY29tIiwibmFtZSI6Iu2drOyImCIsInByb3ZpZGVyIjoiZ29vZ2xlIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3NjQyMzM2NjMsImV4cCI6MTc2NDI1MTY2M30.UpV5ryb_WONAVxw3QpVSyCU0f6JU5D9ZwwNdCVP0sJ9QPQsPc564A1gLtNRcYA-mJw6bzO7q2vL0bdGQbXfa-w"; // 여기에 실제 토큰 삽입

export const options = {
    scenarios: {
        batch_runner: { // 배치
            executor: "constant-vus",
            vus: 1,
            duration: "2m",        // 총 테스트 2분
            exec: "runBatchJob",
        },
        // settlement_viewer: { // 조회
        //     executor: "ramping-vus",
        //     startVUs: 0,
        //     stages: [
        //         { duration: "30s", target: 500 },
        //         { duration: "1m", target: 5000 },     // 조회 5000명
        //         { duration: "30s", target: 0 },
        //     ],
        //     exec: "viewSettlement",
        // },
    },

    thresholds: {
        http_req_failed: ["rate < 0.05"],     // 오류율 5% 미만
        http_req_duration: ["p(95) < 2000"],  // 조회 API p95 < 2초
    },
};

// 배치 실행
export function runBatchJob() {
    const headers = {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
    };

    const res = http.post(
        "http://localhost:8080/api/v1/settlement/batch/run",
        null,
        { headers }
    );

    check(res, {
        "Batch started": (r) => r.status === 200 || r.status === 409, // 이미 실행 중인 경우도 OK
    });

    console.log(`[Batch] status=${res.status} duration=${res.timings.duration}ms`);

    sleep(10); // SLA: 10초 간격 배치
}

// 조회
// export function viewSettlement() {
//     const headers = {
//         Authorization: `Bearer ${token}`,
//     };
//
//     const startDate = "2025-11-01";
//     const endDate = "2025-11-30";
//
//     const urls = [
//         `http://localhost:8080/api/v1/settlement/daily/11?startDate=${startDate}&endDate=${endDate}`,
//         `http://localhost:8080/api/v1/settlement/weekly/11?startDate=${startDate}&endDate=${endDate}`,
//         `http://localhost:8080/api/v1/settlement/monthly/11?startDate=${startDate}&endDate=${endDate}`,
//         `http://localhost:8080/api/v1/settlement/yearly/11?startDate=${startDate}&endDate=${endDate}`,
//     ];
//
//     // 무작위 조회 API 호출
//     const url = urls[Math.floor(Math.random() * urls.length)];
//
//     const res = http.get(url, { headers });
//
//     check(res, {
//         "Settlement 조회 성공": (r) => r.status === 200,
//     });
//
//     console.log(`[조회] ${url} - ${res.timings.duration}ms`);
//
//     sleep(1);
// }
