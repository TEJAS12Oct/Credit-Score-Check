package com.boltpe.creditscore.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent, additive PostgreSQL schema compatibility migration.
 *
 * Runs at application startup, after the Spring/JPA context is initialized.
 * It only modifies tables that already exist; Hibernate's ddl-auto=update (or
 * an initial schema deployment) must create missing tables first.
 * Existing column types are not modified and no user data is deleted.
 */
@Component
public class DatabaseSchemaCompatibilityConfig implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaCompatibilityConfig.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${credit-score.schema-migration.enabled:true}")
    private boolean enabled;

    public DatabaseSchemaCompatibilityConfig(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enabled) {
            log.info("Credit score schema compatibility migration disabled");
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            if (!"PostgreSQL".equalsIgnoreCase(metadata.getDatabaseProductName())) {
                log.info("Skipping credit score schema migration for non-PostgreSQL database");
                return;
            }
        }

        migrateOtpRequests();
        migrateReports();
        migrateConsentAudit();
        migrateVendorLogs();
        log.info("Credit score additive schema compatibility migration completed");
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, tableName));
    }

    private void migrateOtpRequests() {
        if (!tableExists("credit_score_get_otp_request")) {
            return;
        }
        jdbcTemplate.execute("""
                ALTER TABLE credit_score_get_otp_request
                  ADD COLUMN IF NOT EXISTS version BIGINT,
                  ADD COLUMN IF NOT EXISTS otp_verification_attempts INTEGER NOT NULL DEFAULT 0,
                  ADD COLUMN IF NOT EXISTS otp_locked_until TIMESTAMP WITH TIME ZONE,
                  ADD COLUMN IF NOT EXISTS otp_verified_at TIMESTAMP WITH TIME ZONE,
                  ADD COLUMN IF NOT EXISTS otp_send_count INTEGER NOT NULL DEFAULT 0,
                  ADD COLUMN IF NOT EXISTS last_otp_sent_at TIMESTAMP WITH TIME ZONE
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_csr_user_created
                ON credit_score_get_otp_request (user_id, created_at)
                """);
    }

    private void migrateReports() {
        if (!tableExists("credit_score_report")) {
            return;
        }
        jdbcTemplate.execute("""
                ALTER TABLE credit_score_report
                  ADD COLUMN IF NOT EXISTS pdf_s3_object_key VARCHAR(600),
                  ADD COLUMN IF NOT EXISTS pdf_generated_at TIMESTAMP WITH TIME ZONE,
                  ADD COLUMN IF NOT EXISTS partial_match BOOLEAN NOT NULL DEFAULT FALSE,
                  ADD COLUMN IF NOT EXISTS no_hit BOOLEAN NOT NULL DEFAULT FALSE,
                  ADD COLUMN IF NOT EXISTS caution_message VARCHAR(500)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_credit_report_user_created
                ON credit_score_report (user_id, created_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_credit_report_score
                ON credit_score_report (score)
                """);
    }

    private void migrateConsentAudit() {
        if (!tableExists("credit_score_consent_audit")) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_consent_request
                ON credit_score_consent_audit (request_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_consent_user_created
                ON credit_score_consent_audit (user_id, created_at)
                """);
    }

    private void migrateVendorLogs() {
        if (!tableExists("credit_score_vendor_api_log")) {
            return;
        }
        // Remove obsolete Hibernate-generated enum constraint, if present.
        jdbcTemplate.execute("""
                ALTER TABLE credit_score_vendor_api_log
                DROP CONSTRAINT IF EXISTS credit_score_vendor_api_log_operation_check
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_vendor_log_request_operation
                ON credit_score_vendor_api_log (request_id, operation)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_vendor_log_user_created
                ON credit_score_vendor_api_log (user_id, created_at)
                """);
    }
}
