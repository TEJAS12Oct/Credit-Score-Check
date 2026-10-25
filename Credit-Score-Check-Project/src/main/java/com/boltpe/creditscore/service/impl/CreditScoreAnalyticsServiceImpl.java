package com.boltpe.creditscore.service.impl;
import com.boltpe.creditscore.service.CreditScoreAnalyticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
@Slf4j @Service
public class CreditScoreAnalyticsServiceImpl implements CreditScoreAnalyticsService {
 public void record(String event,String requestId,String userId,String outcome){
  // Deliberately excludes PAN, mobile, OTP, vendor token, score and report payload.
  log.info("credit_score_event event={} requestId={} userId={} outcome={}", event, requestId, userId, outcome);
 }
}
