package nexus.combate.reglas;

import nexus.combate.CategoriaEfecto;
import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las ocho epicas de la Tabla 20 y la del Master del Templo, «Velo de Sombras»
 * (7.8.14), en combate — §6.1.2: «otorgan mejoras al
 * ataque del heroe si coincide con su tipo ... no usan puntos de poder y tienen
 * dos turnos de recarga». El efecto general vale para todos; el epico, solo
 * para su tipo de heroe afin.
 */
class EpicasEnCombateTest {

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());

    private static Contendiente conEpica(String id, String prototipo, Integer equipo, String epica) {
        return new Contendiente(id, equipo, prototipo, 1, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                Map.of(), List.of(), List.of(), List.of(epica), null);
    }

    private static Contendiente heroe(String id, String prototipo, Integer equipo) {
        return CatalogoDePrueba.heroe(id, prototipo, equipo);
    }

    private ResultadoDeAccion jugar(String accion, String ejecutor, String objetivo, boolean porEquipos,
                                    AzarGuionado azar, Contendiente... combatientes) {
        ResultadoDeAccion r = motor.resolver(
                new SolicitudDeAccion(accion, ejecutor, objetivo, porEquipos, List.of(combatientes)), azar);
        assertTrue(azar.agotado(), "la regla no gasto todo el azar guionado");
        return r;
    }

    private static Contendiente de(ResultadoDeAccion r, String id) {
        return MotorDeAccionesTest.de(r, id);
    }

    private static Contendiente enTurno(ResultadoDeTurno r, String id) {
        return r.combatientes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Golpe de defensa (general): +1 al ataque; no cuesta poder y recarga dos turnos")
    void golpeDeDefensaGeneral() {
        ResultadoDeAccion r = jugar("Golpe de defensa", "armas", null, false,
                new AzarGuionado().dados(1).filas(1000).dados(2),
                conEpica("armas", "Guerrero Armas", null, "Golpe de defensa"), heroe("tanque", "Guerrero Tanque", null));
        assertTrue(r.esEpica());
        assertFalse(r.potenciada());
        assertEquals(12, r.ataque().ataqueResuelto(), "10 + 1 + 1");
        Contendiente armas = de(r, "armas");
        assertEquals(8, armas.poder(), "las epicas no usan poder");
        assertEquals(Map.of("Golpe de defensa", 2), r.recargas().get("armas"));
    }

    @Test
    @DisplayName("Golpe de defensa (potenciada, Guerrero Tanque): ademas +4 al dano y +2 % de critico")
    void golpeDeDefensaPotenciada() {
        // Tanque: sin critico propio (0 %). Con +2 el critico ocupa las filas 3201-3360.
        ResultadoDeAccion r = jugar("Golpe de defensa", "tanque", null, false,
                new AzarGuionado().dados(1).filas(3300).enteros(0).dados(1),
                conEpica("tanque", "Guerrero Tanque", null, "Golpe de defensa"), heroe("armas", "Guerrero Armas", null));
        assertTrue(r.potenciada());
        assertEquals(CategoriaEfecto.CAUSAR_DANO_CRITICO, r.ataque().categoria());
        assertEquals(6, r.ataque().danoAplicado(), "(1 + 4) x 120 %");
    }

    @Test
    @DisplayName("dos turnos de recarga: ni en el primer turno propio siguiente ni en el segundo")
    void dosTurnosDeRecarga() {
        Contendiente usada = conEpica("armas", "Guerrero Armas", null, "Golpe de defensa")
                .conCarga("Golpe de defensa", 0).conTurnosJugados(2);
        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Golpe de defensa", "armas", null, false,
                        List.of(usada, heroe("tanque", "Guerrero Tanque", null))), new AzarGuionado()));
        assertEquals(MotivoDeRechazo.EN_CARGA, rechazo.motivo());

        ResultadoDeAccion lista = jugar("Golpe de defensa", "armas", null, false, new AzarGuionado().dados(1).filas(1000).dados(1),
                usada.conTurnosJugados(3), heroe("tanque", "Guerrero Tanque", null));
        assertEquals("Golpe de defensa", lista.accionEjecutada());
    }

    @Test
    @DisplayName("una epica que no se tiene no se juega")
    void epicaQueNoSeTiene() {
        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Golpe de defensa", "armas", null, false,
                        List.of(heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null))),
                new AzarGuionado()));
        assertEquals(MotivoDeRechazo.EPICA_NO_DISPONIBLE, rechazo.motivo());
    }

    @Test
    @DisplayName("Segundo impulso: recupera 1d4; el Guerrero Armas ademas +3 y +5 % de critico al siguiente golpe")
    void segundoImpulso() {
        Contendiente herido = new Contendiente("tanque", null, "Guerrero Tanque", 1, null, 20, 10, 0, Map.of(),
                List.of(), List.of(), List.of("Segundo impulso"), null);
        ResultadoDeAccion general = jugar("Segundo impulso", "tanque", null, false, new AzarGuionado().dados(3),
                herido, heroe("armas", "Guerrero Armas", null));
        assertEquals(23, de(general, "tanque").vida());

        Contendiente armas = new Contendiente("armas", null, "Guerrero Armas", 1, null, 20, 8, 0, Map.of(),
                List.of(), List.of(), List.of("Segundo impulso"), null);
        ResultadoDeAccion potenciada = jugar("Segundo impulso", "armas", null, false, new AzarGuionado().dados(3),
                armas, heroe("tanque", "Guerrero Tanque", null));
        assertEquals(26, de(potenciada, "armas").vida(), "20 + 3 + 3");
        assertEquals(5, de(potenciada, "armas").sumaDe(TipoDeEfecto.BONO_CRITICO));
    }

    @Test
    @DisplayName("Luz cegadora: +1 a la vida; el Mago Fuego ademas golpea con +2 al dano y +1 % de critico")
    void luzCegadora() {
        Contendiente armas = new Contendiente("armas", null, "Guerrero Armas", 1, null, 20, 8, 0, Map.of(),
                List.of(), List.of(), List.of("Luz cegadora"), null);
        ResultadoDeAccion general = jugar("Luz cegadora", "armas", null, false, new AzarGuionado(),
                armas, heroe("tanque", "Guerrero Tanque", null));
        assertEquals(21, de(general, "armas").vida());
        assertEquals(null, general.ataque());

        Contendiente fuego = new Contendiente("fuego", null, "Mago Fuego", 1, null, 20, 8, 0, Map.of(),
                List.of(), List.of(), List.of("Luz cegadora"), null);
        ResultadoDeAccion potenciada = jugar("Luz cegadora", "fuego", null, false,
                new AzarGuionado().dados(8).filas(1000).dados(1),
                fuego, heroe("tanque", "Guerrero Tanque", null));
        assertEquals(3, potenciada.ataque().danoAplicado(), "1 + 2");
        assertEquals(21, de(potenciada, "fuego").vida());
    }

    @Test
    @DisplayName("Frio concentrado: -1 de poder al oponente; el Mago Hielo ademas no recibe dano hasta su turno")
    void frioConcentrado() {
        ResultadoDeAccion general = jugar("Frío concentrado", "armas", null, false, new AzarGuionado(),
                conEpica("armas", "Guerrero Armas", null, "Frío concentrado"), heroe("tanque", "Guerrero Tanque", null));
        assertEquals(9, de(general, "tanque").poder());

        ResultadoDeAccion potenciada = jugar("Frío concentrado", "hielo", null, false, new AzarGuionado(),
                conEpica("hielo", "Mago Hielo", null, "Frío concentrado"), heroe("tanque", "Guerrero Tanque", null));
        Contendiente hielo = de(potenciada, "hielo");
        assertTrue(hielo.tiene(TipoDeEfecto.INMUNE_TOTAL));

        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "tanque", null, false,
                new AzarGuionado().dados(6).filas(1000).dados(4),
                de(potenciada, "tanque"), hielo);
        assertEquals(0, golpe.ataque().danoAplicado());
        assertEquals(40, de(golpe, "hielo").vida());
    }

    @Test
    @DisplayName("Toma y lleva: +1 al ataque; el Picaro Veneno ademas recibe la mitad y retorna el resto")
    void tomaYLleva() {
        ResultadoDeAccion potenciada = jugar("Toma y lleva", "veneno", null, false,
                new AzarGuionado().dados(1).filas(1000).dados(1),
                conEpica("veneno", "Pícaro Veneno", null, "Toma y lleva"), heroe("armas", "Guerrero Armas", null));
        assertEquals(12, potenciada.ataque().ataqueResuelto(), "10 + 1 + 1");
        Contendiente veneno = de(potenciada, "veneno");
        assertTrue(veneno.tiene(TipoDeEfecto.REFLEJA_MITAD));

        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "armas", null, false,
                new AzarGuionado().dados(6).filas(1000).dados(5),
                veneno, de(potenciada, "armas"));
        assertEquals(34, de(golpe, "veneno").vida(), "recibe 2 de 5");
        assertEquals(40, de(golpe, "armas").vida(), "43 del primer golpe, menos los 3 que le vuelven");
    }

    @Test
    @DisplayName("Intimidacion sangrienta: +1 al dano; el Picaro Machete ademas +2 a la vida y +2 % de critico")
    void intimidacionSangrienta() {
        Contendiente machete = new Contendiente("machete", null, "Pícaro Machete", 1, null, 20, 8, 0, Map.of(),
                List.of(), List.of(), List.of("Intimidación sangrienta"), null);
        ResultadoDeAccion r = jugar("Intimidación sangrienta", "machete", null, false,
                new AzarGuionado().dados(2).filas(1000).dados(1),
                machete, heroe("tanque", "Guerrero Tanque", null));
        assertTrue(r.potenciada());
        assertEquals(2, r.ataque().danoAplicado(), "1 + 1");
        assertEquals(22, de(r, "machete").vida());
    }

    @Test
    @DisplayName("Te changua: «No aplica» para quien no es Chaman; al Chaman le sana a todos +(4d8)")
    void teChangua() {
        AccionNoPermitida sinEfecto = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Té changua", "armas", null, false, List.of(
                        conEpica("armas", "Guerrero Armas", null, "Té changua"),
                        heroe("tanque", "Guerrero Tanque", null))), new AzarGuionado()));
        assertEquals(MotivoDeRechazo.EPICA_SIN_EFECTO, sinEfecto.motivo());

        Contendiente chaman = new Contendiente("chaman", 1, "Chamán", 1, null, 10, 10, 0, Map.of(),
                List.of(), List.of(), List.of("Té changua"), null);
        Contendiente companero = new Contendiente("armas", 1, "Guerrero Armas", 1, null, 10, 8, 0, Map.of(),
                List.of(), List.of(), List.of(), null);
        ResultadoDeAccion r = jugar("Té changua", "chaman", null, true,
                new AzarGuionado().dados(1, 1, 1, 1, 2, 2, 2, 2),
                chaman, companero, heroe("tanque", "Guerrero Tanque", 2));
        assertEquals(14, de(r, "chaman").vida());
        assertEquals(18, de(r, "armas").vida());
        assertEquals(44, de(r, "tanque").vida(), "al rival no");
    }

    @Test
    @DisplayName("Reanimador 3000 (Medico): se vincula con un companero; si cae, se levanta con el 20 %")
    void reanimador3000() {
        Contendiente medico = conEpica("medico", "Médico", 1, "Reanimador 3000");
        ResultadoDeAccion vinculo = jugar("Reanimador 3000", "medico", "armas", true, new AzarGuionado(),
                medico, heroe("armas", "Guerrero Armas", 1), heroe("tanque", "Guerrero Tanque", 2));
        Contendiente armas = de(vinculo, "armas");
        assertTrue(armas.tiene(TipoDeEfecto.VINCULO_REANIMACION));

        Contendiente casiMuerto = new Contendiente("armas", 1, "Guerrero Armas", 1, null, 2, 8, 0, Map.of(),
                armas.efectos(), List.of(), List.of(), null);
        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "tanque", "armas", true,
                new AzarGuionado().dados(6).filas(1000).dados(4),
                de(vinculo, "medico"), casiMuerto, heroe("tanque", "Guerrero Tanque", 2));
        assertEquals(8, de(golpe, "armas").vida(), "20 % de 44");
        assertFalse(de(golpe, "armas").tiene(TipoDeEfecto.VINCULO_REANIMACION), "se gasta");
        assertTrue(golpe.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.REANIMACION));
    }

    // ------------------------------------------------------------------
    // «Velo de Sombras» (7.8.14): la epica del Master del Templo, no de la Tabla 20.
    // «Efecto general: +2 a la defensa para todos los heroes. Efecto epico (solo
    // Picaro Veneno): intangible durante 1 turno, evitando todo el dano recibido y
    // causando envenenamiento al atacante (+3 de dano por veneno durante 2 turnos).»
    // ------------------------------------------------------------------

    private static Contendiente veneno(String id, List<EfectoActivo> efectos) {
        return new Contendiente(id, null, "Pícaro Veneno", 1, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                Map.of(), efectos, List.of(), List.of(), null);
    }

    /** El Picaro Veneno que acaba de jugar Velo de Sombras, con el azar que no gasta. */
    private ResultadoDeAccion venenoConVelo() {
        return jugar("Velo de Sombras", "veneno", null, false, new AzarGuionado(),
                conEpica("veneno", "Pícaro Veneno", null, "Velo de Sombras"), heroe("tanque", "Guerrero Tanque", null));
    }

    @Test
    @DisplayName("Velo de Sombras (general): +2 a la defensa para quien la juega, no cuesta poder y recarga dos turnos")
    void veloDeSombrasGeneral() {
        ResultadoDeAccion r = jugar("Velo de Sombras", "armas", null, false, new AzarGuionado(),
                conEpica("armas", "Guerrero Armas", null, "Velo de Sombras"), heroe("tanque", "Guerrero Tanque", null));

        assertTrue(r.esEpica());
        assertFalse(r.potenciada());
        assertEquals(TipoDeAccion.DEFENSA, r.tipo());
        assertNull(r.ataque());
        Contendiente armas = de(r, "armas");
        assertEquals(2, armas.sumaDe(TipoDeEfecto.BONO_DEFENSA));
        assertFalse(armas.tiene(TipoDeEfecto.INMUNE_TOTAL), "la intangibilidad es solo del Picaro Veneno");
        assertFalse(armas.tiene(TipoDeEfecto.ENVENENA_AL_ATACANTE), "el veneno es solo del Picaro Veneno");
        assertEquals(8, armas.poder(), "las epicas no usan poder");
        assertEquals(Map.of("Velo de Sombras", 2), r.recargas().get("armas"));
    }

    @Test
    @DisplayName("Velo de Sombras (general): con +2 a la defensa, un ataque que antes pasaba (12 contra 11) ya no la supera (12 contra 13)")
    void veloDeSombrasSubeLaDefensaDeVerdad() {
        ResultadoDeAccion velo = jugar("Velo de Sombras", "armas", null, false, new AzarGuionado(),
                conEpica("armas", "Guerrero Armas", null, "Velo de Sombras"), heroe("tanque", "Guerrero Tanque", null));

        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "tanque", null, false, new AzarGuionado().dados(2),
                de(velo, "tanque"), de(velo, "armas"));

        assertEquals(12, golpe.ataque().ataqueResuelto());
        assertEquals(13, golpe.ataque().defensaObjetivo(), "11 de la Tabla 6 + 2");
        assertFalse(golpe.ataque().acierta());
        assertEquals(44, de(golpe, "armas").vida());
    }

    @Test
    @DisplayName("Velo de Sombras (potenciada, Picaro Veneno): ademas del +2, intangible un turno y con veneno para el atacante")
    void veloDeSombrasPotenciada() {
        ResultadoDeAccion r = venenoConVelo();

        assertTrue(r.esEpica());
        assertTrue(r.potenciada());
        Contendiente veneno = de(r, "veneno");
        assertEquals(2, veneno.sumaDe(TipoDeEfecto.BONO_DEFENSA), "la potenciada incluye el efecto general");
        assertTrue(veneno.tiene(TipoDeEfecto.INMUNE_TOTAL));
        assertTrue(veneno.tiene(TipoDeEfecto.ENVENENA_AL_ATACANTE));
        assertEquals(8, veneno.poder(), "no cuesta poder");
        assertEquals(Map.of("Velo de Sombras", 2), r.recargas().get("veneno"));
    }

    @Test
    @DisplayName("Velo de Sombras: el golpe que llega al intangible no le hace dano y deja al atacante con +3 de veneno por 2 turnos")
    void veloDeSombrasEvitaElDanoYEnvenenaAlAtacante() {
        ResultadoDeAccion velo = venenoConVelo();

        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "tanque", null, false,
                new AzarGuionado().dados(6).filas(1000).dados(4), de(velo, "tanque"), de(velo, "veneno"));

        assertTrue(golpe.ataque().acierta());
        assertEquals(0, golpe.ataque().danoAplicado(), "evita todo el dano recibido");
        assertEquals(36, de(golpe, "veneno").vida());
        Contendiente tanque = de(golpe, "tanque");
        EfectoActivo veneno = tanque.efectos().stream()
                .filter(e -> e.tipo() == TipoDeEfecto.DANO_POR_TURNO).findFirst().orElseThrow();
        assertEquals(3, veneno.valor(), "+3 de dano por veneno");
        assertEquals(2, veneno.turnos(), "durante 2 turnos");
        assertEquals("Velo de Sombras", veneno.nombre());
        assertEquals("veneno", veneno.origen(), "es el Picaro Veneno quien lo envenena");
        assertEquals(44, tanque.vida(), "el veneno actua al empezar su turno, no al recibirlo");
        assertTrue(golpe.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.PROTEGIDO
                && e.combatiente().equals("veneno")), "el motor cuenta que no recibio dano");
        assertTrue(golpe.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.EFECTO_APLICADO
                && e.combatiente().equals("tanque") && "Velo de Sombras".equals(e.efecto())));
    }

    @Test
    @DisplayName("Velo de Sombras: el veneno quita 3 al empezar cada uno de los dos turnos del atacante y se acaba")
    void elVenenoDelVeloActuaDosTurnos() {
        ResultadoDeAccion velo = venenoConVelo();
        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "tanque", null, false,
                new AzarGuionado().dados(6).filas(1000).dados(4), de(velo, "tanque"), de(velo, "veneno"));

        ResultadoDeTurno primero = motor.iniciarTurno(new SolicitudDeTurno("tanque", false,
                List.of(de(golpe, "tanque"), de(golpe, "veneno"))), new AzarGuionado());
        assertEquals(41, enTurno(primero, "tanque").vida());
        assertTrue(primero.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.DANO_POR_TURNO
                && e.combatiente().equals("tanque") && "Velo de Sombras".equals(e.efecto()) && e.cantidad() == 3));

        ResultadoDeTurno segundo = motor.iniciarTurno(new SolicitudDeTurno("tanque", false,
                List.of(enTurno(primero, "tanque"), enTurno(primero, "veneno"))),
                new AzarGuionado());
        assertEquals(38, enTurno(segundo, "tanque").vida());
        assertFalse(enTurno(segundo, "tanque").tiene(TipoDeEfecto.DANO_POR_TURNO),
                "dos turnos y se acaba");

        ResultadoDeTurno tercero = motor.iniciarTurno(new SolicitudDeTurno("tanque", false,
                List.of(enTurno(segundo, "tanque"), enTurno(segundo, "veneno"))),
                new AzarGuionado());
        assertEquals(38, enTurno(tercero, "tanque").vida(), "el tercer turno ya no pierde vida");
    }

    @Test
    @DisplayName("Velo de Sombras: dura un turno, hasta que empieza el del Picaro Veneno; despues el golpe lo daña y no envenena")
    void veloDeSombrasTerminaAlEmpezarSuTurno() {
        ResultadoDeAccion velo = venenoConVelo();

        ResultadoDeTurno turno = motor.iniciarTurno(new SolicitudDeTurno("veneno", false,
                List.of(de(velo, "tanque"), de(velo, "veneno"))), new AzarGuionado());
        Contendiente veneno = enTurno(turno, "veneno");
        assertFalse(veneno.tiene(TipoDeEfecto.INMUNE_TOTAL));
        assertFalse(veneno.tiene(TipoDeEfecto.ENVENENA_AL_ATACANTE));
        assertFalse(veneno.tiene(TipoDeEfecto.BONO_DEFENSA));

        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "tanque", null, false,
                new AzarGuionado().dados(6).filas(1000).dados(4),
                enTurno(turno, "tanque"), veneno);
        assertEquals(4, golpe.ataque().danoAplicado());
        assertEquals(32, de(golpe, "veneno").vida());
        assertTrue(de(golpe, "tanque").efectos().isEmpty(), "ya no hay veneno que devolver");
    }

    @Test
    @DisplayName("Velo de Sombras: un golpe que no supera la defensa no toca al intangible y tampoco envenena")
    void veloDeSombrasNoEnvenenaSiElGolpeNoLlega() {
        Contendiente blindado = veneno("veneno", List.of(
                new EfectoActivo("VELO_DE_SOMBRAS", "Velo de Sombras", TipoDeEfecto.INMUNE_TOTAL, 0, 1, "veneno"),
                new EfectoActivo("VELO_DE_SOMBRAS_VENENO", "Velo de Sombras", TipoDeEfecto.ENVENENA_AL_ATACANTE, 3, 2,
                        "veneno"),
                new EfectoActivo("MANO_DE_PIEDRA", "Mano de piedra", TipoDeEfecto.BONO_DEFENSA, 12, 1, "veneno")));

        ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "tanque", null, false, new AzarGuionado().dados(6),
                heroe("tanque", "Guerrero Tanque", null), blindado);

        assertFalse(golpe.ataque().acierta());
        assertTrue(de(golpe, "tanque").efectos().isEmpty());
    }

    @Test
    @DisplayName("Velo de Sombras: si lo golpean dos veces en su turno, el veneno se renueva y no se acumula")
    void elVenenoDelVeloNoSeAcumula() {
        ResultadoDeAccion velo = venenoConVelo();
        ResultadoDeAccion primero = jugar("ATAQUE_BASICO", "tanque", null, false,
                new AzarGuionado().dados(6).filas(1000).dados(4), de(velo, "tanque"), de(velo, "veneno"));
        ResultadoDeAccion segundo = jugar("ATAQUE_BASICO", "tanque", null, false,
                new AzarGuionado().dados(6).filas(1000).dados(4), de(primero, "tanque"), de(primero, "veneno"));

        assertEquals(1, de(segundo, "tanque").efectos().stream()
                .filter(e -> e.tipo() == TipoDeEfecto.DANO_POR_TURNO).count());
        assertEquals(3, de(segundo, "tanque").sumaDe(TipoDeEfecto.DANO_POR_TURNO));
    }

    @Test
    @DisplayName("un sanador no puede jugar una epica de ataque")
    void sanadorConEpicaDeAtaque() {
        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Golpe de defensa", "chaman", null, false, List.of(
                        conEpica("chaman", "Chamán", null, "Golpe de defensa"),
                        heroe("tanque", "Guerrero Tanque", null))), new AzarGuionado()));
        assertEquals(MotivoDeRechazo.SANADOR_NO_ATACA, rechazo.motivo());
    }
}
