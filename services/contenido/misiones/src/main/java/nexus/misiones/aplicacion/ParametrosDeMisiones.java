package nexus.misiones.aplicacion;

import java.time.Duration;
import java.util.Objects;
import nexus.misiones.dominio.Escalon;
import nexus.misiones.dominio.ParametrosDeRecompensa;

/**
 * La configuracion del modulo que no es del documento: el reloj de las
 * misiones en desarrollo, el ritmo del trabajo en segundo plano y las
 * decisiones del PO con su valor provisional. Sale de variables de entorno
 * (regla 10); ver {@code application.properties} y el README del servicio.
 *
 * @param duracionDeUnaHora  cuanto dura en tiempo real una hora de mision:
 *                           una hora en produccion; menos en el banco E2E
 * @param reintentoBase      espera del primer reintento de una entrega fallida
 * @param loteDelTrabajo     ejecuciones que el trabajo atiende por vuelta
 * @param correoActivo       si se escribe al jugador al terminar
 * @param avisosActivos      si se deja un aviso en su bandeja al terminar
 *                           (RF-NOT-004)
 * @param semillaDePruebas   solo pruebas: fija el azar de todas las
 *                           ejecuciones y la semilla del motor; nula en juego real
 * @param multiplicadorMitico decision del PO; nulo = Mitico no se ofrece
 */
public record ParametrosDeMisiones(
        Duration duracionDeUnaHora,
        Duration reintentoBase,
        int loteDelTrabajo,
        boolean correoActivo,
        boolean avisosActivos,
        Long semillaDePruebas,
        Double multiplicadorMitico,
        ParametrosDeRecompensa recompensas) {

    public ParametrosDeMisiones {
        Objects.requireNonNull(duracionDeUnaHora);
        if (duracionDeUnaHora.isZero() || duracionDeUnaHora.isNegative()) {
            throw new IllegalArgumentException("Una hora de mision tiene que durar algo.");
        }
        Objects.requireNonNull(reintentoBase);
        if (loteDelTrabajo < 1) {
            throw new IllegalArgumentException("El trabajo atiende al menos una ejecucion por vuelta.");
        }
        recompensas = recompensas == null ? ParametrosDeRecompensa.provisionales() : recompensas;
    }

    /** Multiplicador de estadisticas de los enemigos en un escalon; nulo si no se ofrece. */
    public Double multiplicadorDeEstadisticas(Escalon escalon) {
        return escalon == Escalon.MITICO ? multiplicadorMitico : escalon.multiplicadorDelDocumento();
    }

    /** Los de produccion: una hora es una hora. */
    public static ParametrosDeMisiones porOmision() {
        return new ParametrosDeMisiones(Duration.ofHours(1), Duration.ofSeconds(30), 20, true, true, null, null,
                ParametrosDeRecompensa.provisionales());
    }
}
