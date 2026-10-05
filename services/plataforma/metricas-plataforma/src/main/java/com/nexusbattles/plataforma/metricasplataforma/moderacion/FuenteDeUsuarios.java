package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Puerto hacia ms-identidad: {@code GET /api/v1/admin/jugadores/indicadores}
 * (ms-identidad-admin.yaml 1.3.0). Cuentas por estado y altas por dia.
 *
 * <p>La ruta pide el permiso GESTIONAR_CUENTAS de una persona, no una credencial
 * de servicio: por eso el token de quien consulta el tablero se reenvia tal
 * cual y es identidad quien decide.
 */
public interface FuenteDeUsuarios {

    /** Forma del contrato; identidad cuenta, aqui no se recalcula nada. Campos nuevos de identidad se ignoran (cambio aditivo). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicadores(long total, Map<String, Long> porEstado, Registros registros, boolean ocultarPruebas,
                       OffsetDateTime calculadoEn) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Registros(String desde, String hasta, long total, List<DiaDeRegistro> porDia) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DiaDeRegistro(String fecha, long cuentas) { }

    /**
     * @param autorizacion cabecera {@code Authorization} del administrador que consulta el tablero; {@code null} si no vino
     * @throws NoDisponible si identidad no responde, niega el permiso o rechaza el rango. El mensaje es el motivo que
     *                      se muestra: se dice por su nombre, no se disimula (CA-03)
     */
    Indicadores consultar(String desde, String hasta, String autorizacion);

    class NoDisponible extends RuntimeException {
        public NoDisponible(String motivo) {
            super(motivo);
        }
    }
}
