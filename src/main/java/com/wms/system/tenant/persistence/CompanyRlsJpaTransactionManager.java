package com.wms.system.tenant.persistence;

import com.wms.system.tenant.context.CompanyScope;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import com.wms.system.tenant.security.TenantExecutionScope;
import com.wms.system.tenant.security.TenantAccessException;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.lang.Nullable;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Binds PostgreSQL row-level security to the server-controlled company context.
 *
 * <p>The setting is transaction-local, so a pooled connection cannot leak one
 * company's identifier into the next request. APP and PLATFORM transactions
 * receive an impossible identifier and therefore see no tenant-owned rows.</p>
 */
public final class CompanyRlsJpaTransactionManager implements PlatformTransactionManager {

    public static final String COMPANY_SETTING = "app.company_id";

    private final JpaTransactionManager delegate;
    private final DataSource dataSource;
    private final boolean tenancyEnabled;

    public CompanyRlsJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource, boolean tenancyEnabled) {
        this.delegate = new JpaTransactionManager(entityManagerFactory);
        this.delegate.setDataSource(dataSource);
        this.dataSource = dataSource;
        this.tenancyEnabled = tenancyEnabled;
    }

    @Override
    public TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
            throws TransactionException {
        TransactionStatus status = delegate.getTransaction(definition);
        if (status.isCompleted()) {
            return status;
        }

        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT set_config('" + COMPANY_SETTING + "', ?, true)")) {
                statement.setString(1, Long.toString(resolveDatabaseCompanyId()));
                statement.execute();
            }
            TenantExecutionScope admission = TenantExecutionScope.current();
            if (admission != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "select bcwms_check_tenant_activity(?, ?, ?, ?)")) {
                    statement.setLong(1, admission.tenantId());
                    statement.setObject(2, admission.sessionVersion(), java.sql.Types.BIGINT);
                    statement.setString(3, admission.purpose().name());
                    statement.setBoolean(4, !connection.isReadOnly());
                    try (java.sql.ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("Tenant admission missing");
                        admission.pin(result.getLong(1));
                    }
                }
            }
            return status;
        } catch (Exception exception) {
            try {
                delegate.rollback(status);
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            if (exception.getMessage() != null && exception.getMessage().contains("AUTH_SESSION_INVALIDATED"))
                throw new TenantAccessException("AUTH_SESSION_INVALIDATED", 401);
            if (exception.getMessage() != null && exception.getMessage().contains("TENANT_SERVICE_UNAVAILABLE"))
                throw new TenantAccessException("TENANT_SERVICE_UNAVAILABLE", 423);
            throw new CannotCreateTransactionException(
                "Failed to bind PostgreSQL company row-level security", exception);
        }
    }

    @Override
    public void commit(TransactionStatus status) throws TransactionException {
        delegate.commit(status);
    }

    @Override
    public void rollback(TransactionStatus status) throws TransactionException {
        delegate.rollback(status);
    }

    private long resolveDatabaseCompanyId() {
        TenantContext context = TenantContextHolder.current().orElse(null);
        if (context != null
                && (context.surface() == RequestSurface.APP
                    || context.surface() == RequestSurface.PLATFORM)) {
            return CompanyTenantIdentifierResolver.NO_COMPANY_ID;
        }
        return CompanyScope.persistenceCompanyId(tenancyEnabled);
    }
}
