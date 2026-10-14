package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * {@link RolesDeCuentas} preguntando a ms-identidad
 * ({@code GET /api/v1/internal/usuarios/{uid}/rol}, credencial de servicio).
 * Se pregunta en el momento de decidir y no se guarda: un cambio de rol vale
 * desde la siguiente sancion.
 */
public class RolesDesdeIdentidad implements RolesDeCuentas {

    private static final Logger BITACORA = LoggerFactory.getLogger(RolesDesdeIdentidad.class);
    static final Set<String> ROLES = Set.of("JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR");
    static final String NO_DISPONIBLE = "No se pudo comprobar el rol de la cuenta. Inténtalo de nuevo en unos "
            + "segundos.";

    private final ClienteIdentidad identidad;

    public RolesDesdeIdentidad(ClienteIdentidad identidad) {
        this.identidad = Objects.requireNonNull(identidad);
    }

    @Override
    public String rolDe(UUID cuenta) {
        ClienteIdentidad.RolDeCuenta respuesta;
        try {
            respuesta = identidad.rol(cuenta);
        } catch (HttpClientErrorException rechazo) {
            if (rechazo.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new SancionRechazada(SancionRechazada.Motivo.NO_ENCONTRADA, "no hay ninguna cuenta " + cuenta);
            }
            BITACORA.warn("ms-identidad rechazo la consulta del rol de {}: {}", cuenta, rechazo.getStatusCode());
            throw new SancionRechazada(SancionRechazada.Motivo.IDENTIDAD_NO_DISPONIBLE, NO_DISPONIBLE);
        } catch (RestClientException | IllegalStateException caida) {
            BITACORA.warn("ms-identidad no dijo el rol de {}: {}", cuenta, caida.getMessage());
            throw new SancionRechazada(SancionRechazada.Motivo.IDENTIDAD_NO_DISPONIBLE, NO_DISPONIBLE);
        }
        String rol = respuesta.rol() == null ? null : respuesta.rol().strip().toUpperCase(Locale.ROOT);
        if (rol == null || !ROLES.contains(rol)) {
            BITACORA.warn("ms-identidad devolvio un rol desconocido para {}", cuenta);
            throw new SancionRechazada(SancionRechazada.Motivo.IDENTIDAD_NO_DISPONIBLE, NO_DISPONIBLE);
        }
        return rol;
    }
}
