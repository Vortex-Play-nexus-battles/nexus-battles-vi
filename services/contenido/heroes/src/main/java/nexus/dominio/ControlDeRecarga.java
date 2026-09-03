package nexus.dominio;

/**
 * Recarga por turnos de una habilidad. Las acciones especiales "tienen un
 * turno de carga" y su efecto "solo aplica en el turno en que se ejecutan"
 * (habilidades-y-mejoras.md, seccion 6.1.2). Las epicas "tienen dos turnos
 * de recarga" y "no usan puntos de poder" (epicas.md).
 */
public class ControlDeRecarga {

    public static final int TURNOS_DE_CARGA_ACCION_ESPECIAL = 1;
    public static final int TURNOS_DE_RECARGA_EPICA = 2;

    private final int turnosDeCarga;
    private Integer turnoDeUltimoUso;

    private ControlDeRecarga(int turnosDeCarga) {
        this.turnosDeCarga = turnosDeCarga;
    }

    public static ControlDeRecarga paraAccionEspecial() {
        return new ControlDeRecarga(TURNOS_DE_CARGA_ACCION_ESPECIAL);
    }

    public static ControlDeRecarga paraEpica() {
        return new ControlDeRecarga(TURNOS_DE_RECARGA_EPICA);
    }

    public void registrarUso(int turno) {
        if (!disponibleEn(turno)) {
            throw new IllegalStateException(
                    "La habilidad está en recarga: faltan " + turnosRestantesEn(turno) + " turno(s).");
        }
        turnoDeUltimoUso = turno;
    }

    public boolean disponibleEn(int turno) {
        return turnosRestantesEn(turno) == 0;
    }

    public int turnosRestantesEn(int turno) {
        if (turnoDeUltimoUso == null) {
            return 0;
        }
        int disponibleDesde = turnoDeUltimoUso + turnosDeCarga + 1;
        return Math.max(0, disponibleDesde - turno);
    }

    /** El efecto de una accion solo rige durante el turno en que fue ejecutada. */
    public boolean efectoVigenteEn(int turno) {
        return turnoDeUltimoUso != null && turnoDeUltimoUso == turno;
    }
}
