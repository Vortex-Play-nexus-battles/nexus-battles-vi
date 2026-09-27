package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Consulta de sancion activa (RF-USR-004) — la que hacen chat, salas,
 * comentarios, torneos y subastas antes de dejar actuar a un jugador.
 *
 * <p>Lee el historial real: restringe una suspension no vencida o un baneo;
 * una advertencia no (CA-02 de HU-USR-004).
 *
 * <p><b>1.4.0 (B2): ya no es publica.</b> Cualquiera, sin sesion, podia leer el
 * motivo y la vigencia de la sancion de cualquier jugador, y los uid son
 * publicos en los torneos. Ahora un jugador solo consulta la suya (es el
 * contador de tiempo restante del 7.3.2) y un servicio o quien modera, la de
 * cualquiera. La respuesta suma {@code sancionId}, para levantarla o apelarla.
 */
@Service
public class ConsultaSancionActivaService {

    private final SancionesService sanciones;

    public ConsultaSancionActivaService(SancionesService sanciones) {
        this.sanciones = sanciones;
    }

    /**
     * Quien pregunta, tal como sale del token.
     *
     * @param uid        su {@code uid}, si es un usuario ({@code null} en un token de servicio)
     * @param esServicio credencial de servicio (ADR-005)
     * @param modera     MODERADOR o superior (con la jerarquia de roles)
     */
    public record Consultante(UUID uid, boolean esServicio, boolean modera) {

        boolean puedeConsultar(UUID usuarioId) {
            return esServicio || modera || (uid != null && uid.equals(usuarioId));
        }
    }

    /**
     * @throws SancionRechazada {@code PERMISO_INSUFICIENTE} si un jugador pregunta por otro
     */
    public ResultadoSancion consultar(Consultante quien, UUID usuarioId) {
        if (!quien.puedeConsultar(usuarioId)) {
            throw new SancionRechazada(SancionRechazada.Motivo.PERMISO_INSUFICIENTE,
                    "un jugador solo consulta su propia sancion");
        }
        return consultar(usuarioId);
    }

    /** Sin control de acceso: para quien ya lo hizo (y para las pruebas de persistencia). */
    public ResultadoSancion consultar(UUID usuarioId) {
        return sanciones.activaDe(usuarioId)
                .map(s -> new ResultadoSancion(true, s.motivo(), s.vigenteHasta(), s.tipo().name(), s.id()))
                .orElse(ResultadoSancion.SIN_SANCION);
    }

    public record ResultadoSancion(boolean sancionActiva, String motivo, OffsetDateTime vigenteHasta,
                                   String tipo, UUID sancionId) {
        static final ResultadoSancion SIN_SANCION = new ResultadoSancion(false, null, null, null, null);

        /** Forma anterior, para las pruebas que no miran el tipo ni la sancion. */
        public ResultadoSancion(boolean sancionActiva, String motivo, OffsetDateTime vigenteHasta) {
            this(sancionActiva, motivo, vigenteHasta, null, null);
        }

        /** Forma 1.1.0 a 1.3.0, sin {@code sancionId}. */
        public ResultadoSancion(boolean sancionActiva, String motivo, OffsetDateTime vigenteHasta, String tipo) {
            this(sancionActiva, motivo, vigenteHasta, tipo, null);
        }
    }
}
