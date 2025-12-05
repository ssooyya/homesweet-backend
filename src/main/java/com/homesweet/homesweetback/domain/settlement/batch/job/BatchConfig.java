package com.homesweet.homesweetback.domain.settlement.batch.job;

import com.homesweet.homesweetback.domain.auth.entity.User;
import com.homesweet.homesweetback.domain.auth.repository.UserRepository;
import com.homesweet.homesweetback.domain.order.entity.Order;
import com.homesweet.homesweetback.domain.settlement.batch.listener.*;
import com.homesweet.homesweetback.domain.settlement.batch.step.aggregate.MonthlySettlementTasklet;
import com.homesweet.homesweetback.domain.settlement.batch.step.aggregate.WeeklySettlementTasklet;
import com.homesweet.homesweetback.domain.settlement.batch.step.aggregate.YearlySettlementTasklet;
import com.homesweet.homesweetback.domain.settlement.batch.step.cancel.SettlementCancelProcessor;
import com.homesweet.homesweetback.domain.settlement.batch.step.cancel.SettlementCancelReader;
import com.homesweet.homesweetback.domain.settlement.batch.step.cancel.SettlementCancelWriter;
import com.homesweet.homesweetback.domain.settlement.batch.step.create.SettlementCreateProcessor;
import com.homesweet.homesweetback.domain.settlement.batch.step.create.SettlementCreateReader;
import com.homesweet.homesweetback.domain.settlement.batch.step.create.SettlementCreateWriter;
import com.homesweet.homesweetback.domain.settlement.batch.step.aggregate.DailySettlementTasklet;
import com.homesweet.homesweetback.domain.settlement.batch.zeroOffset.ZeroOffsetItemReader;
import com.homesweet.homesweetback.domain.settlement.dto.response.SettlementCreateDto;
import com.homesweet.homesweetback.domain.settlement.entity.Settlement;
import com.homesweet.homesweetback.domain.settlement.repository.SettlementRepository;
import com.homesweet.homesweetback.domain.settlement.repository.querydsl.CustomSettlementRepository;
import com.homesweet.homesweetback.domain.settlement.util.calculator.SettlementCalculator;
import com.homesweet.homesweetback.domain.settlement.validation.SettlementValidator;
import lombok.RequiredArgsConstructor;
import org.hibernate.LazyInitializationException;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class BatchConfig {
    // 취소
    private final SettlementCancelProcessor settlementCancelProcessor;
    private final SettlementCancelWriter settlementCancelWriter;

    // listener
    private final SettlementJobListener settlementJobListener;
    private final SettlementSkipListener settlementSkipListener;
    private final SettlementStepListener  settlementStepListener;
    private final SettlementStepFailListener settlementStepFailListener;
    private final SettlementSlaMonitorListener settlementSLAMonitorListener;
    private final SettlementChunkListener settlementChunkListener;
    /**
    * 1. 정산 생성
    * 2. 정산 취소
    * 3. 일별 집계
    * 4. 주별 집계
    * 5. 월별 집계
    * 6. 연별 집계
    * */
    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settlementCreateStep, Step settlementCancelStep, Step dailyStep, Step weeklyStep, Step monthlyStep, Step yearlyStep) {
        return new JobBuilder("settlementJob", jobRepository)
                .listener(settlementJobListener)
                .start(settlementCreateStep)
                .next(settlementCancelStep)
                .next(dailyStep)
                .next(weeklyStep)
                .next(monthlyStep)
                .next(yearlyStep)
                .preventRestart()   // 같은 jobInstance 재실행 방지
                .build();
    }
    /**
     * Settlement 생성 Step
     * - Reader → 신규 정산 대상 Order 읽기
     * - Processor → Settlement 생성
     * - Writer → saveAll
     */
    // step1 -> 신규 주문건 정산 생성
    @Bean
    public Step settlementCreateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, SettlementCreateProcessor settlementCreateProcessor, SettlementCreateWriter settlementCreateWriter, ZeroOffsetItemReader zeroOffsetItemReader, StepUpdateFlagListener stepUpdateFlagListener) {
        return new StepBuilder("settlementCreateStep", jobRepository)
                .<SettlementCreateDto, Settlement>chunk(1000, transactionManager)
                .reader(zeroOffsetItemReader)
                .processor(settlementCreateProcessor)
                .writer(settlementCreateWriter)
                .listener(stepUpdateFlagListener)
                .listener(settlementSkipListener)
                .listener(settlementStepListener)
                .listener(settlementStepFailListener)
                .listener(settlementSLAMonitorListener)
                .listener(settlementChunkListener)
                .faultTolerant()
                .retry(Exception.class) //
                .retryLimit(3)
                .noSkip(Exception.class)
                .build();
    }
    // step2 -> 주문 취소건 정산 취소
    @Bean
    public Step settlementCancelStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, SettlementCancelReader settlementCancelReader) {
        return new StepBuilder("settlementCancelStep", jobRepository)
                .<Order, Settlement>chunk(1000, transactionManager)
                .reader(settlementCancelReader)
                .processor(settlementCancelProcessor)
                .writer(settlementCancelWriter)
                .listener(settlementSkipListener)
                .listener(settlementStepListener)
                .listener(settlementStepFailListener)
                .listener(settlementSLAMonitorListener)
                .listener(settlementChunkListener)
                .faultTolerant()
                .retry(Exception.class)
                .retryLimit(3)
                .noRetry(LazyInitializationException.class) // Lazy.. 재시도 X
                .noSkip(Exception.class)
                .build();
    }
    // step3 -> 일별 정산 집계
    @Bean
    public Step dailyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, DailySettlementTasklet dailySettlementTasklet) {
        return new StepBuilder("dailyStep", jobRepository)
                .tasklet(dailySettlementTasklet, transactionManager)
                .listener(settlementStepListener)
                .listener(settlementStepFailListener)
                .listener(settlementSLAMonitorListener)
                .build();
    }
    // step4 -> 주별 정산 집계
    @Bean
    public Step weeklyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, WeeklySettlementTasklet weeklySettlementTasklet) {
        return new StepBuilder("weeklyStep", jobRepository)
                .tasklet(weeklySettlementTasklet, transactionManager)
                .listener(settlementStepListener)
                .listener(settlementStepFailListener)
                .listener(settlementSLAMonitorListener)
                .build();
    }
    // step5 -> 월별 정산 집계
    @Bean
    public Step monthlyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, MonthlySettlementTasklet monthlySettlementTasklet) {
        return new StepBuilder("monthlyStep", jobRepository)
                .tasklet(monthlySettlementTasklet, transactionManager)
                .listener(settlementStepListener)
                .listener(settlementStepFailListener)
                .listener(settlementSLAMonitorListener)
                .build();
    }
    // step6 -> 연별 정산 집계
    @Bean
    public Step yearlyStep(JobRepository jobRepository, PlatformTransactionManager transactionManager, YearlySettlementTasklet yearlySettlementTasklet) {
        return new StepBuilder("yearlyStep", jobRepository)
                .tasklet(yearlySettlementTasklet, transactionManager)
                .listener(settlementStepListener)
                .listener(settlementStepFailListener)
                .listener(settlementSLAMonitorListener)
                .build();
    }
    @Bean
    @StepScope
    public ZeroOffsetItemReader zeroOffsetItemReader(
            @Value("#{jobParameters['cutoff']}") String cutoff,
            CustomSettlementRepository customSettlementRepository
    ) {
        return new ZeroOffsetItemReader(cutoff, customSettlementRepository);
    }

    //
    @Bean
    @StepScope
    public SettlementCreateProcessor settlementCreateProcessor(
            SettlementRepository settlementRepository,
            SettlementCalculator settlementCalculator, SettlementValidator settlementValidator){

        Map<Long, User> sellerCache = new HashMap<>();
        settlementRepository.findAllBySellerRole().forEach(seller ->
                sellerCache.put(seller.getId(), seller)
        );

        return new SettlementCreateProcessor(settlementCalculator, settlementValidator, sellerCache);
    }
}