package com.homesweet.homesweetback.domain.order.adapter;

import com.homesweet.homesweetback.common.util.PaymentApiClient;
import com.homesweet.homesweetback.domain.order.dto.request.PaymentConfirmRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component; // @Service 대신 @Component 사용 (외부 시스템 어댑터)
import com.homesweet.homesweetback.common.exception.TossApiFailedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker; // 서킷 브레이커
import io.github.resilience4j.circuitbreaker.CallNotPermittedException; // 서킷 브레이커

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Component // @Service와 동일하게 Bean으로 등록되지만, '어댑터'임을 명시
@RequiredArgsConstructor
@Profile("Prod")
public class TossPaymentsAdapter {
    // 토스 목킹
    @Value("${payments.toss.mock:true}")
    private boolean tossMock;

    // --- PaymentService에서 가져온 의존성 ---
    private final PaymentApiClient paymentApiClient;

    @Value("${payments.toss.secretKey}")
    private String tossSecretKey;

    private static final String TOSS_CONFIRM_URL = "https://api.tosspayments.com/v1/payments/confirm";
    private static final String TOSS_CANCEL_URL_PREFIX = "https://api.tosspayments.com/v1/payments/";
    // --- 여기까지 ---


    /**
     * [API 1] 토스페이먼츠 결제 승인 API 호출
     * (PaymentService의 5단계 로직)
     * * @return 토스페이먼츠가 반환한 응답 Map
     */
    @CircuitBreaker(name = "toss-payments", fallbackMethod = "fallbackConfirmPayment") // 서킷 브레이커 적용
    public Map<String, Object> confirmPaymentToToss(PaymentConfirmRequest dto) {
//        TODO: 부하테스트 할 때 목킹서버를 따로 만들지 않는 경우, 스레드에 시간초를
//        try { Thread.sleep(2000); } catch (InterruptedException e) {}


        // 1) mock 활성화 시 실제 Toss API 호출 절대 X
        if (tossMock) {
            log.info("[MOCK] Toss 결제 승인 Mock 응답 반환");
            return Map.of(
                    "status", "DONE",                          // PaymentProcessor.paymentStatus
                    "method", "CARD",                           // PaymentProcessor.method
                    "paymentKey", dto.paymentKey(),             // PaymentProcessor.pgTransactionId
                    "paidAt", LocalDateTime.now().toString(),   // PaymentProcessor.paidAt
                    "totalAmount", dto.amount(),
                    "pgTransactionId", dto.paymentKey(),        // 선택: DB 저장시에도 필요할 수 있음
                    "result", "MOCK_SUCCESS"
            );
        }


        HttpHeaders headers = createAuthHeaders(); // 1. 헤더 생성 (공통 로직 분리)
        HttpEntity<PaymentConfirmRequest> requestEntity = new HttpEntity<>(dto, headers);

        //TODO: HTTP 호출 부분을 따로 클래스나 유틸로 뺴는게 좋다! v
        //TODO: 만약 API 호출 1번했는데 실패 하면 어떻게할것인지?(재처리) v
        try {
            // [수정] RestTemplate 직접 호출 -> paymentApiClient 호출
            // (여기서 실패하면 PaymentApiClient 내부에서 알아서 3번까지 재시도함)
            Map<String, Object> tossResponse = paymentApiClient.sendPostRequest(
                    TOSS_CONFIRM_URL,
                    requestEntity
            );

            // 3. 응답 상태 검증 (PaymentService 6단계 로직 일부)
            String status = (String) tossResponse.get("status");
            if (!"DONE".equals(status)) {
                log.error("토스 결제 승인 실패. Status: {}, OrderId: {}", status, dto.orderId());
                throw new RuntimeException("결제가 완료되지 않았습니다. 상태: " + status);
            }

            return tossResponse;

        } catch (Exception e) {
            log.error("토스페이먼츠 승인 API 호출 실패. OrderId: {}. Error: {}", dto.orderId(), e.getMessage());
            // (TODO: 토스 API 실패 시, 사용자 정의 예외(TossApiFailedException)를 던지도록 고도화)
            throw new TossApiFailedException("토스페이먼츠 승인 API 호출에 실패했습니다. " + e.getMessage());
        }
    }

    // Fallback 메서드 (비상구)
    // 규칙: 원본 메서드와 파라미터가 같아야 하고, 마지막에 Throwable을 받아야 합니다.
    public Map<String, Object> fallbackConfirmPayment(PaymentConfirmRequest dto, Throwable t) {

        // (1) 서킷이 열려서(Open) 차단된 경우인지 확인
        if (t instanceof CallNotPermittedException) {
            log.error("[Circuit Breaker] Toss API 차단됨 (Open State). 요청을 보내지 않고 즉시 거절합니다.");
        } else {
            log.error("[Circuit Breaker] Toss API 호출 중 에러 발생. Fallback 실행. 원인: {}", t.getMessage());
        }

        // (2) 사용자에게 보여줄 '안전한' 에러 메시지를 던집니다. (Fail-Fast)
        // 이렇게 하면 DB 커넥션을 잡지 않고 0.01초 만에 에러를 반환합니다.
        throw new RuntimeException("현재 결제량이 많아 시스템이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.");
    }

    /**
     * [API 2] 토스페이먼츠 결제 취소 API 호출
     * (PaymentService의 cancelOrder 5단계 로직)
     *
     * @return 토스페이먼츠가 반환한 응답 Map
     */
    public Map<String, Object> cancelPaymentToToss(String paymentKey, String cancelReason) {

        URI cancelUrl = URI.create(TOSS_CANCEL_URL_PREFIX + paymentKey + "/cancel");
        HttpHeaders headers = createAuthHeaders(); // 1. 헤더 생성 (공통 로직 분리)

        Map<String, String> bodyMap = Collections.singletonMap("cancelReason", cancelReason);
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(bodyMap, headers);

        try {
            Map<String, Object> tossResponse = paymentApiClient.sendPostRequest(
                cancelUrl.toString(), // (URI -> String 변환 필요)
                requestEntity
            );

            // 3. 응답 상태 검증 (PaymentService cancelOrder 6단계 로직)
            String status = (String) tossResponse.get("status");
            if (!"CANCELED".equals(status)) {
                log.warn("토스 결제 취소 응답 상태가 CANCELED가 아닙니다: {}", status);
                // (정책에 따라 예외를 던지거나, 경고만 남기고 응답을 반환할 수 있음)
            }

            return tossResponse;

        } catch (Exception e) {
            log.error("토스페이먼츠 취소 API 호출 실패: {}", e.getMessage());
            throw new TossApiFailedException("결제 취소 API 호출에 실패했습니다. " + e.getMessage());
        }
    }


    // --- [신규] 공통 헬퍼 메서드 ---
    /**
     * 토스페이먼츠 API 인증 헤더를 생성합니다.
     */
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String encodedKey = Base64.getEncoder().encodeToString((tossSecretKey + ":").getBytes(StandardCharsets.UTF_8));
        headers.setBasicAuth(encodedKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}