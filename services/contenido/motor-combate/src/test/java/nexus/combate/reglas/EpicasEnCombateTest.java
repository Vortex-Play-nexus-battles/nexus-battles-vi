package nexus.combate.reglas;

import nexus.combate.CategoriaEfecto;
import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las ocho epicas de la Tabla 20 en combate — §6.1.2: «otorgan mejoras al
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
