package com.boltpe.creditscore.controller;

import com.boltpe.creditscore.dto.response.ApiResponse;
import com.boltpe.creditscore.dto.response.SubmitOtpResponse;
import com.boltpe.creditscore.entity.CreditScoreReportEntity;
import com.boltpe.creditscore.repository.CreditScoreReportRepository;
import com.boltpe.creditscore.service.CreditScoreReportService;
import com.boltpe.creditscore.service.CreditScoreAnalyticsService;
import com.boltpe.creditscore.service.impl.CreditScorePdfService;
import com.boltpe.creditscore.service.impl.CreditScorePdfStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/credit-score")
public class CreditScorePdfController {
    private static final String NOT_REPORTED = "Not Reported";
    private static final String ACTION_DOWNLOAD = "download";

    private final CreditScoreReportService reports;
    private final CreditScorePdfService pdf;
    private final CreditScorePdfStorageService storage;
    private final CreditScoreReportRepository repository;
    private final long reportValidityDays;
    private final CreditScoreAnalyticsService analytics;

    public CreditScorePdfController(CreditScoreReportService reports, CreditScorePdfService pdf,
            CreditScorePdfStorageService storage, CreditScoreReportRepository repository,
            @Value("${credit-score.pdf.report-validity-days:30}") long reportValidityDays,
            CreditScoreAnalyticsService analytics) {
        this.reports = reports;
        this.pdf = pdf;
        this.storage = storage;
        this.repository = repository;
        this.reportValidityDays = reportValidityDays;
        this.analytics = analytics;
    }

    /** All operations use a single POST endpoint. requestId is in X-Credit-Report-Id header. */
    @Transactional
    @PostMapping("/reports/pdf")
    public ResponseEntity<Object> generate(@RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Credit-Report-Id") String requestId,
            @RequestParam(defaultValue = "metadata") String action) throws IOException {
        if (!"metadata".equalsIgnoreCase(action) && !"view".equalsIgnoreCase(action)
                && !ACTION_DOWNLOAD.equalsIgnoreCase(action)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid PDF action");
        }
        CreditScoreReportEntity entity = requireValidReport(requestId, userId);
        Instant validUntil = entity.getFetchedAt().plus(Duration.ofDays(reportValidityDays));
        String key = ensurePdfStored(entity, requestId, userId);
        if ("metadata".equalsIgnoreCase(action)) {
            return metadataResponse(requestId, userId, key, validUntil);
        }
        return binaryResponse(requestId, userId, key, action);
    }

    private CreditScoreReportEntity requireValidReport(String requestId, String userId) {
        CreditScoreReportEntity entity = repository.findForPdfUpdate(requestId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
        if (!"SUCCESS".equalsIgnoreCase(entity.getStatus()) || entity.isNoHit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Successful score required");
        }
        Instant validUntil = entity.getFetchedAt().plus(Duration.ofDays(reportValidityDays));
        if (!validUntil.isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Report expired; complete OTP again");
        }
        return entity;
    }

    private String ensurePdfStored(CreditScoreReportEntity entity, String requestId, String userId) throws IOException {
        String key = entity.getPdfS3ObjectKey();
        // Regenerate PDFs stored under an obsolete template key.
        if (key != null && !key.isBlank() && !key.equals(storage.keyForReport(requestId))) {
            key = null;
        }
        if (key != null && !key.isBlank()) {
            return key;
        }
        ApiResponse<SubmitOtpResponse> stored = storedResponse(requestId, userId);
        if (stored.data() == null || !"SUCCESS".equalsIgnoreCase(stored.data().status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Successful report required");
        }
        String expectedKey = storage.keyForReport(requestId);
        boolean existingObject = storage.exists(expectedKey);
        key = existingObject ? expectedKey : storage.uploadForReport(requestId, pdf.render(stored.data()));
        entity.setPdfS3ObjectKey(key);
        entity.setPdfGeneratedAt(Instant.now());
        try {
            repository.saveAndFlush(entity);
        } catch (RuntimeException failure) {
            if (!existingObject) {
                try {
                    storage.delete(key);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        return key;
    }

    private ApiResponse<SubmitOtpResponse> storedResponse(String requestId, String userId) {
        return reports.findStoredResponse(requestId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report missing"));
    }

    private ResponseEntity<Object> binaryResponse(String requestId, String userId,
            String key, String action) throws IOException {
        boolean download = ACTION_DOWNLOAD.equalsIgnoreCase(action);
        String filename = pdfFilename(applicantFullName(reports.findStoredResponse(requestId, userId)
                .map(ApiResponse::data).orElse(null)));
        byte[] content = storage.download(key);
        analytics.record(download ? "cs_report_downloaded" : "cs_report_viewed", requestId, userId,
                "BACKEND_PDF_DELIVERED");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline")
                        + "; filename=\"" + filename + "\"")
                .body(content);
    }

    private ResponseEntity<Object> metadataResponse(String requestId, String userId,
            String key, Instant validUntil) {
        ApiResponse<SubmitOtpResponse> stored = storedResponse(requestId, userId);
        String fullName = applicantFullName(stored.data());
        long seconds = Duration.between(Instant.now(), validUntil).getSeconds();
        long days = (seconds + 86399) / 86400;
        var urls = storage.createPdfUrls(key, Duration.between(Instant.now(), validUntil), pdfFilename(fullName));
        analytics.record("cs_report_links_issued", requestId, userId, "PRESIGNED_S3_URLS");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new PdfGenerationResponse(
                fullName, "pdf_generated", urls.viewUrl(), urls.downloadUrl(),
                days, validUntil,
                "Credit score report is ready. You can view or download it during the 30-day availability period.", true));
    }

    static String pdfFilename(String fullName) {
        if (fullName == null || fullName.isBlank() || NOT_REPORTED.equalsIgnoreCase(fullName)) {
            return "credit-score-report.pdf";
        }
        String clean = java.text.Normalizer.normalize(fullName, java.text.Normalizer.Form.NFKD)
                .replaceAll("[^A-Za-z0-9 ]", " ").trim().replaceAll("\\s+", "_");
        return clean.isBlank() ? "credit-score-report.pdf" : clean + "_credit-score-report.pdf";
    }

    private static String applicantFullName(SubmitOtpResponse data) {
        if (data == null || data.creditReport() == null || data.creditReport().application() == null
                || data.creditReport().application().details() == null) return NOT_REPORTED;
        JsonNode applicant = data.creditReport().application().details()
                .path("currentApplicationDetails").path("currentApplicantDetails");
        StringBuilder name = new StringBuilder();
        for (String field : new String[] {"firstName", "middleName1", "lastName"}) {
            String value = applicant.path(field).asText("").trim();
            if (!value.isBlank() && !"null".equalsIgnoreCase(value)
                    && !"undefined".equalsIgnoreCase(value) && !"?".equals(value)
                    && !NOT_REPORTED.equalsIgnoreCase(value)) {
                if (!name.isEmpty()) name.append(' ');
                name.append(value);
            }
        }
        return name.isEmpty() ? NOT_REPORTED : name.toString();
    }

    public record PdfGenerationResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("full_name") String fullName,
            @com.fasterxml.jackson.annotation.JsonProperty("pdf_status") String pdfStatus,
            @com.fasterxml.jackson.annotation.JsonProperty("report_pdf_url") String reportPdfUrl,
            String downloadUrl, long remainingDays, Instant reportAvailableUntil,
            String message, boolean success) {}
}
