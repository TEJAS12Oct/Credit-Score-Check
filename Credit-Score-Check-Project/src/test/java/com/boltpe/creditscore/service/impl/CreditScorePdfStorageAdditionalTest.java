package com.boltpe.creditscore.service.impl;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreditScorePdfStorageAdditionalTest {
    private final S3Client s3 = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private CreditScorePdfStorageService service(String folder, long ttl) {
        return new CreditScorePdfStorageService(s3, presigner, "bucket", folder, ttl);
    }

    @Test void stableReportKeyValidatesIdentifierAndNormalizesFolder() {
        assertEquals("credit-score/reports/abc_123-layout-v2.pdf", service(null, 60).keyForReport("abc_123"));
        assertEquals("reports/credit-score/reports/a-b-layout-v2.pdf", service("///reports///", 60).keyForReport("a-b"));
        var validator = service("folder", 60);
        String oversizedId = "x".repeat(101);
        assertThrows(IllegalArgumentException.class, () -> validator.keyForReport(null));
        assertThrows(IllegalArgumentException.class, () -> validator.keyForReport("bad/id"));
        assertThrows(IllegalArgumentException.class, () -> validator.keyForReport(oversizedId));
    }

    @Test void existsChecksHeadAndTreatsOnly404AsMissing() {
        var storage = service("", 60);
        assertFalse(storage.exists(null));
        assertFalse(storage.exists("  "));
        assertTrue(storage.exists("key"));
        verify(s3).headObject(argThat((HeadObjectRequest r) -> r.bucket().equals("bucket") && r.key().equals("key")));
        reset(s3);
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(404).build());
        assertFalse(storage.exists("key"));
        reset(s3);
        when(s3.headObject(any(HeadObjectRequest.class))).thenThrow(S3Exception.builder().statusCode(403).build());
        assertThrows(S3Exception.class, () -> storage.exists("key"));
    }

    @Test void reportUploadValidatesPdfAndHandlesStorageFailure() throws Exception {
        var storage = service("folder", 60);
        assertThrows(IOException.class, () -> storage.uploadForReport("abc", null));
        byte[] emptyPdf = new byte[0];
        assertThrows(IOException.class, () -> storage.uploadForReport("abc", emptyPdf));
        assertEquals("folder/credit-score/reports/abc-layout-v2.pdf", storage.uploadForReport("abc", new byte[]{1, 2}));
        verify(s3).putObject(argThat((PutObjectRequest r) -> r.key().contains("abc-layout-v2.pdf")), any(RequestBody.class));
        reset(s3);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new IllegalStateException("S3 down"));
        byte[] samplePdf = {1};
        assertThrows(IOException.class, () -> storage.uploadForReport("abc", samplePdf));
    }

    @Test void urlValidationCoversBothOverloads() {
        var storage = service("", 60);
        assertThrows(IllegalArgumentException.class, () -> storage.createPdfUrls(null, Duration.ofMinutes(2)));
        assertThrows(IllegalArgumentException.class, () -> storage.createPdfUrls("key", null));
        assertThrows(IllegalArgumentException.class, () -> storage.createPdfUrls("key", Duration.ofSeconds(-1)));
        var invalidTtlStorage = service("", 0);
        Duration oneDay = Duration.ofDays(1);
        assertThrows(IllegalArgumentException.class, () -> invalidTtlStorage.createPdfUrls("key", oneDay));
        assertThrows(IllegalArgumentException.class, () -> storage.createPdfUrls("", Duration.ofMinutes(2), "report.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.createPdfUrls("key", Duration.ZERO, "report.pdf"));
        assertThrows(IllegalArgumentException.class, () -> storage.createPdfUrls("key", null, "report.pdf"));
        assertThrows(IllegalArgumentException.class, () -> invalidTtlStorage.createPdfUrls("key", oneDay, "report.pdf"));
        verifyNoInteractions(presigner);
    }

    @Test void customFilenameIsSanitizedAndTtlIsLimited() throws Exception {
        var signed = mock(PresignedGetObjectRequest.class);
        when(signed.url()).thenReturn(new URL("https://example.com/signed"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(signed);
        var urls = service("", 50000).createPdfUrls("key", Duration.ofDays(30), "my report\".pdf");
        assertEquals("https://example.com/signed", urls.viewUrl());
        var captor = org.mockito.ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner, times(2)).presignGetObject(captor.capture());
        assertEquals(Duration.ofDays(7), captor.getAllValues().get(0).signatureDuration());
        assertTrue(captor.getAllValues().get(0).getObjectRequest().responseContentDisposition().contains("my_report_.pdf"));
        assertTrue(captor.getAllValues().get(1).getObjectRequest().responseContentDisposition().startsWith("attachment"));
    }

    @Test void blankDeletionAndDownloadAreHandled() throws Exception {
        var storage = service("", 60);
        storage.delete(" ");
        verifyNoInteractions(s3);
        assertThrows(IOException.class, () -> storage.download(null));
        assertThrows(IOException.class, () -> storage.download("  "));
    }
}
