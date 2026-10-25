package com.boltpe.creditscore.service.impl;
import com.boltpe.creditscore.service.CreditScoreMetricsService;
import java.util.concurrent.atomic.LongAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
@Slf4j @Service
public class CreditScoreMetricsServiceImpl implements CreditScoreMetricsService {
 private final LongAdder calls=new LongAdder(), failures=new LongAdder(), totalMillis=new LongAdder();
 public void recordSubmitOtpDuration(long millis, boolean success){calls.increment();totalMillis.add(Math.max(0,millis));if(!success)failures.increment();log.info("credit_score_metric submitOtpMs={} success={}",millis,success);}
 long calls(){return calls.sum();} long failures(){return failures.sum();} long totalMillis(){return totalMillis.sum();}
}
