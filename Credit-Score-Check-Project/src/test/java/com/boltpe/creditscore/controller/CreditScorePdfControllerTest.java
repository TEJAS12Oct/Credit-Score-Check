package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.CreditScoreReportService;
import com.boltpe.creditscore.service.CreditScoreAnalyticsService;
import com.boltpe.creditscore.service.impl.CreditScorePdfService;
import com.boltpe.creditscore.service.impl.CreditScorePdfStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.Duration;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditScorePdfControllerTest {
    private final CreditScoreReportRepository repository = mock(CreditScoreReportRepository.class);
    private final CreditScoreReportService reports = mock(CreditScoreReportService.class);
    private final CreditScorePdfService pdf = mock(CreditScorePdfService.class);
    private final CreditScorePdfStorageService storage = mock(CreditScorePdfStorageService.class);
    private final CreditScoreAnalyticsService analytics = mock(CreditScoreAnalyticsService.class);
    private final CreditScorePdfController controller = new CreditScorePdfController(reports, pdf, storage, repository, 30, analytics);

    CreditScorePdfControllerTest() {
        when(storage.keyForReport("r")).thenReturn("k");
    }

    private CreditScoreReportEntity entity(Instant fetched) {
        var e = new CreditScoreReportEntity();
        e.setRequestId("r"); e.setUserId("u"); e.setStatus("SUCCESS"); e.setFetchedAt(fetched);
        return e;
    }
    private void report() {
        var data = new SubmitOtpResponse("r", "SUCCESS", 841, "EXCELLENT", "Excellent", null, null, false, null, null);
        when(reports.findStoredResponse("r", "u")).thenReturn(Optional.of(new ApiResponse<>(true, "OK", "ok", data)));
    }

    @Test void missingOrWrongOwnerIsNotFound() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "metadata")).getStatusCode());
        verifyNoInteractions(pdf, storage);
    }
    @Test void expiredReportCannotBeViewedOrDownloaded() {
        when(repository.findForPdfUpdate("r", "u"))
                .thenReturn(Optional.of(entity(Instant.now().minus(Duration.ofDays(31)))));
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "view")).getStatusCode());
        verifyNoInteractions(pdf, storage);
    }
    @Test void doesNotGeneratePdfForFailedScore() {
        var e=entity(Instant.now()); e.setStatus("FAILED");
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "metadata")).getStatusCode());
    }
    @Test void noHitCannotGenerateOrViewPdf() {
        var e = entity(Instant.now()); e.setNoHit(true);
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "metadata")).getStatusCode());
        verifyNoInteractions(pdf, storage);
    }
    @Test void cannotIssueLinksAfterReportDeadline() {
        var e = entity(Instant.now().minus(Duration.ofDays(30).plusSeconds(2)));
        e.setPdfS3ObjectKey("already-generated");
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "metadata")).getStatusCode());
        verifyNoInteractions(storage);
    }
    @Test void reusesStoredPdfForMetadataAndDoesNotUploadAgain() throws Exception {
        var e=entity(Instant.now().minus(Duration.ofDays(10)));e.setPdfS3ObjectKey("k");
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e)); report();
        when(storage.createPdfUrls(eq("k"), any(Duration.class), anyString()))
                .thenReturn(new CreditScorePdfStorageService.PdfUrls(
                        "https://s3.example.invalid/view?X-Amz-Signature=view",
                        "https://s3.example.invalid/download?X-Amz-Signature=download"));
        var response=controller.generate("u", "r", "metadata");
        var body=(CreditScorePdfController.PdfGenerationResponse) response.getBody();
        assertNotNull(body); assertEquals(20,body.remainingDays());
        assertEquals("https://s3.example.invalid/view?X-Amz-Signature=view",body.reportPdfUrl());
        assertEquals("https://s3.example.invalid/download?X-Amz-Signature=download",body.downloadUrl());
        assertTrue(body.message().contains("view or download"));
        verifyNoInteractions(pdf);
        verify(storage).createPdfUrls(eq("k"), any(Duration.class), anyString());
    }
    @Test void firstRequestGeneratesOnceAndStoresS3Key() throws Exception {
        var e=entity(Instant.now());
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e)); report();
        when(pdf.render(any())).thenReturn(new byte[] {37,80,68,70});
        when(storage.uploadForReport(anyString(),any())).thenReturn("k");
        when(storage.createPdfUrls(eq("k"), any(Duration.class), anyString()))
                .thenReturn(new CreditScorePdfStorageService.PdfUrls("https://signed/view", "https://signed/download"));
        controller.generate("u", "r", "metadata");
        assertEquals("k", e.getPdfS3ObjectKey());
        assertNotNull(e.getPdfGeneratedAt());
        verify(repository).saveAndFlush(e);
        controller.generate("u", "r", "metadata");
        verify(storage,times(1)).uploadForReport(anyString(),any());
    }
    @Test void removesNewS3FileWhenImmediateDatabasePersistenceFails() throws Exception {
        var e = entity(Instant.now());
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        report();
        when(pdf.render(any())).thenReturn(new byte[] {37,80,68,70});
        when(storage.uploadForReport(anyString(),any())).thenReturn("k");
        when(repository.saveAndFlush(e)).thenThrow(new IllegalStateException("DB unavailable"));
        assertThrows(IllegalStateException.class, () -> controller.generate("u", "r", "metadata"));
        verify(storage).delete("k");
        verify(storage, never()).createPdfUrls(anyString(), any(Duration.class), anyString());
    }

    @Test void viewAndDownloadReturnSamePdfWithDifferentDisposition() throws Exception {
        var e=entity(Instant.now()); e.setPdfS3ObjectKey("k");
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        report();
        when(storage.download("k")).thenReturn(new byte[]{37,80,68,70});
        var view=controller.generate("u", "r", "view");
        var download=controller.generate("u", "r", "download");
        assertTrue(view.getHeaders().getFirst("Content-Disposition").startsWith("inline"));
        assertTrue(download.getHeaders().getFirst("Content-Disposition").startsWith("attachment"));
        assertArrayEquals((byte[])view.getBody(), (byte[])download.getBody());
    }
    @Test void reportValidityConfiguredAtTwoDays() {
        var shortController = new CreditScorePdfController(reports, pdf, storage, repository, 2, analytics);
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(entity(Instant.now().minus(Duration.ofDays(3)))));
        assertEquals(HttpStatus.GONE, assertThrows(ResponseStatusException.class, () -> shortController.generate("u", "r", "metadata")).getStatusCode());
    }
    @Test void unexpectedActionRejected() {
        var e=entity(Instant.now());e.setPdfS3ObjectKey("k");
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        assertEquals(HttpStatus.BAD_REQUEST,assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "other")).getStatusCode());
    }
    @Test void filenameFallsBackForMissingAndUnsupportedNames() {
        assertEquals("credit-score-report.pdf", CreditScorePdfController.pdfFilename(null));
        assertEquals("credit-score-report.pdf", CreditScorePdfController.pdfFilename(" "));
        assertEquals("credit-score-report.pdf", CreditScorePdfController.pdfFilename("Not Reported"));
        assertEquals("credit-score-report.pdf", CreditScorePdfController.pdfFilename("@@@"));
        assertEquals("Tejas_Jawale_credit-score-report.pdf", CreditScorePdfController.pdfFilename("Tejas  Jawale"));
    }

    @Test void existingObjectIsReusedWhenDatabaseKeyIsMissing() throws Exception {
        var e = entity(Instant.now());
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        report();
        when(storage.exists("k")).thenReturn(true);
        when(storage.createPdfUrls(eq("k"), any(Duration.class), anyString()))
                .thenReturn(new CreditScorePdfStorageService.PdfUrls("https://signed/view", "https://signed/download"));
        controller.generate("u", "r", "metadata");
        assertEquals("k", e.getPdfS3ObjectKey());
        verify(storage, never()).uploadForReport(anyString(), any());
        verifyNoInteractions(pdf);
    }

    @Test void existingObjectIsNotDeletedWhenDatabasePersistenceFails() throws Exception {
        var e = entity(Instant.now());
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        report();
        when(storage.exists("k")).thenReturn(true);
        when(repository.saveAndFlush(e)).thenThrow(new IllegalStateException("DB unavailable"));
        assertThrows(IllegalStateException.class, () -> controller.generate("u", "r", "metadata"));
        verify(storage, never()).delete(anyString());
        verify(storage, never()).uploadForReport(anyString(), any());
    }

    @Test void missingStoredResponsePreventsPdfGeneration() {
        var e = entity(Instant.now());
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "metadata")).getStatusCode());
        verifyNoInteractions(pdf);
    }

    @Test void failedStoredResponsePreventsPdfGeneration() {
        var e = entity(Instant.now());
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        var failed = new SubmitOtpResponse("r", "FAILED", 0, "", "", null, null, false, null, null);
        when(reports.findStoredResponse("r", "u"))
                .thenReturn(Optional.of(new ApiResponse<>(false, "FAILED", "failed", failed)));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> controller.generate("u", "r", "metadata")).getStatusCode());
        verifyNoInteractions(pdf);
    }

    @Test void obsoleteTemplateKeyIsReplacedWithCurrentKey() throws Exception {
        var e = entity(Instant.now());
        e.setPdfS3ObjectKey("obsolete/template.pdf");
        when(repository.findForPdfUpdate("r", "u")).thenReturn(Optional.of(e));
        report();
        when(storage.exists("k")).thenReturn(true);
        when(storage.createPdfUrls(eq("k"), any(Duration.class), anyString()))
                .thenReturn(new CreditScorePdfStorageService.PdfUrls("https://signed/view", "https://signed/download"));
        controller.generate("u", "r", "metadata");
        assertEquals("k", e.getPdfS3ObjectKey());
        verify(storage, never()).download("obsolete/template.pdf");
    }

}
