package nexus.combate.reglas;

import java.util.List;
import java.util.Objects;

/**
 * Lo que va a hacer una accion en ESTE turno, ya resuelta la regla: que tira,
 * a quien, cuanto suma cada cosa y que efectos deja. Lo construye
 * {@link Reglamento} a partir de la Tabla 7, la Tabla 20 o la accion basica;
 * lo ejecuta {@link MotorDeAcciones}.
 *
 * @param codigo          lo que se registra en las cargas y se anuncia
 * @param nombre          nombre del documento
 * @param tipo            clase de accion
 * @param objetivo        a quien puede ir
 * @param esEpica         epica de la Tabla 20 (sin poder, dos turnos de recarga)
 * @param potenciada      epica jugada por su tipo de heroe afin
 * @param datos           coste y carga del catalogo (acciones de la Tabla 7); nulo en las demas
 * @param turnosDeCarga   0 las basicas, 1 las de la Tabla 7, 2 las epicas
 * @param ataque          el golpe, si golpea
 * @param sanacion        la sanacion, si sana
 * @param efectosPropios  efectos que deja sobre si mismo
 * @param quitaPoderAlObjetivo puntos de poder que le quita al objetivo (Frio concentrado)
 * @param vinculo         se vincula con un companero (Reanimador 3000)
 */
public record Plan(
        String codigo,
        String nombre,
        TipoDeAccion tipo,
        Objetivo objetivo,
        boolean esEpica,
        boolean potenciada,
        AccionDelCatalogo datos,
        int turnosDeCarga,
        Ataque ataque,
        Sanacion sanacion,
        List<PlantillaDeEfecto> efectosPropios,
        int quitaPoderAlObjetivo,
        boolean vinculo) {

    public Plan {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(objetivo);
        efectosPropios = efectosPropios == null ? List.of() : List.copyOf(efectosPropios);
    }

    /** A quien puede ir una accion. */
    public enum Objetivo {
        /** Un rival en pie. */
        RIVAL,
        /** Uno mismo; no admite otro objetivo. */
        SI_MISMO,
        /** Uno mismo o un companero en pie. */
        ALIADO_O_SI_MISMO,
        /** Un companero en pie, no uno mismo. */
        COMPANERO,
        /** Un companero, caido o no, no uno mismo (Reanimacion). */
        COMPANERO_CAIDO_O_VIVO,
        /** Todo el grupo en pie; no admite objetivo. */
        GRUPO
    }

    /** Cuanto cuesta en poder: 0 las basicas y las epicas (§6.1.2: «no usan puntos de poder»). */
    public boolean alcanzaCon(int poder) {
        return datos == null || datos.alcanzaCon(poder);
    }

    /** El poder que queda despues de pagarla. */
    public int poderTrasPagar(int poder) {
        if (datos == null) {
            return poder;
        }
        return datos.todoElPoder() ? 0 : Math.max(0, poder - datos.costoPoder());
    }

    /**
     * El golpe.
     *
     * @param bonoAtaque          suma a la tirada de ataque
     * @param bonoDano            suma al dano
     * @param bonoCritico         puntos de % de critico solo para este golpe
     * @param alAcertar           efectos sobre el objetivo si el golpe causa efecto
     * @param retornaDanoRecibido «Pare de fuego»: suma de 0 a lo que el objetivo
     *                            le hizo en su ultimo golpe (D-B7-03)
     */
    public record Ataque(Tirada bonoAtaque, Tirada bonoDano, int bonoCritico,
                         List<PlantillaDeEfecto> alAcertar, boolean retornaDanoRecibido) {

        public static final Ataque BASICO = new Ataque(Tirada.NINGUNA, Tirada.NINGUNA, 0, List.of(), false);

        public Ataque {
            bonoAtaque = bonoAtaque == null ? Tirada.NINGUNA : bonoAtaque;
            bonoDano = bonoDano == null ? Tirada.NINGUNA : bonoDano;
            alAcertar = alAcertar == null ? List.of() : List.copyOf(alAcertar);
        }
    }

    /**
     * La sanacion.
     *
     * @param fuente   de donde sale la cantidad
     * @param cantidad bono sobre la formula, o la cantidad entera si es fija
     * @param destino  a quien sana
     * @param porTurno efectos de sanacion por turno que deja en cada sanado
     */
    public record Sanacion(Fuente fuente, Tirada cantidad, Destino destino, List<PlantillaDeEfecto> porTurno) {

        public Sanacion {
            Objects.requireNonNull(fuente);
            Objects.requireNonNull(destino);
            cantidad = cantidad == null ? Tirada.NINGUNA : cantidad;
            porTurno = porTurno == null ? List.of() : List.copyOf(porTurno);
        }

        /** La formula «Sanar» de la Tabla 6, una cantidad fija de la epica, o toda la vida. */
        public enum Fuente { FORMULA_SANAR, FIJA, VIDA_COMPLETA }

        /** El objetivo elegido, uno mismo o todo el grupo. */
        public enum Destino { OBJETIVO, SI_MISMO, GRUPO }
    }
}
