package com.boltpe.creditscore.service.impl;

import java.io.IOException;
import java.time.Duration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class CreditScorePdfStorageService {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;
    private final String folder;
    private final long urlTtlMinutes;

    public CreditScorePdfStorageService(S3Client s3, S3Presigner presigner,
            @Value("${credit-score.pdf.s3-bucket}") String bucket,
            @Value("${credit-score.pdf.s3-folder}") String folder,
            @Value("${credit-score.pdf.download-ttl-minutes:10080}") long urlTtlMinutes) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
        this.folder = normalizeFolder(folder);
        this.urlTtlMinutes = urlTtlMinutes;
    }

    private static String normalizeFolder(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }

    public String upload(byte[] pdf) throws IOException {
        if (pdf == null || pdf.length == 0) throw new IOException("PDF is empty");
        String key = (folder.isEmpty() ? "" : folder + "/")
                + "credit-score/reports/" + UUID.randomUUID() + ".pdf";
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType("application/pdf").build(), RequestBody.fromBytes(pdf));
            return key;
        } catch (RuntimeException e) {
            throw new IOException("PDF storage failure", e);
        }
    }

    /** Stable object key allows a later request to recover an upload whose DB commit failed. */
    public String keyForReport(String requestId) {
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("Invalid report identifier");
        }
        return (folder.isEmpty() ? "" : folder + "/") + "credit-score/reports/" + requestId + "-layout-v2.pdf";
    }

    public boolean exists(String key) {
        if (key == null || key.isBlank()) return false;
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return false;
            throw exception;
        }
    }

    public String uploadForReport(String requestId, byte[] pdf) throws IOException {
        if (pdf == null || pdf.length == 0) throw new IOException("PDF is empty");
        String key = keyForReport(requestId);
        try {
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key)
                    .contentType("application/pdf").build(), RequestBody.fromBytes(pdf));
            return key;
        } catch (RuntimeException exception) {
            throw new IOException("PDF storage failure", exception);
        }
    }

    /** Sign both URLs: the private bucket's ordinary object URL cannot be opened by users. */
    public PdfUrls createPdfUrls(String key, Duration remainingAccess) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("PDF object key missing");
        if (remainingAccess == null || remainingAccess.isZero() || remainingAccess.isNegative()) {
            throw new IllegalArgumentException("Report availability has expired");
        }
        // S3 SigV4 presigned URLs have a seven-day upper limit; do not sign beyond report expiry.
        if (urlTtlMinutes <= 0) throw new IllegalArgumentException("PDF link TTL must be positive");
        Duration configured = Duration.ofMinutes(urlTtlMinutes);
        Duration ttl = remainingAccess.compareTo(configured) < 0 ? remainingAccess : configured;
        if (ttl.compareTo(Duration.ofDays(7)) > 0) ttl = Duration.ofDays(7);
        return new PdfUrls(sign(key, ttl, false), sign(key, ttl, true));
    }

    public PdfUrls createPdfUrls(String key, Duration remainingAccess, String filename) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("PDF object key missing");
        if (remainingAccess == null || remainingAccess.isNegative() || remainingAccess.isZero())
            throw new IllegalArgumentException("Report availability has expired");
        if (urlTtlMinutes <= 0) throw new IllegalArgumentException("PDF link TTL must be positive");
        Duration ttl = remainingAccess.compareTo(Duration.ofMinutes(urlTtlMinutes)) < 0
                ? remainingAccess : Duration.ofMinutes(urlTtlMinutes);
        if (ttl.compareTo(Duration.ofDays(7)) > 0) ttl = Duration.ofDays(7);
        return new PdfUrls(sign(key, ttl, false, filename), sign(key, ttl, true, filename));
    }

    private String sign(String key, Duration ttl, boolean download) {
        return sign(key, ttl, download, "credit-score-report.pdf");
    }

    private String sign(String key, Duration ttl, boolean download, String filename) {
        String disposition = (download ? "attachment" : "inline")
                + "; filename=\"" + filename.replaceAll("[^A-Za-z0-9_.-]", "_") + "\"";
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(builder -> builder
                        .bucket(bucket).key(key)
                        .responseContentType("application/pdf")
                        .responseContentDisposition(disposition))
                .build();
        return presigner.presignGetObject(request).url().toExternalForm();
    }

    public record PdfUrls(String viewUrl, String downloadUrl) {}

    /** Delete an owned report object by its stored S3 key; never accept arbitrary client keys. */
    public void delete(String key) throws IOException {
        if (key == null || key.isBlank()) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            throw new IOException("Unable to delete stored PDF", e);
        }
    }

    public byte[] download(String key) throws IOException {
        if (key == null || key.isBlank()) throw new IOException("PDF object key missing");
        try {
            ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return bytes.asByteArray();
        } catch (RuntimeException e) {
            throw new IOException("Unable to retrieve PDF", e);
        }
    }
}
