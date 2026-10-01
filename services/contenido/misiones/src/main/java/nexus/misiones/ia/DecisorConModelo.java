package nexus.misiones.ia;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import nexus.misiones.dominio.simulacion.DecididaPor;
import nexus.misiones.dominio.simulacion.DecisionDeTurno;
import nexus.misiones.dominio.simulacion.DecisorDeTurno;
import nexus.misiones.dominio.simulacion.TurnoParaDecidir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La IA de combate con red neuronal propia (HU-SIM-008, RF-MOT-59): <b>el modelo propone y la regla acota</b>.
 *
 * <p>Envuelve al decisor de la regla (heroes, 7.8.5) y sirve a los dos lados —el heroe y los enemigos—, que usan
 * la misma interfaz. En cada turno:
 * <ol>
 *   <li>Pregunta a la regla lo de siempre. Esa respuesta es la opcion de mayor prioridad y la salida de emergencia.</li>
 *   <li>Reune las <b>candidatas legales</b>: la jugada de la regla, la que seria la siguiente viable de cada
 *       rotacion de menor prioridad (se la pregunta a la regla, rotacion por rotacion, sin reimplementar su
 *       viabilidad) y el ataque basico. Todas respetan la rotacion y su cursor, el costo en poder y la recarga
 *       porque las dijo la regla; el modelo no puede inventar una.</li>
 *   <li>El modelo puntua cada candidata. Si su mejor tiene al menos la confianza minima (probabilidad
 *       softmax) y supera a la jugada de la regla, decide el modelo; con empate, confianza baja, un fallo, una
 *       salida invalida o sin contexto del duelo, decide la regla.</li>
 * </ol>
 *
 * <p>Costo: una llamada a heroes por turno como hasta ahora, mas hasta dos por cada rotacion de menor prioridad
 * que la regla no haya agotado (maximo 2 extra con tres rotaciones). Con el modelo apagado este decorador ni
 * existe: ver {@code ConfiguracionDeIa}.
 *
 * <p>Nada de lo del modelo puede tumbar una simulacion: cualquier error propio (no de la regla al primer intento) se
 * registra y decide la regla. Un fallo de la regla en la primera llamada sube como siempre.
 */
public class DecisorConModelo implements DecisorDeTurno, AutoCloseable {

    private static final Logger BITACORA = LoggerFactory.getLogger(DecisorConModelo.class);

    /** Una mejora de menos que esto sobre la jugada de la regla es un empate. */
    private static final double EMPATE = 1e-6;
    /** De cada tantos fallos seguidos del modelo se vuelve a registrar el detalle (el primero siempre). */
    private static final long REGISTRAR_CADA = 200;

    private final DecisorDeTurno regla;
    private final Puntuador modelo;
    private final double confianzaMinima;
    private final AtomicLong fallos = new AtomicLong();

    /**
     * @param confianzaMinima probabilidad (0, 1] que debe tener la mejor candidata para que decida el modelo
     */
    public DecisorConModelo(DecisorDeTurno regla, Puntuador modelo, double confianzaMinima) {
        if (!(confianzaMinima > 0.0 && confianzaMinima <= 1.0)) {
            throw new IllegalArgumentException("La confianza minima es una probabilidad entre 0 (excluido) y 1.");
        }
        this.regla = java.util.Objects.requireNonNull(regla);
        this.modelo = java.util.Objects.requireNonNull(modelo);
        this.confianzaMinima = confianzaMinima;
    }

    @Override
    public DecisionDeTurno decidir(TurnoParaDecidir turno) {
        DecisionDeTurno deLaRegla = regla.decidir(turno);
        if (turno.rotaciones().isEmpty()) {
            return deLaRegla;
        }
        Optional<Caracteristicas.Situacion> situacion = Caracteristicas.Situacion.de(turno);
        if (situacion.isEmpty()) {
            return deLaRegla;
        }
        try {
            List<Opcion> opciones = candidatas(turno, deLaRegla);
            if (opciones.size() < 2) {
                return deLaRegla;
            }
            return elegir(turno, deLaRegla, opciones, situacion.get());
        } catch (RuntimeException e) {
            registrarFallo(e);
            return deLaRegla;
        }
    }

    // ------------------------------------------------------------------ candidatas

    /** Una opcion legal con los cursores que dejaria si se juega. */
    private record Opcion(DecisionDeTurno.Candidata candidata, List<Integer> cursores) {
    }

    private List<Opcion> candidatas(TurnoParaDecidir turno, DecisionDeTurno deLaRegla) {
        int n = turno.rotaciones().size();
        List<Integer> actuales = cursoresActuales(turno);
        List<Opcion> opciones = new ArrayList<>();
        opciones.add(new Opcion(new DecisionDeTurno.Candidata(deLaRegla.accion(), deLaRegla.costoDePoder(),
                deLaRegla.rotacion(), null), deLaRegla.cursoresSiguientes()));

        // Las rotaciones de menor prioridad que la elegida: la siguiente viable de cada una, preguntada a la regla.
        int desde = deLaRegla.rotacion() == null || deLaRegla.esAtaqueBasico() ? n : deLaRegla.rotacion();
        while (desde < n) {
            TurnoParaDecidir resto = new TurnoParaDecidir(turno.prototipo(), turno.nivel(),
                    turno.rotaciones().subList(desde, n), turno.turno(), turno.poder(), turno.vida(),
                    turno.turnoDeUltimoUso(), turno.cursores().isEmpty() ? List.of()
                            : actuales.subList(desde, n), turno.contexto());
            DecisionDeTurno d = regla.decidir(resto);
            if (d.rotacion() == null || d.esAtaqueBasico()) {
                break;
            }
            int global = desde + d.rotacion();
            List<Integer> cursores = new ArrayList<>(actuales);
            cursores.set(global - 1, d.cursoresSiguientes().get(d.rotacion() - 1));
            if (opciones.stream().noneMatch(o -> o.candidata().accion().equals(d.accion()))) {
                opciones.add(new Opcion(new DecisionDeTurno.Candidata(d.accion(), d.costoDePoder(), global, null),
                        cursores));
            }
            desde = global;
        }

        if (opciones.stream().noneMatch(o -> o.candidata().accion().equals(DecisionDeTurno.ATAQUE_BASICO))) {
            // El ataque basico siempre se puede jugar y no mueve ningun cursor.
            opciones.add(new Opcion(new DecisionDeTurno.Candidata(DecisionDeTurno.ATAQUE_BASICO, 0, null, null),
                    actuales));
        }
        return opciones;
    }

    /** Los cursores de cada rotacion tal como entran al turno (los ausentes valen cero). */
    private static List<Integer> cursoresActuales(TurnoParaDecidir turno) {
        List<Integer> cursores = new ArrayList<>();
        for (int i = 0; i < turno.rotaciones().size(); i++) {
            cursores.add(turno.cursores().size() > i ? turno.cursores().get(i) : 0);
        }
        return cursores;
    }

    // ------------------------------------------------------------------ eleccion

    private DecisionDeTurno elegir(TurnoParaDecidir turno, DecisionDeTurno deLaRegla, List<Opcion> opciones,
                                   Caracteristicas.Situacion situacion) {
        float[][] filas = new float[opciones.size()][];
        for (int i = 0; i < filas.length; i++) {
            DecisionDeTurno.Candidata c = opciones.get(i).candidata();
            filas[i] = Caracteristicas.de(situacion, c.accion(), c.costoDePoder());
        }
        float[] puntajes = modelo.puntuar(filas);
        if (puntajes == null || puntajes.length != filas.length) {
            throw new IllegalStateException("El modelo devolvio " + (puntajes == null ? "nada" : puntajes.length)
                    + " puntajes para " + filas.length + " candidatas.");
        }
        for (float p : puntajes) {
            if (!Float.isFinite(p)) {
                throw new IllegalStateException("El modelo devolvio un puntaje que no es un numero: " + p);
            }
        }
        double[] probabilidades = softmax(puntajes);
        int mejor = 0;
        for (int i = 1; i < probabilidades.length; i++) {
            if (probabilidades[i] > probabilidades[mejor]) {
                mejor = i;   // estrictamente mayor: con igualdad queda la de mayor prioridad
            }
        }

        List<DecisionDeTurno.Candidata> puntuadas = new ArrayList<>();
        for (int i = 0; i < opciones.size(); i++) {
            DecisionDeTurno.Candidata c = opciones.get(i).candidata();
            puntuadas.add(new DecisionDeTurno.Candidata(c.accion(), c.costoDePoder(), c.rotacion(),
                    (double) puntajes[i]));
        }

        boolean seguro = probabilidades[mejor] >= confianzaMinima
                && (mejor == 0 || probabilidades[mejor] - probabilidades[0] > EMPATE);
        Opcion elegida = opciones.get(mejor);
        if (!seguro || !esLegal(turno, elegida.candidata())) {
            return deLaRegla.consultandoAlModelo(DecididaPor.REGLA, modelo.version(), puntuadas);
        }
        DecisionDeTurno.Candidata c = elegida.candidata();
        return new DecisionDeTurno(c.accion(), c.costoDePoder(), elegida.cursores(), c.rotacion(),
                DecididaPor.MODELO, modelo.version(), puntuadas);
    }

    /**
     * Ultima guarda, estructural: lo elegido es el ataque basico o un paso de alguna de las rotaciones del turno, y
     * el poder alcanza. Las candidatas ya salen de la regla, asi que esto solo salta ante un error de programacion.
     */
    private static boolean esLegal(TurnoParaDecidir turno, DecisionDeTurno.Candidata c) {
        if (DecisionDeTurno.ATAQUE_BASICO.equals(c.accion())) {
            return true;
        }
        boolean enAlgunaRotacion = turno.rotaciones().stream().anyMatch(r -> r.contains(c.accion()));
        return enAlgunaRotacion && c.costoDePoder() <= turno.poder();
    }

    private static double[] softmax(float[] puntajes) {
        double maximo = Double.NEGATIVE_INFINITY;
        for (float p : puntajes) {
            maximo = Math.max(maximo, p);
        }
        double[] e = new double[puntajes.length];
        double suma = 0;
        for (int i = 0; i < e.length; i++) {
            e[i] = Math.exp(puntajes[i] - maximo);
            suma += e[i];
        }
        for (int i = 0; i < e.length; i++) {
            e[i] /= suma;
        }
        return e;
    }

    /** Libera el modelo si es de los que tienen recursos nativos (ONNX Runtime); Spring lo llama al apagar. */
    @Override
    public void close() {
        if (modelo instanceof AutoCloseable cerrable) {
            try {
                cerrable.close();
            } catch (Exception e) {
                BITACORA.warn("No se pudo cerrar el modelo de IA: {}", e.toString());
            }
        }
    }

    private void registrarFallo(RuntimeException e) {
        long n = fallos.incrementAndGet();
        if (n == 1 || n % REGISTRAR_CADA == 0) {
            BITACORA.warn("La IA con modelo fallo ({} veces desde que arranco): decide la regla. {}", n, e.toString(), e);
        }
    }
}
