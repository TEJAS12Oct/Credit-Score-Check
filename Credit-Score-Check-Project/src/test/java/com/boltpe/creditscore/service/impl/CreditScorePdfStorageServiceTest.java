package com.boltpe.creditscore.service.impl;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import java.io.IOException;
import java.time.Duration;
import java.net.URL;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreditScorePdfStorageServiceTest {
    private final S3Client s3=mock(S3Client.class);
    private final S3Presigner presigner=mock(S3Presigner.class);
    private CreditScorePdfStorageService service() {
        return new CreditScorePdfStorageService(s3,presigner,"bucket","/reports/",10080);
    }
    @Test void signsTwoPrivateS3UrlsWithDifferentContentDisposition() throws Exception {
        var result=mock(PresignedGetObjectRequest.class);
        when(result.url()).thenReturn(new URL("https://bucket.s3.ap-south-1.amazonaws.com/key?X-Amz-Signature=example"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(result);
        var urls=service().createPdfUrls("reports/key.pdf", Duration.ofDays(30));
        assertTrue(urls.viewUrl().startsWith("https://"));
        assertTrue(urls.downloadUrl().contains("X-Amz-Signature"));
        var cap=ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner,times(2)).presignGetObject(cap.capture());
        var view=cap.getAllValues().get(0);
        var download=cap.getAllValues().get(1);
        assertEquals(Duration.ofDays(7), view.signatureDuration());
        assertEquals(Duration.ofDays(7), download.signatureDuration());
        assertTrue(view.getObjectRequest().responseContentDisposition().startsWith("inline"));
        assertTrue(download.getObjectRequest().responseContentDisposition().startsWith("attachment"));
        assertEquals("bucket",view.getObjectRequest().bucket());
    }
    @Test void signsOnlyUntilReportExpires() {
        var result=mock(PresignedGetObjectRequest.class);
        try { when(result.url()).thenReturn(new URL("https://example.invalid/file?sig=test")); }
        catch (java.net.MalformedURLException e) { throw new AssertionError(e); }
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(result);
        service().createPdfUrls("key",Duration.ofHours(2));
        var cap=ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner,times(2)).presignGetObject(cap.capture());
        assertEquals(Duration.ofHours(2),cap.getAllValues().get(0).signatureDuration());
    }
    @Test void honorsConfiguredMinutes() throws Exception {
        var presigned=mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.invalid/signed"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
        new CreditScorePdfStorageService(s3,presigner,"bucket","folder",30)
                .createPdfUrls("key", Duration.ofDays(30));
        var captor=ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner,times(2)).presignGetObject(captor.capture());
        assertEquals(Duration.ofMinutes(30),captor.getAllValues().get(0).signatureDuration());
    }
    @Test void configuredTtlNeverExceedsSevenDays() throws Exception {
        var presigned=mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://example.invalid/signed"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
        new CreditScorePdfStorageService(s3,presigner,"bucket","folder",43200)
                .createPdfUrls("key", Duration.ofDays(30));
        var captor=ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner,times(2)).presignGetObject(captor.capture());
        assertEquals(Duration.ofDays(7),captor.getAllValues().get(0).signatureDuration());
    }
    @Test void deletionUsesStoredObjectKey() throws Exception {
        service().delete("reports/key.pdf");
        verify(s3).deleteObject(argThat((DeleteObjectRequest req) ->
                req.bucket().equals("bucket") && req.key().equals("reports/key.pdf")));
        service().delete(null);
        verifyNoMoreInteractions(s3);
    }
    @Test void deletionFailureIsReported() {
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new IllegalStateException("failed"));
        assertThrows(IOException.class,()->service().delete("key"));
    }
    @Test void rejectsExpiredReportForSigning() {
        CreditScorePdfStorageService storageService = service();
        assertThrows(IllegalArgumentException.class, () -> storageService.createPdfUrls("key", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> storageService.createPdfUrls("", Duration.ofDays(1)));
        verifyNoInteractions(presigner);
    }
    @Test void rejectsEmptyPdf() {
        assertThrows(IOException.class,()->service().upload(null));
        assertThrows(IOException.class,()->service().upload(new byte[0]));
        verifyNoInteractions(s3);
    }
    @Test void uploadsIntoPrivateS3Object() throws Exception {
        String key=service().upload(new byte[]{37,80,68,70});
        assertTrue(key.startsWith("reports/credit-score/reports/"));
        assertTrue(key.endsWith(".pdf"));
        verify(s3).putObject(argThat((PutObjectRequest r)->"bucket".equals(r.bucket())
                        && key.equals(r.key()) && "application/pdf".equals(r.contentType())),any(RequestBody.class));
    }
    @Test void uploadStorageFailureIsCheckedIOException() {
        when(s3.putObject(any(PutObjectRequest.class),any(RequestBody.class))).thenThrow(new IllegalStateException("failed"));
        assertThrows(IOException.class,()->service().upload(new byte[]{1}));
    }
    @Test void downloadRetrievesStoredPdf() throws Exception {
        var result=ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{1,2,3});
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(result);
        assertArrayEquals(new byte[]{1,2,3},service().download("key"));
    }
    @Test void rejectsMissingKey() {
        assertThrows(IOException.class,()->service().download(""));
    }
    @Test void downloadStorageFailureIsCheckedIOException() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(new IllegalStateException("down"));
        assertThrows(IOException.class,()->service().download("key"));
    }
}
