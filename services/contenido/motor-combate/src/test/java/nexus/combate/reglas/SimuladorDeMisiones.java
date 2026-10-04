package nexus.combate.reglas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nexus.combate.IndiceNormal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Misiones completas contra el motor real — auditoria del 4-oct, cambio
 * autorizado n.º 2 («siempre falla», progresion de 1 a 8).
 *
 * <p>Reproduce la estructura de la simulacion del servicio de misiones
 * (HU-SIM-003, {@code SimuladorDeMision}): los encuentros de la semilla en su
 * orden y el jefe al final; la vida del heroe se arrastra de un encuentro al
 * siguiente; el poder, las cargas y los efectos empiezan de cero en cada duelo
 * (§6.1.1: «el poder se recupera instantaneamente al concluir el combate»);
 * quien empieza cada duelo se sortea (§6.1.3); un duelo dura como mucho
 * {@value #RONDAS_MAXIMAS_POR_DUELO} rondas; cada enemigo derrotado da
 * 10 x 1,2^(1d8) de experiencia (§6.1.1).
 *
 * <p>Los enemigos pelean en el NIVEL RECOMENDADO de la mision (§7.8.13: «las
 * estadisticas de enemigos deben escalar segun nivel de mision») con la vida y
 * la defensa que fija la semilla. Juegan, como en el servicio, su ESTRATEGIA
 * PREDEFINIDA (HU-SIM-004, {@code estrategias-de-enemigos.json}) del prototipo
 * y el tramo de ese nivel, con la regla de rotaciones del heroe (7.8.5): en
 * cada turno la primera rotacion cuyo paso sea viable (poder y recarga), si no
 * la siguiente, si no el ataque basico. El prototipo que no trae estrategia, y
 * todo el simulador si el archivo no existe, juega su ataque mas fuerte al
 * alcance (la regla fija de D-B7-12, la de un rival agresivo; es lo que
 * median las cifras de D-42). El heroe juega como un jugador con rotaciones
 * (esa misma regla de D-B7-12) o como la IA tactica.
 *
 * <p>Lee las semillas del servicio de misiones como datos: no depende de su
 * codigo, que es otro microservicio.
 */
final class SimuladorDeMisiones {

    /** Desde la carpeta de motor-combate, donde corren sus pruebas. */
    static final Path SEMILLAS = Path.of("..", "misiones", "src", "main", "resources", "semilla");

    /** El tope de {@code SimuladorDeMision.RONDAS_MAXIMAS_POR_COMBATE}. */
    static final int RONDAS_MAXIMAS_POR_DUELO = 100;

    /** §6.1.1: nivel maximo. */
    static final int NIVEL_MAXIMO = 8;

    /** Un enemigo de la semilla: su prototipo y, si los fija, su vida y su defensa. */
    record Enemigo(String nombre, String prototipo, Integer vida, Integer defensa) {
    }

    /** Lo que la simulacion necesita de una mision de la semilla. */
    record Mision(String id, String nombre, String origen, Integer nivelRecomendado, List<String> requisitos,
                  List<Enemigo> encuentros) {
    }

    /**
     * Una estrategia predefinida de enemigo (HU-SIM-004): hasta tres rotaciones
     * por prioridad; vale desde {@code desdeNivel} hasta el tramo siguiente.
     */
    record EstrategiaPredefinida(String id, String prototipo, int desdeNivel, List<List<String>> rotaciones) {
    }

    /** Lo que decide jugar un enemigo: la accion y contra quien (nulo si no lleva objetivo). */
    record Jugada(String accion, String objetivo) {
    }

    /** El archivo de las estrategias, junto a las semillas de misiones. */
    static final String ARCHIVO_DE_ESTRATEGIAS = "estrategias-de-enemigos.json";

    /** Como acabo una ejecucion. */
    record Resultado(boolean exito, int derrotados, double experiencia) {
    }

    /** Como juega el heroe. */
    enum Juego {
        /** Como un jugador con rotaciones: su ataque mas fuerte al alcance. */
        ROTACION,
        /** La IA tactica en NORMAL. */
        IA
    }

    private final CatalogoDePrueba catalogo = new CatalogoDePrueba();
    private final MotorDeAcciones motor = new MotorDeAcciones(catalogo, IndiceNormal.porOmision());
    private final List<EstrategiaPredefinida> estrategias;
    /** Lo que jugaron los enemigos («prototipo|accion»), si se pidio registrarlo. */
    private List<String> jugadas;

    /** Con las estrategias predefinidas de la semilla, si existe el archivo. */
    SimuladorDeMisiones() {
        this(estrategias(ARCHIVO_DE_ESTRATEGIAS));
    }

    /** Con estas estrategias; ninguna = los enemigos juegan su ataque mas fuerte al alcance, como antes de HU-SIM-004. */
    SimuladorDeMisiones(List<EstrategiaPredefinida> estrategias) {
        this.estrategias = List.copyOf(estrategias);
    }

    /** La politica de antes de HU-SIM-004: todos los enemigos juegan su ataque mas fuerte al alcance. */
    static SimuladorDeMisiones conPoliticaAnterior() {
        return new SimuladorDeMisiones(List.of());
    }

    /** Anota cada jugada de un enemigo como «prototipo|accion» (para las pruebas). */
    SimuladorDeMisiones registrandoJugadas() {
        this.jugadas = new ArrayList<>();
        return this;
    }

    List<String> jugadasRegistradas() {
        return List.copyOf(jugadas);
    }

    // =====================================================================
    // Semillas
    // =====================================================================

    /** Las misiones de las semillas que existan, en el orden de los archivos. */
    static List<Mision> semillas(String... archivos) {
        ObjectMapper json = new ObjectMapper();
        List<Mision> misiones = new ArrayList<>();
        for (String archivo : archivos) {
            Path ruta = SEMILLAS.resolve(archivo);
            if (!Files.exists(ruta)) {
                continue;
            }
            JsonNode raiz;
            try {
                raiz = json.readTree(ruta.toFile());
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer " + ruta, e);
            }
            for (JsonNode m : raiz.path("misiones")) {
                List<Enemigo> encuentros = new ArrayList<>();
                for (JsonNode grupo : m.path("enemigos")) {
                    for (int i = 0; i < grupo.path("cantidad").asInt(); i++) {
                        encuentros.add(enemigo(grupo));
                    }
                }
                if (!m.path("jefe").isMissingNode() && !m.path("jefe").isNull()) {
                    encuentros.add(enemigo(m.path("jefe")));
                }
                List<String> requisitos = new ArrayList<>();
                m.path("requisitosPrevios").forEach(r -> requisitos.add(r.asText()));
                misiones.add(new Mision(m.path("id").asText(), m.path("nombre").asText(), m.path("origen").asText(),
                        m.path("nivelRecomendado").isNull() ? null : m.path("nivelRecomendado").asInt(),
                        requisitos, encuentros));
            }
        }
        return misiones;
    }

    private static Enemigo enemigo(JsonNode nodo) {
        return new Enemigo(nodo.path("nombre").asText(), nodo.path("prototipo").asText(),
                nodo.path("vida").isNull() || nodo.path("vida").isMissingNode() ? null : nodo.path("vida").asInt(),
                nodo.path("defensa").isNull() || nodo.path("defensa").isMissingNode() ? null
                        : nodo.path("defensa").asInt());
    }

    /** Las estrategias de ese archivo de la semilla; ninguna si el archivo no existe. */
    static List<EstrategiaPredefinida> estrategias(String archivo) {
        Path ruta = SEMILLAS.resolve(archivo);
        if (!Files.exists(ruta)) {
            return List.of();
        }
        JsonNode raiz;
        try {
            raiz = new ObjectMapper().readTree(ruta.toFile());
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + ruta, e);
        }
        List<EstrategiaPredefinida> lista = new ArrayList<>();
        for (JsonNode e : raiz.path("estrategias")) {
            List<List<String>> rotaciones = new ArrayList<>();
            for (JsonNode rotacion : e.path("rotaciones")) {
                List<String> pasos = new ArrayList<>();
                rotacion.forEach(paso -> pasos.add(paso.asText()));
                rotaciones.add(pasos);
            }
            lista.add(new EstrategiaPredefinida(e.path("id").asText(), e.path("prototipo").asText(),
                    e.path("desdeNivel").asInt(), rotaciones));
        }
        return lista;
    }

    /** La estrategia del prototipo en el tramo de ese nivel: la de mayor {@code desdeNivel} que no lo pasa. */
    Optional<EstrategiaPredefinida> estrategiaPara(String prototipo, int nivel) {
        String buscado = Nombres.normalizar(prototipo);
        return estrategias.stream()
                .filter(e -> Nombres.normalizar(e.prototipo()).equals(buscado) && e.desdeNivel() <= nivel)
                .max(Comparator.comparingInt(EstrategiaPredefinida::desdeNivel));
    }

    // =====================================================================
    // Una ejecucion
    // =====================================================================

    /** Una ejecucion de la mision con un heroe de ese prototipo y nivel, sin equipo ni epicas. */
    Resultado jugar(Mision mision, String prototipo, int nivelDelHeroe, Juego juego, long semilla) {
        Random azar = new Random(semilla);
        int nivelDeLosEnemigos = mision.nivelRecomendado() == null ? nivelDelHeroe : mision.nivelRecomendado();
        int vidaDelHeroe = Integer.MAX_VALUE;
        int derrotados = 0;
        double experiencia = 0;
        for (Enemigo enemigo : mision.encuentros()) {
            Contendiente heroe = new Contendiente("heroe", null, prototipo, nivelDelHeroe, null, vidaDelHeroe,
                    Integer.MAX_VALUE, 0, Map.of(), List.of(), List.of(), List.of(), null);
            List<Contendiente> mesa = duelo(heroe, rival(enemigo, nivelDeLosEnemigos), juego,
                    estrategiaPara(enemigo.prototipo(), nivelDeLosEnemigos).orElse(null), azar);
            vidaDelHeroe = de(mesa, "heroe").vida();
            if (de(mesa, "rival").enPie()) {
                return new Resultado(false, derrotados, experiencia);
            }
            derrotados++;
            experiencia += 10 * Math.pow(1.2, 1 + azar.nextInt(8));
        }
        return new Resultado(true, derrotados, experiencia);
    }

    private Contendiente rival(Enemigo enemigo, int nivel) {
        Estadisticas del = catalogo.ficha(enemigo.prototipo(), nivel).estadisticas();
        Estadisticas suyas = new Estadisticas(del.poder(),
                enemigo.vida() != null ? enemigo.vida() : del.vida(),
                enemigo.defensa() != null ? enemigo.defensa() : del.defensa(),
                del.ataque(), del.dano(), del.sanar());
        return new Contendiente("rival", null, enemigo.prototipo(), nivel, suyas, suyas.vida(), Integer.MAX_VALUE,
                0, Map.of(), List.of(), List.of(), List.of(), null);
    }

    private List<Contendiente> duelo(Contendiente heroe, Contendiente rival, Juego juego,
                                     EstrategiaPredefinida estrategiaDelRival, Random azar) {
        List<Contendiente> mesa = List.of(heroe, rival);
        int[] cursores = new int[estrategiaDelRival == null ? 0 : estrategiaDelRival.rotaciones().size()];
        boolean empiezaElHeroe = azar.nextBoolean();
        List<String> orden = empiezaElHeroe ? List.of("heroe", "rival") : List.of("rival", "heroe");
        for (int ronda = 1; ronda <= RONDAS_MAXIMAS_POR_DUELO && ambosEnPie(mesa); ronda++) {
            for (String quien : orden) {
                if (!ambosEnPie(mesa)) {
                    break;
                }
                mesa = motor.iniciarTurno(new SolicitudDeTurno(quien, false, mesa), azar).combatientes();
                if (!ambosEnPie(mesa)) {
                    break;
                }
                mesa = quien.equals("heroe") ? actuar(quien, mesa, juego, azar)
                        : actuarElRival(mesa, estrategiaDelRival, cursores, azar);
            }
        }
        return mesa;
    }

    private List<Contendiente> actuar(String quien, List<Contendiente> mesa, Juego juego, Random azar) {
        if (juego == Juego.IA) {
            return resolver(quien, new Jugada(Reglamento.DECISION_DE_LA_MAQUINA, null), mesa, azar);
        }
        return resolver(quien, ataqueMasFuerte(quien, mesa), mesa, azar);
    }

    /** El rival juega su estrategia predefinida si tiene una, y si no su ataque mas fuerte al alcance. */
    private List<Contendiente> actuarElRival(List<Contendiente> mesa, EstrategiaPredefinida estrategia,
                                             int[] cursores, Random azar) {
        Jugada jugada = estrategia == null ? ataqueMasFuerte("rival", mesa)
                : jugadaDeEstrategia("rival", mesa, estrategia.rotaciones(), cursores);
        if (jugadas != null) {
            jugadas.add(de(mesa, "rival").prototipo() + "|" + jugada.accion());
        }
        return resolver("rival", jugada, mesa, azar);
    }

    private List<Contendiente> resolver(String quien, Jugada jugada, List<Contendiente> mesa, Random azar) {
        try {
            return motor.resolver(new SolicitudDeAccion(jugada.accion(), quien, jugada.objetivo(), false, mesa),
                    azar).combatientes();
        } catch (AccionNoPermitida rechazo) {
            // Como en el servicio de misiones: la siguiente opcion, y al final el basico.
            return motor.resolver(new SolicitudDeAccion(Reglamento.ATAQUE_BASICO, quien, null, false, mesa), azar)
                    .combatientes();
        }
    }

    /** D-B7-12: la especial de ataque mas cara al alcance contra el rival con menos vida. */
    private Jugada ataqueMasFuerte(String quien, List<Contendiente> mesa) {
        Mesa sentada = sentar(mesa);
        PoliticaDeLaMaquina.Decision d = PoliticaDeLaMaquina.respaldo(quien, sentada, false, motor.reglamento());
        return new Jugada(d.accion(), d.objetivo());
    }

    private Mesa sentar(List<Contendiente> mesa) {
        Mesa sentada = new Mesa();
        for (Contendiente c : mesa) {
            sentada.sentar(c, catalogo.ficha(c.prototipo(), c.nivel()));
        }
        return sentada;
    }

    /**
     * La regla de rotaciones de heroes (7.8.5, {@code DecisorDeRotaciones}): se mira el paso que le toca a cada
     * rotacion por orden de prioridad y se juega el primero viable —el motor lo da por disponible: nivel, poder
     * y recarga—; solo avanza el cursor de la rotacion jugada, que da la vuelta al terminar. Si ninguna es viable,
     * el ataque basico (el sanador, que no ataca, su sanacion basica).
     *
     * @param cursores el paso de cada rotacion; se modifica
     */
    Jugada jugadaDeEstrategia(String quien, List<Contendiente> mesa, List<List<String>> rotaciones, int[] cursores) {
        Contendiente yo = de(mesa, quien);
        FichaDeCombate ficha = catalogo.ficha(yo.prototipo(), yo.nivel());
        Map<String, EstadoDeAccion> disponibles = new HashMap<>();
        for (EstadoDeAccion estado : motor.accionesDe(yo, ficha)) {
            if (estado.disponible()) {
                disponibles.put(Nombres.normalizar(estado.nombre()), estado);
            }
        }
        for (int i = 0; i < rotaciones.size(); i++) {
            List<String> pasos = rotaciones.get(i);
            String paso = pasos.get(cursores[i] % pasos.size());
            EstadoDeAccion estado = disponibles.get(Nombres.normalizar(paso));
            if (estado != null) {
                cursores[i]++;
                return new Jugada(estado.codigo(), objetivoDe(estado, yo, ficha, mesa));
            }
        }
        // RF-MIS-15: ninguna rotacion viable, ataque basico sin consumir poder.
        if (yo.estadisticasResueltas().ataca()) {
            return new Jugada(Reglamento.ATAQUE_BASICO, rivalDe(yo, mesa));
        }
        return new Jugada(Reglamento.SANACION_BASICA, yo.id());
    }

    private String objetivoDe(EstadoDeAccion estado, Contendiente yo, FichaDeCombate ficha, List<Contendiente> mesa) {
        return switch (motor.reglamento().planPara(estado.codigo(), yo, ficha).objetivo()) {
            case RIVAL -> rivalDe(yo, mesa);
            case ALIADO_O_SI_MISMO, COMPANERO, COMPANERO_CAIDO_O_VIVO -> yo.id();
            case SI_MISMO, GRUPO -> null;
        };
    }

    private static String rivalDe(Contendiente yo, List<Contendiente> mesa) {
        return mesa.stream().filter(c -> c.enPie() && !c.id().equals(yo.id())).map(Contendiente::id).findFirst()
                .orElse(null);
    }

    private static boolean ambosEnPie(List<Contendiente> mesa) {
        return mesa.stream().allMatch(Contendiente::enPie);
    }

    private static Contendiente de(List<Contendiente> mesa, String id) {
        return mesa.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    // =====================================================================
    // Una campana: de nivel 1 a 8
    // =====================================================================

    /** Como acabo una campana. */
    record Campana(int nivelAlcanzado, int ejecuciones, int exitos, List<String> recorrido, boolean callejonSinSalida) {
    }

    /**
     * Un jugador nuevo con un heroe de nivel 1 que, cada vez, juega la mision
     * desbloqueada mas alta cuyo nivel recomendado no pasa del suyo (si no hay
     * ninguna, la desbloqueada mas baja). La experiencia sube de nivel con
     * 100 x 1,2^(nivel-1) (§6.1.1), hasta 8. Sin experiencia por completar
     * (los parametros del PO estan a 0).
     */
    Campana campana(List<Mision> catalogo, String prototipo, Juego juego, int ejecucionesMaximas, long semilla) {
        int nivel = 1;
        double experiencia = 0;
        Set<String> completadas = new HashSet<>();
        List<String> recorrido = new ArrayList<>();
        int exitos = 0;
        int ejecuciones = 0;
        while (nivel < NIVEL_MAXIMO && ejecuciones < ejecucionesMaximas) {
            final int nivelActual = nivel;
            List<Mision> desbloqueadas = catalogo.stream()
                    .filter(m -> completadas.containsAll(m.requisitos()))
                    .toList();
            Mision elegida = desbloqueadas.stream()
                    .filter(m -> m.nivelRecomendado() != null && m.nivelRecomendado() <= nivelActual)
                    .max((a, b) -> Integer.compare(a.nivelRecomendado(), b.nivelRecomendado()))
                    .orElse(desbloqueadas.stream()
                            .filter(m -> m.nivelRecomendado() != null)
                            .min((a, b) -> Integer.compare(a.nivelRecomendado(), b.nivelRecomendado()))
                            .orElse(null));
            if (elegida == null) {
                return new Campana(nivel, ejecuciones, exitos, recorrido, true);
            }
            Resultado r = jugar(elegida, prototipo, nivel, juego, semilla + ejecuciones);
            ejecuciones++;
            experiencia += r.experiencia();
            if (r.exito()) {
                exitos++;
                completadas.add(elegida.id());
            }
            recorrido.add(elegida.id() + "@" + nivel + (r.exito() ? "+" : "-"));
            while (nivel < NIVEL_MAXIMO && experiencia >= 100 * Math.pow(1.2, nivel - 1)) {
                experiencia -= 100 * Math.pow(1.2, nivel - 1);
                nivel++;
            }
        }
        return new Campana(nivel, ejecuciones, exitos, recorrido, false);
    }
}
