package com.nexusbattles.ms_identidad.rbac.security;

import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Interceptor Server-Side con soporte JWT y política Fail-Closed (HU-RBAC-004).
 */
@Component
public class SecurityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);
    private final RbacAuthorizationService rbacService;
    private final AuditoriaEventClient auditoriaClient;
    private final JwtService jwtService;

    public SecurityInterceptor(RbacAuthorizationService rbacService) {
        this(rbacService, null, null);
    }

    public SecurityInterceptor(RbacAuthorizationService rbacService, AuditoriaEventClient auditoriaClient) {
        this(rbacService, auditoriaClient, null);
    }

    @Autowired
    public SecurityInterceptor(
            RbacAuthorizationService rbacService,
            AuditoriaEventClient auditoriaClient,
            @Autowired(required = false) JwtService jwtService) {
        this.rbacService = rbacService;
        this.auditoriaClient = auditoriaClient;
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }

        if (annotation != null) {
            Action requiredAction = annotation.value();
            String authHeader = request.getHeader("Authorization");
            String roleHeader = request.getHeader("X-User-Role");
            String usernameHeader = request.getHeader("X-User-Name");
            String ipOrigen = request.getRemoteAddr();

            String roleName = null;
            String username = usernameHeader;

            // 1. Intentar validar JWT si viene en header Authorization: Bearer <token>
            if (authHeader != null && authHeader.startsWith("Bearer ") && jwtService != null) {
                String token = authHeader.substring(7).trim();
                try {
                    Claims claims = jwtService.validarYObtenerClaims(token);
                    username = claims.getSubject();
                    roleName = claims.get("rol", String.class);
                } catch (JwtException e) {
                    auditBypass(username, "INVALID_JWT", requiredAction, "JWT_INVALID_OR_EXPIRED: " + e.getMessage(), ipOrigen);
                    sendForbidden(response, request.getRequestURI(), "Token de autenticación inválido o expirado");
                    return false;
                }
            } else if (roleHeader != null && !roleHeader.trim().isEmpty()) {
                // 2. Respaldo para demo / tests usando header temporal X-User-Role
                roleName = roleHeader.trim();
            }

            // Si no hay rol verificado -> FAIL-CLOSED (Denegar)
            if (roleName == null || roleName.trim().isEmpty()) {
                auditBypass(username, "ANONYMOUS", requiredAction, "CREDENTIAL_MISSING", ipOrigen);
                sendForbidden(response, request.getRequestURI(), "No tienes permiso para esta acción");
                return false;
            }

            try {
                Role role = Role.valueOf(roleName.trim().toUpperCase());
                boolean isAllowed = rbacService.isActionPermitted(role, requiredAction);

                if (!isAllowed) {
                    auditBypass(username, role.name(), requiredAction, "FORBIDDEN_ROLE", ipOrigen);
                    sendForbidden(response, request.getRequestURI(), "No tienes permiso para esta acción");
                    return false;
                }
            } catch (Exception e) {
                auditBypass(username, roleName, requiredAction, "FAIL_CLOSED_ERROR", ipOrigen);
                sendForbidden(response, request.getRequestURI(), "No tienes permiso para esta acción");
                return false;
            }
        }

        return true;
    }

    private void auditBypass(String username, String role, Action action, String reason, String ipOrigen) {
        String logJson = String.format(
            "{\"event\": \"SECURITY_BYPASS_ATTEMPT\", \"timestamp\": \"%s\", \"username\": \"%s\", \"role\": \"%s\", \"action\": \"%s\", \"result\": \"403_FORBIDDEN\", \"reason\": \"%s\"}",
            Instant.now(), username != null ? username : "unknown", role, action, reason
        );
        log.warn("AUDIT_TRAIL: {}", logJson);

        if (auditoriaClient != null) {
            auditoriaClient.registrarBypassAsync(username, role, action != null ? action.name() : "UNKNOWN", reason, ipOrigen);
        }
    }

    private void sendForbidden(HttpServletResponse response, String uri, String detail) throws Exception {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.getWriter().write(String.format(
            "{\"type\": \"https://nexusbattles.upb.edu.co/errors/forbidden\", \"title\": \"Acceso denegado\", \"status\": 403, \"detail\": \"%s\", \"instance\": \"%s\"}",
            detail != null ? detail : "No tienes permiso para esta acción",
            uri
        ));
    }
}
