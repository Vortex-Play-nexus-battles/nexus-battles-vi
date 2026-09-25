package com.nexusbattles.plataforma.moderacionsanciones.seguridad;

import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La jerarquia de roles de la Tabla 24 del documento del curso, en un solo
 * sitio: el Super Administrador puede todo lo del Administrador, y el
 * Administrador todo lo del Moderador (7.3.1).
 *
 * <p>Hasta B2 el panel de la lista negra pedia {@code hasAnyRole(ADMINISTRADOR,
 * MODERADOR)} sin jerarquia, y un Super Administrador —el rol con «acceso total
 * al sistema»— recibia 403. La cadena de seguridad usa esta misma jerarquia
 * como bean ({@link SecurityConfig}), y el codigo que decide por rol fuera de
 * la cadena (que detalle ve quien, quien consulta que sancion) pregunta aqui,
 * no a una lista escrita a mano.
 *
 * <p>{@code SERVICIO} (credencial de otro microservicio, ADR-005) queda fuera
 * de la jerarquia a proposito: un servicio no es un moderador.
 */
public final class JerarquiaDeRoles {

    public static final String SERVICIO = "SERVICIO";
    public static final String JUGADOR = "JUGADOR";
    public static final String MODERADOR = "MODERADOR";
    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String SUPER_ADMINISTRADOR = "SUPER_ADMINISTRADOR";

    /** SUPER_ADMINISTRADOR > ADMINISTRADOR > MODERADOR. */
    public static final RoleHierarchy JERARQUIA = RoleHierarchyImpl.withDefaultRolePrefix()
            .role(SUPER_ADMINISTRADOR).implies(ADMINISTRADOR)
            .role(ADMINISTRADOR).implies(MODERADOR)
            .build();

    private JerarquiaDeRoles() {
    }

    /** Los roles que alcanza una autenticacion, ya con la jerarquia aplicada y sin prefijo. */
    public static Set<String> rolesDe(Authentication autenticacion) {
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
                || !autenticacion.isAuthenticated()) {
            return Set.of();
        }
        Collection<? extends GrantedAuthority> alcanzables =
                JERARQUIA.getReachableGrantedAuthorities(autenticacion.getAuthorities());
        return alcanzables.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(autoridad -> autoridad != null && autoridad.startsWith("ROLE_"))
                .map(autoridad -> autoridad.substring("ROLE_".length()))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Si la autenticacion alcanza {@code rol}, con la jerarquia. */
    public static boolean alcanza(Authentication autenticacion, String rol) {
        return rolesDe(autenticacion).contains(rol);
    }

    /**
     * Quien puede ver que termino coincidio y su categoria en
     * {@code /lista-negra/verificar}: un servicio o quien modera
     * (moderacion-lista-negra.yaml 2.0.x, «Quien puede llamar»).
     */
    public static boolean puedeVerDetalleDeListaNegra(Authentication autenticacion) {
        Set<String> roles = rolesDe(autenticacion);
        return roles.contains(SERVICIO) || roles.contains(MODERADOR);
    }
}
