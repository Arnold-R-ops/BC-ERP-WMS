package com.wms.system.tenant.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class TenantSecurityCommand {
    private TenantSecurityCommand() { }
    public enum Action { SUSPEND, REQUEST_RESUME, APPROVE_RESUME, REJECT_RESUME, WITHDRAW_RESUME, RECORD_CONNECTION_REVIEW, REPLACE_CONNECTION_CREDENTIALS, RECORD_RECONCILIATION, RESOLVE_ALERT }
    public record CredentialUpdate(@Size(max=10000) String clientSecret,@Size(max=10000) String accessToken) {
        @Override public String toString() { return "CredentialUpdate[REDACTED]"; }
    }
    public record Draft(@NotNull Action action, @NotBlank @Size(max=2000) String reason,
                        @Size(max=36) String requestId, @Size(max=1000) List<Long> affectedConnectionIds,
                        Long configId, String remoteStatus, Boolean connectionCheckPassed,
                        @Valid CredentialUpdate credentials, Long reportEventId, Long alertId) {
        public Draft(Action action,String reason,String requestId,List<Long> affectedConnectionIds,Long configId,String remoteStatus,Boolean passed) {
            this(action,reason,requestId,affectedConnectionIds,configId,remoteStatus,passed,null,null,null);
        }
        @Override public String toString() { return "TenantSecurityCommand.Draft[REDACTED]"; }
    }
    public record Execute(@NotBlank @Size(max=100) String idempotencyKey,
                          @NotBlank @Size(max=100) String challengeToken,
                          @NotBlank @Size(max=200) @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password,
                          @NotBlank @Pattern(regexp="\\d{6}") @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String code,
                          @Valid @NotNull Draft draft) {
        @Override public String toString() { return "TenantSecurityCommand.Execute[REDACTED]"; }
    }
}
