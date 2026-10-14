package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Las dos rutas internas de ms-identidad que usa este servicio
 * ({@code contracts/openapi/ms-identidad-admin.yaml}), con la credencial de
 * servicio de este modulo ({@code client_credentials}, {@code azp =
 * moderacion-sanciones}; la pone el {@link RestClient} que llega del
 * cableado):
 *
 * <ul>
 *   <li>{@code PUT /api/v1/internal/usuarios/{uid}/estado-sancion}: proyecta
 *       el estado de acceso de la cuenta. Es lo que hace que el login niegue la
 *       entrada a una cuenta suspendida o baneada y revoque sus sesiones.
 *       Idempotente en ms-identidad por {@code sancionId} + {@code estado}.</li>
 *   <li>{@code GET /api/v1/internal/usuarios/{uid}/contacto}: el correo y el
 *       apodo para enviar el correo de la sancion. Se pide en el momento de
 *       enviar y no se guarda (regla 7, minimizacion de datos personales).</li>
 *   <li>{@code GET /api/v1/internal/usuarios/{uid}/rol} (1.6.0): el rol
 *       vigente de la cuenta, para la jerarquia de 7.3.1 ({@link RolesDeCuentas}).
 *       Sin datos de contacto.</li>
 *   <li>{@code PUT /api/v1/internal/usuarios/{uid}/historial-sanciones} (1.7.0):
 *       el resumen del historial de sanciones de la cuenta
 *       ({@link HistorialEnIdentidad}).</li>
 * </ul>
 *
 * <p>No traduce errores: un 4xx sale como {@code HttpClientErrorException} y
 * un destino caido como la excepcion de conexion; decidir que es rechazo y
 * que es reintento es de cada destino ({@link Respuestas}).
 */
public class ClienteIdentidad {

    static final String RUTA_ESTADO = "/api/v1/internal/usuarios/{uid}/estado-sancion";
    static final String RUTA_CONTACTO = "/api/v1/internal/usuarios/{uid}/contacto";
    static final String RUTA_ROL = "/api/v1/internal/usuarios/{uid}/rol";
    static final String RUTA_HISTORIAL = "/api/v1/internal/usuarios/{uid}/historial-sanciones";

    private final RestClient http;
    private final String base;

    public ClienteIdentidad(RestClient http, String base) {
        this.http = Objects.requireNonNull(http);
        this.base = Objects.requireNonNull(base).replaceAll("/+$", "");
    }

    public void proyectar(UUID uid, ProyeccionDeSancion proyeccion) {
        http.put()
                .uri(base + RUTA_ESTADO, uid)
                .body(proyeccion)
                .retrieve()
                .toBodilessEntity();
    }

    public Contacto contacto(UUID uid) {
        Contacto contacto = http.get()
                .uri(base + RUTA_CONTACTO, uid)
                .retrieve()
                .body(Contacto.class);
        if (contacto == null) {
            throw new IllegalStateException("ms-identidad respondio el contacto vacio");
        }
        return contacto;
    }

    /**
     * SA-3 (ms-identidad-admin.yaml 1.7.0): fija el resumen del historial de
     * sanciones de la cuenta, con valores absolutos. Idempotente.
     */
    public void historial(UUID uid, HistorialDeSanciones historial) {
        http.put()
                .uri(base + RUTA_HISTORIAL, uid)
                .body(historial)
                .retrieve()
                .toBodilessEntity();
    }

    public RolDeCuenta rol(UUID uid) {
        RolDeCuenta rol = http.get()
                .uri(base + RUTA_ROL, uid)
                .retrieve()
                .body(RolDeCuenta.class);
        if (rol == null) {
            throw new IllegalStateException("ms-identidad respondio el rol vacio");
        }
        return rol;
    }

    /**
     * {@code ProyeccionDeSancion} del contrato de ms-identidad.
     *
     * @param estado    {@code ACTIVO}, {@code SUSPENDIDO} o {@code BANEADO}
     * @param hasta     fin de la suspension; solo con {@code SUSPENDIDO}
     * @param sancionId la sancion que produce el estado
     * @param motivo    causal, o motivo del levantamiento
     */
    public record ProyeccionDeSancion(String estado, OffsetDateTime hasta, UUID sancionId, String motivo) {
    }

    /**
     * {@code HistorialDeSanciones} del contrato de ms-identidad (1.7.0).
     *
     * @param total    sanciones emitidas a la cuenta, las revertidas tambien
     * @param ultimaEn la emision mas reciente; nula sin sanciones
     */
    public record HistorialDeSanciones(int total, OffsetDateTime ultimaEn) {
    }

    /** Respuesta de {@code rol}: el rol vigente y el estado de la cuenta. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RolDeCuenta(UUID uid, String rol, String estado) {
    }

    /** Respuesta de {@code contacto}; lo que no se usa se ignora. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contacto(UUID uid, String email, String apodo, String estado) {
    }
}
