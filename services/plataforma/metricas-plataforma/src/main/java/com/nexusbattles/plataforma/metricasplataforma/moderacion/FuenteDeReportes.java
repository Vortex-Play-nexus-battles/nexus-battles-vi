package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Puerto hacia comentarios: {@code GET /api/v1/comentarios/moderacion/reportes/frecuencia}
 * (comentarios.yaml 1.15.0). Cuantos reportes hubo en el periodo, por dia UTC
 * y por categoria — la «alta frecuencia de reportes» de §7.3.8 y RF-MET-001.
 *
 * <p>Como las cuentas de ms-identidad, la ruta es de quien modera (Moderador,
 * Administrador, Super Administrador), no de un servicio: el token de quien
 * mira el tablero se reenvia tal cual y es comentarios quien decide.
 */
public interface FuenteDeReportes {

    /** Las seis categorias de RF-COM-006 que comentarios publica SIEMPRE en {@code porCategoria}, con 0 si no hubo. */
    List<String> CATEGORIAS_DEL_CONTRATO = List.of("SPAM", "CONTENIDO_OFENSIVO", "ACOSO", "INFORMACION_FALSA",
            "CONTENIDO_INAPROPIADO", "VIOLACION_DE_DERECHOS");

    /**
     * Forma del contrato; comentarios cuenta, aqui no se recalcula nada. Los numeros van en {@code Long} y no en
     * {@code long} por la misma razon que en {@link FuenteDeUsuarios.Indicadores}: un campo que no llego queda en
     * {@code null} y se rechaza, en vez de publicarse como un 0 que nadie dio.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Frecuencia(OffsetDateTime desde, OffsetDateTime hasta, Long total, List<PorDia> porDia,
                      Map<String, Long> porCategoria, Long comentariosReportados, Long reportantes) {

        /** Que falta de lo obligatorio del contrato, o vacio si esta completo. */
        Optional<String> incompleto() {
            if (total == null) {
                return Optional.of("total");
            }
            if (porDia == null || porDia.stream().anyMatch(d -> d == null || d.fecha() == null || d.reportes() == null)) {
                return Optional.of("porDia");
            }
            if (porCategoria == null || !porCategoria.keySet().containsAll(CATEGORIAS_DEL_CONTRATO)
                    || porCategoria.values().stream().anyMatch(Objects::isNull)) {
                return Optional.of("porCategoria con sus seis categorias");
            }
            if (comentariosReportados == null || reportantes == null) {
                return Optional.of("comentariosReportados y reportantes");
            }
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PorDia(String fecha, Long reportes) { }

    /**
     * @param autorizacion cabecera {@code Authorization} de quien consulta el tablero; {@code null} si no vino
     * @throws NoDisponible si comentarios no responde, niega el permiso, rechaza el periodo o responde algo que no es
     *                      la frecuencia del contrato. El tablero sigue con lo demas (RF-MET-001)
     */
    Frecuencia consultar(OffsetDateTime desde, OffsetDateTime hasta, String autorizacion);

    class NoDisponible extends RuntimeException {

        private final CausaDeIndisponibilidad causa;

        public NoDisponible(CausaDeIndisponibilidad causa, String motivo) {
            super(motivo);
            this.causa = causa;
        }

        public CausaDeIndisponibilidad causa() {
            return causa;
        }
    }
}
