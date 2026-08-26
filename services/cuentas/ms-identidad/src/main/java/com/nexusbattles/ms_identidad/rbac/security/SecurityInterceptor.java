package com.nexusbattles.ms_identidad.rbac.security;

import com.nexusbattles.ms_identidad.rbac.model.Action;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Interceptor Server-Side con política Fail-Closed (HU-RBAC-004).
 */
@Component
public class SecurityInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);
    private final RbacAuthorizationService rbacService;

    public SecurityInterceptor(RbacAuthorizationService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission annotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }

        if (annotation != null) {
            Action requiredAction = annotation.value();
            String roleHeader = request.getHeader("X-User-Role");
            String usernameHeader = request.getHeader("X-User-Name");

            // Si no hay rol o token -> FAIL-CLOSED (Denegar)
            if (roleHeader == null || roleHeader.trim().isEmpty()) {
                auditBypass(usernameHeader, "ANONYMOUS", requiredAction, "TOKEN_MISSING");
                sendForbidden(response, request.getRequestURI());
                return false;
            }

            try {
                Role role = Role.valueOf(roleHeader.trim().toUpperCase());
                boolean isAllowed = rbacService.isActionPermitted(role, requiredAction);

                if (!isAllowed) {
                    auditBypass(usernameHeader, role.name(), requiredAction, "FORBIDDEN_ROLE");
                    sendForbidden(response, request.getRequestURI());
                    return false;
                }
            } catch (Exception e) {
                auditBypass(usernameHeader, roleHeader, requiredAction, "FAIL_CLOSED_ERROR");
                sendForbidden(response, request.getRequestURI());
                return false;
            }
        }

        return true;
    }

    private void auditBypass(String username, String role, Action action, String reason) {
        String logJson = String.format(
            "{\"event\": \"SECURITY_BYPASS_ATTEMPT\", \"timestamp\": \"%s\", \"username\": \"%s\", \"role\": \"%s\", \"action\": \"%s\", \"result\": \"403_FORBIDDEN\", \"reason\": \"%s\"}",
            Instant.now(), username != null ? username : "unknown", role, action, reason
        );
        log.warn("AUDIT_TRAIL: {}", logJson);
    }

    private void sendForbidden(HttpServletResponse response, String uri) throws Exception {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.getWriter().write(String.format(
            "{\"type\": \"https://nexusbattles.upb.edu.co/errors/forbidden\", \"title\": \"Acceso denegado\", \"status\": 403, \"detail\": \"No tienes permiso para esta acción\", \"instance\": \"%s\"}",
            uri
        ));
    }
}
