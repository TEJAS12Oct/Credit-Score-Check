package com.boltpe.creditscore.service.impl;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.CreditScoreReportService;
import com.boltpe.creditscore.util.SensitiveDataEncryptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CreditScoreReportServiceImpl implements CreditScoreReportService {
 private final CreditScoreReportRepository repository; private final ObjectMapper objectMapper; private final SensitiveDataEncryptionService encryption;
 public CreditScoreReportServiceImpl(CreditScoreReportRepository repository,ObjectMapper objectMapper,SensitiveDataEncryptionService encryption){this.repository=repository;this.objectMapper=objectMapper;this.encryption=encryption;}
 @Override public void save(String requestId,String userId,String vendor,ApiResponse<SubmitOtpResponse> response){
  SubmitOtpResponse data=response.data(); Instant now=Instant.now(); CreditScoreReportEntity entity=repository.findByRequestId(requestId).orElseGet(CreditScoreReportEntity::new);
  entity.setRequestId(requestId);entity.setUserId(userId);entity.setVendor(vendor);entity.setStatus(data.status());entity.setScore(data.score());entity.setScoreBand(data.band());entity.setInterpretation(data.interpretation());
  if(data.report()!=null){entity.setReportNumber(data.report().reportNumber());entity.setReportDate(data.report().reportDate());entity.setReportTime(data.report().reportTime());}
  if(data.summary()!=null){entity.setTotalAccounts(data.summary().totalAccounts());entity.setActiveAccounts(data.summary().activeAccounts());entity.setClosedAccounts(data.summary().closedAccounts());entity.setTotalOutstanding(data.summary().totalOutstanding());entity.setRecentEnquiries(data.summary().recentEnquiries());}
  entity.setPartialMatch(data.partialMatch());entity.setNoHit("NO_HIT".equals(data.status()));entity.setCautionMessage(data.cautionMessage());entity.setBoltpeResponseBody(encryption.encrypt(toJson(response)));entity.setFetchedAt(now);if(entity.getCreatedAt()==null)entity.setCreatedAt(now);entity.setUpdatedAt(now);repository.save(entity);
 }
 @Override public Optional<ApiResponse<SubmitOtpResponse>> findStoredResponse(String requestId,String userId){
  return repository.findByRequestIdAndUserId(requestId,userId).map(e->{try{return objectMapper.readValue(encryption.decrypt(e.getBoltpeResponseBody()),new TypeReference<ApiResponse<SubmitOtpResponse>>(){});}catch(Exception ex){throw new IllegalStateException("Unable to read stored credit-score response",ex);}});
 }
 private String toJson(Object value){try{return objectMapper.writeValueAsString(value);}catch(JsonProcessingException ex){throw new IllegalStateException("Unable to serialize standardized credit score response",ex);}}
}
