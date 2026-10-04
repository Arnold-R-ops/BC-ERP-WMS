package com.wms.system.tenant.security;

import com.wms.system.subscription.model.*;
import com.wms.system.subscription.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpringSecurityFixtureTest {
    SubscriptionPlan validPlan() { return SubscriptionPlan.builder().id(2L).planCode(PlanCode.TRIAL).active(true).trialDays(30).build(); }
    SubscriptionPlanRepository plans(SubscriptionPlan plan) {
        var repository=mock(SubscriptionPlanRepository.class);
        when(repository.findByPlanCodeAndActiveTrue(PlanCode.TRIAL)).thenReturn(Optional.ofNullable(plan));return repository;
    }
    @Test void existingTrialIsReadOnly() { var repository=plans(validPlan());SpringSecurityFixture.requireTrial(repository);verify(repository).findByPlanCodeAndActiveTrue(PlanCode.TRIAL);verifyNoMoreInteractions(repository); }
    @Test void missingTrialIsNotCreated() { var repository=plans(null);assertThrows(IllegalStateException.class,()->SpringSecurityFixture.requireTrial(repository));verify(repository).findByPlanCodeAndActiveTrue(PlanCode.TRIAL);verifyNoMoreInteractions(repository); }
    @Test void inactiveTrialIsRejected() { var plan=validPlan();plan.setActive(false);assertThrows(IllegalStateException.class,()->SpringSecurityFixture.requireTrial(plans(plan))); }
    @Test void wrongCodeIsRejected() { var plan=validPlan();plan.setPlanCode(PlanCode.FREE);assertThrows(IllegalStateException.class,()->SpringSecurityFixture.requireTrial(plans(plan))); }
    @Test void wrongDurationIsRejected() { var plan=validPlan();plan.setTrialDays(31);assertThrows(IllegalStateException.class,()->SpringSecurityFixture.requireTrial(plans(plan))); }
    @Test void unexpectedFallbackIsRejected() { var plan=validPlan();plan.setFallbackPlanCode("FREE");assertThrows(IllegalStateException.class,()->SpringSecurityFixture.requireTrial(plans(plan))); }
    @Test void missingIdentityIsRejected() { var plan=validPlan();plan.setId(null);assertThrows(IllegalStateException.class,()->SpringSecurityFixture.requireTrial(plans(plan))); }
    SimpleMailMessage message() { var m=new SimpleMailMessage();m.setSubject("BCWMS platform safety alert #7");m.setTo("synthetic@p3.invalid");m.setText("Tenant #8: Synthetic");return m; }
    @Test void exactOwnedMailIsAcceptedWithoutSending() { SpringSecurityFixture.requireOwnedMail(message(),7,8,"synthetic@p3.invalid"); }
    @Test void otherAlertRaisesErrorNotCaughtAsDeliveryRetry() { var m=message();m.setSubject("BCWMS platform safety alert #9");assertThrows(AssertionError.class,()->SpringSecurityFixture.requireOwnedMail(m,7,8,"synthetic@p3.invalid")); }
    @Test void otherTenantIsRefused() { var m=message();m.setText("Tenant #9: Synthetic");assertThrows(AssertionError.class,()->SpringSecurityFixture.requireOwnedMail(m,7,8,"synthetic@p3.invalid")); }
    @Test void otherRecipientIsRefused() { var m=message();m.setTo("unknown@p3.invalid");assertThrows(AssertionError.class,()->SpringSecurityFixture.requireOwnedMail(m,7,8,"synthetic@p3.invalid")); }
    static class DatabaseMocks {
        final DataSource source=mock(DataSource.class); final Connection connection=mock(Connection.class);
        final Statement settings=mock(Statement.class); final PreparedStatement owned=mock(PreparedStatement.class),locked=mock(PreparedStatement.class),after=mock(PreparedStatement.class);
        final ResultSet ownedRows=mock(ResultSet.class),beforeRows=mock(ResultSet.class),afterRows=mock(ResultSet.class);
        DatabaseMocks() throws Exception {
            when(source.getConnection()).thenReturn(connection);when(connection.createStatement()).thenReturn(settings);
            when(connection.prepareStatement(contains("issue_key='TEST_MAIL'"))).thenReturn(owned);
            when(connection.prepareStatement(endsWith("for update nowait"))).thenReturn(locked);
            when(connection.prepareStatement(endsWith("order by id"))).thenReturn(after);
            when(owned.executeQuery()).thenReturn(ownedRows);when(ownedRows.next()).thenReturn(true,false);when(ownedRows.getLong(1)).thenReturn(7L);
            when(locked.executeQuery()).thenReturn(beforeRows);when(after.executeQuery()).thenReturn(afterRows);
            for(var rows:new ResultSet[]{beforeRows,afterRows}) {when(rows.next()).thenReturn(true,false);when(rows.getLong(1)).thenReturn(9L);when(rows.getString(2)).thenReturn("a".repeat(32));}
        }
    }
    @Test void independentLocksRollbackWithoutForeignWrites() throws Exception {
        var m=new DatabaseMocks();try(var locks=SpringSecurityFixture.AlertLocks.acquire(m.source,7,8)){}
        verify(m.connection).setAutoCommit(false);verify(m.connection).rollback();verify(m.connection).close();verify(m.connection,never()).commit();
        verify(m.owned).setLong(1,7);verify(m.owned).setLong(2,8);verify(m.locked).setLong(1,7);
        verify(m.locked,never()).executeUpdate();verify(m.after,never()).executeUpdate();
    }
    @Test void missingOwnedAlertReleasesConnection() throws Exception {
        var m=new DatabaseMocks();when(m.ownedRows.next()).thenReturn(false);
        assertThrows(SQLException.class,()->SpringSecurityFixture.AlertLocks.acquire(m.source,7,8));verify(m.connection).rollback();verify(m.connection).close();verifyNoInteractions(m.locked);
    }
    @Test void lockFailurePreservesFirstCauseAndReleasesConnection() throws Exception {
        var m=new DatabaseMocks();var first=new SQLException("LOCK_DENIED");when(m.locked.executeQuery()).thenThrow(first);doThrow(new SQLException("CLOSE_FAILED")).when(m.connection).close();
        assertSame(first,assertThrows(SQLException.class,()->SpringSecurityFixture.AlertLocks.acquire(m.source,7,8)));assertEquals(1,first.getSuppressed().length);verify(m.connection).rollback();
    }
    @Test void foreignMutationFailsAndStillReleasesLocks() throws Exception {
        var m=new DatabaseMocks();when(m.afterRows.getString(2)).thenReturn("b".repeat(32));
        var locks=SpringSecurityFixture.AlertLocks.acquire(m.source,7,8);
        assertEquals("FOREIGN_ALERT_ROWS_CHANGED",assertThrows(SQLException.class,locks::close).getMessage());verify(m.connection).rollback();verify(m.connection).close();
    }
    @Test void bodyFailureRemainsPrimaryWhenCloseFails() throws Exception {
        var m=new DatabaseMocks();when(m.afterRows.getString(2)).thenReturn("b".repeat(32));var first=new AssertionError("ORIGINAL_ASSERTION");
        var actual=assertThrows(AssertionError.class,()->{try(var locks=SpringSecurityFixture.AlertLocks.acquire(m.source,7,8)){throw first;}});
        assertSame(first,actual);assertEquals(1,actual.getSuppressed().length);verify(m.connection).close();
    }
    TenantSecuritySpringPostgresTest mailFixture() {
        var fixture=new TenantSecuritySpringPostgresTest(){@Override long newTenant(){return 8L;}};
        fixture.jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        fixture.fixtures=mock(com.wms.platformverification.PlatformFixtureScope.class);
        fixture.settings=new com.wms.system.tenant.config.TenantSecurityProperties();
        fixture.settings.setAlertRecipientIds(java.util.List.of(99L));fixture.settings.setControlsEnabled(false);
        fixture.mail=mock(org.springframework.mail.javamail.JavaMailSender.class);
        fixture.alerts=mock(TenantSecurityAlertService.class);
        when(fixture.jdbc.queryForObject(startsWith("insert into platform_users"),eq(Long.class),anyString())).thenReturn(7L);
        when(fixture.jdbc.queryForObject(startsWith("select normalized_email"),eq(String.class),eq(7L))).thenReturn("synthetic@p3.invalid");
        return fixture;
    }
    @Test void actualMailCaseRestoresConfigurationAndMockOnRecordFailure() {
        var fixture=mailFixture();var first=new IllegalStateException("SYNTHETIC_RECORD_FAILURE");
        doThrow(first).when(fixture.alerts).record(8L,"TEST_MAIL","No payload included.");
        doThrow(new AssertionError("OLD_MAIL_STUB")).when(fixture.mail).send(any(SimpleMailMessage.class));
        assertSame(first,assertThrows(IllegalStateException.class,fixture::alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate));
        assertEquals(java.util.List.of(99L),fixture.settings.getAlertRecipientIds());assertFalse(fixture.settings.isControlsEnabled());
        assertDoesNotThrow(()->fixture.mail.send(new SimpleMailMessage()));
    }
    @Test void actualMailCaseRestoresConfigurationAndMockOnLockFailure() throws Exception {
        var fixture=mailFixture();var source=mock(DataSource.class);var first=new SQLException("SYNTHETIC_CONNECTION_REFUSED");
        when(fixture.jdbc.queryForObject(startsWith("select id from tenant_security_alerts"),eq(Long.class),eq(8L))).thenReturn(7L);
        when(fixture.jdbc.getDataSource()).thenReturn(source);when(source.getConnection()).thenThrow(first);
        assertSame(first,assertThrows(SQLException.class,fixture::alertMailRetryTimesAreRelativeToFirstFailureAndEscalationIsImmediate));
        assertEquals(java.util.List.of(99L),fixture.settings.getAlertRecipientIds());assertFalse(fixture.settings.isControlsEnabled());
        assertDoesNotThrow(()->fixture.mail.send(new SimpleMailMessage()));verify(fixture.alerts,never()).deliverDue();
    }
}
