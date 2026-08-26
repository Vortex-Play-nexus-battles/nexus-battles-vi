package com.nexusbattles.ms_cumplimiento.auditoria.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RequireSuperAdmin2FAAspect {

    @Before("@annotation(com.nexusbattles.ms_cumplimiento.auditoria.security.RequireSuperAdmin2FA)")
    public void verificar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Sesión no autenticada");
        }

        boolean esSuperAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);

        if (!esSuperAdmin) {
            throw new AccessDeniedException("Requiere rol Super Administrador");
        }

        if (!isTwoFactorVerified(auth)) {
            throw new AccessDeniedException("Requiere sesión con 2FA verificado");
        }
    }

    private boolean isTwoFactorVerified(Authentication auth) {
        // TODO: aquí debes conectar con el mecanismo real de 2FA de tu
        // proyecto (identidad-acceso / rbac). Por ahora es un placeholder.
        if (auth.getDetails() instanceof TwoFactorAwareDetails details) {
            return details.isTwoFactorVerified();
        }
        return false;
    }

    public interface TwoFactorAwareDetails {
        boolean isTwoFactorVerified();
    }
}