package com.wms.system.signup.controller;

import com.wms.system.dto.LoginResponse;
import com.wms.system.signup.service.SessionHandoffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/v1/session-handoff")
@RequiredArgsConstructor
public class SessionHandoffController {
    private final SessionHandoffService handoffService;

    @PostMapping("/consume")
    public LoginResponse consume(@Valid @RequestBody ConsumeRequest request) {
        return handoffService.consume(request.code());
    }

    public record ConsumeRequest(@NotBlank String code) {}
}
