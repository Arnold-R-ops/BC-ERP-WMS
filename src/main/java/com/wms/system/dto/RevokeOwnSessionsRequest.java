package com.wms.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevokeOwnSessionsRequest {
    @NotBlank
    @ToString.Exclude
    private String currentPassword;
}
