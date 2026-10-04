package com.wms.platformverification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real Surefire report landing probe; only an empty in-memory fixture lifecycle is executed. */
@EnabledIfSystemProperty(named = "p3.report.landing-authorized", matches = "true")
class PlatformReportLandingTest {
    @Test
    void writesPhaseBoundFixtureEvidenceWithoutDatabase() {
        assertEquals("false", System.getProperty("p3.r2.database-authorized"));
        assertEquals("false", System.getProperty("p3.r2.migration-authorized"));
        assertNull(System.getProperty("p3.test.jdbcUrl"));
        assertNull(System.getProperty("p3.r2.cluster-id"));

        String phase = System.getProperty("p3.report.phase");
        assertNotNull(phase, "A report phase is required");
        assertTrue(Set.of("independent", "sequential", "full").contains(phase), "Unexpected report phase");
        String invocation = System.getProperty("p3.report.invocation");
        assertNotNull(invocation, "A report invocation is required");
        UUID run = assertDoesNotThrow(() -> UUID.fromString(invocation));
        assertEquals(run.toString(), invocation, "The invocation must be a canonical UUID");

        System.out.println("PLATFORM_REPORT_INVOCATION run=" + run + " phase=" + phase);
        EmptyMemoryStore store = new EmptyMemoryStore();
        try (PlatformFixtureScope scope = PlatformFixtureScope.start(store,
                getClass().getName(), "writesPhaseBoundFixtureEvidenceWithoutDatabase")) {
            assertNotNull(scope.owner());
            assertEquals(0, store.transactions);
        }
        assertEquals(1, store.transactions, "The real fixture close must execute exactly once");
    }

    private static final class EmptyMemoryStore implements PlatformFixtureScope.Store {
        private int transactions;

        @Override public Set<Long> activeUsers() { return Set.of(); }
        @Override public Set<Long> pendingInvitations() { return Set.of(); }
        @Override public Set<Long> allUserIds() { return Set.of(); }
        @Override public Set<Long> allInvitationIds() { return Set.of(); }
        @Override public Set<Long> allTenantIds() { return Set.of(); }
        @Override public PlatformFixtureScope.User user(long id) { return null; }
        @Override public List<PlatformFixtureScope.Invitation> invitations(String email) { return List.of(); }
        @Override public boolean tenantExists(long id) { return false; }
        @Override public void transaction(Runnable work) { transactions++; work.run(); }
        @Override public void retireInvitation(PlatformFixtureScope.Invitation invitation) {
            fail("An empty report probe must not retire an invitation");
        }
        @Override public void retireUser(PlatformFixtureScope.User user) {
            fail("An empty report probe must not retire a user");
        }
    }
}
