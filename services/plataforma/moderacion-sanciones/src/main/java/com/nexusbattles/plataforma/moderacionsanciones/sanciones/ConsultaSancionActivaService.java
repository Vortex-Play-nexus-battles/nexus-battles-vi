package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Consulta de sancion activa (RF-USR-004) — la que hacen chat, comentarios y
 * subastas antes de dejar actuar a un jugador.
 *
 * <p>Hasta el Sprint 2 respondia «sin sancion» para todo el mundo (backing
 * provisional). Desde HU-USR-004..006 lee el historial real: restringe una
 * suspension no vencida o un baneo; una advertencia no (CA-02 de
 * HU-USR-004). La firma no cambia; el contrato solo suma {@code tipo}
 * (moderacion-sanciones-consulta.yaml 1.1.0).
 */
@Service
public class ConsultaSancionActivaService {

    private final SancionesService sanciones;

    public ConsultaSancionActivaService(SancionesService sanciones) {
        this.sanciones = sanciones;
    }

    public ResultadoSancion consultar(UUID usuarioId) {
        return sanciones.activaDe(usuarioId)
                .map(s -> new ResultadoSancion(true, s.motivo(), s.vigenteHasta(), s.tipo().name()))
                .orElse(ResultadoSancion.SIN_SANCION);
    }

    public record ResultadoSancion(boolean sancionActiva, String motivo, java.time.OffsetDateTime vigenteHasta,
                                   String tipo) {
        static final ResultadoSancion SIN_SANCION = new ResultadoSancion(false, null, null, null);

        /** Forma anterior, para las pruebas que no miran el tipo. */
        public ResultadoSancion(boolean sancionActiva, String motivo, java.time.OffsetDateTime vigenteHasta) {
            this(sancionActiva, motivo, vigenteHasta, null);
        }
    }
}
