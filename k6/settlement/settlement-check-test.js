import http from "k6/http";
import { check, sleep } from "k6";

// 🔐 JWT Token
const TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMSIsImVtYWlsIjoiaHNrd29vbjdAZ21haWwuY29tIiwibmFtZSI6Iu2drOyImCIsInByb3ZpZGVyIjoiZ29vZ2xlIiwicm9sZSI6IlNFTExFUiIsImlhdCI6MTc2NTA3NzIwMiwiZXhwIjoxNzY1MDk1MjAyfQ.Wz7-jTn_QV69z1pOWSNyljvYiVUM8n89jdwIn8H5VLLPCNTBwetYEqeUVnbfaQ-tnTTAOPoshvNtKntlEhwyhg";

const BASE_URL = "http://localhost:8080/api/v1/settlement";
const USER_ID = 11;

export const options = {
    discardResponseBodies: true,

    scenarios: {
        daily_load: {
            executor: "constant-arrival-rate",
            rate: 50,                 // 50 RPS
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 50,
            maxVUs: 200,
            exec: "dailyScenario",
        },

        weekly_load: {
            executor: "constant-arrival-rate",
            rate: 10,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 20,
            maxVUs: 50,
            exec: "weeklyScenario",
        },
        monthly_load: {
            executor: "constant-arrival-rate",
            rate: 10,                 // 300 RPS
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 20,
            maxVUs: 100,
            exec: "monthlyScenario",
        },

        yearly_load: {
            executor: "constant-arrival-rate",
            rate: 5,
            timeUnit: "1s",
            duration: "5m",
            preAllocatedVUs: 10,
            maxVUs: 30,
            exec: "yearlyScenario",
        },
    },

    thresholds: {
        http_req_failed: ["rate<0.01"],     // 실패율 < 1%
        http_req_duration: ["p(95)<1500"],  // 95% 1.5초 이하
    },
};


// =============================
// 토큰 + GET 공통 요청 함수
// =============================
function authGet(url) {
    return http.get(url, {
        headers: {
            Authorization: `Bearer ${TOKEN}`,
        },
    });
}

// DAILY
export function dailyScenario() {
    const url = `${BASE_URL}/daily/${USER_ID}?startDate=2025-12-04&endDate=2025-12-05`;
    const res = authGet(url);
    check(res, { "daily status 200": (r) => r.status === 200 });
}
// WEEKLY
export function weeklyScenario() {
    const url = `${BASE_URL}/weekly/${USER_ID}?startDate=2025-12-01&endDate=2025-12-07`;
    const res = authGet(url);
    check(res, { "weekly status 200": (r) => r.status === 200 });
}

// MONTHLY
export function monthlyScenario() {
    const url = `${BASE_URL}/monthly/${USER_ID}?startDate=2025-12-01&endDate=2025-12-31`;
    const res = authGet(url);
    check(res, { "monthly status 200": (r) => r.status === 200 });
}


// YEARLY
export function yearlyScenario() {
    const url = `${BASE_URL}/yearly/${USER_ID}?startDate=2025-12-01&endDate=2025-12-31`;
    const res = authGet(url);
    check(res, { "yearly status 200": (r) => r.status === 200 });
}