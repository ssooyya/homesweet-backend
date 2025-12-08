package com.homesweet.homesweetback.domain.settlement.batch.job;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
public class SettlementBatchRunTest {
    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Test
    void runSettlementBatch() throws Exception {
        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("cutoff", "2031-01-01T00:00:00")
                        .addLong("timestamp", System.currentTimeMillis())
                        .toJobParameters()
        );

        System.out.println("Batch Execution Status = " + execution.getStatus());
        execution.getStepExecutions().forEach(step -> {
            System.out.println(step.getStepName() + " took = " + step.getReadCount() + " reads");
            System.out.println(step.getSummary());
        });
    }
}
