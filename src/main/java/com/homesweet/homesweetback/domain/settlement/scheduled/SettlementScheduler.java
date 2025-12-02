package com.homesweet.homesweetback.domain.settlement.scheduled;
import com.homesweet.homesweetback.domain.settlement.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Component
public class SettlementScheduler {
    private final JobLauncher jobLauncher;
    private final Job settlementJob;
    // 정산의 모든 step(생성 ~ 연별 집계)-> 10초 간격으로 진행
//    @Scheduled(fixedRate = 10000)
    public void runSettlementCreateJob() {
        Map<String, JobParameter<?>> parameters = new HashMap<>();
        parameters.put("cutoff", new JobParameter<>(
                LocalDateTime.now().toString(), String.class, true
        ));
        parameters.put("time", new JobParameter<>(
                System.currentTimeMillis(), Long.class, true
        ));
        try {
            jobLauncher.run(settlementJob, new JobParameters(parameters));
            log.info("SettlementJob 실행");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        log.info("======== 정산 스케줄러 ========");
    }
}
