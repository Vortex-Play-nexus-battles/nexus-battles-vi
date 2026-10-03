package nexus.misiones.dominio.simulacion;

import java.util.List;

/**
 * La jugada decidida: que accion, cuanto poder cuesta y con que cursores se
 * llama en el turno siguiente ({@code DecisionDelTurno} de heroes.yaml).
 *
 * <p>Ademas de la jugada, lleva la TRAZABILIDAD de HU-SIM-008: de que rotacion
 * salio, quien la decidio (la regla o el modelo propio), con que version del
 * modelo y entre que candidatas puntuadas. Una decision de la regla sola trae
 * todo eso vacio.
 *
 * @param rotacion         la rotacion (desde 1) de la que salio la accion; nula si fue el ataque basico de respaldo
 * @param decididaPor      quien tomo la decision
 * @param versionDelModelo la version del modelo que se consulto; nula si no se consulto ninguno
 * @param candidatas       las opciones legales que puntuo el modelo; vacia si no se consulto
 */
public record DecisionDeTurno(String accion, int costoDePoder, List<Integer> cursoresSiguientes, Integer rotacion,
                              DecididaPor decididaPor, String versionDelModelo, List<Candidata> candidatas) {

    /** Seccion 7.8.5: «ataque basico sin consumir poder». */
    public static final String ATAQUE_BASICO = "Ataque básico";

    public DecisionDeTurno {
        if (accion == null || accion.isBlank()) {
            throw new IllegalArgumentException("Una decision sin accion no es una decision.");
        }
        if (costoDePoder < 0) {
            throw new IllegalArgumentException("El costo de poder no puede ser negativo.");
        }
        cursoresSiguientes = cursoresSiguientes == null ? List.of() : List.copyOf(cursoresSiguientes);
        decididaPor = decididaPor == null ? DecididaPor.REGLA : decididaPor;
        candidatas = candidatas == null ? List.of() : List.copyOf(candidatas);
    }

    /** Una decision de la regla de siempre: sin rotacion conocida, sin modelo y sin candidatas. */
    public DecisionDeTurno(String accion, int costoDePoder, List<Integer> cursoresSiguientes) {
        this(accion, costoDePoder, cursoresSiguientes, null, DecididaPor.REGLA, null, List.of());
    }

    public boolean esAtaqueBasico() {
        return ATAQUE_BASICO.equals(accion);
    }

    public DecisionDeTurno deLaRotacion(Integer rotacion) {
        return new DecisionDeTurno(accion, costoDePoder, cursoresSiguientes, rotacion, decididaPor, versionDelModelo,
                candidatas);
    }

    /**
     * La misma jugada, dejando constancia de que se consulto al modelo: quien
     * la decidio al final, la version y las candidatas que puntuo.
     */
    public DecisionDeTurno consultandoAlModelo(DecididaPor quien, String version, List<Candidata> candidatas) {
        return new DecisionDeTurno(accion, costoDePoder, cursoresSiguientes, rotacion, quien, version, candidatas);
    }

    /**
     * Una opcion legal del turno (la regla ya la admite) con el puntaje que le dio el modelo.
     *
     * @param rotacion la rotacion de la que sale; nula para el ataque basico
     * @param puntaje  el puntaje del modelo; nulo si no se puntuo
     */
    public record Candidata(String accion, int costoDePoder, Integer rotacion, Double puntaje) {

        public Candidata {
            if (accion == null || accion.isBlank()) {
                throw new IllegalArgumentException("Una candidata sin accion no es una candidata.");
            }
            if (costoDePoder < 0) {
                throw new IllegalArgumentException("El costo de poder no puede ser negativo.");
            }
        }
    }
}
