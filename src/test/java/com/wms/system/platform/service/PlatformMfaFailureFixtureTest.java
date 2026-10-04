package com.wms.system.platform.service;

import com.wms.system.exception.BusinessException;
import com.wms.system.exception.ErrorKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class PlatformMfaFailureFixtureTest {
    static PlatformMfaFailureFixture.Before ready(){return new PlatformMfaFailureFixture.Before(1,0,0,1,1,4,4);}
    @Test void exactBranchPreconditionsPass(){List<String> out=new ArrayList<>();PlatformMfaFailureFixture.requireReady(ready(),out::add);assertEquals(1,out.size());assertTrue(out.get(0).contains("pendingSuperInvitations=0"));}
    static Stream<PlatformMfaFailureFixture.Before> invalid(){return Stream.of(
        new PlatformMfaFailureFixture.Before(0,0,0,1,1,4,4),new PlatformMfaFailureFixture.Before(2,0,0,1,1,4,4),
        new PlatformMfaFailureFixture.Before(1,1,1,1,1,4,4),new PlatformMfaFailureFixture.Before(1,0,1,1,1,4,4),
        new PlatformMfaFailureFixture.Before(1,0,0,0,1,4,4),new PlatformMfaFailureFixture.Before(1,0,0,1,0,4,4),
        new PlatformMfaFailureFixture.Before(1,0,0,1,2,4,4),new PlatformMfaFailureFixture.Before(1,0,0,1,1,3,4),
        new PlatformMfaFailureFixture.Before(1,0,0,1,1,5,4),new PlatformMfaFailureFixture.Before(1,0,0,1,1,4,3),
        new PlatformMfaFailureFixture.Before(1,0,0,1,1,4,5),new PlatformMfaFailureFixture.Before(1,0,0,0,0,-1,-1));}
    @ParameterizedTest @MethodSource("invalid") void wrongCountsOrChallengeStateStopBeforeInvocation(PlatformMfaFailureFixture.Before before){List<String> out=new ArrayList<>();assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.requireReady(before,out::add));assertEquals(1,out.size());}
    @Test void exactCredentialFailureTypeAndKeyPass(){List<String> out=new ArrayList<>();PlatformMfaFailureFixture.assertCredentialFailure(new PlatformAdminInvitationMfaCredentialException(1L,"private-challenge-hash"),1,"private-challenge-hash",out::add);assertTrue(out.get(0).contains("errorKey=AUTH_FAILED"));assertFalse(out.get(0).contains("private-challenge-hash"));}
    @Test void genericBusinessExceptionWithSameKeyDoesNotPass(){assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(new BusinessException(ErrorKeys.AUTH_FAILED),1,"hash",s->{}));}
    @Test void seatLimitIsReportedButCannotPass(){List<String> out=new ArrayList<>();assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(new BusinessException(ErrorKeys.PLATFORM_ADMIN_SUPER_ADMIN_LIMIT),1,"hash",out::add));assertTrue(out.get(0).contains(ErrorKeys.PLATFORM_ADMIN_SUPER_ADMIN_LIMIT));}
    @Test void noExceptionFails(){assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(null,1,"hash",s->{}));}
    @Test void unrelatedExceptionDoesNotPrintPrivateMessage(){List<String> out=new ArrayList<>();assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(new IllegalStateException("private-request-body"),1,"hash",out::add));assertFalse(out.toString().contains("private-request-body"));}
    @Test void unknownErrorKeyAndParamsAreNotPrinted(){List<String> out=new ArrayList<>();assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(new BusinessException("private-request-body",Map.of("password","private-param")),1,"hash",out::add));assertTrue(out.get(0).contains("UNRECOGNIZED_ERROR_KEY"));assertFalse(out.toString().contains("private-request-body"));assertFalse(out.toString().contains("private-param"));}
    @Test void wrongReasonFailsWithoutPrintingValue(){var failure=new PlatformAdminInvitationMfaCredentialException(1L,"hash");org.springframework.test.util.ReflectionTestUtils.setField(failure,"params",Map.of("reason","private-reason"));assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(failure,1,"hash",s->assertFalse(s.contains("private-reason"))));}
    @Test void wrongActorFails(){assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(new PlatformAdminInvitationMfaCredentialException(2L,"hash"),1,"hash",s->{}));}
    @Test void wrongChallengeFailsWithoutPrintingHash(){List<String> out=new ArrayList<>();assertThrows(AssertionError.class,()->PlatformMfaFailureFixture.assertCredentialFailure(new PlatformAdminInvitationMfaCredentialException(1L,"private-other-hash"),1,"private-expected-hash",out::add));assertFalse(out.toString().contains("private-"));}
}
