import http from "k6/http";
import { check, sleep } from "k6";

// JWT Token
const TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMSIsImVtYWlsIjoiaHNrd29vbjdAZ21haWwuY29tIiwibmFtZSI6Iu2drOyImCIsInByb3ZpZGVyIjoiZ29vZ2xlIiwicm9sZSI6IlNFTExFUiIsImlhdCI6MTc2NTA3NzIwMiwiZXhwIjoxNzY1MDk1MjAyfQ.Wz7-jTn_QV69z1pOWSNyljvYiVUM8n89jdwIn8H5VLLPCNTBwetYEqeUVnbfaQ-tnTTAOPoshvNtKntlEhwyhg";
const BASE_URL = "http://localhost:8080/api/v1/settlement";
const USER_ID = 11;

export const options = {
    discardResponseBodies: true,

    stages: [
        { duration: "10s", target: 50 },    // Warm-up
        { duration: "5s", target: 500 },    // Spike #1
        { duration: "5s", target: 1000 },   // Spike #2
        { duration: "5s", target: 2000 },   // Spike #3
        { duration: "5s", target: 3000 },   // Spike #4 (upper limit)
        { duration: "10s", target: 30 },    // Cool down
        { duration: "5s", target: 0 },      // End
    ],

    thresholds: {
        http_req_failed: ["rate < 0.05"],     // 실패율 < 5%
        http_req_duration: ["p(95) < 2000"],  // 95%가 2초 미만
    },
};

// 공통 헤더
function authGet(url) {
    return http.get(url, {
        headers: { Authorization: `Bearer ${TOKEN}` },
    });
}

export default function () {
    const endpoints = [
        `${BASE_URL}/daily/${USER_ID}?startDate=2025-12-01&endDate=2025-12-05`,
        `${BASE_URL}/weekly/${USER_ID}?startDate=2025-12-01&endDate=2025-12-07`,
        `${BASE_URL}/monthly/${USER_ID}?startDate=2025-12-01&endDate=2025-12-31`,
        `${BASE_URL}/yearly/${USER_ID}?startDate=2025-12-01&endDate=2025-12-31`,
    ];

    const idx = Math.floor(Math.random() * endpoints.length);
    const res = authGet(endpoints[idx]);

    check(res, { "status 200": (r) => r.status === 200 });

    sleep(0.1);
}
