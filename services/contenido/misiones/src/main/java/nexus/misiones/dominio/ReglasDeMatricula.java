package nexus.misiones.dominio;

import java.time.Instant;

/**
 * Lo que la MISION exige para aceptar un heroe (7.8.6, 7.8.2 y 7.8.11). Lo que
 * exige del HEROE (que sea suyo, que este libre, que lleve equipo) lo
 * comprueban el inventario y heroes, que son sus duenos.
 */
public final class ReglasDeMatricula {

    private ReglasDeMatricula() {
    }

    /**
     * @param multiplicadorMitico el que fije el PO para Mitico; nulo = sin fijar
     * @param intentosEnElPeriodo ejecuciones que el jugador empezo en el periodo
     *                            actual de un desafio
     * @throws ReglaDeMisionIncumplida con un mensaje apto para el jugador
     */
    public static void exigir(Mision mision, SituacionDelJugador situacion, Escalon escalon,
                              Double multiplicadorMitico, long intentosEnElPeriodo, Instant ahora) {
        if (!mision.vigente(ahora)) {
            throw new ReglaDeMisionIncumplida("«" + mision.nombre() + "» ya no está disponible: su tiempo terminó.");
        }
        if (situacion.estado() == EstadoMision.EN_PROGRESO) {
            throw new ReglaDeMisionIncumplida("Ahora mismo ya tienes esta misión en curso: espera a que termine "
                    + "o cancélala antes de volver a empezarla.");
        }
        if (situacion.estado() == EstadoMision.BLOQUEADA) {
            throw new ReglaDeMisionIncumplida(situacion.motivoBloqueo());
        }
        if (!situacion.escalonesDesbloqueados().contains(escalon)) {
            Escalon anterior = escalon.anterior();
            throw new ReglaDeMisionIncumplida("Para jugarla en " + nombre(escalon) + " primero tienes que completarla en "
                    + nombre(anterior) + ".");
        }
        if (escalon == Escalon.MITICO && multiplicadorMitico == null) {
            throw new ReglaDeMisionIncumplida(
                    "El escalón Mítico todavía no está disponible: el documento no fija cuánto más fuertes son sus enemigos.");
        }
        Intentos intentos = mision.intentos();
        if (intentos != null && intentosEnElPeriodo >= intentos.maximo()) {
            throw new ReglaDeMisionIncumplida("Ya usaste tus " + intentos.maximo() + " intentos "
                    + (intentos.periodo() == Periodo.DIARIO ? "de hoy" : "de esta semana")
                    + " en este desafío. Vuelve a intentarlo en el siguiente periodo.");
        }
    }

    static String nombre(Escalon escalon) {
        return switch (escalon) {
            case NORMAL -> "Normal";
            case HEROICO -> "Heroico";
            case LEGENDARIO -> "Legendario";
            case MITICO -> "Mítico";
        };
    }
}
