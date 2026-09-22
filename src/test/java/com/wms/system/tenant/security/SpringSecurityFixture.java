package com.wms.system.tenant.security;

import com.wms.system.subscription.model.PlanCode;
import com.wms.system.subscription.repository.SubscriptionPlanRepository;
import org.springframework.mail.SimpleMailMessage;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/** Test-only isolation. No query replacement, foreign-row writes or real mail delivery. */
final class SpringSecurityFixture {
    static void requireTrial(SubscriptionPlanRepository repository) {
        var plan = repository.findByPlanCodeAndActiveTrue(PlanCode.TRIAL)
            .orElseThrow(() -> new IllegalStateException("BASELINE_TRIAL_MISSING"));
        if (plan.getId() == null || plan.getId() <= 0 || plan.getPlanCode() != PlanCode.TRIAL
            || !Boolean.TRUE.equals(plan.getActive()) || !Objects.equals(plan.getTrialDays(), 30)
            || plan.getFallbackPlanCode() != null)
            throw new IllegalStateException("BASELINE_TRIAL_MISMATCH");
    }
    static void requireOwnedMail(SimpleMailMessage message, long alert, long tenant, String recipient) {
        // AssertionError is deliberate: the product's catch(Exception) must NOT commit a retry
        // against an unexpected row. The real TransactionTemplate rolls that transaction back.
        if (!Objects.equals(message.getSubject(), "BCWMS platform safety alert #" + alert)
            || !Arrays.equals(message.getTo(), new String[]{recipient})
            || message.getText() == null || !message.getText().startsWith("Tenant #" + tenant + ": "))
            throw new AssertionError("UNOWNED_ALERT_DELIVERY_REFUSED");
    }
    static final class AlertLocks implements AutoCloseable {
        private final Connection connection;
        private final long alert;
        private final Map<Long,String> before;
        private boolean closed;
        private AlertLocks(Connection connection, long alert, Map<Long,String> before) {
            this.connection=connection; this.alert=alert; this.before=before;
        }
        static AlertLocks acquire(DataSource source, long alert, long tenant) throws SQLException {
            if (alert <= 0 || tenant <= 0) throw new IllegalArgumentException("INVALID_ALERT_OWNER");
            Connection connection=source.getConnection();
            try {
                connection.setAutoCommit(false);
                try (Statement settings=connection.createStatement()) {
                    settings.execute("set local statement_timeout=5000");
                    settings.execute("set local lock_timeout=2000");
                }
                try (PreparedStatement owned=connection.prepareStatement("select id from tenant_security_alerts where id=? and tenant_id=? and issue_key='TEST_MAIL'")) {
                    owned.setLong(1,alert); owned.setLong(2,tenant);
                    try (ResultSet rows=owned.executeQuery()) {
                        if (!rows.next() || rows.getLong(1)!=alert || rows.next()) throw new SQLException("OWNED_ALERT_MISSING_OR_AMBIGUOUS");
                    }
                }
                // Real row locks on a separate guarded connection make the UNCHANGED production
                // SKIP LOCKED query ignore other fixtures. Their values are never modified.
                Map<Long,String> before=snapshot(connection,alert,true);
                return new AlertLocks(connection,alert,before);
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.rollback(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                try { connection.close(); } catch (SQLException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        private static Map<Long,String> snapshot(Connection connection, long alert, boolean lock) throws SQLException {
            try (PreparedStatement query=connection.prepareStatement("select id,md5(row_to_json(a)::text) from tenant_security_alerts a where id<>? order by id"+(lock?" for update nowait":""))) {
                query.setLong(1,alert);
                Map<Long,String> result=new LinkedHashMap<>();
                try (ResultSet rows=query.executeQuery()) {
                    while(rows.next()) {
                        long id=rows.getLong(1); String hash=rows.getString(2);
                        if (id<=0 || hash==null || !hash.matches("[a-f0-9]{32}") || result.put(id,hash)!=null)
                            throw new SQLException("INVALID_FOREIGN_ALERT_FINGERPRINT");
                    }
                }
                return result;
            }
        }
        @Override public void close() throws SQLException {
            if(closed) return;
            SQLException primary=null;
            try { if(!before.equals(snapshot(connection,alert,false))) throw new SQLException("FOREIGN_ALERT_ROWS_CHANGED"); }
            catch(SQLException failure) { primary=failure; }
            try { connection.rollback(); } catch(SQLException failure) { if(primary==null) primary=failure; else primary.addSuppressed(failure); }
            try { connection.close(); } catch(SQLException failure) { if(primary==null) primary=failure; else primary.addSuppressed(failure); }
            closed=true;
            if(primary!=null) throw primary;
        }
    }
}
