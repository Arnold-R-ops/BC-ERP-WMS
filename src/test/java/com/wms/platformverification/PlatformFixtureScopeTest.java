package com.wms.platformverification;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** In-memory ownership/retirement checks and mocked JDBC SQL checks. No datasource or Spring boot. */
class PlatformFixtureScopeTest {
    static class Memory implements PlatformFixtureScope.Store {
        Map<Long,PlatformFixtureScope.User> users=new LinkedHashMap<>();
        Map<Long,PlatformFixtureScope.Invitation> invites=new LinkedHashMap<>();
        Set<Long> active=new HashSet<>(), pending=new HashSet<>(), tenants=new HashSet<>();
        List<Long> retiredUsers=new ArrayList<>(), retiredInvites=new ArrayList<>();
        int transactions; boolean failRetirement;
        public Set<Long> activeUsers(){return Set.copyOf(active);}
        public Set<Long> pendingInvitations(){return Set.copyOf(pending);}
        public Set<Long> allUserIds(){return Set.copyOf(users.keySet());}
        public Set<Long> allInvitationIds(){return Set.copyOf(invites.keySet());}
        public Set<Long> allTenantIds(){return Set.copyOf(tenants);}
        public PlatformFixtureScope.User user(long id){return users.get(id);}
        public List<PlatformFixtureScope.Invitation> invitations(String email){return invites.values().stream().filter(i->i.email().equals(email)).toList();}
        public boolean tenantExists(long id){return tenants.contains(id);}
        public void transaction(Runnable work){
            transactions++;var a=new HashSet<>(active);var p=new HashSet<>(pending);
            try{work.run();}catch(RuntimeException e){active=a;pending=p;throw e;}
        }
        public void retireInvitation(PlatformFixtureScope.Invitation i){pending.remove(i.id());retiredInvites.add(i.id());}
        public void retireUser(PlatformFixtureScope.User u){if(failRetirement)throw new IllegalStateException("synthetic-retirement-failure");active.remove(u.id());retiredUsers.add(u.id());}
        void addUser(long id){users.put(id,new PlatformFixtureScope.User(id,"owned-"+id+"@fixture.invalid"));active.add(id);}
        void invite(long id,long actor,String email,Long accepted){invites.put(id,new PlatformFixtureScope.Invitation(id,actor,email,accepted));if(accepted==null)pending.add(id);}
    }
    private PlatformFixtureScope scope(Memory m){return PlatformFixtureScope.start(m,"fixture.Suite","method");}
    @Test void missingDatabaseAuthorizationRefusesBeforeJdbcInteraction(){
        var jdbc=mock(JdbcTemplate.class);var manager=mock(PlatformTransactionManager.class);
        String before=System.getProperty("p3.r2.database-authorized");
        try{System.clearProperty("p3.r2.database-authorized");assertThrows(IllegalStateException.class,()->PlatformFixtureScope.open(jdbc,manager,"fixture.Suite","method"));verifyNoInteractions(jdbc,manager);}
        finally{if(before!=null)System.setProperty("p3.r2.database-authorized",before);}
    }
    @Test void startsAndClosesAnEmptyScope(){var m=new Memory();scope(m).close();assertEquals(1,m.transactions);}
    @Test void unknownActiveUserFailsBeforeAnyMutation(){var m=new Memory();m.addUser(9);assertThrows(IllegalStateException.class,()->scope(m));assertEquals(0,m.transactions);assertTrue(m.retiredUsers.isEmpty());}
    @Test void unknownPendingInvitationFailsBeforeAnyMutation(){var m=new Memory();m.invite(8,9,"outside@fixture.invalid",null);assertThrows(IllegalStateException.class,()->scope(m));assertTrue(m.retiredInvites.isEmpty());}
    @Test void cannotAdoptPreexistingInactiveUser(){var m=new Memory();m.addUser(9);m.active.clear();var f=scope(m);assertThrows(IllegalStateException.class,()->f.ownUser(9));f.close();assertEquals(Set.of(9L),m.users.keySet());assertTrue(m.retiredUsers.isEmpty());}
    @Test void cannotAdoptPreexistingTenant(){var m=new Memory();m.tenants.add(9L);var f=scope(m);assertThrows(IllegalStateException.class,()->f.ownTenant(9));f.close();assertTrue(m.tenants.contains(9L));}
    @Test void missingUserCannotBeRegistered(){var m=new Memory();var f=scope(m);assertThrows(IllegalStateException.class,()->f.ownUser(9));}
    @Test void registrationIsIdempotentOnlyForSameIdentity(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.ownUser(1);m.users.put(1L,new PlatformFixtureScope.User(1,"other@fixture.invalid"));assertThrows(IllegalStateException.class,()->f.ownUser(1));}
    @Test void retiresExactOwnedUsersAndRetainsRowsAndTenants(){var m=new Memory();var f=scope(m);m.addUser(1);m.addUser(2);f.ownUser(1);f.ownUser(2);m.tenants.add(3L);f.ownTenant(3);f.close();assertEquals(Set.of(1L,2L),m.users.keySet());assertEquals(Set.of(3L),m.tenants);assertTrue(m.active.isEmpty());assertEquals(List.of(1L,2L),m.retiredUsers);}
    @Test void unexpectedNewInactiveUserAlsoFailsNotAdopted(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);m.addUser(2);m.active.remove(2L);assertThrows(IllegalStateException.class,f::close);assertTrue(m.retiredUsers.isEmpty());}
    @Test void unknownTenantFailsBeforeRetirement(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);m.tenants.add(9L);assertThrows(IllegalStateException.class,f::close);assertTrue(m.retiredUsers.isEmpty());}
    @Test void changedOwnedIdentityFailsBeforeAnyMutation(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);m.users.put(1L,new PlatformFixtureScope.User(1,"changed@fixture.invalid"));assertThrows(IllegalStateException.class,f::close);assertTrue(m.retiredUsers.isEmpty());}
    @Test void unknownNewInvitationFailsBeforeAnyMutation(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);m.invite(2,1,"unknown@fixture.invalid",null);assertThrows(IllegalStateException.class,f::close);assertTrue(m.retiredUsers.isEmpty());assertTrue(m.retiredInvites.isEmpty());}
    @Test void invitationRequiresOwnedActor(){var m=new Memory();var f=scope(m);assertThrows(IllegalStateException.class,()->f.expectInvitation(1,"new@fixture.invalid"));}
    @Test void existingInvitationCannotBeAdopted(){var m=new Memory();m.invite(8,9,"existing@fixture.invalid",9L);var f=scope(m);m.addUser(1);f.ownUser(1);assertThrows(IllegalStateException.class,()->f.expectInvitation(1,"existing@fixture.invalid"));}
    @Test void duplicateIntentRejected(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");assertThrows(IllegalStateException.class,()->f.expectInvitation(1,"new@fixture.invalid"));}
    @Test void pendingOwnedInvitationIsRetiredNotDeleted(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");m.invite(2,1,"new@fixture.invalid",null);f.close();assertEquals(List.of(2L),m.retiredInvites);assertTrue(m.invites.containsKey(2L));assertTrue(m.pending.isEmpty());}
    @Test void rolledBackInvitationLeavesNoRowAndCleanupStillWorks(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");f.close();assertTrue(m.retiredInvites.isEmpty());}
    @Test void wrongInviterIsNotAdoptedByMatchingEmail(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");m.invite(2,99,"new@fixture.invalid",null);assertThrows(IllegalStateException.class,f::close);assertTrue(m.retiredInvites.isEmpty());}
    @Test void duplicateInvitationRowsFail(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");m.invite(2,1,"new@fixture.invalid",null);m.invite(3,1,"new@fixture.invalid",null);assertThrows(IllegalStateException.class,f::close);}
    @Test void acceptedUserOwnedOnlyThroughExactRegisteredInvitation(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");m.users.put(3L,new PlatformFixtureScope.User(3,"new@fixture.invalid"));m.active.add(3L);m.invite(2,1,"new@fixture.invalid",3L);f.close();assertEquals(List.of(1L,3L),m.retiredUsers);}
    @Test void acceptedUserEmailMismatchFails(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");m.addUser(3);m.invite(2,1,"new@fixture.invalid",3L);assertThrows(IllegalStateException.class,f::close);assertTrue(m.retiredUsers.isEmpty());}
    @Test void missingAcceptedUserFails(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");m.invite(2,1,"new@fixture.invalid",3L);assertThrows(IllegalStateException.class,f::close);}
    @Test void exactOwnedRowsAlreadyDeletedByExistingFixtureAreNotRecreated(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);m.users.clear();m.active.clear();f.close();assertTrue(m.retiredUsers.isEmpty());}
    @Test void closeIsIdempotentAndClosedScopeCannotAdopt(){var m=new Memory();var f=scope(m);f.close();f.close();assertEquals(1,m.transactions);m.addUser(1);assertThrows(IllegalStateException.class,()->f.ownUser(1));}
    @Test void retirementFailureDoesNotCommitPartialRetirement(){var m=new Memory();var f=scope(m);m.addUser(1);f.ownUser(1);f.expectInvitation(1,"new@fixture.invalid");m.invite(2,1,"new@fixture.invalid",null);m.failRetirement=true;assertThrows(IllegalStateException.class,f::close);assertEquals(Set.of(1L),m.active);assertEquals(Set.of(2L),m.pending);}
    @Test void nextScopeAfterOwnedRetirementHasSameEmptyLivePreconditions(){var m=new Memory();var a=scope(m);m.addUser(1);a.ownUser(1);a.close();var b=scope(m);m.addUser(2);b.ownUser(2);b.close();assertEquals(Set.of(1L,2L),m.users.keySet());assertTrue(m.active.isEmpty());}
    @Test void sqlRetirementTouchesOnlyExactIdentityAndMutableReservation(){
        var jdbc=mock(JdbcTemplate.class);var store=new PlatformFixtureScope.JdbcStore(jdbc,mock(PlatformTransactionManager.class));
        String update="update platform_users set enabled=false,security_version=security_version+1 where id=? and normalized_email=?";
        when(jdbc.update(update,1L,"owned@fixture.invalid")).thenReturn(1);
        store.retireUser(new PlatformFixtureScope.User(1,"owned@fixture.invalid"));
        verify(jdbc).update(update,1L,"owned@fixture.invalid");
        verify(jdbc).update("update platform_sessions set revoked_at=coalesce(revoked_at,clock_timestamp()) where platform_user_id=?",1L);
        verify(jdbc).update("update platform_mfa_challenges set consumed_at=coalesce(consumed_at,clock_timestamp()) where platform_user_id=?",1L);
        store.retireInvitation(new PlatformFixtureScope.Invitation(2,1,"new@fixture.invalid",null));
        verify(jdbc).update("update platform_admin_invitations set revoked_at=coalesce(revoked_at,clock_timestamp()),revoked_by_platform_user_id=coalesce(revoked_by_platform_user_id,?) where id=? and normalized_email=? and invited_by_platform_user_id=? and accepted_at is null",1L,2L,"new@fixture.invalid",1L);
        verify(jdbc).update("delete from platform_admin_invitation_active_emails where invitation_id=? and normalized_email=?",2L,"new@fixture.invalid");
        verifyNoMoreInteractions(jdbc);
    }
    @Test void sqlUpdateCountMismatchFailsBeforeSessionChanges(){var jdbc=mock(JdbcTemplate.class);var store=new PlatformFixtureScope.JdbcStore(jdbc,mock(PlatformTransactionManager.class));assertThrows(IllegalStateException.class,()->store.retireUser(new PlatformFixtureScope.User(1,"owned@fixture.invalid")));assertEquals(1,mockingDetails(jdbc).getInvocations().size());}
}
