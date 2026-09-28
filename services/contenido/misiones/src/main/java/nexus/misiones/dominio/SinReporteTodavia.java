package nexus.misiones.dominio;

/**
 * La ejecucion no tiene reporte: sigue en curso, o se abandono (409). El
 * reporte se genera al completarse el tiempo (7.8.6).
 */
public class SinReporteTodavia extends ReglaDeMisionIncumplida {

    public SinReporteTodavia(EstadoEjecucion estado) {
        super(estado == EstadoEjecucion.EN_PROGRESO
                ? "La misión sigue en curso: el reporte estará listo cuando termine."
                : "Cancelaste esta misión: una misión abandonada no tiene reporte.");
    }
}
