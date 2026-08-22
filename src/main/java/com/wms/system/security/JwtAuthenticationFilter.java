package com.wms.system.security;

import com.wms.system.dto.UserPermissionDTO;
import com.wms.system.service.DynamicPermissionService;
import com.wms.system.tenant.config.TenancyProperties;
import com.wms.system.tenant.context.RequestSurface;
import com.wms.system.tenant.context.TenantContext;
import com.wms.system.tenant.context.TenantContextHolder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authenticates either a company principal or a platform principal according
 * to the Host-derived request surface. Company/Host mismatch is rejected
 * before any user, role or permission lookup occurs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final DynamicPermissionService permissionService;
    private final PlatformUserDetailsService platformUserDetailsService;
    private final TenancyProperties tenancyProperties;

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        TenantContext context = TenantContextHolder.current().orElse(null);

        try {
            if (context == null) {
                // The runtime tenancy switch remains locked off until all
                // repositories/RLS are ready. Local development still has two
                // strictly separate API surfaces, so platform paths must be
                // parsed with the platform key rather than the tenant key.
                if (tenancyProperties.isEnabled()) {
                    reject(response, "AUTH_TOKEN_INVALID");
                    return;
                }
                if (tenancyProperties.isAllowLocalDevelopmentHost()
                        && request.getRequestURI().startsWith("/api/platform/")) {
                    authenticatePlatform(token, request);
                } else {
                    authenticateTenant(token, null, request);
                }
            } else if (context.surface() == RequestSurface.TENANT) {
                authenticateTenant(token, context, request);
            } else if (context.surface() == RequestSurface.PLATFORM) {
                authenticatePlatform(token, request);
            } else if (context.surface() == RequestSurface.LOCAL_DEVELOPMENT
                    && tenancyProperties.isAllowLocalDevelopmentHost()) {
                authenticateTenant(token, null, request);
            } else {
                reject(response, "AUTH_TOKEN_INVALID");
                return;
            }
        } catch (ExpiredJwtException exception) {
            log.info("Expired tenant or platform JWT rejected: path={}", request.getRequestURI());
            reject(response, "AUTH_TOKEN_EXPIRED");
            return;
        } catch (TenantSessionInvalidatedException exception) {
            log.info("Tenant session invalidated by identity or permission change: path={}",
                request.getRequestURI());
            reject(response, "AUTH_SESSION_INVALIDATED");
            return;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("JWT rejected: path={}, reason={}",
                request.getRequestURI(), exception.getClass().getSimpleName());
            reject(response, "AUTH_TOKEN_INVALID");
            return;
        } catch (Exception exception) {
            log.warn("JWT authentication failed: path={}, reason={}",
                request.getRequestURI(), exception.getClass().getSimpleName());
            reject(response, "AUTH_TOKEN_INVALID");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateTenant(
        String token,
        TenantContext context,
        HttpServletRequest request
    ) {
        TenantJwtClaims claims = jwtUtil.parseTenantToken(token);

        if (context != null && (!context.isTenantRequest()
                || !claims.companyId().equals(context.tenantId()))) {
            throw new JwtException("Company token does not match request Host");
        }

        SecurityUser securityUser = userDetailsService.loadTenantUser(
            claims.userId(), claims.companyId());
        if (!securityUser.isEnabled()
                || !claims.securityVersion().equals(
                    securityUser.getUser().getSecurityVersion())
                || !claims.username().equals(securityUser.getUsername())
                || !claims.companyId().equals(
                    securityUser.getUser().getCompanyId())) {
            throw new TenantSessionInvalidatedException();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + claims.currentRole()));
        authorities.add(new SimpleGrantedAuthority(claims.currentRole()));

        UserPermissionDTO permissions = permissionService.getUserPermissionsForRole(
            claims.companyId(), securityUser.getId(),
            claims.currentRole(), claims.securityVersion());
        permissions.getPermissions().stream()
            .map(permission -> permission.getPermissionCode())
            .filter(code -> code != null && !code.isBlank())
            .map(SimpleGrantedAuthority::new)
            .forEach(authorities::add);

        publishAuthentication(securityUser, authorities, request);
    }

    private void authenticatePlatform(String token, HttpServletRequest request) {
        PlatformJwtClaims claims = jwtUtil.parsePlatformToken(token);
        PlatformSecurityUser platformUser =
            platformUserDetailsService.loadEnabledUser(claims.platformUserId());
        if (!claims.securityVersion().equals(
                platformUser.getUser().getSecurityVersion())
                || !claims.normalizedEmail().equals(
                    platformUser.getUser().getNormalizedEmail())) {
            throw new JwtException("Platform token identity is stale or invalid");
        }

        List<String> liveRoles = platformUserDetailsService
            .loadRoleCodes(claims.platformUserId());
        if (!new java.util.HashSet<>(liveRoles)
                .equals(new java.util.HashSet<>(claims.roles()))) {
            throw new JwtException("Platform token roles are stale");
        }

        List<GrantedAuthority> authorities = liveRoles.stream()
            .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        publishAuthentication(platformUser, authorities, request);
    }

    private void publishAuthentication(
        Object principal,
        List<GrantedAuthority> authorities,
        HttpServletRequest request
    ) {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);
        authentication.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void reject(HttpServletResponse response, String errorKey)
            throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"errorKey\":\"" + errorKey + "\"}");
    }

    private static final class TenantSessionInvalidatedException extends JwtException {
        private TenantSessionInvalidatedException() {
            super("Tenant session identity or authorization context changed");
        }
    }
}
