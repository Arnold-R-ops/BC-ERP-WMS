package com.wms.system.signup.dto;

import com.wms.system.signup.model.SignupStatus;

public record SignupResponse(
    String signupId,
    SignupStatus status,
    String maskedEmail,
    Integer resendAfterSeconds,
    String companySlug,
    String redirectUrl,
    String handoffCode,
    Integer handoffExpiresInSeconds
) {}
