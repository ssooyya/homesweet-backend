import http from "k6/http";
import { check, sleep } from "k6";

const TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMSIsImVtYWlsIjoiaHNrd29vbjdAZ21haWwuY29tIiwibmFtZSI6Iu2drOyImCIsInByb3ZpZGVyIjoiZ29vZ2xlIiwicm9sZSI6IlNFTExFUiIsImlhdCI6MTc2NTA3NzIwMiwiZXhwIjoxNzY1MDk1MjAyfQ.Wz7-jTn_QV69z1pOWSNyljvYiVUM8n89jdwIn8H5VLLPCNTBwetYEqeUVnbfaQ-tnTTAOPoshvNtKntlEhwyhg";
const BASE_URL = "http://localhost:8080/api/v1/settlement";
const USER_ID = 11;

export const options = {
    scenarios: {
        spike_test: {
            executor: "ramping-arrival-rate",
            startRate: 0,
            timeUnit: "1s",
            preAllocatedVUs: 300,
            maxVUs: 1000,

            stages: [
                // warm-up
                { duration: "10s", target: 100 },

                // sudden spike
                { duration: "1s", target: 2000 },

                // hold
                { duration: "10s", target: 2000 },

                // drop
                { duration: "5s", target: 50 },
            ],
            exec: "dailyScenario", // or weekly/monthly/yearly
        }
    }

    // discardResponseBodies: true,
    //
    // stages: [
    //     { duration: "30s", target: 50 },   // warmup
    //     { duration: "30s", target: 100 },
    //     { duration: "30s", target: 200 },
    //     { duration: "30s", target: 300 },
    //     { duration: "30s", target: 400 },
    //     { duration: "30s", target: 500 },
    //     { duration: "30s", target: 600 },  // max load
    // ],
    //
    // thresholds: {
    //     http_req_failed: ["rate<0.05"],       // 5% 이하면 pass
    //     http_req_duration: ["p(95)<2000"],    // stress니까 기준 넉넉히 2초
    // },
};

function authGet(url) {
    return http.get(url, {
        headers: { Authorization: `Bearer ${TOKEN}` },
    });
}

export default function () {
    const url = `${BASE_URL}/daily/${USER_ID}?startDate=2025-12-04&endDate=2025-12-05`;
    const res = authGet(url);

    check(res, { "status 200": (r) => r.status === 200 });
}
