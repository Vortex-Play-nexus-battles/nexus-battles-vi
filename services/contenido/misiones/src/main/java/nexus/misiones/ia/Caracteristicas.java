package nexus.misiones.ia;

import java.util.List;
import java.util.Optional;
import nexus.misiones.dominio.simulacion.ContextoDelDuelo;
import nexus.misiones.dominio.simulacion.EventoDeCombate;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;

/**
 * Las caracteristicas que ve la red de IA (VERSION 1): el vector numerico de una candidata en una situacion.
 *
 * <p>Es la MISMA definicion que el entrenamiento ({@code ia/nexus_ia/caracteristicas.py}); la prueba de los
 * casos dorados ({@code CaracteristicasTest}) garantiza que coinciden numero por numero. Si cambias algo aqui,
 * sube {@link #VERSION}, cambia el archivo de Python, regenera los dorados y reentrena: el cargador del modelo se
 * niega a usar un modelo hecho con otra version.
 *
 * <p>El modelo puntua UNA candidata: recibe el vector [situacion ++ candidata] y devuelve un numero. Posiciones
 * (DIMENSION = 54):
 *
 * <pre>
 *  0  vida propia / vida maxima                          [0, 1]
 *  1  poder propio / poder maximo                        [0, 1]
 *  2  min(poder propio, 12) / 12                         [0, 1]
 *  3  min(nivel, 8) / 8                                  [0, 1]
 *  4  min(turno, 20) / 20                                [0, 1]
 *  5  min(efectos propios, 4) / 4                        [0, 1]
 *  6  vida del oponente / su vida maxima                 [0, 1]
 *  7  poder del oponente / su poder maximo               [0, 1]
 *  8  min(nivel del oponente, 8) / 8                     [0, 1]
 *  9  min(efectos del oponente, 4) / 4                   [0, 1]
 * 10  posicion 0 menos posicion 6 (ventaja de vida)      [-1, 1]
 * 11-18 one-hot del prototipo propio (orden de la Tabla 7)
 * 19-26 one-hot del prototipo del oponente
 * 27-51 one-hot de la accion (0 = Ataque basico, 1-24 = Tabla 7)
 * 52  min(costo, 12) / 12                                [0, 1]
 * 53  costo / max(poder propio, 1), acotado              [0, 1]
 * </pre>
 *
 * Un prototipo o una accion desconocidos dejan su bloque one-hot en ceros.
 */
public final class Caracteristicas {

    public static final int VERSION = 1;

    /** Los ocho prototipos, en el orden de la Tabla 7 (el mismo que {@code tabla7.PROTOTIPOS} de Python). */
    public static final List<String> PROTOTIPOS = List.of("Guerrero Tanque", "Guerrero Armas", "Mago Fuego",
            "Mago Hielo", "Pícaro Veneno", "Pícaro Machete", "Chamán", "Médico");

    /** El ataque basico y las 24 acciones de la Tabla 7, en su orden. */
    public static final List<String> ACCIONES = List.of("Ataque básico",
            "Golpe con escudo", "Mano de piedra", "Defensa feroz",
            "Embate sangriento", "Lanza de los dioses", "Golpe de tormenta",
            "Misiles de magma", "Vulcano", "Pare de fuego",
            "Lluvia de hielo", "Cono de hielo", "Bola de hielo",
            "Flor de loto", "Agonía", "Piquete",
            "Cortada", "Machetazo", "Planazo",
            "Toque de la Vida", "Vínculo Natural", "Canto del Bosque",
            "Curación Directa", "Neutralización de Efectos", "Reanimación");

    public static final int DIMENSION = 11 + 2 * PROTOTIPOS.size() + ACCIONES.size() + 2;

    private static final double TOPE_PODER = 12;
    private static final double TOPE_NIVEL = 8;
    private static final double TOPE_TURNO = 20;
    private static final double TOPE_EFECTOS = 4;

    private Caracteristicas() {
    }

    /** Lo que se sabe al decidir: el estado propio y el del oponente. */
    public record Situacion(String prototipo, int nivel, int turno, int vida, int vidaMaxima, int poder,
                            int poderMaximo, int efectos, String oponentePrototipo, int oponenteNivel,
                            int oponenteVida, int oponenteVidaMaxima, int oponentePoder, int oponentePoderMaximo,
                            int oponenteEfectos) {

        /**
         * La situacion de un turno, con el contexto del duelo que arma el simulador; vacia si el turno no lo trae
         * (sin el, el modelo no puede decidir).
         */
        public static Optional<Situacion> de(TurnoParaDecidir turno) {
            ContextoDelDuelo c = turno.contexto();
            if (c == null || c.propio() == null || c.oponente() == null) {
                return Optional.empty();
            }
            EventoDeCombate.EstadoDeCombatiente propio = c.propio();
            EventoDeCombate.EstadoDeCombatiente otro = c.oponente();
            return Optional.of(new Situacion(turno.prototipo(), turno.nivel(), turno.turno(), turno.vida(),
                    propio.vidaMaxima(), turno.poder(), propio.poderMaximo(), propio.efectos().size(),
                    c.prototipoDelOponente(), c.nivelDelOponente(), otro.vida(), otro.vidaMaxima(), otro.poder(),
                    otro.poderMaximo(), otro.efectos().size()));
        }
    }

    /** El vector de {@link #DIMENSION} numeros de la candidata (accion, costo) en la situacion. */
    public static float[] de(Situacion s, String accion, int costo) {
        if (costo < 0) {
            throw new IllegalArgumentException("El costo de una candidata no puede ser negativo.");
        }
        double vidaPropia = fraccion(s.vida(), s.vidaMaxima());
        double vidaOponente = fraccion(s.oponenteVida(), s.oponenteVidaMaxima());
        float[] v = new float[DIMENSION];
        int i = 0;
        v[i++] = (float) vidaPropia;
        v[i++] = (float) fraccion(s.poder(), s.poderMaximo());
        v[i++] = (float) acotar(s.poder() / TOPE_PODER);
        v[i++] = (float) acotar(s.nivel() / TOPE_NIVEL);
        v[i++] = (float) acotar(s.turno() / TOPE_TURNO);
        v[i++] = (float) acotar(s.efectos() / TOPE_EFECTOS);
        v[i++] = (float) vidaOponente;
        v[i++] = (float) fraccion(s.oponentePoder(), s.oponentePoderMaximo());
        v[i++] = (float) acotar(s.oponenteNivel() / TOPE_NIVEL);
        v[i++] = (float) acotar(s.oponenteEfectos() / TOPE_EFECTOS);
        v[i++] = (float) (vidaPropia - vidaOponente);
        i = unoCaliente(v, i, PROTOTIPOS, s.prototipo());
        i = unoCaliente(v, i, PROTOTIPOS, s.oponentePrototipo());
        i = unoCaliente(v, i, ACCIONES, accion);
        v[i++] = (float) acotar(costo / TOPE_PODER);
        v[i] = (float) acotar(costo / (double) Math.max(s.poder(), 1));
        return v;
    }

    private static int unoCaliente(float[] destino, int desde, List<String> vocabulario, String valor) {
        int posicion = valor == null ? -1 : vocabulario.indexOf(valor);
        if (posicion >= 0) {
            destino[desde + posicion] = 1f;
        }
        return desde + vocabulario.size();
    }

    private static double acotar(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    private static double fraccion(int parte, int total) {
        return total <= 0 ? 0.0 : acotar(parte / (double) total);
    }
}
