package com.homesweet.homesweetback.domain.settlement.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SettlementBatchAsyncRunner {
    private final JobLauncher jobLauncher;
    private final Job settlementJob;

    @Async("batchTaskExecutor")
    public void runAsync(LocalDateTime cutoff) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("cutoff", cutoff.toString())
                    .addLong("timestamp", System.currentTimeMillis()) // 매번 새로운 JobInstance
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(settlementJob, params);

        } catch (
                JobExecutionAlreadyRunningException e) {
            ResponseEntity.status(409).body("SETTLEMENT BATCH ALREADY RUNNING");
        } catch (Exception e) {
            ResponseEntity.status(500).body("SETTLEMENT BATCH FAILED: " + e.getMessage());
        }
    }
}