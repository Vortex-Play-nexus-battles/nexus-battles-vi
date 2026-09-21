package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Backing provisional de Sprint 2, acordado con ms-subastas (HU-SUB-001).
 *
 * <p>Todavia no existe el modelo ni el mecanismo para imponer sanciones, asi
 * que esto siempre responde "sin sancion activa". En Sprint 3 se reemplaza
 * por la consulta real contra el modelo de sanciones, manteniendo esta misma
 * firma -- el controlador y sus consumidores no cambian.
 */
@Service
public class ConsultaSancionActivaService {

    public ResultadoSancion consultar(UUID usuarioId) {
        return ResultadoSancion.SIN_SANCION;
    }

    public record ResultadoSancion(boolean sancionActiva, String motivo, java.time.OffsetDateTime vigenteHasta) {
        static final ResultadoSancion SIN_SANCION = new ResultadoSancion(false, null, null);
    }
}
