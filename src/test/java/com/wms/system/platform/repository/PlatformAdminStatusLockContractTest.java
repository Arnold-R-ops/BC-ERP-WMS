package com.wms.system.platform.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminStatusLockContractTest {
    @Test
    void seatTargetChallengeAndIdempotencyLookupsUseDatabaseWriteLocks() throws Exception {
        assertWriteLock(PlatformRoleRepository.class, "findByRoleCodeForUpdate", String.class);
        assertWriteLock(PlatformUserRepository.class, "findByIdForUpdate", Long.class);
        assertWriteLock(PlatformMfaChallengeRepository.class, "findByTokenHashForUpdate", String.class);
        assertWriteLock(PlatformAdminCommandRepository.class, "findByActorAndKeyForUpdate", Long.class, String.class);
    }

    @Test
    void targetVersionConflictsRemainDiagnosableAfterOwnedChallengesAreCleared() throws Exception {
        Query query = PlatformMfaChallengeRepository.class
            .getMethod("deletePendingOwnedByPlatformUserId", Long.class)
            .getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
            .contains("c.platformUserId = :platformUserId")
            .doesNotContain("c.targetPlatformUserId");
    }

    private void assertWriteLock(Class<?> repository, String method, Class<?>... parameters) throws Exception {
        Lock lock = repository.getMethod(method, parameters).getAnnotation(Lock.class);
        assertThat(lock).as(repository.getSimpleName() + "." + method + " lock").isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
