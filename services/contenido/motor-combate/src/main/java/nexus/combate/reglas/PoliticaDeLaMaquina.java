package nexus.combate.reglas;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * Como juega la maquina: IA TACTICA POR REGLAS — decision D-41 (auditoria del
 * 4-oct, cambio autorizado n.º 4), que sustituye a la politica fija de D-B7-12.
 *
 * <p><b>Que no es.</b> No es una IA entrenada ni aprende de las partidas: el
 * «aprendizaje profundo» del §7.6 sigue fuera de este bloque (D-B7-13), porque
 * no hay modelo, ni datos, ni forma honesta de simularlo. Es un evaluador de
 * jugadas con puntaje, escrito a mano y comprobable.
 *
 * <p><b>Mismas reglas que un humano</b> (§6.1.3). La maquina:
 * <ol>
 *   <li>Enumera sus jugadas posibles: cada accion que el motor le marca como
 *       disponible ({@link MotorDeAcciones#accionesDe}) con cada objetivo que
 *       esa accion admite — un ataque solo contra rivales en pie, una sanacion
 *       solo sobre si misma o un companero. Nunca contra si misma ni contra un
 *       companero si la accion no lo permite.</li>
 *   <li>Ensaya cada una varias veces con {@link MotorDeAcciones#ensayar}: el
 *       MISMO codigo que resuelve una jugada de verdad, sobre una copia de la
 *       mesa. Si el reglamento la rechaza, la descarta. Por eso no puede
 *       elegir una jugada ilegal.</li>
 *   <li>Puntua lo que deja cada ensayo y elige la de mejor puntaje medio.</li>
 * </ol>
 *
 * <p><b>Sin mirar el azar de la partida.</b> Los ensayos usan un generador
 * propio, sembrado con el estado de la mesa: la maquina no ve las tiradas que
 * van a salir ni consume las de la partida, y el mismo estado da la misma
 * decision (se puede probar y auditar).
 *
 * <p><b>El puntaje</b>, desde el bando de la maquina (ella y sus companeros)
 * contra sus rivales:
 * <ul>
 *   <li>vida: {@value #ESCALA_DE_VIDA}·√(vida/maxima) — un punto de vida pesa
 *       mas cuanto menos le queda a quien lo pierde o lo recupera: rematar al
 *       rival tocado y curar al companero en apuros valen mas;</li>
 *   <li>seguir en pie: {@value #EN_PIE} — tumbar a un rival vale ademas eso;</li>
 *   <li>efectos activos, en vida equivalente (bonos propios, penalizaciones
 *       al rival, sangrados, sanaciones por turno, protecciones, vinculo);</li>
 *   <li>poder que no se perderia (§6.1.1: +2 por turno hasta el maximo), a
 *       {@value #VALOR_DEL_PODER} por punto hasta una reserva de
 *       {@value #RESERVA_DE_PODER}: gastar poder cuesta si escasea y no cuesta
 *       si sobra;</li>
 *   <li>en {@link DificultadDeLaMaquina#DIFICIL}, ademas, la respuesta
 *       esperada: cada rival que puede golpear ensaya su ataque mas fuerte al
 *       alcance contra el mas tocado de sus rivales, y lo que perderia el
 *       bando de la maquina resta. Asi valora defenderse, debilitar al que mas
 *       pega y tumbar primero.</li>
 * </ul>
 * El reflejo de unos Pinchos de escudo o de un «Toma y lleva» resta como
 * cualquier dano propio: la maquina lo evita cuando no le compensa.
 */
public final class PoliticaDeLaMaquina {

    /** Lo que decidio: la accion y a quien. */
    public record Decision(String accion, String objetivo) {
    }

    /** Una jugada legal posible: accion, objetivo y orden de aparicion (el desempate). */
    record Candidata(String accion, String objetivo, TipoDeAccion tipo, int orden) {
    }

    /** Una candidata con su puntaje esperado. */
    record Valorada(Candidata candidata, double puntaje) {
    }

    /** Valor de seguir en pie, aparte de la vida. */
    static final double EN_PIE = 60.0;
    /** La vida vale ESCALA·√(vida/maxima). */
    static final double ESCALA_DE_VIDA = 100.0;
    /** Valor de un punto de poder que no se perderia. */
    static final double VALOR_DEL_PODER = 0.6;
    /** Poder a partir del cual un punto mas ya no aporta: sobra para las especiales. */
    static final int RESERVA_DE_PODER = 12;
    /** Cuanto pesa la respuesta esperada de los rivales (solo DIFICIL). */
    static final double PESO_DE_LA_RESPUESTA = 0.8;
    /** Dos puntajes a menos de esto son el mismo: decide el orden. */
    private static final double EMPATE = 1e-9;

    private final MotorDeAcciones motor;
    private final DificultadDeLaMaquina dificultad;

    PoliticaDeLaMaquina(MotorDeAcciones motor, DificultadDeLaMaquina dificultad) {
        this.motor = Objects.requireNonNull(motor);
        this.dificultad = Objects.requireNonNull(dificultad);
    }

    /** La jugada de la maquina para {@code idEjecutor} sobre esta mesa, que no se toca. */
    Decision decidir(String idEjecutor, Mesa mesa, boolean porEquipos) {
        SplittableRandom propio = new SplittableRandom(semilla(idEjecutor, mesa, porEquipos));
        List<Valorada> valoradas = valorar(idEjecutor, mesa, porEquipos, propio);
        if (valoradas.isEmpty()) {
            // Ninguna jugada pasa el reglamento (p. ej. un prototipo sin fila en
            // la Tabla 21): la regla de siempre, para que el motor responda el
            // rechazo que corresponde en lugar de inventarse una jugada.
            return respaldo(idEjecutor, mesa, porEquipos, motor.reglamento());
        }
        return elegir(valoradas, propio);
    }

    // =====================================================================
    // Jugadas posibles
    // =====================================================================

    List<Candidata> candidatas(Contendiente yo, Mesa mesa, boolean porEquipos) {
        FichaDeCombate ficha = mesa.ficha(yo.id());
        List<Candidata> lista = new ArrayList<>();
        int orden = 0;
        for (EstadoDeAccion estado : motor.accionesDe(yo, ficha)) {
            if (!estado.disponible()) {
                continue;
            }
            Plan plan;
            try {
                plan = motor.reglamento().planPara(estado.codigo(), yo, ficha);
            } catch (AccionNoPermitida noEsSuya) {
                continue;
            }
            for (String objetivo : objetivosPara(plan.objetivo(), yo, mesa, porEquipos)) {
                lista.add(new Candidata(estado.codigo(), objetivo, plan.tipo(), orden++));
            }
        }
        return lista;
    }

    /** Los objetivos que admite la accion: los mismos que acepta {@code elegirObjetivo} del motor. */
    private static List<String> objetivosPara(Plan.Objetivo objetivo, Contendiente yo, Mesa mesa,
                                              boolean porEquipos) {
        List<String> ids = new ArrayList<>();
        switch (objetivo) {
            // Sin objetivo que elegir: el motor la dirige a quien la juega.
            case SI_MISMO, GRUPO -> ids.add(null);
            case RIVAL -> mesa.todos().stream()
                    .filter(c -> c.enPie() && yo.esRivalDe(c, porEquipos))
                    .forEach(c -> ids.add(c.id()));
            case ALIADO_O_SI_MISMO -> mesa.todos().stream()
                    .filter(c -> c.enPie() && (c.id().equals(yo.id()) || yo.esCompaneroDe(c, porEquipos)))
                    .forEach(c -> ids.add(c.id()));
            case COMPANERO -> mesa.todos().stream()
                    .filter(c -> c.enPie() && yo.esCompaneroDe(c, porEquipos))
                    .forEach(c -> ids.add(c.id()));
            case COMPANERO_CAIDO_O_VIVO -> mesa.todos().stream()
                    .filter(c -> yo.esCompaneroDe(c, porEquipos))
                    .forEach(c -> ids.add(c.id()));
        }
        return ids;
    }

    // =====================================================================
    // Ensayo y puntaje
    // =====================================================================

    List<Valorada> valorar(String idEjecutor, Mesa mesa, boolean porEquipos, SplittableRandom propio) {
        Contendiente yo = mesa.de(idEjecutor);
        // Las mismas semillas para todas las candidatas: se comparan con los
        // mismos dados, y la diferencia de puntaje es de la jugada, no del azar.
        long[] semillas = new long[dificultad.muestras()];
        for (int i = 0; i < semillas.length; i++) {
            semillas[i] = propio.nextLong();
        }
        double antes = valor(mesa, yo, porEquipos);
        List<Valorada> valoradas = new ArrayList<>();
        for (Candidata candidata : candidatas(yo, mesa, porEquipos)) {
            double suma = 0;
            boolean vale = true;
            for (long semilla : semillas) {
                SplittableRandom azar = new SplittableRandom(semilla);
                Mesa despues;
                try {
                    despues = motor.ensayar(mesa, idEjecutor, candidata.accion(), candidata.objetivo(), porEquipos,
                            azar);
                } catch (AccionNoPermitida noVale) {
                    vale = false;
                    break;
                }
                double ganancia = valor(despues, yo, porEquipos) - antes;
                if (dificultad.anticipa()) {
                    ganancia -= PESO_DE_LA_RESPUESTA * respuestaEsperada(despues, yo, porEquipos, azar);
                }
                suma += ganancia;
            }
            if (vale) {
                valoradas.add(new Valorada(candidata, suma / semillas.length));
            }
        }
        return valoradas;
    }

    /**
     * Lo que perderia el bando de la maquina con la respuesta de cada rival
     * que puede golpear: su ataque mas fuerte al alcance (la regla fija de
     * D-B7-12, la de un rival agresivo) contra el mas tocado de ese bando. Cada
     * respuesta se ensaya por separado sobre la mesa que deja la jugada.
     */
    double respuestaEsperada(Mesa despues, Contendiente yo, boolean porEquipos, SplittableRandom azar) {
        double base = valor(despues, yo, porEquipos);
        double perdida = 0;
        for (Contendiente rival : despues.todos()) {
            if (!rival.enPie() || !yo.esRivalDe(rival, porEquipos) || !rival.estadisticasResueltas().ataca()) {
                continue;
            }
            Decision golpe = respaldo(rival.id(), despues, porEquipos, motor.reglamento());
            if (golpe.objetivo() == null) {
                continue;
            }
            Mesa tras;
            try {
                tras = motor.ensayar(despues, rival.id(), golpe.accion(), golpe.objetivo(), porEquipos, azar);
            } catch (AccionNoPermitida noPuede) {
                try {
                    tras = motor.ensayar(despues, rival.id(), Reglamento.ATAQUE_BASICO, golpe.objetivo(), porEquipos,
                            azar);
                } catch (AccionNoPermitida tampoco) {
                    // Ese rival no puede golpear (sin fila en la Tabla 21): no amenaza.
                    continue;
                }
            }
            perdida += base - valor(tras, yo, porEquipos);
        }
        return perdida;
    }

    /** El valor de la mesa para el bando de {@code yo}: lo suyo menos lo de sus rivales. */
    double valor(Mesa mesa, Contendiente yo, boolean porEquipos) {
        List<Contendiente> todos = mesa.todos();
        double vidaMedia = todos.stream().mapToInt(Contendiente::vidaMaxima).average().orElse(1);
        // Un punto de vida cerca del maximo vale ESCALA/2 por vida maxima (derivada de √).
        double puntosPorVida = ESCALA_DE_VIDA / 2 / Math.max(1, vidaMedia);
        double total = 0;
        for (Contendiente c : todos) {
            double suyo = salud(c) + efectos(c, puntosPorVida) + poder(c);
            boolean mio = c.id().equals(yo.id()) || yo.esCompaneroDe(c, porEquipos);
            total += mio ? suyo : -suyo;
        }
        return total;
    }

    static double salud(Contendiente c) {
        if (!c.enPie()) {
            return 0;
        }
        return EN_PIE + ESCALA_DE_VIDA * Math.sqrt((double) c.vida() / c.vidaMaxima());
    }

    /** El poder que de verdad le sirve: ni el que se perderia al recuperar, ni el que sobra. */
    static double poder(Contendiente c) {
        if (!c.enPie()) {
            return 0;
        }
        int util = Math.min(c.poder(),
                Math.min(RESERVA_DE_PODER, Math.max(0, c.poderMaximo() - MotorDeAcciones.PODER_POR_TURNO)));
        return VALOR_DEL_PODER * util;
    }

    /**
     * Los efectos que lleva, en vida equivalente. Con anticipacion (DIFICIL)
     * no se cuentan las protecciones ni las penalizaciones al rival: las mide
     * la respuesta ensayada, que es mas exacta.
     */
    double efectos(Contendiente c, double puntosPorVida) {
        if (!c.enPie()) {
            return 0;
        }
        boolean mideLaRespuesta = dificultad.anticipa();
        double vida = 0;
        for (EfectoActivo e : c.efectos()) {
            int veces = Math.max(1, e.turnos());
            vida += switch (e.tipo()) {
                case BONO_DANO, BONO_SANACION -> e.valor() * veces;
                case BONO_ATAQUE -> 0.5 * e.valor() * veces;
                case BONO_CRITICO -> 0.2 * e.valor();
                case PENALIZA_DANO -> mideLaRespuesta ? 0 : -1.0 * e.valor() * veces;
                case PENALIZA_ATAQUE -> mideLaRespuesta ? 0 : -0.5 * e.valor() * veces;
                case BONO_DEFENSA -> mideLaRespuesta ? 0 : 0.4 * e.valor();
                case REDUCE_MAGICO -> mideLaRespuesta ? 0 : 0.5 * e.valor();
                case INMUNE_FISICO -> mideLaRespuesta ? 0 : 0.15 * c.vidaMaxima();
                case INMUNE_TOTAL -> mideLaRespuesta ? 0 : 0.20 * c.vidaMaxima();
                case REFLEJA_MITAD -> mideLaRespuesta ? 0 : 0.10 * c.vidaMaxima();
                case ENVENENA_AL_ATACANTE -> mideLaRespuesta ? 0 : 0.5 * e.valor() * e.turnos();
                case DANO_POR_TURNO -> -1.0 * e.valor() * veces;
                case SANACION_POR_TURNO -> e.valor() * veces;
                case VINCULO_REANIMACION -> 0.20 * c.vidaMaxima();
            };
        }
        return vida * puntosPorVida;
    }

    // =====================================================================
    // Eleccion
    // =====================================================================

    private Decision elegir(List<Valorada> valoradas, SplittableRandom propio) {
        List<Valorada> orden = new ArrayList<>(valoradas);
        orden.sort((a, b) -> {
            double diferencia = b.puntaje() - a.puntaje();
            if (Math.abs(diferencia) > EMPATE) {
                return diferencia > 0 ? 1 : -1;
            }
            return Integer.compare(a.candidata().orden(), b.candidata().orden());
        });
        Valorada elegida = orden.get(0);
        if (dificultad.descuido() > 0 && orden.size() > 1 && propio.nextDouble() < dificultad.descuido()) {
            elegida = orden.get(propio.nextInt(Math.min(dificultad.entreLasMejores(), orden.size())));
        }
        return new Decision(elegida.candidata().accion(), elegida.candidata().objetivo());
    }

    /**
     * Semilla del generador propio: el estado de la mesa, quien decide y la
     * dificultad (FNV-1a de 64 bits). Nada del azar de la partida.
     */
    private long semilla(String idEjecutor, Mesa mesa, boolean porEquipos) {
        StringBuilder estado = new StringBuilder()
                .append(dificultad).append('|').append(idEjecutor).append('|').append(porEquipos);
        for (Contendiente c : mesa.todos()) {
            estado.append('|').append(c.id()).append(',').append(c.equipo()).append(',').append(c.prototipo())
                    .append(',').append(c.nivel()).append(',').append(c.vida()).append(',').append(c.poder())
                    .append(',').append(c.turnosJugados()).append(',').append(new TreeMap<>(c.cargas()))
                    .append(',');
            for (EfectoActivo e : c.efectos()) {
                estado.append(e.codigo()).append(':').append(e.valor()).append(':').append(e.turnos()).append(';');
            }
        }
        long h = 0xcbf29ce484222325L;
        for (byte b : estado.toString().getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return h;
    }

    // =====================================================================
    // Respaldo: la regla fija de D-B7-12
    // =====================================================================

    /**
     * La politica anterior (D-B7-12): la especial de ataque mas cara que pueda
     * pagar contra el rival en pie con menos vida; un sanador sana al mas
     * herido de su bando. Solo se usa si ninguna jugada pasa el reglamento, y
     * en las simulaciones como referencia contra la que medir la nueva.
     */
    static Decision respaldo(String idEjecutor, Mesa mesa, boolean porEquipos, Reglamento reglamento) {
        Contendiente yo = mesa.de(idEjecutor);
        FichaDeCombate ficha = mesa.ficha(idEjecutor);
        List<Contendiente> todos = mesa.todos();

        if (!yo.estadisticasResueltas().ataca()) {
            Contendiente masHerido = todos.stream()
                    .filter(c -> c.enPie() && (c.id().equals(idEjecutor) || yo.esCompaneroDe(c, porEquipos)))
                    .min(Comparator.comparingDouble(c -> (double) c.vida() / c.vidaMaxima()))
                    .orElse(yo);
            return new Decision(Reglamento.SANACION_BASICA, masHerido.id());
        }

        // max() de un flujo ordenado se queda con el PRIMERO de los empatados
        // (BinaryOperator.maxBy): a igual coste gana el orden de la Tabla 7.
        Optional<AccionDelCatalogo> especial = ficha.acciones().stream()
                .filter(a -> reglamento.tipoDe(a).filter(t -> t == TipoDeAccion.ATAQUE).isPresent())
                .filter(a -> yo.nivel() >= a.nivelRequerido())
                .filter(a -> fueraDeCarga(yo, a))
                .filter(a -> a.alcanzaCon(yo.poder()))
                .max(Comparator.comparingInt(a -> costeDe(a, yo)));

        Contendiente objetivo = todos.stream()
                .filter(c -> c.enPie() && yo.esRivalDe(c, porEquipos))
                .min(Comparator.comparingInt(Contendiente::vida))
                .orElse(null);
        return new Decision(especial.map(AccionDelCatalogo::nombre).orElse(Reglamento.ATAQUE_BASICO),
                objetivo == null ? null : objetivo.id());
    }

    private static int costeDe(AccionDelCatalogo accion, Contendiente yo) {
        return accion.todoElPoder() ? yo.poder() : accion.costoPoder();
    }

    private static boolean fueraDeCarga(Contendiente yo, AccionDelCatalogo accion) {
        Integer ultimo = yo.cargas().get(accion.nombre());
        int carga = accion.turnosDeCarga() > 0 ? accion.turnosDeCarga() : Reglamento.TURNOS_DE_CARGA_ACCION;
        return ultimo == null || MotorDeAcciones.turnosQueFaltan(carga, ultimo, yo.turnosJugados()) == 0;
    }
}
