package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariantes del combate en partidas enteras — G2 (continuación técnica del
 * 4-oct): regresiones permanentes para los ocho prototipos de la Tabla 6.
 *
 * <p>{@link MatrizDeAccionesTest} y {@link SimulacionDeLaMaquinaTest} (F1, F2)
 * fijan el auto-daño y el fuego amigo. Esta prueba fija todo lo demás que una
 * partida no puede romper, y lo comprueba con el motor real después de CADA
 * comienzo de turno y de CADA acción:
 * <ol>
 *   <li><b>objetivo</b> (§6.1.3): una acción dirigida a un rival va a un rival
 *       en pie, nunca a quien la lanza ni a un compañero; una sanación, una
 *       defensa o un apoyo propio, nunca a un rival. El daño solo cae en
 *       rivales; el único daño que puede volver a quien ataca es un reflejo
 *       contractual (Pinchos de escudo, «Toma y lleva»), con su evento;</li>
 *   <li><b>efectos</b>: las penalizaciones y los sangrados de alguien solo
 *       están en sus rivales; sus bonos, protecciones, sanaciones por turno y
 *       vínculos, solo en su bando;</li>
 *   <li><b>vida</b>: dentro de [0, máximo], y cada punto que se pierde o se
 *       gana tiene su evento (daño, reflejo, sangrado; sanación, reanimación);</li>
 *   <li><b>muerte</b>: quien cae tiene su evento; un caído no recibe daño,
 *       sanación ni efectos, no vuelve sin una reanimación y no actúa;</li>
 *   <li><b>poder</b>: dentro de [0, máximo]; la acción cobra exactamente su
 *       coste (o todo el poder), el comienzo del turno suma
 *       {@value MotorDeAcciones#PODER_POR_TURNO} hasta el máximo y a los demás
 *       solo se lo quita una acción que lo dice;</li>
 *   <li><b>carga</b>: lo que se juega estaba disponible, no se juega en valor
 *       base, y una acción con carga no se repite antes de tiempo;</li>
 *   <li><b>turnos</b>: cada acción suma un turno a quien la juega y a nadie
 *       más; el comienzo de un turno solo toca a quien empieza;</li>
 *   <li><b>fin</b>: la partida termina cuando queda un solo bando, y es
 *       reproducible: la misma semilla da la misma partida.</li>
 * </ol>
 *
 * <p>Juegan la máquina, en sus tres dificultades, y un jugador al azar que
 * elige entre TODAS las jugadas legales: así se juega también lo que la
 * máquina casi nunca elige. Con el jugador al azar, además, en cada decisión
 * se comprueba que todo lo que el motor ofrece como disponible se puede jugar
 * contra un objetivo válido, y que contra uno inválido se rechaza con
 * {@link MotivoDeRechazo#OBJETIVO_INVALIDO}.
 *
 * <p>Nada de esto cambia el motor: si una de estas pruebas falla, el defecto
 * está en las reglas, no en la prueba.
 */
class InvariantesDeCombateTest {

    private static final int TOPE_DE_TURNOS = 120;

    private static final List<String> PROTOTIPOS = SimuladorDeCombates.PROTOTIPOS;

    private static final List<String> TODAS_LAS_EPICAS = List.of("Golpe de defensa", "Segundo impulso",
            "Luz cegadora", "Frío concentrado", "Toma y lleva", "Intimidación sangrienta", "Té changua",
            "Reanimador 3000");

    /** Penalizaciones y sangrados: solo pueden estar en un rival de quien los puso. */
    private static final Set<TipoDeEfecto> CONTRA_RIVALES =
            EnumSet.of(TipoDeEfecto.PENALIZA_ATAQUE, TipoDeEfecto.PENALIZA_DANO, TipoDeEfecto.DANO_POR_TURNO);

    private final CatalogoDePrueba catalogo = new CatalogoDePrueba();
    private final Map<DificultadDeLaMaquina, MotorDeAcciones> motores = new EnumMap<>(DificultadDeLaMaquina.class);
    private final MotorDeAcciones motor;

    InvariantesDeCombateTest() {
        IndiceNormal indice = IndiceNormal.porOmision();
        for (DificultadDeLaMaquina d : DificultadDeLaMaquina.values()) {
            motores.put(d, new MotorDeAcciones(catalogo, indice, d));
        }
        motor = motores.get(DificultadDeLaMaquina.NORMAL);
    }

    // =====================================================================
    // Partidas enteras
    // =====================================================================

    @Test
    @DisplayName("8×8 duelos de la máquina, niveles 1 y 8, en sus tres dificultades: ninguna invariante rota")
    void laMaquinaEnDuelos() {
        Arbitro arbitro = new Arbitro(false);
        DificultadDeLaMaquina[][] cruces = {
                {DificultadDeLaMaquina.NORMAL, DificultadDeLaMaquina.NORMAL},
                {DificultadDeLaMaquina.FACIL, DificultadDeLaMaquina.DIFICIL}};
        for (int nivel : List.of(1, 8)) {
            for (DificultadDeLaMaquina[] cruce : cruces) {
                for (String a : PROTOTIPOS) {
                    for (String b : PROTOTIPOS) {
                        arbitro.contexto = a + " (" + cruce[0] + ") contra " + b + " (" + cruce[1] + "), nivel " + nivel;
                        jugar(List.of(SimuladorDeCombates.heroe("A1", a, nivel, null)), Jugador.maquina(cruce[0]),
                                List.of(SimuladorDeCombates.heroe("B1", b, nivel, null)), Jugador.maquina(cruce[1]),
                                false, 7919L * nivel + 31L * a.hashCode() + b.hashCode() + cruce[0].ordinal(),
                                arbitro);
                    }
                }
            }
        }
        arbitro.sinViolaciones(256);
        assertTrue(arbitro.ganadas > 0, "alguna partida termina con un ganador");
        assertTrue(arbitro.caidos > 0, "alguien cae");
    }

    @Test
    @DisplayName("3 contra 3 de la máquina, con sanadores y épicas, niveles 1, 4 y 8: ninguna invariante rota")
    void laMaquinaPorEquipos() {
        Arbitro arbitro = new Arbitro(true);
        List<List<String>> alineaciones = List.of(
                List.of("Guerrero Tanque", "Mago Hielo", "Médico"),
                List.of("Pícaro Veneno", "Guerrero Armas", "Chamán"),
                List.of("Mago Fuego", "Pícaro Machete", "Médico"),
                List.of("Chamán", "Guerrero Tanque", "Pícaro Veneno"));
        int partidas = 0;
        for (int nivel : List.of(1, 4, 8)) {
            for (int i = 0; i < alineaciones.size(); i++) {
                List<String> a = alineaciones.get(i);
                List<String> b = alineaciones.get((i + 1) % alineaciones.size());
                for (DificultadDeLaMaquina d : DificultadDeLaMaquina.values()) {
                    arbitro.contexto = a + " contra " + b + ", nivel " + nivel + ", " + d;
                    jugar(equipo("A", a, nivel, 1, false), Jugador.maquina(d),
                            equipo("B", b, nivel, 2, false), Jugador.maquina(DificultadDeLaMaquina.NORMAL),
                            true, 104729L * nivel + 977L * i + d.ordinal(), arbitro);
                    partidas++;
                }
            }
        }
        arbitro.sinViolaciones(partidas);
        assertTrue(arbitro.sanaciones > 0, "los sanadores sanan");
    }

    @Test
    @DisplayName("jugador al azar entre TODAS las jugadas legales: lo disponible se juega y lo inválido se rechaza")
    void jugadorAlAzar() {
        Arbitro arbitro = new Arbitro(false);
        for (String a : PROTOTIPOS) {
            for (String b : PROTOTIPOS) {
                arbitro.contexto = a + " contra " + b + " al azar, nivel 8";
                jugar(List.of(conTodasLasEpicas("A1", a, 8, null)), Jugador.AL_AZAR,
                        List.of(conTodasLasEpicas("B1", b, 8, null)), Jugador.AL_AZAR,
                        false, 15485863L + 31L * a.hashCode() + b.hashCode(), arbitro);
            }
        }
        Arbitro equipos = new Arbitro(true);
        for (int i = 0; i < 12; i++) {
            List<String> a = List.of(PROTOTIPOS.get(i % 8), PROTOTIPOS.get((i + 3) % 8), "Médico");
            List<String> b = List.of(PROTOTIPOS.get((i + 5) % 8), "Chamán", PROTOTIPOS.get((i + 1) % 8));
            equipos.contexto = a + " contra " + b + " al azar, nivel 8";
            jugar(equipo("A", a, 8, 1, true), Jugador.AL_AZAR, equipo("B", b, 8, 2, true), Jugador.AL_AZAR,
                    true, 32452843L + i, equipos);
        }
        arbitro.sinViolaciones(64);
        equipos.sinViolaciones(12);

        // Cobertura: cada acción que el motor ofreció alguna vez se llegó a jugar.
        Set<String> ofrecidas = new TreeSet<>(arbitro.ofrecidas);
        ofrecidas.addAll(equipos.ofrecidas);
        Set<String> jugadas = new TreeSet<>(arbitro.jugadas);
        jugadas.addAll(equipos.jugadas);
        Set<String> nunca = new TreeSet<>(ofrecidas);
        nunca.removeAll(jugadas);
        assertAll(
                () -> assertTrue(nunca.isEmpty(), "ofrecidas y nunca jugadas: " + nunca),
                () -> assertTrue(jugadas.contains("Médico · Reanimación"), "la Reanimación se jugó"),
                () -> assertTrue(jugadas.contains("Médico · Reanimador 3000"), "el vínculo se jugó"),
                () -> assertTrue(jugadas.contains("Chamán · Té changua"), "la sanación grupal épica se jugó"),
                () -> assertTrue(jugadas.contains("Mago Hielo · Cono de hielo"), "una penalización se jugó"),
                () -> assertTrue(arbitro.sondasNegativas + equipos.sondasNegativas > 1000,
                        "objetivos inválidos probados: " + (arbitro.sondasNegativas + equipos.sondasNegativas)),
                () -> assertTrue(arbitro.reflejos + equipos.reflejos > 0, "un reflejo contractual se vio"));
    }

    @Test
    @DisplayName("la misma semilla da la misma partida, jugada a jugada")
    void reproducible() {
        String una = huella(9001L);
        String otra = huella(9001L);
        String distinta = huella(9002L);
        assertEquals(una, otra);
        assertFalse(una.equals(distinta), "otra semilla, otra partida");
    }

    private String huella(long semilla) {
        Arbitro arbitro = new Arbitro(true);
        arbitro.contexto = "huella " + semilla;
        jugar(equipo("A", List.of("Mago Fuego", "Médico"), 4, 1, true), Jugador.AL_AZAR,
                equipo("B", List.of("Pícaro Veneno", "Chamán"), 4, 2, false),
                Jugador.maquina(DificultadDeLaMaquina.DIFICIL), true, semilla, arbitro);
        arbitro.sinViolaciones(1);
        return arbitro.huella.toString();
    }

    // =====================================================================
    // Muerte, carga y poder, uno por uno
    // =====================================================================

    @Test
    @DisplayName("un caído no actúa, no se le ataca ni se le cura; la Reanimación lo devuelve entero")
    void muerte() {
        for (String p : PROTOTIPOS) {
            List<Contendiente> mesa = resueltos(List.of(
                    SimuladorDeCombates.heroe("yo", p, 8, 1),
                    SimuladorDeCombates.heroe("aliado", "Guerrero Armas", 8, 1),
                    SimuladorDeCombates.heroe("rival1", "Guerrero Tanque", 8, 2),
                    SimuladorDeCombates.heroe("rival2", "Mago Fuego", 8, 2)));
            Contendiente yo = de(mesa, "yo");
            String basica = yo.estadisticasResueltas().ataca() ? Reglamento.ATAQUE_BASICO : Reglamento.SANACION_BASICA;

            List<Contendiente> yoCaido = con(mesa, yo.conVida(0));
            for (String accion : List.of(basica, Reglamento.DECISION_DE_LA_MAQUINA)) {
                AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class,
                        () -> motor.resolver(new SolicitudDeAccion(accion, "yo", null, true, yoCaido), new Random(1)));
                assertEquals(MotivoDeRechazo.EJECUTOR_CAIDO, rechazo.motivo(), p + " caído con " + accion);
            }

            if (yo.estadisticasResueltas().ataca()) {
                List<Contendiente> rivalCaido = con(mesa, de(mesa, "rival1").conVida(0));
                AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                        new SolicitudDeAccion(Reglamento.ATAQUE_BASICO, "yo", "rival1", true, rivalCaido),
                        new Random(1)));
                assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, rechazo.motivo(), p + " ataca a un caído");
            } else {
                List<Contendiente> aliadoCaido = con(mesa, de(mesa, "aliado").conVida(0));
                AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                        new SolicitudDeAccion(Reglamento.SANACION_BASICA, "yo", "aliado", true, aliadoCaido),
                        new Random(1)));
                assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, rechazo.motivo(), p + " cura a un caído");
            }
        }

        // La Reanimación del Médico: el compañero caído vuelve con toda su vida, y cuesta todo el poder.
        List<Contendiente> mesa = resueltos(List.of(
                SimuladorDeCombates.heroe("medico", "Médico", 8, 1),
                SimuladorDeCombates.heroe("aliado", "Guerrero Armas", 8, 1),
                SimuladorDeCombates.heroe("rival", "Guerrero Tanque", 8, 2)));
        List<Contendiente> conCaido = con(mesa, de(mesa, "aliado").conVida(0));
        ResultadoDeAccion r = motor.resolver(
                new SolicitudDeAccion("Reanimación", "medico", "aliado", true, conCaido), new Random(1));
        Contendiente aliado = de(r.combatientes(), "aliado");
        assertAll(
                () -> assertEquals(aliado.vidaMaxima(), aliado.vida(), "vuelve con toda su vida"),
                () -> assertTrue(r.eventos().stream().anyMatch(
                        e -> e.tipo() == TipoDeEvento.REANIMACION && e.combatiente().equals("aliado"))),
                () -> assertEquals(0, de(r.combatientes(), "medico").poder(), "cuesta todo el poder"));
        // ...y no se puede dirigir a un rival.
        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Reanimación", "medico", "rival", true, conCaido), new Random(1)));
        assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, rechazo.motivo());
    }

    @Test
    @DisplayName("el vínculo del Reanimador 3000: el compañero que cae se levanta con el 20 % y sin evento de caída")
    void vinculoDeReanimacion() {
        List<Contendiente> mesa = resueltos(List.of(
                conTodasLasEpicas("medico", "Médico", 8, 1),
                SimuladorDeCombates.heroe("aliado", "Mago Fuego", 8, 1),
                SimuladorDeCombates.heroe("rival", "Pícaro Machete", 8, 2)));
        ResultadoDeAccion vinculo = motor.resolver(
                new SolicitudDeAccion("Reanimador 3000", "medico", "aliado", true, mesa), new Random(1));
        Contendiente aliado = de(vinculo.combatientes(), "aliado");
        assertTrue(aliado.tiene(TipoDeEfecto.VINCULO_REANIMACION), "el vínculo queda en el compañero");
        assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Reanimador 3000", "medico", "rival", true, mesa), new Random(1)),
                "el vínculo no se le pone a un rival");

        // El rival golpea al compañero, que está a un punto de vida.
        List<Contendiente> aUnPunto = con(vinculo.combatientes(), aliado.conVida(1));
        ResultadoDeAccion golpe = null;
        for (int semilla = 1; semilla <= 200 && golpe == null; semilla++) {
            ResultadoDeAccion intento = motor.resolver(
                    new SolicitudDeAccion(Reglamento.ATAQUE_BASICO, "rival", "aliado", true, aUnPunto),
                    new Random(semilla));
            if (intento.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.DANO)) {
                golpe = intento;
            }
        }
        assertTrue(golpe != null, "en doscientas tiradas el rival acierta alguna vez");
        ResultadoDeAccion r = golpe;
        Contendiente levantado = de(r.combatientes(), "aliado");
        assertAll(
                () -> assertEquals(Math.max(1, levantado.vidaMaxima() * Mesa.PORCENTAJE_DE_REANIMACION_DEL_VINCULO / 100),
                        levantado.vida(), "se levanta con el 20 % de su salud"),
                () -> assertTrue(r.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.REANIMACION
                        && e.combatiente().equals("aliado") && "medico".equals(e.origen()))),
                () -> assertTrue(r.eventos().stream().noneMatch(
                        e -> e.tipo() == TipoDeEvento.CAIDO && e.combatiente().equals("aliado")), "no cae"),
                () -> assertFalse(levantado.tiene(TipoDeEfecto.VINCULO_REANIMACION), "el vínculo se gasta"));
    }

    @Test
    @DisplayName("cada especial y cada épica: tras jugarla queda en carga, y sin poder no se ofrece ni se cobra")
    void cargaYPoder() {
        Set<String> probadas = new TreeSet<>();
        for (String p : PROTOTIPOS) {
            List<Contendiente> mesa = resueltos(List.of(
                    conTodasLasEpicas("yo", p, 8, 1),
                    SimuladorDeCombates.heroe("aliado", "Guerrero Armas", 8, 1),
                    SimuladorDeCombates.heroe("rival", "Guerrero Tanque", 8, 2)));
            Contendiente yo = de(mesa, "yo");
            FichaDeCombate ficha = catalogo.ficha(p, 8);
            for (EstadoDeAccion accion : motor.accionesDe(yo, ficha)) {
                if (!accion.disponible() || accion.turnosDeCarga() <= 0) {
                    continue;
                }
                Plan plan = motor.reglamento().planPara(accion.codigo(), yo, ficha);
                String objetivo = objetivoValido(plan.objetivo(), yo, mesa, true);
                String casilla = p + " · " + accion.codigo();

                // 1. Jugada: queda en carga en la lista y pedida otra vez se rechaza.
                ResultadoDeAccion r = motor.resolver(
                        new SolicitudDeAccion(accion.codigo(), "yo", objetivo, true, mesa), new Random(3));
                List<Contendiente> despues = r.combatientes();
                EstadoDeAccion trasJugarla = r.acciones().get("yo").stream()
                        .filter(e -> e.codigo().equals(accion.codigo())).findFirst().orElseThrow();
                assertFalse(trasJugarla.disponible(), casilla + " sigue disponible tras jugarla");
                assertTrue(trasJugarla.motivo().startsWith("En carga"), casilla + ": " + trasJugarla.motivo());
                List<Contendiente> otraVez = con(despues, de(despues, "yo").conPoder(de(despues, "yo").poderMaximo()));
                AccionNoPermitida enCarga = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                        new SolicitudDeAccion(accion.codigo(), "yo", objetivo, true, otraVez), new Random(4)));
                assertEquals(MotivoDeRechazo.EN_CARGA, enCarga.motivo(), casilla);

                // 2. Sin poder suficiente: si cuesta, no se ofrece; pedida igual, va en valor
                //    base (la básica, §6.1.1) y no cobra nada.
                boolean cuesta = accion.todoElPoder() || (accion.costoPoder() != null && accion.costoPoder() > 0);
                if (cuesta) {
                    int poderCorto = accion.todoElPoder() ? 0 : accion.costoPoder() - 1;
                    List<Contendiente> corto = con(mesa, yo.conPoder(poderCorto));
                    EstadoDeAccion sinPoder = motor.accionesDe(de(corto, "yo"), ficha).stream()
                            .filter(e -> e.codigo().equals(accion.codigo())).findFirst().orElseThrow();
                    assertFalse(sinPoder.disponible(), casilla + " se ofrece sin poder suficiente");
                    // La básica de quien ataca va a un rival; la de un sanador, a sí mismo.
                    String objetivoBase = yo.estadisticasResueltas().ataca() ? "rival" : null;
                    ResultadoDeAccion base = motor.resolver(
                            new SolicitudDeAccion(accion.codigo(), "yo", objetivoBase, true, corto), new Random(5));
                    assertAll(casilla + " sin poder suficiente",
                            () -> assertTrue(base.enValorBase(), "va en valor base"),
                            () -> assertTrue(base.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.VALOR_BASE)),
                            () -> assertEquals(poderCorto, de(base.combatientes(), "yo").poder(), "no cobra"),
                            () -> assertFalse(base.accionEjecutada().equals(accion.codigo()),
                                    "se juega la básica, no la especial"));
                }
                probadas.add(casilla);
            }
        }
        // Las tres especiales de cada uno de los ocho, y las épicas que aplican.
        assertTrue(probadas.size() >= 24 + 8, "especiales y épicas probadas: " + probadas);
    }

    // =====================================================================
    // El conductor de la partida
    // =====================================================================

    /** Quien juega un bando: la máquina en una dificultad, o el jugador al azar (siempre legal). */
    private record Jugador(DificultadDeLaMaquina maquina) {
        static final Jugador AL_AZAR = new Jugador(null);

        static Jugador maquina(DificultadDeLaMaquina d) {
            return new Jugador(d);
        }

        @Override
        public String toString() {
            return maquina == null ? "al azar" : maquina.name();
        }
    }

    private record Jugada(String accion, String objetivo) {
    }

    /**
     * Una partida de principio a fin, como el §6.1.3: orden sorteado al empezar
     * e invariable; al empezar cada turno, efectos por turno y poder; después,
     * una acción. Termina cuando queda un solo bando en pie, o al tope.
     */
    private void jugar(List<Contendiente> bandoA, Jugador a, List<Contendiente> bandoB, Jugador b,
                       boolean porEquipos, long semilla, Arbitro arbitro) {
        Random azar = new Random(semilla);
        Random sondas = new Random(~semilla);
        Map<String, Jugador> jugador = new LinkedHashMap<>();
        Map<String, Character> bando = new LinkedHashMap<>();
        List<Contendiente> crudos = new ArrayList<>();
        for (Contendiente c : bandoA) {
            jugador.put(c.id(), a);
            bando.put(c.id(), 'A');
            crudos.add(c);
        }
        for (Contendiente c : bandoB) {
            jugador.put(c.id(), b);
            bando.put(c.id(), 'B');
            crudos.add(c);
        }
        List<Contendiente> mesa = resueltos(crudos);
        List<String> orden = new ArrayList<>(jugador.keySet());
        for (int i = orden.size() - 1; i > 0; i--) {
            Collections.swap(orden, i, azar.nextInt(i + 1));
        }
        arbitro.empezar();
        int turno = 0;
        while (ganador(mesa, bando) == null && turno < TOPE_DE_TURNOS) {
            String id = orden.get(turno % orden.size());
            turno++;
            if (!de(mesa, id).enPie()) {
                continue;
            }
            ResultadoDeTurno inicio = motor.iniciarTurno(new SolicitudDeTurno(id, porEquipos, mesa), azar);
            arbitro.inicioDeTurno(id, mesa, inicio);
            mesa = inicio.combatientes();
            if (!de(mesa, id).enPie() || ganador(mesa, bando) != null) {
                continue;
            }
            List<EstadoDeAccion> disponibles = inicio.acciones().get(id);
            Jugador quien = jugador.get(id);
            ResultadoDeAccion r;
            try {
                if (quien.maquina() != null) {
                    r = motores.get(quien.maquina()).resolver(new SolicitudDeAccion(
                            Reglamento.DECISION_DE_LA_MAQUINA, id, null, porEquipos, mesa), azar);
                } else {
                    Jugada jugada = alAzar(id, mesa, disponibles, porEquipos, azar, sondas, arbitro);
                    r = motor.resolver(new SolicitudDeAccion(jugada.accion(), id, jugada.objetivo(), porEquipos,
                            mesa), azar);
                }
            } catch (AccionNoPermitida rechazo) {
                arbitro.falla("turno " + turno + ": " + id + " (" + quien + ") jugó algo que el motor rechaza: "
                        + rechazo.motivo() + " — " + rechazo.getMessage());
                continue;
            }
            arbitro.accion(mesa, disponibles, r);
            mesa = r.combatientes();
        }
        arbitro.fin(mesa, bando, ganador(mesa, bando));
    }

    /**
     * Una jugada legal al azar entre TODAS las que admite el reglamento. De paso,
     * cada jugada legal se ensaya (el motor tiene que aceptarla) y cada objetivo
     * inválido también (el motor tiene que rechazarlo con OBJETIVO_INVALIDO).
     * Los ensayos usan su propio generador: la partida no se entera.
     */
    private Jugada alAzar(String id, List<Contendiente> mesa, List<EstadoDeAccion> disponibles, boolean porEquipos,
                          Random azar, Random sondas, Arbitro arbitro) {
        Contendiente yo = de(mesa, id);
        FichaDeCombate ficha = catalogo.ficha(yo.prototipo(), yo.nivel());
        List<Jugada> legales = new ArrayList<>();
        for (EstadoDeAccion estado : disponibles) {
            if (!estado.disponible()) {
                continue;
            }
            arbitro.ofrecidas.add(yo.prototipo() + " · " + estado.codigo());
            Plan plan = motor.reglamento().planPara(estado.codigo(), yo, ficha);
            if (plan.objetivo() == Plan.Objetivo.SI_MISMO || plan.objetivo() == Plan.Objetivo.GRUPO) {
                // Sin objetivo que elegir: el motor la dirige a quien la juega.
                legales.add(new Jugada(estado.codigo(), null));
                try {
                    motor.resolver(new SolicitudDeAccion(estado.codigo(), id, null, porEquipos, mesa), sondas);
                } catch (AccionNoPermitida rechazo) {
                    arbitro.falla("el motor ofrece " + estado.codigo() + " a " + id + " pero la rechaza: "
                            + rechazo.motivo());
                }
                continue;
            }
            for (Contendiente otro : mesa) {
                Jugada jugada = new Jugada(estado.codigo(), otro.id());
                SolicitudDeAccion solicitud = new SolicitudDeAccion(jugada.accion(), id, jugada.objetivo(),
                        porEquipos, mesa);
                if (admite(plan.objetivo(), yo, otro, porEquipos)) {
                    legales.add(jugada);
                    try {
                        motor.resolver(solicitud, sondas);
                    } catch (AccionNoPermitida rechazo) {
                        arbitro.falla("el motor ofrece " + estado.codigo() + " a " + id + " pero la rechaza contra "
                                + otro.id() + ": " + rechazo.motivo());
                    }
                } else {
                    arbitro.sondasNegativas++;
                    try {
                        motor.resolver(solicitud, sondas);
                        arbitro.falla(estado.codigo() + " de " + id + " contra " + otro.id()
                                + " no se rechazó (objetivo " + plan.objetivo() + ")");
                    } catch (AccionNoPermitida rechazo) {
                        if (rechazo.motivo() != MotivoDeRechazo.OBJETIVO_INVALIDO) {
                            arbitro.falla(estado.codigo() + " de " + id + " contra " + otro.id()
                                    + " se rechazó con " + rechazo.motivo() + " y no con OBJETIVO_INVALIDO");
                        }
                    }
                }
            }
        }
        if (legales.isEmpty()) {
            arbitro.falla(id + " no tiene ninguna jugada legal con " + disponibles.size() + " acciones");
            return new Jugada(Reglamento.DECISION_DE_LA_MAQUINA, null);
        }
        return legales.get(azar.nextInt(legales.size()));
    }

    /** Los objetivos que el reglamento admite (§6.1.3), escritos aquí aparte del motor. */
    private static boolean admite(Plan.Objetivo objetivo, Contendiente yo, Contendiente otro, boolean porEquipos) {
        boolean soyYo = otro.id().equals(yo.id());
        return switch (objetivo) {
            case RIVAL -> otro.enPie() && yo.esRivalDe(otro, porEquipos);
            case SI_MISMO, GRUPO -> soyYo;
            case ALIADO_O_SI_MISMO -> otro.enPie() && (soyYo || yo.esCompaneroDe(otro, porEquipos));
            case COMPANERO -> otro.enPie() && yo.esCompaneroDe(otro, porEquipos);
            case COMPANERO_CAIDO_O_VIVO -> yo.esCompaneroDe(otro, porEquipos);
        };
    }

    private static String objetivoValido(Plan.Objetivo objetivo, Contendiente yo, List<Contendiente> mesa,
                                         boolean porEquipos) {
        if (objetivo == Plan.Objetivo.SI_MISMO || objetivo == Plan.Objetivo.GRUPO) {
            return null;
        }
        return mesa.stream().filter(c -> admite(objetivo, yo, c, porEquipos)).map(Contendiente::id).findFirst()
                .orElseThrow(() -> new AssertionError("sin objetivo válido para " + objetivo));
    }

    // =====================================================================
    // El árbitro
    // =====================================================================

    /** Lo que se comprueba después de cada paso, y lo que se ha visto. */
    private final class Arbitro {
        final boolean porEquipos;
        final List<String> violaciones = new ArrayList<>();
        final Set<String> ofrecidas = new TreeSet<>();
        final Set<String> jugadas = new TreeSet<>();
        final StringBuilder huella = new StringBuilder();
        /** Por combatiente y acción: sus turnos jugados cuando la jugó por última vez. */
        final Map<String, Map<String, Integer>> usadaEn = new HashMap<>();
        String contexto = "";
        int partidas;
        int ganadas;
        int tablas;
        long acciones;
        long caidos;
        long reanimaciones;
        long reflejos;
        long sanaciones;
        long sondasNegativas;
        int omitidas;

        Arbitro(boolean porEquipos) {
            this.porEquipos = porEquipos;
        }

        void empezar() {
            usadaEn.clear();
            partidas++;
        }

        void falla(String que) {
            if (violaciones.size() < 40) {
                violaciones.add(contexto + " · " + que);
            } else {
                omitidas++;
            }
        }

        void sinViolaciones(int partidasEsperadas) {
            assertEquals(partidasEsperadas, partidas, "partidas jugadas");
            assertTrue(violaciones.isEmpty(), violaciones.size() + omitidas + " invariantes rotas:\n"
                    + String.join("\n", violaciones));
        }

        // ------------------------------------------------ comienzo de turno

        void inicioDeTurno(String id, List<Contendiente> antes, ResultadoDeTurno r) {
            String donde = "comienzo del turno de " + id;
            Map<String, Contendiente> a = porId(antes);
            Map<String, Contendiente> d = porId(r.combatientes());
            for (Evento e : r.eventos()) {
                if (!e.combatiente().equals(id)) {
                    falla(donde + " tocó a " + e.combatiente() + ": " + e);
                }
            }
            for (String otro : a.keySet()) {
                if (!otro.equals(id) && !a.get(otro).equals(d.get(otro))) {
                    falla(donde + " cambió a " + otro);
                }
            }
            Contendiente yoA = a.get(id);
            Contendiente yoD = d.get(id);
            if (yoD.turnosJugados() != yoA.turnosJugados()) {
                falla(donde + " cambió sus turnos jugados");
            }
            if (yoD.enPie()) {
                int esperado = Math.min(yoD.poderMaximo(), yoA.poder() + MotorDeAcciones.PODER_POR_TURNO);
                if (yoD.poder() != esperado) {
                    falla(donde + ": poder " + yoA.poder() + " → " + yoD.poder() + ", se esperaba " + esperado);
                }
            } else if (yoD.poder() != yoA.poder()) {
                falla(donde + ": cayó y aun así cambió su poder");
            }
            vida(a, d, r.eventos(), donde);
            estado(r.combatientes(), "tras el " + donde);
        }

        // --------------------------------------------------------- acción

        void accion(List<Contendiente> antes, List<EstadoDeAccion> disponibles, ResultadoDeAccion r) {
            acciones++;
            String yo = r.ejecutor();
            Map<String, Contendiente> a = porId(antes);
            Map<String, Contendiente> d = porId(r.combatientes());
            Contendiente yoA = a.get(yo);
            Contendiente yoD = d.get(yo);
            String donde = yo + " (" + yoA.prototipo() + ") juega " + r.accion() + " → " + r.objetivo();
            huella.append(donde).append(" | ");
            r.combatientes().forEach(c -> huella.append(c.id()).append('=').append(c.vida()).append('/')
                    .append(c.poder()).append(' '));
            huella.append('\n');

            if (!yoA.enPie()) {
                falla(donde + ": actuó un caído");
            }
            // Carga y disponibilidad: lo jugado estaba disponible y no va en valor base.
            EstadoDeAccion jugada = disponibles.stream().filter(e -> e.codigo().equals(r.accion())).findFirst()
                    .orElse(null);
            if (jugada == null || !jugada.disponible()) {
                falla(donde + ": no estaba disponible (" + (jugada == null ? "no está" : jugada.motivo()) + ")");
                return;
            }
            jugadas.add(yoA.prototipo() + " · " + jugada.codigo());
            if (r.enValorBase()) {
                falla(donde + ": se jugó en valor base teniendo poder");
            }
            Plan plan = motor.reglamento().planPara(r.accion(), yoA, catalogo.ficha(yoA.prototipo(), yoA.nivel()));
            if (!plan.codigo().equals(r.accionEjecutada())) {
                falla(donde + ": se ejecutó " + r.accionEjecutada() + " y no " + plan.codigo());
            }

            // Objetivo.
            Contendiente objetivo = r.objetivo() == null ? null : a.get(r.objetivo());
            boolean objetivoValido = objetivo != null && switch (plan.objetivo()) {
                case RIVAL -> !objetivo.id().equals(yo) && yoA.esRivalDe(objetivo, porEquipos) && objetivo.enPie();
                case SI_MISMO, GRUPO -> objetivo.id().equals(yo);
                case ALIADO_O_SI_MISMO -> objetivo.enPie()
                        && (objetivo.id().equals(yo) || yoA.esCompaneroDe(objetivo, porEquipos));
                case COMPANERO -> objetivo.enPie() && yoA.esCompaneroDe(objetivo, porEquipos);
                case COMPANERO_CAIDO_O_VIVO -> yoA.esCompaneroDe(objetivo, porEquipos);
            };
            if (!objetivoValido) {
                falla(donde + ": objetivo inválido para " + plan.objetivo());
            }

            // Eventos: daño a rivales; reflejo solo de vuelta a quien ataca; sanación y
            // reanimación en el propio bando; poder perdido, solo el rival.
            for (Evento e : r.eventos()) {
                Contendiente quien = a.get(e.combatiente());
                switch (e.tipo()) {
                    case DANO -> {
                        if (e.combatiente().equals(yo) || !yoA.esRivalDe(quien, porEquipos)) {
                            falla(donde + ": daño a uno de los suyos: " + e);
                        }
                    }
                    case REFLEJO -> {
                        reflejos++;
                        if (!e.combatiente().equals(yo) || e.origen() == null
                                || !yoA.esRivalDe(a.get(e.origen()), porEquipos)) {
                            falla(donde + ": un reflejo que no vuelve de un rival a quien ataca: " + e);
                        }
                    }
                    case SANACION -> {
                        sanaciones++;
                        if (!e.combatiente().equals(yo) && !yoA.esCompaneroDe(quien, porEquipos)) {
                            falla(donde + ": sanó a un rival: " + e);
                        }
                    }
                    case REANIMACION -> {
                        reanimaciones++;
                        Contendiente origen = e.origen() == null ? null : a.get(e.origen());
                        if (origen != null && origen.esRivalDe(quien, porEquipos)) {
                            falla(donde + ": reanimó a un rival: " + e);
                        }
                    }
                    case PODER_PERDIDO -> {
                        if (!yoA.esRivalDe(quien, porEquipos)) {
                            falla(donde + ": quitó poder a uno de los suyos: " + e);
                        }
                    }
                    case DANO_POR_TURNO, SANACION_POR_TURNO, PODER_RECUPERADO ->
                            falla(donde + ": " + e.tipo() + " fuera del comienzo de un turno");
                    case CAIDO -> caidos++;
                    default -> {
                    }
                }
            }
            vida(a, d, r.eventos(), donde);

            // Poder: quien actúa paga exactamente su coste; a los demás solo se lo quita un evento.
            int coste = jugada.todoElPoder() ? yoA.poder()
                    : Math.min(yoA.poder(), jugada.costoPoder() == null ? 0 : jugada.costoPoder());
            if (yoD.poder() != yoA.poder() - coste) {
                falla(donde + ": poder " + yoA.poder() + " → " + yoD.poder() + " con coste " + coste);
            }
            for (String otro : a.keySet()) {
                if (otro.equals(yo)) {
                    continue;
                }
                int perdido = r.eventos().stream()
                        .filter(e -> e.tipo() == TipoDeEvento.PODER_PERDIDO && e.combatiente().equals(otro))
                        .mapToInt(e -> e.cantidad() == null ? 0 : e.cantidad()).sum();
                if (d.get(otro).poder() != a.get(otro).poder() - perdido) {
                    falla(donde + ": el poder de " + otro + " cambió sin su evento");
                }
                if (d.get(otro).turnosJugados() != a.get(otro).turnosJugados()) {
                    falla(donde + ": sumó un turno a " + otro);
                }
            }
            if (yoD.turnosJugados() != yoA.turnosJugados() + 1) {
                falla(donde + ": no sumó exactamente un turno a quien actúa");
            }

            // Carga: no se repite antes de tiempo, y tras jugarla queda en carga.
            if (jugada.turnosDeCarga() > 0) {
                Map<String, Integer> suyas = usadaEn.computeIfAbsent(yo, k -> new HashMap<>());
                Integer ultima = suyas.get(jugada.codigo());
                if (ultima != null && yoA.turnosJugados() - ultima < jugada.turnosDeCarga() + 1) {
                    falla(donde + ": repetida en carga (" + (yoA.turnosJugados() - ultima) + " turnos después, carga "
                            + jugada.turnosDeCarga() + ")");
                }
                suyas.put(jugada.codigo(), yoA.turnosJugados());
                if (yoD.enPie()) {
                    r.acciones().get(yo).stream().filter(e -> e.codigo().equals(jugada.codigo())).findFirst()
                            .filter(EstadoDeAccion::disponible)
                            .ifPresent(e -> falla(donde + ": sigue disponible después de jugarla"));
                }
            }
            estado(r.combatientes(), "tras " + donde);
        }

        // ------------------------------------------------------- comunes

        /** Cada punto de vida que cambia tiene su evento; quien cae, el suyo; un caído no recibe nada. */
        void vida(Map<String, Contendiente> a, Map<String, Contendiente> d, List<Evento> eventos, String donde) {
            for (String id : a.keySet()) {
                int gana = 0;
                int pierde = 0;
                boolean reanimado = false;
                boolean cayo = false;
                for (Evento e : eventos) {
                    if (!e.combatiente().equals(id)) {
                        continue;
                    }
                    int n = e.cantidad() == null ? 0 : e.cantidad();
                    switch (e.tipo()) {
                        case SANACION, SANACION_POR_TURNO -> gana += n;
                        case REANIMACION -> {
                            gana += n;
                            reanimado = true;
                        }
                        case DANO, DANO_POR_TURNO, REFLEJO -> pierde += n;
                        case CAIDO -> cayo = true;
                        default -> {
                        }
                    }
                    if (!a.get(id).enPie() && !reanimado && e.tipo() != TipoDeEvento.REANIMACION) {
                        falla(donde + ": " + id + " estaba caído y recibió " + e);
                    }
                }
                int delta = d.get(id).vida() - a.get(id).vida();
                if (delta != gana - pierde) {
                    falla(donde + ": la vida de " + id + " cambió " + delta + " y los eventos explican "
                            + (gana - pierde));
                }
                if (a.get(id).enPie() && !d.get(id).enPie() && !cayo) {
                    falla(donde + ": " + id + " cayó sin su evento");
                }
                if (cayo && d.get(id).enPie()) {
                    falla(donde + ": " + id + " tiene un evento de caída y sigue en pie");
                }
            }
        }

        /** Vida y poder dentro de sus límites; cada efecto, en el bando que le toca. */
        void estado(List<Contendiente> todos, String donde) {
            Map<String, Contendiente> m = porId(todos);
            for (Contendiente c : todos) {
                if (c.vida() < 0 || c.vida() > c.vidaMaxima()) {
                    falla(donde + ": vida de " + c.id() + " fuera de [0, " + c.vidaMaxima() + "]: " + c.vida());
                }
                if (c.poder() < 0 || c.poder() > c.poderMaximo()) {
                    falla(donde + ": poder de " + c.id() + " fuera de [0, " + c.poderMaximo() + "]: " + c.poder());
                }
                for (EfectoActivo e : c.efectos()) {
                    Contendiente origen = e.origen() == null ? null : m.get(e.origen());
                    if (origen == null) {
                        continue;
                    }
                    boolean rival = origen.esRivalDe(c, porEquipos);
                    if (CONTRA_RIVALES.contains(e.tipo()) && !rival) {
                        falla(donde + ": " + e.nombre() + " (" + e.tipo() + ") de " + origen.id() + " está en "
                                + c.id() + ", de su bando");
                    }
                    if (!CONTRA_RIVALES.contains(e.tipo()) && rival) {
                        falla(donde + ": " + e.nombre() + " (" + e.tipo() + ") de " + origen.id() + " está en "
                                + c.id() + ", su rival");
                    }
                }
            }
        }

        void fin(List<Contendiente> mesa, Map<String, Character> bando, Character ganador) {
            if (ganador == null || ganador == '-') {
                tablas++;
                return;
            }
            ganadas++;
            for (Contendiente c : mesa) {
                boolean delGanador = Objects.equals(bando.get(c.id()), ganador);
                if (!delGanador && c.enPie()) {
                    falla("fin: ganó " + ganador + " y " + c.id() + ", del otro bando, sigue en pie");
                }
            }
        }
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    /** Con las estadísticas resueltas y la vida y el poder acotados, como los sienta el motor. */
    private List<Contendiente> resueltos(List<Contendiente> crudos) {
        List<Contendiente> lista = new ArrayList<>();
        for (Contendiente c : crudos) {
            Contendiente r = c.conEstadisticas(catalogo.ficha(c.prototipo(), c.nivel()).estadisticas());
            lista.add(r.conVida(r.vida()).conPoder(r.poder()));
        }
        return lista;
    }

    private static Contendiente conTodasLasEpicas(String id, String prototipo, int nivel, Integer equipo) {
        return new Contendiente(id, equipo, prototipo, nivel, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                Map.of(), List.of(), List.of(), TODAS_LAS_EPICAS, null);
    }

    private static List<Contendiente> equipo(String prefijo, List<String> prototipos, int nivel, int equipo,
                                             boolean todasLasEpicas) {
        List<Contendiente> lista = new ArrayList<>();
        for (int i = 0; i < prototipos.size(); i++) {
            String id = prefijo + (i + 1);
            lista.add(todasLasEpicas ? conTodasLasEpicas(id, prototipos.get(i), nivel, equipo)
                    : SimuladorDeCombates.heroe(id, prototipos.get(i), nivel, equipo));
        }
        return lista;
    }

    private static Contendiente de(List<Contendiente> mesa, String id) {
        return mesa.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static List<Contendiente> con(List<Contendiente> mesa, Contendiente cambiado) {
        List<Contendiente> lista = new ArrayList<>();
        for (Contendiente c : mesa) {
            lista.add(c.id().equals(cambiado.id()) ? cambiado : c);
        }
        return lista;
    }

    private static Map<String, Contendiente> porId(List<Contendiente> mesa) {
        Map<String, Contendiente> m = new LinkedHashMap<>();
        for (Contendiente c : mesa) {
            m.put(c.id(), c);
        }
        return m;
    }

    /** El bando que queda solo en pie, '-' si cayeron los dos, o nulo si siguen dos. */
    private static Character ganador(List<Contendiente> mesa, Map<String, Character> bando) {
        boolean a = mesa.stream().anyMatch(c -> c.enPie() && Objects.equals(bando.get(c.id()), 'A'));
        boolean b = mesa.stream().anyMatch(c -> c.enPie() && Objects.equals(bando.get(c.id()), 'B'));
        if (a && b) {
            return null;
        }
        return a ? Character.valueOf('A') : b ? Character.valueOf('B') : Character.valueOf('-');
    }
}
