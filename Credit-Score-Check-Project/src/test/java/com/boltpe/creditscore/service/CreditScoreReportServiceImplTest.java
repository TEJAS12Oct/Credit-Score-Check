package com.boltpe.creditscore.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.impl.CreditScoreReportServiceImpl;
import com.boltpe.creditscore.util.SensitiveDataEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreditScoreReportServiceImplTest {

 private SensitiveDataEncryptionService encryption() {

  byte[] keyBytes = new byte[16];

  new SecureRandom().nextBytes(keyBytes);

  String hexKey = HexFormat.of().formatHex(keyBytes);

  return new SensitiveDataEncryptionService(hexKey);
 }

 @Test
 void savesEncryptedNormalizedBoltpeReport() {

  CreditScoreReportRepository repository =
          mock(CreditScoreReportRepository.class);

  when(repository.findByRequestId("CS-1"))
          .thenReturn(Optional.empty());

  CreditScoreReportServiceImpl service =
          new CreditScoreReportServiceImpl(
                  repository,
                  new ObjectMapper(),
                  encryption()
          );

  SubmitOtpResponse data = new SubmitOtpResponse(
          "CS-1",
          "SUCCESS",
          774,
          "VERY_GOOD",
          "Strong profile",
          new SubmitOtpResponse.ReportMetadata(
                  "07102026",
                  "120000",
                  "R-1"
          ),
          new SubmitOtpResponse.CreditSummary(
                  5,
                  3,
                  2,
                  10000L,
                  1
          ),
          false,
          null,
          null
  );

  service.save(
          "CS-1",
          "user-1",
          "KYC_FLOW",
          ApiResponse.ok(
                  "CREDIT_SCORE_FETCHED",
                  "Credit score fetched successfully",
                  data
          )
  );

  ArgumentCaptor<CreditScoreReportEntity> captor =
          ArgumentCaptor.forClass(CreditScoreReportEntity.class);

  verify(repository).save(captor.capture());

  CreditScoreReportEntity savedEntity = captor.getValue();

  assertEquals(774, savedEntity.getScore());

  assertTrue(
          savedEntity.getBoltpeResponseBody().startsWith("enc:v1:")
  );

  assertFalse(
          savedEntity.getBoltpeResponseBody()
                  .contains("CREDIT_SCORE_FETCHED")
  );
 }

 @Test
 void replaysStoredEncryptedResponse() {

  CreditScoreReportRepository repository =
          mock(CreditScoreReportRepository.class);

  SensitiveDataEncryptionService encryptionService = encryption();

  ObjectMapper mapper = new ObjectMapper();

  SubmitOtpResponse data = new SubmitOtpResponse(
          "CS-1",
          "SUCCESS",
          774,
          "VERY_GOOD",
          "Strong",
          null,
          null,
          false,
          null,
          null
  );

  String json;

  try {
   json = mapper.writeValueAsString(
           ApiResponse.ok(
                   "CREDIT_SCORE_FETCHED",
                   "ok",
                   data
           )
   );
  } catch (Exception ex) {
   throw new IllegalStateException(
           "Unable to serialize test response",
           ex
   );
  }

  CreditScoreReportEntity entity =
          new CreditScoreReportEntity();

  entity.setBoltpeResponseBody(
          encryptionService.encrypt(json)
  );

  when(repository.findByRequestIdAndUserId("CS-1", "u"))
          .thenReturn(Optional.of(entity));

  CreditScoreReportServiceImpl service =
          new CreditScoreReportServiceImpl(
                  repository,
                  mapper,
                  encryptionService
          );

  var response = service.findStoredResponse("CS-1", "u");

  assertTrue(response.isPresent());

  assertEquals(
          774,
          response.get().data().score()
  );
 }

 @Test
 void savesNoHitWithoutOptionalReportOrSummaryAndPreservesCreatedAt() {

  CreditScoreReportRepository repository =
          mock(CreditScoreReportRepository.class);

  SensitiveDataEncryptionService encryptionService = encryption();

  CreditScoreReportEntity existing =
          new CreditScoreReportEntity();

  Instant createdAt =
          Instant.parse("2026-01-01T00:00:00Z");

  existing.setCreatedAt(createdAt);

  when(repository.findByRequestId("CS-2"))
          .thenReturn(Optional.of(existing));

  CreditScoreReportServiceImpl service =
          new CreditScoreReportServiceImpl(
                  repository,
                  new ObjectMapper(),
                  encryptionService
          );

  SubmitOtpResponse data = new SubmitOtpResponse(
          "CS-2",
          "NO_HIT",
          null,
          null,
          null,
          null,
          null,
          false,
          "No history",
          null
  );

  service.save(
          "CS-2",
          "user-2",
          "KYC_FLOW",
          ApiResponse.ok(
                  "NO_CREDIT_HISTORY",
                  "No credit history",
                  data
          )
  );

  ArgumentCaptor<CreditScoreReportEntity> captor =
          ArgumentCaptor.forClass(CreditScoreReportEntity.class);

  verify(repository).save(captor.capture());

  CreditScoreReportEntity savedEntity = captor.getValue();

  assertTrue(savedEntity.isNoHit());

  assertEquals(
          createdAt,
          savedEntity.getCreatedAt()
  );

  assertNull(savedEntity.getReportNumber());

  assertNull(savedEntity.getTotalAccounts());
 }

 @Test
 void returnsEmptyWhenStoredResponseDoesNotExist() {

  CreditScoreReportRepository repository =
          mock(CreditScoreReportRepository.class);

  when(repository.findByRequestIdAndUserId("missing", "u"))
          .thenReturn(Optional.empty());

  CreditScoreReportServiceImpl service =
          new CreditScoreReportServiceImpl(
                  repository,
                  new ObjectMapper(),
                  encryption()
          );

  var result = service.findStoredResponse("missing", "u");

  assertTrue(result.isEmpty());
 }

 @Test
 void wrapsCorruptStoredResponse() {

  CreditScoreReportRepository repository =
          mock(CreditScoreReportRepository.class);

  SensitiveDataEncryptionService encryptionService =
          mock(SensitiveDataEncryptionService.class);

  CreditScoreReportEntity entity =
          new CreditScoreReportEntity();

  entity.setBoltpeResponseBody("cipher");

  when(repository.findByRequestIdAndUserId("CS-3", "u"))
          .thenReturn(Optional.of(entity));

  when(encryptionService.decrypt("cipher"))
          .thenReturn("not-json");

  CreditScoreReportServiceImpl service =
          new CreditScoreReportServiceImpl(
                  repository,
                  new ObjectMapper(),
                  encryptionService
          );

  IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> service.findStoredResponse("CS-3", "u")
  );

  assertEquals(
          "Unable to read stored credit-score response",
          exception.getMessage()
  );
 }
}