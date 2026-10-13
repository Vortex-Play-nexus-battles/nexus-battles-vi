package nexus.misiones.dominio.simulacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import nexus.misiones.aplicacion.Dobles;
import nexus.misiones.dominio.Epica;
import nexus.misiones.dominio.HeroeEnMision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El simulador lleva la mision turno a turno contra el MOTOR DE COMBATE (el de
 * las batallas en linea) sin reimplementarlo: quien resuelve cada accion es el
 * motor ({@code turnos} y {@code acciones}) y quien decide la jugada es el
 * decisor de heroes (7.8.5). Aqui se prueba lo que SI es suyo: que le pide al
 * motor lo correcto con el heroe real, el orden de los encuentros, la vida que
 * se arrastra, que hace cuando el motor rechaza una accion, que se cuenta para
 * el reporte y los turnos que deja registrados.
 */
class SimuladorDeMisionTest {

    private static final UUID EJECUCION = UUID.fromString("0c7a2d9e-4f8b-4a55-9b65-6f1d3b2f0a11");
    private static final String MISION = "templo-olvidado";

    private static final Formula ATAQUE = new Formula(10, 1, 6);
    private static final Formula DANO = new Formula(2, 1, 4);

    private static final HeroeEnMision HEROE =
            new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 1, 0, 8, 44, 11);
    private static final PerfilDeCombate PERFIL =
            new PerfilDeCombate(new EstadisticasDeCombate(8, 44, 11, ATAQUE, DANO, null), List.of(), List.of());

    private static Rival regular(String nombre, int vida) {
        return new Rival(nombre, TipoDeRival.REGULAR, "Guerrero Tanque", 1, vida, 11, 10, List.of(), null);
    }

    private static Rival jefe(int vida) {
        return new Rival("El Guardian Eterno", TipoDeRival.JEFE, "Guerrero Tanque", 1, vida, 11, 10, List.of(), null);
    }

    private static final Epica VELO = new Epica("Velo de Sombras", "+2 a la defensa", "Intangible", null);

    private static Simulacion simular(SimuladorDeMision simulador, List<List<String>> estrategia, List<Rival> rivales,
                                      Azar azar, Long semilla) {
        return simulador.simular(EJECUCION, MISION, HEROE, PERFIL, estrategia, rivales, azar, semilla);
    }

    @Test
    @DisplayName("un heroe que gana cada duelo completa la mision y derrota al jefe")
    void heroeQueGanaTodo() {
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), new Dobles.Motor(),
                dado -> 10 * Math.pow(1.2, dado));

        ResultadoDeMision resultado = simular(simulador, List.of(),
                List.of(regular("Sombras Corrompidas", 5), regular("Sombras Corrompidas", 5), jefe(20)),
                new AzarGuionado(true, 3, true, 4, true, 8), null).resultado();

        assertThat(resultado.exito()).isTrue();
        assertThat(resultado.jefeDerrotado()).isTrue();
        assertThat(resultado.encuentrosRegularesCompletos()).isTrue();
        assertThat(resultado.encuentrosCompletados()).isEqualTo(3);
        assertThat(resultado.enemigosDerrotados())
                .containsExactly(new ResultadoDeMision.EnemigoDerrotado("Sombras Corrompidas", 2));
        // Seccion 6.1.1: 10 x 1,2^(1d8) por cada enemigo no jugador, con el
        // dado que tiro el servidor (3, 4 y 8 en este guion).
        assertThat(resultado.dados()).containsExactly(3, 4, 8);
        assertThat(resultado.experiencia())
                .isCloseTo(10 * Math.pow(1.2, 3) + 10 * Math.pow(1.2, 4) + 10 * Math.pow(1.2, 8),
                        org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("la vida no se recupera entre encuentros: el heroe cae en el segundo")
    void laVidaSeArrastra() {
        // Empieza el rival en los dos duelos y pega 30: el heroe (44) aguanta el
        // primero y cae en el segundo.
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 10;
        motor.danoDeLosEnemigos = 30;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        ResultadoDeMision resultado = simular(simulador, List.of(),
                List.of(regular("Sombras Corrompidas", 10), regular("Guardianes de Piedra", 10), jefe(10)),
                new AzarGuionado(false, 2, false), null).resultado();

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.jefeDerrotado()).isFalse();
        assertThat(resultado.encuentrosCompletados()).isEqualTo(1);
        assertThat(resultado.danoRecibido()).isEqualTo(60);
        assertThat(resultado.vidaMinimaPorcentaje()).isZero();
        assertThat(resultado.experiencia()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("el poder lo lleva el motor: se gasta con la accion, sube 2 por turno y vuelve al maximo en cada combate")
    void poderDelHeroe() {
        DecisorQueRecuerda decisor = new DecisorQueRecuerda("Embate sangriento", 4);
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 3;
        motor.danoDeLosEnemigos = 0;
        motor.costos.put("Embate sangriento", 4);
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, motor, dado -> 1);

        simular(simulador, List.of(List.of("Embate sangriento")),
                List.of(regular("A", 6), regular("B", 3)), new AzarGuionado(true, 1, true, 1), null);

        // Combate 1: 8 -> gasta 4 (4) -> +2 al empezar el turno siguiente (6) -> gasta 4 (2) -> fin.
        // Combate 2: vuelve al maximo, 8.
        assertThat(decisor.poderes).containsExactly(8, 6, 8);
        assertThat(decisor.turnos).containsExactly(1, 2, 1);
    }

    @Test
    @DisplayName("el reporte cuenta criticos, turnos y las habilidades mas usadas")
    void estadisticasDelReporte() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 5;
        motor.danoDeLosEnemigos = 2;
        motor.criticoPara = Dobles.Motor.HEROE;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        ResultadoDeMision resultado = simular(simulador, List.of(), List.of(regular("A", 10)),
                new AzarGuionado(true, 1), null).resultado();

        assertThat(resultado.turnos()).isEqualTo(2);
        assertThat(resultado.criticos()).isEqualTo(2);
        assertThat(resultado.danoInfligido()).isEqualTo(10);
        assertThat(resultado.danoRecibido()).isEqualTo(2);
        assertThat(resultado.habilidadesMasUsadas())
                .containsExactly(new ResultadoDeMision.UsoDeHabilidad("Ataque básico", 2));
    }

    @Test
    @DisplayName("un duelo que nadie puede ganar se corta y la mision falla, en vez de girar para siempre")
    void dueloSinSalida() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 0;
        motor.danoDeLosEnemigos = 0;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        ResultadoDeMision resultado = simular(simulador, List.of(), List.of(regular("Inmortal", 10)),
                new AzarGuionado(true), null).resultado();

        assertThat(resultado.exito()).isFalse();
        assertThat(resultado.turnos()).isEqualTo(SimuladorDeMision.RONDAS_MAXIMAS_POR_COMBATE);
    }

    @Test
    @DisplayName("un Master derrotado queda en el resultado con su epica")
    void masterDerrotado() {
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), new Dobles.Motor(), dado -> 1);
        Rival master = new Rival("Sombra del Olvido", TipoDeRival.MASTER, "Pícaro Veneno", 3, 30, 8, 8, List.of(), VELO);

        ResultadoDeMision resultado = simular(simulador, List.of(), List.of(master), new AzarGuionado(true, 5), null)
                .resultado();

        assertThat(resultado.masters()).containsExactly(
                new ResultadoDeMision.MasterEnfrentado("Sombra del Olvido", VELO, true));
        assertThat(resultado.enemigosDerrotados()).isEmpty();
    }

    @Test
    @DisplayName("con semilla de pruebas cada llamada al motor lleva una semilla distinta y reproducible")
    void semillaDeGolpes() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 5;
        motor.danoDeLosEnemigos = 1;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        simular(simulador, List.of(), List.of(regular("A", 10)), new AzarGuionado(true, 1), 100L);

        // turno(heroe) accion(heroe) turno(rival) accion(rival) turno(heroe) accion(heroe: cae el rival)
        assertThat(motor.semillas).containsExactly(100L, 101L, 102L, 103L, 104L, 105L);
    }

    @Test
    @DisplayName("sin semilla de pruebas el motor no recibe ninguna: el azar es el suyo")
    void sinSemillaDeGolpes() {
        Dobles.Motor motor = new Dobles.Motor();
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        simular(simulador, List.of(), List.of(regular("A", 10)), new AzarGuionado(true, 1), null);

        assertThat(motor.semillas).isNotEmpty().containsOnlyNulls();
    }

    @Test
    @DisplayName("un rival con estrategia predefinida tambien la consulta al decisor")
    void rivalConEstrategia() {
        DecisorQueRecuerda decisor = new DecisorQueRecuerda("Golpe con escudo", 2);
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, new Dobles.Motor(), dado -> 1);
        Rival conRotacion = new Rival("El Guardian Eterno", TipoDeRival.JEFE, "Guerrero Tanque", 1, 60, 11, 10,
                List.of(List.of("Golpe con escudo")), null);

        simular(simulador, List.of(), List.of(conRotacion), new AzarGuionado(false, 1), null);

        assertThat(decisor.prototipos).contains("Guerrero Tanque");
    }

    // ----------------------------------------------- el motor recibe al heroe real

    @Test
    @DisplayName("al motor se le piden turnos y acciones con el heroe real: nivel, estadisticas con equipo, equipo y epicas")
    void elMotorRecibeAlHeroeReal() {
        HeroeEnMision nivelCinco = new HeroeEnMision("h-1", "Vorn", "Guerrero Armas", "p-1", 5, 0, 12, 60, 14);
        EstadisticasDeCombate conEquipo = new EstadisticasDeCombate(12, 60, 14, ATAQUE, DANO, null);
        PerfilDeCombate perfil = new PerfilDeCombate(conEquipo, List.of("Espada de una mano"),
                List.of("Golpe de defensa"));
        Rival enemigo = new Rival("Sombras Corrompidas", TipoDeRival.REGULAR, "Guerrero Armas", 5, 20, 9, 10,
                List.of(), null, new Formula(8, 1, 6), new Formula(1, 1, 4), null);
        Dobles.Motor motor = new Dobles.Motor();
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        simulador.simular(EJECUCION, MISION, nivelCinco, perfil, List.of(), List.of(enemigo),
                new AzarGuionado(true, 1), null);

        // El primer paso de un turno es empezarlo; despues, la accion.
        assertThat(motor.llamadas.subList(0, 2)).containsExactly("turnos heroe", "acciones heroe ATAQUE_BASICO");
        assertThat(motor.llamadas).allMatch(l -> l.startsWith("turnos ") || l.startsWith("acciones "));

        List<Combatiente> mesa = motor.recibidos.getFirst();
        Combatiente heroe = mesa.stream().filter(c -> c.id().equals("heroe")).findFirst().orElseThrow();
        assertThat(heroe.prototipo()).isEqualTo("Guerrero Armas");
        assertThat(heroe.nivel()).isEqualTo(5);
        assertThat(heroe.estadisticas()).isEqualTo(conEquipo);
        assertThat(heroe.vidaActual()).isEqualTo(60);
        assertThat(heroe.poderActual()).isNull();
        assertThat(heroe.equipamiento()).containsExactly("Espada de una mano");
        assertThat(heroe.epicas()).containsExactly("Golpe de defensa");

        Combatiente rival = mesa.stream().filter(c -> c.id().equals("rival")).findFirst().orElseThrow();
        assertThat(rival.prototipo()).isEqualTo("Guerrero Armas");
        assertThat(rival.nivel()).isEqualTo(5);
        assertThat(rival.vidaActual()).isEqualTo(20);
        // La vida y la defensa de la semilla (y del escalon) viajan con las formulas del catalogo.
        assertThat(rival.estadisticas())
                .isEqualTo(new EstadisticasDeCombate(10, 20, 9, new Formula(8, 1, 6), new Formula(1, 1, 4), null));
        assertThat(rival.equipamiento()).isEmpty();
    }

    @Test
    @DisplayName("un rival sin formulas conocidas se manda sin estadisticas: el motor usa las del catalogo")
    void rivalSinFormulas() {
        Dobles.Motor motor = new Dobles.Motor();
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        simular(simulador, List.of(), List.of(regular("A", 5)), new AzarGuionado(true, 1), null);

        Combatiente rival = motor.recibidos.getFirst().stream().filter(c -> c.id().equals("rival")).findFirst()
                .orElseThrow();
        assertThat(rival.estadisticas()).isNull();
    }

    @Test
    @DisplayName("un heroe sin perfil conocido se manda sin estadisticas, con la vida con la que salio")
    void heroeSinPerfil() {
        Dobles.Motor motor = new Dobles.Motor();
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        simulador.simular(EJECUCION, MISION, HEROE, PerfilDeCombate.delCatalogo(), List.of(),
                List.of(regular("A", 5)), new AzarGuionado(true, 1), null);

        Combatiente heroe = motor.recibidos.getFirst().stream().filter(c -> c.id().equals("heroe")).findFirst()
                .orElseThrow();
        assertThat(heroe.estadisticas()).isNull();
        assertThat(heroe.vidaActual()).isEqualTo(44);
    }

    // ----------------------------------------------- la IA y las acciones rechazadas

    @Test
    @DisplayName("si el motor rechaza la opcion de la rotacion, la IA prueba la siguiente")
    void caeALaSiguienteOpcion() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.costos.put("Embate sangriento", 2);
        motor.costos.put("Golpe con escudo", 2);
        motor.rechazadas.put("Embate sangriento", "EN_CARGA");
        SimuladorDeMision simulador = new SimuladorDeMision(
                new DecisorPorRotaciones(motor.costos), motor, dado -> 1);

        Simulacion simulacion = simular(simulador,
                List.of(List.of("Embate sangriento"), List.of("Golpe con escudo")),
                List.of(regular("A", 10)), new AzarGuionado(true, 1), null);

        assertThat(motor.accionesPedidas).containsExactly("Embate sangriento", "Golpe con escudo");
        EventoDeCombate.Jugada jugada = simulacion.eventos().getFirst().jugada();
        assertThat(jugada.decidida()).isEqualTo("Golpe con escudo");
        assertThat(jugada.ejecutada()).isEqualTo("Golpe con escudo");
        assertThat(jugada.rechazadas()).containsExactly(new EventoDeCombate.Rechazo("Embate sangriento", "EN_CARGA"));
        assertThat(simulacion.resultado().habilidadesMasUsadas())
                .containsExactly(new ResultadoDeMision.UsoDeHabilidad("Golpe con escudo", 1));
    }

    @Test
    @DisplayName("si el motor rechaza todas las opciones de la rotacion, la IA juega el ataque basico")
    void caeAlAtaqueBasico() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.costos.put("Embate sangriento", 2);
        motor.costos.put("Golpe con escudo", 2);
        motor.rechazadas.put("Embate sangriento", "EN_CARGA");
        motor.rechazadas.put("Golpe con escudo", "BLOQUEADA_POR_NIVEL");
        SimuladorDeMision simulador = new SimuladorDeMision(
                new DecisorPorRotaciones(motor.costos), motor, dado -> 1);

        Simulacion simulacion = simular(simulador,
                List.of(List.of("Embate sangriento"), List.of("Golpe con escudo")),
                List.of(regular("A", 10)), new AzarGuionado(true, 1), null);

        assertThat(motor.accionesPedidas).containsExactly("Embate sangriento", "Golpe con escudo", "ATAQUE_BASICO");
        EventoDeCombate.Jugada jugada = simulacion.eventos().getFirst().jugada();
        assertThat(jugada.ejecutada()).isEqualTo("Ataque básico");
        assertThat(jugada.rechazadas()).extracting(EventoDeCombate.Rechazo::motivo)
                .containsExactly("EN_CARGA", "BLOQUEADA_POR_NIVEL");
        assertThat(simulacion.resultado().exito()).isTrue();
    }

    @Test
    @DisplayName("un decisor que insiste en la misma accion rechazada no cuelga la simulacion: ataque basico")
    void decisorTerco() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.rechazadas.put("Embate sangriento", "EN_CARGA");
        DecisionDeTurno siempreLoMismo = new DecisionDeTurno("Embate sangriento", 2, List.of(1));
        SimuladorDeMision simulador = new SimuladorDeMision(turno -> siempreLoMismo, motor, dado -> 1);

        ResultadoDeMision resultado = simular(simulador, List.of(List.of("Embate sangriento")),
                List.of(regular("A", 10)), new AzarGuionado(true, 1), null).resultado();

        assertThat(motor.accionesPedidas).containsExactly("Embate sangriento", "ATAQUE_BASICO");
        assertThat(resultado.exito()).isTrue();
    }

    @Test
    @DisplayName("aunque el motor rechace hasta el ataque basico, la simulacion termina (con la mision fallida)")
    void nuncaALaMitad() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.rechazadas.put("ATAQUE_BASICO", "SANADOR_NO_ATACA");
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        Simulacion simulacion = simular(simulador, List.of(), List.of(regular("A", 10)),
                new AzarGuionado(true, 1), null);

        assertThat(simulacion.resultado().exito()).isFalse();
        assertThat(simulacion.resultado().turnos()).isEqualTo(SimuladorDeMision.RONDAS_MAXIMAS_POR_COMBATE);
        EventoDeCombate.Jugada jugada = simulacion.eventos().getFirst().jugada();
        assertThat(jugada.ejecutada()).isNull();
        assertThat(jugada.resultado()).isNull();
        assertThat(jugada.rechazadas()).extracting(EventoDeCombate.Rechazo::motivo)
                .containsExactly("SANADOR_NO_ATACA");
    }

    @Test
    @DisplayName("una accion que el motor jugo en valor base no cuenta como usada: no entra en recarga para el decisor")
    void valorBaseNoEsUso() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 3;
        motor.danoDeLosEnemigos = 0;
        motor.costos.put("Embate sangriento", 99);
        DecisorEspia decisor = new DecisorEspia("Embate sangriento", 99);
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, motor, dado -> 1);

        Simulacion simulacion = simular(simulador, List.of(List.of("Embate sangriento")),
                List.of(regular("A", 6)), new AzarGuionado(true, 1), null);

        assertThat(decisor.turnosRecibidos).hasSize(2);
        assertThat(decisor.turnosRecibidos.get(1).turnoDeUltimoUso()).isEmpty();
        EventoDeCombate.Jugada jugada = simulacion.eventos().getFirst().jugada();
        assertThat(jugada.decidida()).isEqualTo("Embate sangriento");
        assertThat(jugada.enValorBase()).isTrue();
        assertThat(jugada.ejecutada()).isEqualTo("Ataque básico");
        assertThat(jugada.costoDecidido()).isEqualTo(99);
        assertThat(jugada.costoDePoder()).isZero();
        assertThat(simulacion.resultado().habilidadesMasUsadas())
                .containsExactly(new ResultadoDeMision.UsoDeHabilidad("Ataque básico", 2));
    }

    @Test
    @DisplayName("una accion que el motor ejecuto queda como usada en el turno: el decisor respeta su recarga")
    void usoQuedaRegistrado() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 3;
        motor.danoDeLosEnemigos = 0;
        motor.costos.put("Embate sangriento", 2);
        DecisorEspia decisor = new DecisorEspia("Embate sangriento", 2);
        SimuladorDeMision simulador = new SimuladorDeMision(decisor, motor, dado -> 1);

        Simulacion simulacion = simular(simulador, List.of(List.of("Embate sangriento")),
                List.of(regular("A", 6)), new AzarGuionado(true, 1), null);

        assertThat(decisor.turnosRecibidos.get(1).turnoDeUltimoUso()).containsEntry("Embate sangriento", 1);
        assertThat(decisor.turnosRecibidos.get(1).cursores()).containsExactly(1);
        EventoDeCombate.Jugada jugada = simulacion.eventos().getFirst().jugada();
        assertThat(jugada.enValorBase()).isFalse();
        assertThat(jugada.costoDePoder()).isEqualTo(2);
    }

    @Test
    @DisplayName("un enemigo sanador juega la sanacion basica: el motor le veda el ataque")
    void enemigoSanador() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 5;
        motor.sanadores.add("Chamán");
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);
        Rival chaman = new Rival("Chaman Corrupto", TipoDeRival.REGULAR, "Chamán", 1, 10, 8, 10, List.of(), null);

        ResultadoDeMision resultado = simular(simulador, List.of(), List.of(chaman), new AzarGuionado(true, 1), null)
                .resultado();

        assertThat(motor.accionesPedidas).contains("SANACION_BASICA", "ATAQUE_BASICO");
        assertThat(resultado.exito()).isTrue();
    }

    @Test
    @DisplayName("si el motor no responde, el fallo sube: la simulacion no inventa un resultado")
    void motorCaido() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.fallar = Dobles.caido("motor-combate");
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        assertThatThrownBy(() -> simular(simulador, List.of(), List.of(regular("A", 5)),
                new AzarGuionado(true, 1), null)).isInstanceOf(DependenciaDegradada.class);
    }

    // ----------------------------------------------- los turnos que quedan registrados

    @Test
    @DisplayName("cada turno deja un evento con actor, estado antes y despues, accion, costo y resultado del motor")
    void eventoPorTurno() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 5;
        motor.danoDeLosEnemigos = 2;
        motor.criticoPara = Dobles.Motor.HEROE;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);
        Rival enemigo = new Rival("Sombras Corrompidas", TipoDeRival.REGULAR, "Guerrero Tanque", 1, 10, 11, 10,
                List.of(), null, ATAQUE, DANO, null);

        List<EventoDeCombate> eventos = simular(simulador, List.of(), List.of(enemigo),
                new AzarGuionado(true, 1), null).eventos();

        // Ronda 1: heroe y enemigo. Ronda 2: el heroe remata. Tres turnos, tres eventos.
        assertThat(eventos).hasSize(3);
        assertThat(eventos).extracting(EventoDeCombate::secuencia).containsExactly(1, 2, 3);
        assertThat(eventos).extracting(EventoDeCombate::turno).containsExactly(1, 1, 2);
        assertThat(eventos).allSatisfy(e -> {
            assertThat(e.ejecucionId()).isEqualTo(EJECUCION);
            assertThat(e.misionId()).isEqualTo(MISION);
            assertThat(e.encuentro()).isEqualTo(1);
            assertThat(e.enemigo()).isEqualTo("Sombras Corrompidas");
        });

        EventoDeCombate golpeDelHeroe = eventos.get(0);
        assertThat(golpeDelHeroe.actor())
                .isEqualTo(new EventoDeCombate.Actor(EventoDeCombate.Lado.HEROE, "Vorn", "Guerrero Armas", 1));
        assertThat(golpeDelHeroe.antes().actor().vida()).isEqualTo(44);
        assertThat(golpeDelHeroe.antes().actor().vidaMaxima()).isEqualTo(44);
        assertThat(golpeDelHeroe.antes().actor().poder()).isEqualTo(8);
        assertThat(golpeDelHeroe.antes().actor().poderMaximo()).isEqualTo(8);
        assertThat(golpeDelHeroe.antes().oponente().vida()).isEqualTo(10);
        EventoDeCombate.Jugada jugada = golpeDelHeroe.jugada();
        assertThat(jugada.decidida()).isEqualTo("Ataque básico");
        assertThat(jugada.ejecutada()).isEqualTo("Ataque básico");
        assertThat(jugada.enValorBase()).isFalse();
        assertThat(jugada.costoDePoder()).isZero();
        assertThat(jugada.rechazadas()).isEmpty();
        EventoDeCombate.Resultado resultado = jugada.resultado();
        assertThat(resultado.categoria()).isEqualTo("CAUSAR_DANO_CRITICO");
        assertThat(resultado.critico()).isTrue();
        assertThat(resultado.acierta()).isTrue();
        assertThat(resultado.danoAplicado()).isEqualTo(5);
        // Los sucesos hablan de HEROE y ENEMIGO, no de los ids que se le mandaron al motor.
        assertThat(resultado.sucesos()).containsExactly(new Suceso("DANO", "ENEMIGO", "HEROE", "ATAQUE_BASICO", 5));
        assertThat(golpeDelHeroe.despues().oponente().vida()).isEqualTo(5);
        assertThat(golpeDelHeroe.despues().actor().vida()).isEqualTo(44);

        EventoDeCombate golpeDelEnemigo = eventos.get(1);
        assertThat(golpeDelEnemigo.actor().lado()).isEqualTo(EventoDeCombate.Lado.ENEMIGO);
        assertThat(golpeDelEnemigo.actor().nombre()).isEqualTo("Sombras Corrompidas");
        assertThat(golpeDelEnemigo.antes().actor().vida()).isEqualTo(5);
        assertThat(golpeDelEnemigo.antes().oponente().vida()).isEqualTo(44);
        assertThat(golpeDelEnemigo.despues().oponente().vida()).isEqualTo(42);
        assertThat(golpeDelEnemigo.jugada().resultado().critico()).isFalse();

        assertThat(eventos.get(2).despues().oponente().vida()).isZero();
    }

    @Test
    @DisplayName("el evento guarda la recarga y el poder gastado de una accion especial")
    void eventoConCostoDePoder() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 3;
        motor.danoDeLosEnemigos = 0;
        motor.costos.put("Embate sangriento", 4);
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorQueRecuerda("Embate sangriento", 4), motor,
                dado -> 1);

        List<EventoDeCombate> eventos = simular(simulador, List.of(List.of("Embate sangriento")),
                List.of(regular("A", 6)), new AzarGuionado(true, 1), null).eventos();

        EventoDeCombate.Jugada jugada = eventos.getFirst().jugada();
        assertThat(jugada.decidida()).isEqualTo("Embate sangriento");
        assertThat(jugada.costoDecidido()).isEqualTo(4);
        assertThat(jugada.costoDePoder()).isEqualTo(4);
        assertThat(eventos.getFirst().antes().actor().poder()).isEqualTo(8);
        assertThat(eventos.getFirst().despues().actor().poder()).isEqualTo(4);
    }

    @Test
    @DisplayName("el decisor ve tambien al oponente (su prototipo, nivel y estado): lo que necesita un modelo")
    void decisorVeAlOponente() {
        DecisorEspia espia = new DecisorEspia("Ataque básico", 0);
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 3;
        motor.danoDeLosEnemigos = 0;
        SimuladorDeMision simulador = new SimuladorDeMision(espia, motor, dado -> 1);

        Simulacion simulacion = simular(simulador, List.of(), List.of(regular("A", 10)), new AzarGuionado(true, 1),
                null);

        ContextoDelDuelo contexto = espia.turnosRecibidos.getFirst().contexto();
        assertThat(contexto).isNotNull();
        assertThat(contexto.prototipoDelOponente()).isEqualTo("Guerrero Tanque");
        assertThat(contexto.nivelDelOponente()).isEqualTo(1);
        // El mismo estado que queda en el evento como «antes»: es lo que vio quien decidio.
        EventoDeCombate primero = simulacion.eventos().getFirst();
        assertThat(contexto.propio()).isEqualTo(primero.antes().actor());
        assertThat(contexto.oponente()).isEqualTo(primero.antes().oponente());
        assertThat(contexto.propio().vidaMaxima()).isEqualTo(44);
        assertThat(contexto.oponente().vida()).isEqualTo(10);
    }

    @Test
    @DisplayName("cada evento dice contra quien se juega (para entrenar sin reconstruir el encuentro)")
    void eventoConOponente() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 5;
        motor.danoDeLosEnemigos = 2;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        List<EventoDeCombate> eventos = simular(simulador, List.of(), List.of(regular("A", 10)),
                new AzarGuionado(true, 1), null).eventos();

        assertThat(eventos.get(0).oponente())
                .isEqualTo(new EventoDeCombate.Actor(EventoDeCombate.Lado.ENEMIGO, "A", "Guerrero Tanque", 1));
        assertThat(eventos.get(1).oponente())
                .isEqualTo(new EventoDeCombate.Actor(EventoDeCombate.Lado.HEROE, "Vorn", "Guerrero Armas", 1));
    }

    @Test
    @DisplayName("lo que decide la regla queda como REGLA, sin version de modelo ni candidatas")
    void jugadaDeLaRegla() {
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), new Dobles.Motor(), dado -> 1);

        EventoDeCombate.Jugada jugada = simular(simulador, List.of(), List.of(regular("A", 5)),
                new AzarGuionado(true, 1), null).eventos().getFirst().jugada();

        assertThat(jugada.decididaPor()).isEqualTo(DecididaPor.REGLA);
        assertThat(jugada.versionDelModelo()).isNull();
        assertThat(jugada.candidatas()).isEmpty();
    }

    @Test
    @DisplayName("lo que decide el modelo queda dicho en el evento, con su version y las candidatas que puntuo")
    void jugadaDelModelo() {
        List<DecisionDeTurno.Candidata> candidatas = List.of(
                new DecisionDeTurno.Candidata("Embate sangriento", 4, 1, 0.2),
                new DecisionDeTurno.Candidata("Ataque básico", 0, null, 0.8));
        DecisionDeTurno delModelo = new DecisionDeTurno("Ataque básico", 0, List.of())
                .consultandoAlModelo(DecididaPor.MODELO, "v1-prueba", candidatas);
        SimuladorDeMision simulador = new SimuladorDeMision(turno -> delModelo, new Dobles.Motor(), dado -> 1);

        EventoDeCombate.Jugada jugada = simular(simulador, List.of(), List.of(regular("A", 5)),
                new AzarGuionado(true, 1), null).eventos().getFirst().jugada();

        assertThat(jugada.decididaPor()).isEqualTo(DecididaPor.MODELO);
        assertThat(jugada.versionDelModelo()).isEqualTo("v1-prueba");
        assertThat(jugada.candidatas()).isEqualTo(candidatas);
    }

    @Test
    @DisplayName("el lado del enemigo en el evento es MASTER o JEFE segun quien sea")
    void ladoDelEnemigo() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 1;
        motor.danoDeLosEnemigos = 1;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);
        Rival master = new Rival("Sombra del Olvido", TipoDeRival.MASTER, "Pícaro Veneno", 3, 2, 8, 8, List.of(), VELO);

        List<EventoDeCombate> eventos = simular(simulador, List.of(), List.of(master, jefe(2)),
                new AzarGuionado(false, 1, false, 1), null).eventos();

        assertThat(eventos).filteredOn(e -> e.actor().lado() != EventoDeCombate.Lado.HEROE)
                .extracting(e -> e.actor().lado())
                .containsExactly(EventoDeCombate.Lado.MASTER, EventoDeCombate.Lado.MASTER,
                        EventoDeCombate.Lado.JEFE, EventoDeCombate.Lado.JEFE);
        assertThat(eventos).extracting(EventoDeCombate::encuentro).containsOnly(1, 2);
    }

    @Test
    @DisplayName("quien cae al empezar su turno (un sangrado) deja un evento sin jugada y el duelo termina")
    void caerAlIniciar() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 1;
        motor.caeAlIniciar = Dobles.Motor.RIVAL;
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorBasico(), motor, dado -> 1);

        Simulacion simulacion = simular(simulador, List.of(), List.of(regular("A", 10)),
                new AzarGuionado(true, 1), null);

        List<EventoDeCombate> eventos = simulacion.eventos();
        assertThat(eventos).hasSize(2);
        EventoDeCombate caido = eventos.get(1);
        assertThat(caido.actor().lado()).isEqualTo(EventoDeCombate.Lado.ENEMIGO);
        assertThat(caido.alIniciar()).extracting(Suceso::tipo).contains("DANO_POR_TURNO");
        assertThat(caido.alIniciar().getFirst().combatiente()).isEqualTo("ENEMIGO");
        assertThat(caido.jugada()).isNull();
        assertThat(caido.despues().actor().vida()).isZero();
        assertThat(simulacion.resultado().exito()).isTrue();
        assertThat(simulacion.resultado().encuentrosCompletados()).isEqualTo(1);
    }

    @Test
    @DisplayName("lo que pasa al empezar el turno (poder recuperado) queda en el evento")
    void alIniciarElTurno() {
        Dobles.Motor motor = new Dobles.Motor();
        motor.danoDelHeroe = 3;
        motor.danoDeLosEnemigos = 0;
        motor.costos.put("Embate sangriento", 4);
        SimuladorDeMision simulador = new SimuladorDeMision(new DecisorQueRecuerda("Embate sangriento", 4), motor,
                dado -> 1);

        List<EventoDeCombate> eventos = simular(simulador, List.of(List.of("Embate sangriento")),
                List.of(regular("A", 6)), new AzarGuionado(true, 1), null).eventos();

        // Ronda 2 del heroe: tenia 4, recupera 2 al empezar el turno.
        EventoDeCombate segundoTurnoDelHeroe = eventos.stream()
                .filter(e -> e.actor().lado() == EventoDeCombate.Lado.HEROE && e.turno() == 2).findFirst()
                .orElseThrow();
        assertThat(segundoTurnoDelHeroe.alIniciar())
                .containsExactly(new Suceso("PODER_RECUPERADO", "HEROE", null, null, 2));
        assertThat(segundoTurnoDelHeroe.antes().actor().poder()).isEqualTo(6);
    }

    // ------------------------------------------------------------- dobles

    /** El decisor sin estrategia: siempre ataque basico, sin gastar poder. */
    static final class DecisorBasico implements DecisorDeTurno {
        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
            return new DecisionDeTurno("Ataque básico", 0, turno.cursores());
        }
    }

    /** Usa una habilidad cuando le alcanza el poder y recuerda con que le llamaron. */
    static final class DecisorQueRecuerda implements DecisorDeTurno {
        final String habilidad;
        final int costo;
        final List<Integer> poderes = new ArrayList<>();
        final List<Integer> turnos = new ArrayList<>();
        final List<String> prototipos = new ArrayList<>();

        DecisorQueRecuerda(String habilidad, int costo) {
            this.habilidad = habilidad;
            this.costo = costo;
        }

        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
            prototipos.add(turno.prototipo());
            if (turno.prototipo().equals("Guerrero Armas")) {
                poderes.add(turno.poder());
                turnos.add(turno.turno());
            }
            if (turno.poder() >= costo) {
                return new DecisionDeTurno(habilidad, costo, List.of(1));
            }
            return new DecisionDeTurno("Ataque básico", 0, turno.cursores());
        }
    }

    /** Siempre elige la misma habilidad, sin mirar el poder (lo valida el motor), y guarda lo que recibe. */
    static final class DecisorEspia implements DecisorDeTurno {
        final String habilidad;
        final int costo;
        final List<TurnoParaDecidir> turnosRecibidos = new ArrayList<>();

        DecisorEspia(String habilidad, int costo) {
            this.habilidad = habilidad;
            this.costo = costo;
        }

        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
            turnosRecibidos.add(turno);
            return new DecisionDeTurno(habilidad, costo, List.of(turno.cursores().isEmpty() ? 1
                    : turno.cursores().getFirst() + 1));
        }
    }

    /**
     * Lo esencial del decisor de heroes: la primera rotacion viable (poder y
     * recarga de un turno), el cursor que avanza solo en la ejecutada y el
     * ataque basico de respaldo.
     */
    static final class DecisorPorRotaciones implements DecisorDeTurno {
        private final java.util.Map<String, Integer> costos;

        DecisorPorRotaciones(java.util.Map<String, Integer> costos) {
            this.costos = costos;
        }

        @Override
        public DecisionDeTurno decidir(TurnoParaDecidir turno) {
            List<Integer> cursores = new ArrayList<>();
            for (int i = 0; i < turno.rotaciones().size(); i++) {
                cursores.add(turno.cursores().size() > i ? turno.cursores().get(i) : 0);
            }
            for (int i = 0; i < turno.rotaciones().size(); i++) {
                List<String> pasos = turno.rotaciones().get(i);
                String paso = pasos.get(cursores.get(i) % pasos.size());
                int costo = costos.getOrDefault(paso, 0);
                Integer ultimo = turno.turnoDeUltimoUso().get(paso);
                boolean enRecarga = ultimo != null && turno.turno() < ultimo + 2;
                if (costo <= turno.poder() && !enRecarga) {
                    cursores.set(i, cursores.get(i) + 1);
                    return new DecisionDeTurno(paso, costo, cursores);
                }
            }
            return new DecisionDeTurno("Ataque básico", 0, cursores);
        }
    }

    /**
     * Azar con guion: los booleanos dicen quien empieza cada duelo (true = el
     * heroe) y los enteros son los dados de experiencia, en el orden en que
     * se piden.
     */
    static final class AzarGuionado implements Azar {
        private final Deque<Object> guion = new ArrayDeque<>();

        AzarGuionado(Object... pasos) {
            guion.addAll(List.of(pasos));
        }

        @Override
        public int entre(int minimo, int maximo) {
            Object siguiente = guion.isEmpty() ? minimo : guion.poll();
            int valor = (Integer) siguiente;
            assertThat(valor).isBetween(minimo, maximo);
            return valor;
        }

        @Override
        public boolean acierta(double probabilidad) {
            Object siguiente = guion.isEmpty() ? Boolean.TRUE : guion.poll();
            return (Boolean) siguiente;
        }

        @Override
        public long largo() {
            return 7L;
        }
    }
}
