package com.wms.system.entity;

import com.wms.system.entity.enums.OutboundTaskStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OutboundTaskTerminationTest {
    @Test
    void requiresCompleteAuditEvidenceAndNeverChangesActualQuantity() {
        OutboundTask task = OutboundTask.builder().status(OutboundTaskStatus.PENDING).planQty(5).build();
        LocalDateTime at = LocalDateTime.now();
        assertThatThrownBy(() -> task.voidUnstarted(null, at, "Reason")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task.voidUnstarted(7L, at, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> task.voidUnstarted(7L, at, "x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
        assertThat(task.getStatus()).isEqualTo(OutboundTaskStatus.PENDING);
        task.voidUnstarted(7L, at, "x".repeat(500));
        assertThat(task.getArchivedAt()).isEqualTo(at);
        assertThat(task.getArchivedBy()).isEqualTo(7L);
        assertThat(task.getArchiveReason()).hasSize(500);
        assertThat(task.getActualQty()).isZero();
        assertThat(task.getPlanQty()).isEqualTo(5);
        assertThat(task.canConfirmDispatch()).isFalse();
    }

    @Test
    void refusesStartedCompletedAndAlreadyVoidedTasksWithoutOverwritingEvidence() {
        for (OutboundTaskStatus status : OutboundTaskStatus.values()) {
            if (status == OutboundTaskStatus.PENDING) continue;
            OutboundTask task = OutboundTask.builder().status(status).archiveReason("old evidence").build();
            assertThatThrownBy(() -> task.voidUnstarted(7L, LocalDateTime.now(), "new"))
                .isInstanceOf(IllegalStateException.class);
            assertThat(task.getArchiveReason()).isEqualTo("old evidence");
        }
    }
}
