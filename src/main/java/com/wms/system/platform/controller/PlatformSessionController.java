package com.wms.system.platform.controller;

import com.wms.system.platform.service.PlatformSessionService;
import com.wms.system.platform.service.PlatformBrowserBinding;
import com.wms.system.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/auth")
@RequiredArgsConstructor
public class PlatformSessionController {
    private final PlatformSessionService sessions;
    private final JwtUtil jwt;
    private final PlatformBrowserBinding browsers;

    @PostMapping("/browser-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void browserSession(HttpServletRequest request, HttpServletResponse response) {
        browsers.initialize(request, response);
    }

    @PostMapping("/session/activity")
    public PlatformSessionService.Window activity(@RequestHeader("Authorization") String authorization) {
        // No client timestamp or duration is accepted; the database supplies the clock.
        return sessions.activity(jwt.parsePlatformToken(authorization.substring(7)));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization, HttpServletRequest request) {
        sessions.logout(jwt.parsePlatformToken(authorization.substring(7)), request);
    }
}
