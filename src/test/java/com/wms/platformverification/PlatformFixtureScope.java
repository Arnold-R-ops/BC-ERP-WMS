package com.wms.platformverification;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Test-only ownership ledger. Never deletes identities, public roles, tenants or immutable audit.
 * Each scope starts with no live platform identity/invitation. Unknown live residue is an error,
 * not an instruction to adopt/clean it. Owned tenants and their company-scoped children are retained.
 */
public final class PlatformFixtureScope implements AutoCloseable {
    public record User(long id, String email) {
        @Override public String toString() { return "OwnedFixtureUser[id=" + id + "]"; }
    }
    public record Invitation(long id, long actor, String email, Long acceptedUser) {
        @Override public String toString() { return "OwnedFixtureInvitation[id=" + id + "]"; }
    }
    public interface Store {
        Set<Long> activeUsers();
        Set<Long> pendingInvitations();
        Set<Long> allUserIds();
        Set<Long> allInvitationIds();
        Set<Long> allTenantIds();
        User user(long id);
        List<Invitation> invitations(String email);
        boolean tenantExists(long id);
        void transaction(Runnable work);
        void retireInvitation(Invitation invitation);
        void retireUser(User user);
    }
    private final Store store;
    private final String owner;
    private final Map<Long, User> users = new LinkedHashMap<>();
    private final Map<String, Long> invitationIntents = new LinkedHashMap<>();
    private final Set<Long> tenants = new LinkedHashSet<>();
    private final Set<Long> initialUsers, initialInvitations, initialTenants;
    private boolean closed;

    private PlatformFixtureScope(Store store, String suite, String method) {
        this.store = store;
        if (!suite.matches("[A-Za-z0-9_.]+") || !method.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Unsafe scope label");
        owner = suite + "." + method + ":" + UUID.randomUUID();
        var liveUsers=store.activeUsers(); var liveInvitations=store.pendingInvitations();
        System.out.println("PLATFORM_FIXTURE_START owner="+owner+" activeUsers="+liveUsers.size()+" pendingInvitations="+liveInvitations.size());
        require(liveUsers.isEmpty() && liveInvitations.isEmpty(), "UNKNOWN_LIVE_FIXTURE_AT_START");
        initialUsers = Set.copyOf(store.allUserIds());
        initialInvitations = Set.copyOf(store.allInvitationIds());
        initialTenants = Set.copyOf(store.allTenantIds());
    }
    public static PlatformFixtureScope start(Store store, String suite, String method) {
        return new PlatformFixtureScope(store, suite, method);
    }
    public static PlatformFixtureScope open(JdbcTemplate jdbc, PlatformTransactionManager manager, String suite, String method) {
        // Authorization and exact target parsing happen before the first query; the supplied datasource
        // is additionally protected by PlatformR2Isolation's connection/cluster/history checks.
        PlatformR2Isolation.settings(PlatformR2Isolation.configuration(), false, PlatformR2Isolation.MAIN);
        return start(new JdbcStore(jdbc, manager), suite, method);
    }
    public long ownUser(long id) {
        require(!closed && !initialUsers.contains(id), "CANNOT_ADOPT_EXISTING_USER");
        User found = store.user(id);
        require(found != null && found.id() == id, "OWNED_USER_MISSING");
        User old = users.putIfAbsent(id, found);
        require(old == null || old.equals(found), "OWNED_USER_IDENTITY_CHANGED");
        return id;
    }
    public long ownTenant(long id) {
        require(!closed && id > 0 && !initialTenants.contains(id) && store.tenantExists(id), "OWNED_TENANT_MISSING_OR_PREEXISTING");
        tenants.add(id); return id;
    }
    public void expectInvitation(long actor, String email) {
        require(!closed && users.containsKey(actor) && email != null && email.equals(email.toLowerCase(Locale.ROOT)), "INVALID_INVITATION_OWNER");
        require(!invitationIntents.containsKey(email) && store.invitations(email).isEmpty(), "EXISTING_INVITATION_INTENT");
        invitationIntents.put(email, actor);
    }
    public void requireOwnedUser(long id) { require(users.containsKey(id), "UNREGISTERED_USER"); }
    public String owner() { return owner; }
    @Override public void close() {
        if (closed) return;
        List<Long> invitationIds = new ArrayList<>();
        store.transaction(() -> {
            // Resolve only pre-registered exact email + inviter intents. Never adopt by role or domain.
            List<Invitation> ownedInvitations = new ArrayList<>();
            for (var intent : invitationIntents.entrySet()) {
                List<Invitation> rows = store.invitations(intent.getKey());
                require(rows.size() <= 1, "DUPLICATE_OWNED_INVITATION");
                for (Invitation row : rows) {
                    require(!initialInvitations.contains(row.id()) && row.actor() == intent.getValue()
                        && row.email().equals(intent.getKey()), "INVITATION_OWNERSHIP_MISMATCH");
                    ownedInvitations.add(row); invitationIds.add(row.id());
                    if (row.acceptedUser() != null) {
                        User accepted = store.user(row.acceptedUser());
                        require(accepted != null && accepted.email().equals(row.email()), "ACCEPTED_USER_OWNERSHIP_MISMATCH");
                        ownUser(accepted.id());
                    }
                }
            }
            Set<Long> newUsers = new HashSet<>(store.allUserIds()); newUsers.removeAll(initialUsers);
            Set<Long> newInvitations = new HashSet<>(store.allInvitationIds()); newInvitations.removeAll(initialInvitations);
            Set<Long> newTenants = new HashSet<>(store.allTenantIds()); newTenants.removeAll(initialTenants);
            require(users.keySet().containsAll(newUsers) && new HashSet<>(invitationIds).containsAll(newInvitations), "UNKNOWN_NEW_FIXTURE_AT_CLOSE");
            require(tenants.containsAll(newTenants), "UNKNOWN_NEW_TENANT_AT_CLOSE");
            require(users.keySet().containsAll(store.activeUsers()) && new HashSet<>(invitationIds).containsAll(store.pendingInvitations()), "UNKNOWN_LIVE_FIXTURE_AT_CLOSE");
            for (User expected : users.values()) {
                User actual = store.user(expected.id());
                // Some existing fixtures explicitly delete their own rows; never recreate them.
                require(actual == null || actual.equals(expected), "OWNED_USER_IDENTITY_CHANGED");
            }
            // All ownership checks precede the first mutation, in one transaction.
            for (Invitation row : ownedInvitations) store.retireInvitation(row);
            for (User user : users.values()) if (store.user(user.id()) != null) store.retireUser(user);
            require(store.activeUsers().isEmpty() && store.pendingInvitations().isEmpty(), "FIXTURE_RETIREMENT_INCOMPLETE");
        });
        closed = true;
        System.out.println("PLATFORM_FIXTURE_SCOPE owner=" + owner + " userIds=" + users.keySet()
            + " invitationIds=" + invitationIds + " tenantIds=" + tenants + " status=RETIRED_AUDIT_RETAINED");
    }
    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalStateException(code);
    }
    static final class JdbcStore implements Store {
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transaction;
        JdbcStore(JdbcTemplate jdbc, PlatformTransactionManager manager) { this.jdbc=jdbc; transaction=new TransactionTemplate(manager); }
        public Set<Long> activeUsers() { return new HashSet<>(jdbc.queryForList("select id from platform_users where enabled=true", Long.class)); }
        public Set<Long> pendingInvitations() { return new HashSet<>(jdbc.queryForList("select id from platform_admin_invitations where accepted_at is null and revoked_at is null and expires_at>clock_timestamp()", Long.class)); }
        public Set<Long> allUserIds() { return new HashSet<>(jdbc.queryForList("select id from platform_users", Long.class)); }
        public Set<Long> allInvitationIds() { return new HashSet<>(jdbc.queryForList("select id from platform_admin_invitations", Long.class)); }
        public Set<Long> allTenantIds() { return new HashSet<>(jdbc.queryForList("select id from tenants", Long.class)); }
        public User user(long id) {
            var rows=jdbc.query("select id,normalized_email from platform_users where id=?", (r,n)->new User(r.getLong(1),r.getString(2)),id);
            require(rows.size()<=1,"DUPLICATE_USER"); return rows.isEmpty()?null:rows.get(0);
        }
        public List<Invitation> invitations(String email) {
            return jdbc.query("select id,invited_by_platform_user_id,normalized_email,accepted_platform_user_id from platform_admin_invitations where normalized_email=?",
                (r,n)->new Invitation(r.getLong(1),r.getLong(2),r.getString(3),r.getObject(4,Long.class)),email);
        }
        public boolean tenantExists(long id) { return jdbc.queryForObject("select count(*) from tenants where id=?",Integer.class,id)==1; }
        public void transaction(Runnable work) { transaction.executeWithoutResult(tx->work.run()); }
        public void retireInvitation(Invitation row) {
            jdbc.update("update platform_admin_invitations set revoked_at=coalesce(revoked_at,clock_timestamp()),revoked_by_platform_user_id=coalesce(revoked_by_platform_user_id,?) where id=? and normalized_email=? and invited_by_platform_user_id=? and accepted_at is null",
                row.actor(),row.id(),row.email(),row.actor());
            // Mutable reservation only; invitation/activation/audit rows are retained.
            jdbc.update("delete from platform_admin_invitation_active_emails where invitation_id=? and normalized_email=?",row.id(),row.email());
        }
        public void retireUser(User user) {
            require(jdbc.update("update platform_users set enabled=false,security_version=security_version+1 where id=? and normalized_email=?",user.id(),user.email())==1,"OWNED_USER_UPDATE_MISMATCH");
            jdbc.update("update platform_sessions set revoked_at=coalesce(revoked_at,clock_timestamp()) where platform_user_id=?",user.id());
            jdbc.update("update platform_mfa_challenges set consumed_at=coalesce(consumed_at,clock_timestamp()) where platform_user_id=?",user.id());
        }
    }
}
