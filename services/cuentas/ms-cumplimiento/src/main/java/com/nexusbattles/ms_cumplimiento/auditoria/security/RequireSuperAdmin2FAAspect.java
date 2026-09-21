package com.nexusbattles.ms_cumplimiento.auditoria.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Guarda de {@link RequireSuperAdmin2FA}: solo un superadministrador con
 * segundo factor consulta la bitacora.
 *
 * <p><b>Rol.</b> El rol que emite ms-identidad se llama
 * {@code SUPER_ADMINISTRADOR} ({@code Role.SUPER_ADMINISTRADOR}), y
 * {@code ConversorDeRoles} lo deja como {@code ROLE_SUPER_ADMINISTRADOR}.
 * Hasta ahora se comparaba con {@code ROLE_SUPER_ADMIN}, que ningun token
 * lleva: la consulta era inalcanzable por construccion.
 *
 * <p><b>Segundo factor.</b> ms-identidad todavia no emite ningun claim de
 * segundo factor (no hay HU de 2FA implementada), asi que exigirlo hoy
 * equivale a negar siempre. La exigencia queda <b>configurable</b> con
 * {@code cumplimiento.auditoria.exigir-2fa} (por defecto apagada, decision
 * pendiente del Product Owner anotada en el PR). Cuando se encienda, se
 * comprueba el claim estandar {@code amr} (RFC 8176) buscando {@code mfa},
 * {@code otp} o {@code hwk}, que es lo que emitiria Keycloak con un segundo
 * factor; ms-identidad tendra que emitir lo mismo.
 */
@Aspect
@Component
public class RequireSuperAdmin2FAAspect {

    static final String ROL_SUPER_ADMINISTRADOR = "ROLE_SUPER_ADMINISTRADOR";
    static final List<String> METODOS_DE_SEGUNDO_FACTOR = List.of("mfa", "otp", "hwk");

    private final boolean exigir2fa;

    public RequireSuperAdmin2FAAspect(@Value("${cumplimiento.auditoria.exigir-2fa:false}") boolean exigir2fa) {
        this.exigir2fa = exigir2fa;
    }

    @Before("@annotation(com.nexusbattles.ms_cumplimiento.auditoria.security.RequireSuperAdmin2FA)")
    public void verificar() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Sesión no autenticada");
        }

        boolean esSuperAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROL_SUPER_ADMINISTRADOR::equals);

        if (!esSuperAdmin) {
            throw new AccessDeniedException("Requiere rol Super Administrador");
        }

        if (exigir2fa && !isTwoFactorVerified(auth)) {
            throw new AccessDeniedException("Requiere sesión con 2FA verificado");
        }
    }

    /** Si el token declara un segundo factor en {@code amr} (RFC 8176). */
    static boolean isTwoFactorVerified(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwt) {
            List<String> amr = jwt.getToken().getClaimAsStringList("amr");
            return amr != null && amr.stream().anyMatch(METODOS_DE_SEGUNDO_FACTOR::contains);
        }
        if (auth.getDetails() instanceof TwoFactorAwareDetails details) {
            return details.isTwoFactorVerified();
        }
        return false;
    }

    public interface TwoFactorAwareDetails {
        boolean isTwoFactorVerified();
    }
}
