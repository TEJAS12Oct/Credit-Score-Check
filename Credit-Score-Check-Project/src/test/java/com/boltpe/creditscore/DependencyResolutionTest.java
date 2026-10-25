package com.boltpe.creditscore;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.*;

/** Compile-time smoke check: PDFBox and AWS SDK must be on Maven's test classpath. */
class DependencyResolutionTest {
    @Test
    void pdfBoxAndS3TypesResolve() {
        assertNotNull(PDDocument.class);
        assertNotNull(S3Client.class);
        assertNotNull(S3Presigner.class);
    }
}
