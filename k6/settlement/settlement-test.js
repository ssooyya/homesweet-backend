import http from "k6/http";
import { sleep, check } from "k6";

// 조회테스트
export const options = {
    stages: [
        { duration: "30s", target: 0 },    // 2단계: 100명
        { duration: "1m", target: 700 },   // 3단계: 200명
        { duration: "30s", target: 0 },    // 종료
    ],
};
// 갱신될 때마다 변경
const token = "" +
    "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMSIsImVtYWlsIjoiaHNrd29vbjdAZ21haWwuY29tIiwibmFtZSI6Iu2drOyImCIsInByb3ZpZGVyIjoiZ29vZ2xlIiwicm9sZSI6IlNFTExFUiIsImlhdCI6MTc2NDE0MzE0NiwiZXhwIjoxNzY0MTYxMTQ2fQ.EayDtoLl9J9rX_f_vmfQsvC0mdjwYX9YLAzgFpT0ToefwoFLOrSxe5rZI4jb_TrBHHva0UCaA5YcgNqk_7ZQOg" +
    "";
export default function () {
    // const token = data.accessToken;
    const headers = {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
    };
    const res = http.get(
        "http://localhost:8080/api/v1/settlement/daily/11?startDate=2025-11-26&endDate=2025-11-26",
        { headers }
    );

    check(res, {
        "status is 200": (r) => r.status === 200,
    });
    console.log(`status=${res.status}, body=${res.body}`);
    sleep(1);
}




