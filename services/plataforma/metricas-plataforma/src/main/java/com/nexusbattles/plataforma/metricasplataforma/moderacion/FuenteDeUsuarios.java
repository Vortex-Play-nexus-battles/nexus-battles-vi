package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Puerto hacia ms-identidad: {@code GET /api/v1/admin/jugadores/indicadores}
 * (ms-identidad-admin.yaml 1.3.0). Cuentas por estado y altas por dia.
 *
 * <p>La ruta pide el permiso GESTIONAR_CUENTAS de una persona, no una credencial
 * de servicio: por eso el token de quien consulta el tablero se reenvia tal
 * cual y es identidad quien decide.
 */
public interface FuenteDeUsuarios {

    /** Los cinco estados que identidad publica SIEMPRE en {@code porEstado} (1.3.0), con 0 si no hay ninguna cuenta. */
    List<String> ESTADOS_DEL_CONTRATO = List.of("ACTIVO", "PENDIENTE_VERIFICACION", "INACTIVO", "SUSPENDIDO", "BANEADO");

    /**
     * Forma del contrato; identidad cuenta, aqui no se recalcula nada. Campos nuevos de identidad se ignoran (cambio
     * aditivo). Los numeros van en {@code Long} y no en {@code long} a proposito: un campo que identidad no mando
     * queda en {@code null} y se rechaza, en vez de publicarse como un 0 que nadie dio.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Indicadores(Long total, Map<String, Long> porEstado, Registros registros, boolean ocultarPruebas,
                       OffsetDateTime calculadoEn) {

        /**
         * Que falta de lo que el contrato de identidad marca obligatorio, o vacio si esta completo. Una respuesta
         * a la que le falta algo no se publica: el tablero la trata como «no disponible» (CA-03).
         */
        Optional<String> incompleto() {
            if (total == null) {
                return Optional.of("total");
            }
            if (porEstado == null || !porEstado.keySet().containsAll(ESTADOS_DEL_CONTRATO)
                    || porEstado.values().stream().anyMatch(Objects::isNull)) {
                return Optional.of("porEstado con sus cinco estados");
            }
            if (registros == null || registros.desde() == null || registros.hasta() == null
                    || registros.total() == null || registros.porDia() == null) {
                return Optional.of("registros");
            }
            if (registros.porDia().stream().anyMatch(d -> d == null || d.fecha() == null || d.cuentas() == null)) {
                return Optional.of("registros.porDia");
            }
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Registros(String desde, String hasta, Long total, List<DiaDeRegistro> porDia) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DiaDeRegistro(String fecha, Long cuentas) { }

    /**
     * @param autorizacion cabecera {@code Authorization} del administrador que consulta el tablero; {@code null} si no vino
     * @throws NoDisponible si identidad no responde, niega el permiso, rechaza el rango o responde algo que no son los
     *                      indicadores del contrato. El mensaje es el motivo que se muestra: se dice por su nombre, no
     *                      se disimula (CA-03)
     */
    Indicadores consultar(String desde, String hasta, String autorizacion);

    class NoDisponible extends RuntimeException {
        public NoDisponible(String motivo) {
            super(motivo);
        }
    }
}
