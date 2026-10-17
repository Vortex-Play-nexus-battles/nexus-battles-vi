package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.rbac.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lo configurable del segundo factor — HU-AUT-007.
 *
 * <p><b>Obligatoriedad por rol</b> ({@code IDENTIDAD_2FA_OBLIGATORIO_ROLES}):
 * RF-AUT-007 y 7.3.9 la piden para Moderador, Administrador y Super
 * Administrador, y desde la auditoria Super Admin del 13-oct viene encendida
 * por omision para esos tres roles (application.properties). Una cuenta
 * administrativa sin segundo factor se enrola en su siguiente inicio de sesion
 * (403 {@code segundo-factor-enrolamiento-requerido}); para esos roles no se
 * puede desactivar, y si alguien pierde la aplicacion y los codigos, un Super
 * Administrador lo restablece. Las pruebas automaticas usan la cuenta de
 * pruebas {@code ADMINS_QA} o se enrolan solas con el secreto que reciben.
 *
 * <p><b>Cifras PROVISIONALES</b>, porque ningun documento las fija (se
 * buscaron en admin-parametros y en las decisiones del PO): el desafio del
 * login vive 5 minutos y se entregan 10 codigos de recuperacion. Se cambian
 * por variable de entorno, sin tocar codigo.
 */
@Component
public class PoliticaDeSegundoFactor {

    private static final Logger log = LoggerFactory.getLogger(PoliticaDeSegundoFactor.class);

    static final String EMISOR_POR_OMISION = "Nexus Battles VI";
    private static final int MAXIMO_DE_CODIGOS = 100;

    private final Set<String> rolesObligatorios;
    private final Duration vigenciaDelDesafio;
    private final int codigosDeRecuperacion;
    private final String emisor;

    public PoliticaDeSegundoFactor(
            @Value("${identidad.segundo-factor.obligatorio-roles:}") String roles,
            @Value("${identidad.segundo-factor.minutos-desafio:5}") int minutosDesafio,
            @Value("${identidad.segundo-factor.codigos-recuperacion:10}") int codigosDeRecuperacion,
            @Value("${identidad.segundo-factor.emisor:Nexus Battles VI}") String emisor) {
        if (minutosDesafio < 1) {
            throw new IllegalArgumentException("identidad.segundo-factor.minutos-desafio debe ser 1 o mas: "
                    + minutosDesafio);
        }
        if (codigosDeRecuperacion < 1 || codigosDeRecuperacion > MAXIMO_DE_CODIGOS) {
            throw new IllegalArgumentException("identidad.segundo-factor.codigos-recuperacion fuera de 1.."
                    + MAXIMO_DE_CODIGOS + ": " + codigosDeRecuperacion);
        }
        this.rolesObligatorios = leerRoles(roles);
        this.vigenciaDelDesafio = Duration.ofMinutes(minutosDesafio);
        this.codigosDeRecuperacion = codigosDeRecuperacion;
        this.emisor = emisor == null || emisor.isBlank() ? EMISOR_POR_OMISION : emisor.strip();
        if (!rolesObligatorios.isEmpty()) {
            log.info("Segundo factor obligatorio para los roles {}", rolesObligatorios);
        }
    }

    private static Set<String> leerRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        Set<String> conocidos = new LinkedHashSet<>();
        for (String crudo : roles.split("[,;\\s]+")) {
            if (crudo.isBlank()) {
                continue;
            }
            String rol = crudo.strip().toUpperCase(Locale.ROOT);
            if (Arrays.stream(Role.values()).anyMatch(r -> r.name().equals(rol))) {
                conocidos.add(rol);
            } else {
                log.warn("IDENTIDAD_2FA_OBLIGATORIO_ROLES nombra un rol que no existe y se ignora: {}", rol);
            }
        }
        return Collections.unmodifiableSet(conocidos);
    }

    /** Si una cuenta con este rol no puede recibir sesion sin segundo factor. */
    public boolean esObligatorioPara(String rol) {
        return rol != null && rolesObligatorios.contains(rol.toUpperCase(Locale.ROOT));
    }

    public Set<String> rolesObligatorios() {
        return rolesObligatorios;
    }

    public Duration vigenciaDelDesafio() {
        return vigenciaDelDesafio;
    }

    public int codigosDeRecuperacion() {
        return codigosDeRecuperacion;
    }

    /** Nombre con el que la aplicacion de autenticacion muestra la cuenta. */
    public String emisor() {
        return emisor;
    }
}
