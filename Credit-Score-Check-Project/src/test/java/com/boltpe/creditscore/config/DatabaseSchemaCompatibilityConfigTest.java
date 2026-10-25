package com.boltpe.creditscore.config;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class DatabaseSchemaCompatibilityConfigTest {
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private DatabaseSchemaCompatibilityConfig migration;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        migration = new DatabaseSchemaCompatibilityConfig(dataSource, jdbcTemplate);
        ReflectionTestUtils.setField(migration, "enabled", true);
    }

    @Test
    void skipsNonPostgresDatabase() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        migration.run(new DefaultApplicationArguments());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void skipsMissingTablesWithoutFailing() throws Exception {
        configurePostgres();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), anyString()))
                .thenReturn(false);
        migration.run(new DefaultApplicationArguments());
        verify(jdbcTemplate, times(4)).queryForObject(anyString(), eq(Boolean.class), anyString());
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    void appliesAdditiveMigrationsToExistingTables() throws Exception {
        configurePostgres();
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), anyString()))
                .thenReturn(true);
        migration.run(new DefaultApplicationArguments());
        verify(jdbcTemplate, times(10)).execute(anyString());
    }

    @Test
    void skipsWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(migration, "enabled", false);
        migration.run(new DefaultApplicationArguments());
        verifyNoInteractions(dataSource, jdbcTemplate);
    }

    private void configurePostgres() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
    }
}
