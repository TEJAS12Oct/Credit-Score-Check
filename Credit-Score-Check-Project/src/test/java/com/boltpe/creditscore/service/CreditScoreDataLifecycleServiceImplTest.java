package com.boltpe.creditscore.service;

import com.boltpe.creditscore.config.CreditScoreRetentionProperties;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.service.impl.CreditScorePdfStorageService;
import java.time.Instant;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.impl.CreditScoreDataLifecycleServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreditScoreDataLifecycleServiceImplTest {

 @Test
 void cleanupDisabledDoesNothing() {
  var repository = mock(CreditScoreReportRepository.class);

  var service = new CreditScoreDataLifecycleServiceImpl(
          repository,
          new CreditScoreRetentionProperties(false, 30),
          mock(CreditScorePdfStorageService.class),
          mock(com.boltpe.creditscore.repository.GetOtpRequestRepository.class),
          mock(com.boltpe.creditscore.repository.VendorApiLogRepository.class)
  );

  assertEquals(0, service.deleteExpiredReports());

  verifyNoInteractions(repository);
 }

    @Test
    void deletesExpiredAndUserReports() throws Exception {
  var repository = mock(CreditScoreReportRepository.class);

  var first = new CreditScoreReportEntity(); first.setPdfS3ObjectKey("key1");
  var second = new CreditScoreReportEntity(); second.setPdfS3ObjectKey("key2");
  var third = new CreditScoreReportEntity(); third.setPdfS3ObjectKey("key3");
  when(repository.findByFetchedAtBefore(any())).thenReturn(List.of(first, second, third));
  when(repository.findByUserId("U")).thenReturn(List.of(first, second));

  var storage = mock(CreditScorePdfStorageService.class);
  var service = new CreditScoreDataLifecycleServiceImpl(
          repository,
          new CreditScoreRetentionProperties(true, 30), storage,
          mock(com.boltpe.creditscore.repository.GetOtpRequestRepository.class),
          mock(com.boltpe.creditscore.repository.VendorApiLogRepository.class)
  );

  assertEquals(3, service.deleteExpiredReports());
  assertEquals(2, service.withdrawConsentAndDeleteCachedReports("u"));
  assertEquals(0, service.withdrawConsentAndDeleteCachedReports(" "));

  verify(repository).findByUserId("U");
  verify(storage, atLeastOnce()).delete("key1");
  verify(repository, times(2)).delete(first);
 }
 @Test void deleteS3FailurePreservesDatabaseRecord() throws Exception {
   var repo = mock(CreditScoreReportRepository.class);
   var storage = mock(CreditScorePdfStorageService.class);
   var entity = new CreditScoreReportEntity(); entity.setPdfS3ObjectKey("key");
   when(repo.findByUserId("U")).thenReturn(List.of(entity));
   doThrow(new IOException("S3 down")).when(storage).delete("key");
   var service = new CreditScoreDataLifecycleServiceImpl(repo,
       new CreditScoreRetentionProperties(true, 30), storage,
       mock(com.boltpe.creditscore.repository.GetOtpRequestRepository.class),
       mock(com.boltpe.creditscore.repository.VendorApiLogRepository.class));
   assertThrows(IllegalStateException.class, () -> service.withdrawConsentAndDeleteCachedReports("u"));
   verify(repo, never()).delete(entity);
 }
}