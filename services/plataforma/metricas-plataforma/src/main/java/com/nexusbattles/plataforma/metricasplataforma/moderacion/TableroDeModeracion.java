package com.nexusbattles.plataforma.metricasplataforma.moderacion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metricas de usuarios y moderacion — HU-MET-001 (RF-MET-001) y el panel
 * principal de §7.3.8.
 *
 * <p>Lo que hay: sanciones por periodo, por tipo y por dia, apelaciones,
 * moderadores activos, revertidas (fuente: moderacion-sanciones); las cuentas
 * por estado con las altas por dia (fuente: ms-identidad, ruta de indicadores
 * de HU-USR-008); y, desde SA-4, la frecuencia de reportes por dia y por
 * categoria (fuente: comentarios 1.15.0). Las sanciones son obligatorias; las
 * otras dos son accesorias: si una no se pudo leer, el tablero sale igual y lo
 * dice en {@code noDisponibles} (causa, para la consola) y en
 * {@code pendientes} (motivo tecnico, para quien diagnostica). RF-MET-001: el
 * error en un indicador «se informa sin bloquear el resto».
 *
 * <p><b>Alertas.</b> Cada serie se compara dia a dia con su umbral (D-25),
 * leido del catalogo de parametros ({@link UmbralesDeModeracion}). El umbral
 * es «a partir de»: el PO fijo el de sanciones en 3 el 2026-10-05, y un dia
 * con 3 o mas ya es alta frecuencia; el de reportes sigue sin cifra del PO.
 * Sin umbral no se evalua esa alerta y no se inventa ninguno. Las alertas van
 * dos veces: en {@code avisos}, con sus datos, para que la consola las
 * redacte; y en {@code alertas}, ya redactadas, por compatibilidad.
 *
 * @param alertasConfiguradas true si hay al menos un umbral (contrato 1.11.0; hasta la 1.10.0, solo el de sanciones)
 */
public record TableroDeModeracion(FuenteDeModeracion.Agregados sanciones, boolean alertasConfiguradas,
                                  Integer umbralSancionesPorDia, Integer umbralReportesPorDia,
                                  List<String> alertas, List<Aviso> avisos, List<String> pendientes,
                                  List<IndicadorNoDisponible> noDisponibles,
                                  FuenteDeUsuarios.Indicadores registroDeUsuarios,
                                  FuenteDeReportes.Frecuencia reportes) {

    /** Las dos series que tienen umbral de alta frecuencia. */
    public enum TipoDeAviso { SANCIONES, REPORTES }

    /** Los indicadores accesorios, los que pueden faltar sin tumbar el tablero. */
    public enum Indicador { REGISTRO_DE_USUARIOS, FRECUENCIA_DE_REPORTES }

    /** Un dia que llego al umbral de su serie. */
    public record Aviso(TipoDeAviso tipo, String fecha, long cantidad, int umbral) { }

    /** Un indicador accesorio que no se pudo leer, y por que. */
    public record IndicadorNoDisponible(Indicador indicador, CausaDeIndisponibilidad causa) { }

    /**
     * Lo que se leyo de una fuente accesoria: el valor, o por que no hay.
     *
     * @param <T> lo que publica la fuente
     */
    public record Lectura<T>(T valor, CausaDeIndisponibilidad causa, String motivo) {

        public static <T> Lectura<T> de(T valor) {
            return new Lectura<>(Objects.requireNonNull(valor, "una lectura buena tiene valor"), null, null);
        }

        public static <T> Lectura<T> fallida(CausaDeIndisponibilidad causa, String motivo) {
            return new Lectura<>(null, Objects.requireNonNull(causa, "una lectura fallida tiene causa"), motivo);
        }
    }

    public static TableroDeModeracion de(FuenteDeModeracion.Agregados agregados, UmbralesDeModeracion.Umbrales umbrales,
                                         Lectura<FuenteDeUsuarios.Indicadores> usuarios,
                                         Lectura<FuenteDeReportes.Frecuencia> reportes) {
        UmbralesDeModeracion.Umbrales vigentes = umbrales == null ? UmbralesDeModeracion.Umbrales.NINGUNO : umbrales;
        List<Aviso> avisos = new ArrayList<>();
        if (vigentes.sancionesPorDia() != null && agregados.porDia() != null) {
            int umbral = vigentes.sancionesPorDia();
            agregados.porDia().stream()
                    .filter(d -> d.emitidas() >= umbral)
                    .forEach(d -> avisos.add(new Aviso(TipoDeAviso.SANCIONES, d.fecha(), d.emitidas(), umbral)));
        }
        if (vigentes.reportesPorDia() != null && reportes.valor() != null) {
            int umbral = vigentes.reportesPorDia();
            reportes.valor().porDia().stream()
                    .filter(d -> d.reportes() >= umbral)
                    .forEach(d -> avisos.add(new Aviso(TipoDeAviso.REPORTES, d.fecha(), d.reportes(), umbral)));
        }

        List<String> pendientes = new ArrayList<>();
        List<IndicadorNoDisponible> noDisponibles = new ArrayList<>();
        if (usuarios.valor() == null) {
            pendientes.add("registro de nuevos usuarios: " + usuarios.motivo());
            noDisponibles.add(new IndicadorNoDisponible(Indicador.REGISTRO_DE_USUARIOS, usuarios.causa()));
        }
        if (reportes.valor() == null) {
            pendientes.add("frecuencia de reportes: " + reportes.motivo());
            noDisponibles.add(new IndicadorNoDisponible(Indicador.FRECUENCIA_DE_REPORTES, reportes.causa()));
        }
        boolean configuradas = vigentes.sancionesPorDia() != null || vigentes.reportesPorDia() != null;
        return new TableroDeModeracion(agregados, configuradas, vigentes.sancionesPorDia(), vigentes.reportesPorDia(),
                avisos.stream().map(TableroDeModeracion::redactar).toList(), List.copyOf(avisos),
                List.copyOf(pendientes), List.copyOf(noDisponibles), usuarios.valor(), reportes.valor());
    }

    /** La alerta ya redactada, para quien todavia lee {@code alertas} (contrato hasta la 1.10.0). */
    private static String redactar(Aviso aviso) {
        String serie = aviso.tipo() == TipoDeAviso.SANCIONES ? "sanciones" : "reportes";
        return "Alta frecuencia de " + serie + " el " + aviso.fecha() + ": " + aviso.cantidad()
                + " (umbral: " + aviso.umbral() + " o más por día)";
    }
}
