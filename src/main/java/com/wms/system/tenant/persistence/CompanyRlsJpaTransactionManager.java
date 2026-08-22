package com.wms.system.tenant.persistence;

import com.wms.system.tenant.context.CompanyScope;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
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

    public CompanyRlsJpaTransactionManager(
            EntityManagerFactory entityManagerFactory,
            DataSource dataSource) {
        this.delegate = new JpaTransactionManager(entityManagerFactory);
        this.delegate.setDataSource(dataSource);
        this.dataSource = dataSource;
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
            return status;
        } catch (Exception exception) {
            try {
                delegate.rollback(status);
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
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
        return CompanyScope.currentCompanyId();
    }
}
