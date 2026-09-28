package nexus.combate.reglas;

import nexus.combate.CategoriaEfecto;
import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static nexus.combate.reglas.CatalogoDePrueba.heroe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El nucleo del combate con numeros exactos (§6.1.1 a §6.1.4): tirada contra
 * defensa, tabla, porcentaje sobre la tirada de DANO, poder, carga, valor base
 * y objetivos. Cada tirada esta guionada ({@link AzarGuionado}), asi que cada
 * cifra de la prueba sale de una regla del documento.
 */
class MotorDeAccionesTest {

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());

    static Contendiente de(ResultadoDeAccion r, String id) {
        return r.combatientes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private ResultadoDeAccion jugar(String accion, String ejecutor, String objetivo, AzarGuionado azar,
                                    Contendiente... combatientes) {
        return jugar(accion, ejecutor, objetivo, false, azar, combatientes);
    }

    private ResultadoDeAccion jugar(String accion, String ejecutor, String objetivo, boolean porEquipos,
                                    AzarGuionado azar, Contendiente... combatientes) {
        ResultadoDeAccion r = motor.resolver(new SolicitudDeAccion(accion, ejecutor, objetivo, porEquipos,
                List.of(combatientes)), azar);
        assertTrue(azar.agotado(), "la regla no gasto todo el azar guionado");
        return r;
    }

    // ------------------------------------------------------------ §6.1.4

    @Test
    @DisplayName("un ataque que supera la defensa sortea fila y aplica el 100 % de la tirada de DANO")
    void golpeQueAcierta() {
        // Guerrero Armas: ataque 10 + 1d6 (saca 4 = 14) contra la defensa 11 del
        // Tanque. Fila 1000: «causar dano» (1-4800). Dano 1d6: saca 5.
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(4).filas(1000).dados(5),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));

        assertEquals(14, r.ataque().ataqueResuelto());
        assertEquals(11, r.ataque().defensaObjetivo());
        assertTrue(r.ataque().acierta());
        assertEquals(CategoriaEfecto.CAUSAR_DANO, r.ataque().categoria());
        assertEquals(1000, r.ataque().indiceTabla());
        assertEquals(5, r.ataque().danoAplicado());
        assertEquals(39, de(r, "tanque").vida());
        assertEquals(List.of(new Afectado("tanque", 44, 39)), r.afectados());
    }

    @Test
    @DisplayName("el dano es la formula de DANO, no la tirada de ataque (corrige 1.1.0)")
    void elDanoNoEsLaTiradaDeAtaque() {
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(6).filas(1000).dados(1),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));
        assertEquals(16, r.ataque().ataqueResuelto());
        assertEquals(1, r.ataque().danoAplicado());
    }

    @Test
    @DisplayName("un ataque que no supera la defensa no produce ningun efecto ni sortea fila")
    void golpeQueNoSupera() {
        // 10 + 1 = 11, igual a la defensa: «superar» es estrictamente mayor.
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(1),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));
        assertFalse(r.ataque().acierta());
        assertEquals(CategoriaEfecto.SIN_EFECTO, r.ataque().categoria());
        assertNull(r.ataque().indiceTabla());
        assertEquals(44, de(r, "tanque").vida());
        assertTrue(r.afectados().isEmpty());
    }

    @Test
    @DisplayName("critico: la fila del critico aplica entre 120 y 180 % sorteado (Tabla 22)")
    void critico() {
        // Guerrero Armas: critico en 4801-5200. Porcentaje: 120 + 30 = 150 %. Dano 6 -> 9.
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(5).filas(5000).enteros(30).dados(6),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));
        assertEquals(CategoriaEfecto.CAUSAR_DANO_CRITICO, r.ataque().categoria());
        assertEquals(150, r.ataque().porcentajeDano());
        assertEquals(9, r.ataque().danoAplicado());
    }

    @Test
    @DisplayName("evadir el golpe aplica el 80 %, escapar el 20 % y no causar dano el 0 % (Tabla 22)")
    void porcentajesDeLaTabla22() {
        ResultadoDeAccion evade = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(5).filas(5300).dados(5),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));
        assertEquals(CategoriaEfecto.EVADIR_EL_GOLPE, evade.ataque().categoria());
        assertEquals(4, evade.ataque().danoAplicado());

        ResultadoDeAccion escapa = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(5).filas(5500).dados(5),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));
        assertEquals(CategoriaEfecto.ESCAPAR_AL_GOLPE, escapa.ataque().categoria());
        assertEquals(1, escapa.ataque().danoAplicado());

        ResultadoDeAccion nada = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(5).filas(7000).dados(5),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));
        assertEquals(CategoriaEfecto.SIN_EFECTO, nada.ataque().categoria());
        assertTrue(nada.ataque().acierta());
        assertEquals(0, nada.ataque().danoAplicado());
    }

    // ------------------------------------------------------------ poder §6.1.1

    @Test
    @DisplayName("una accion especial cuesta su poder y entra en carga")
    void accionEspecialCuestaPoderYEntraEnCarga() {
        // Embate sangriento: 4 de poder, «+2 al ataque +1 de dano».
        ResultadoDeAccion r = jugar("Embate sangriento", "armas", null,
                new AzarGuionado().dados(1).filas(1000).dados(3),
                heroe("armas", "Guerrero Armas", null), heroe("tanque", "Guerrero Tanque", null));
        assertEquals(13, r.ataque().ataqueResuelto());
        assertEquals(4, r.ataque().danoAplicado());
        Contendiente armas = de(r, "armas");
        assertEquals(4, armas.poder(), "8 - 4");
        assertEquals(Map.of("Embate sangriento", 0), armas.cargas());
        assertEquals(1, armas.turnosJugados());
        assertEquals(Map.of("Embate sangriento", 1), r.recargas().get("armas"));
    }

    @Test
    @DisplayName("un turno de carga: no se repite en el turno propio siguiente, si en el otro")
    void unTurnoDeCarga() {
        Contendiente usada = heroe("armas", "Guerrero Armas", null).conCarga("Embate sangriento", 0)
                .conTurnosJugados(1);
        AccionNoPermitida enCarga = assertThrows(AccionNoPermitida.class, () ->
                motor.resolver(new SolicitudDeAccion("Embate sangriento", "armas", null, false,
                        List.of(usada, heroe("tanque", "Guerrero Tanque", null))), new AzarGuionado()));
        assertEquals(MotivoDeRechazo.EN_CARGA, enCarga.motivo());
        assertTrue(enCarga.getMessage().contains("1 turno"));

        ResultadoDeAccion otraVez = jugar("Embate sangriento", "armas", null,
                new AzarGuionado().dados(1).filas(1000).dados(3),
                usada.conTurnosJugados(2), heroe("tanque", "Guerrero Tanque", null));
        assertEquals("Embate sangriento", otraVez.accionEjecutada());
    }

    @Test
    @DisplayName("sin poder suficiente no se ejecuta: el ataque se reduce a su valor base (§6.1.1)")
    void sinPoderValorBase() {
        // Poder 2 < 4 de Embate sangriento. Valor base del ataque: 10, que no
        // supera la defensa 11: ninguna tirada.
        ResultadoDeAccion r = jugar("Embate sangriento", "armas", null, new AzarGuionado(),
                new Contendiente("armas", null, "Guerrero Armas", 1, null, 44, 2, 0, Map.of(), List.of(),
                        List.of(), List.of(), null),
                heroe("tanque", "Guerrero Tanque", null));
        assertTrue(r.enValorBase());
        assertEquals("Embate sangriento", r.accion());
        assertEquals(Reglamento.ATAQUE_BASICO, r.accionEjecutada());
        assertEquals(10, r.ataque().ataqueResuelto());
        Contendiente armas = de(r, "armas");
        assertEquals(2, armas.poder(), "no se gasta poder");
        assertTrue(armas.cargas().isEmpty(), "no entra en carga una accion que no se ejecuto");
        assertTrue(r.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.VALOR_BASE));
    }

    @Test
    @DisplayName("en valor base el dano no lleva los bonos de la accion que no se ejecuto")
    void valorBaseQueAcierta() {
        // Picaro Veneno contra un Chaman (defensa 4): base 10 > 4 acierta.
        ResultadoDeAccion r = jugar("Flor de loto", "veneno", null,
                new AzarGuionado().filas(1000).dados(2),
                new Contendiente("veneno", null, "Pícaro Veneno", 1, null, 36, 1, 0, Map.of(), List.of(),
                        List.of(), List.of(), null),
                heroe("chaman", "Chamán", null));
        assertTrue(r.enValorBase());
        assertEquals(2, r.ataque().danoAplicado(), "1d6 sin el +(4d8) de Flor de loto");
    }

    // ------------------------------------------------------------ objetivos §6.1.3

    @Test
    @DisplayName("en combate por equipos no se ataca a un companero")
    void noSeAtacaAUnCompanero() {
        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("ATAQUE_BASICO", "a", "b", true, List.of(
                        heroe("a", "Guerrero Armas", 1), heroe("b", "Guerrero Tanque", 1),
                        heroe("c", "Mago Fuego", 2))), new AzarGuionado()));
        assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, rechazo.motivo());
        assertTrue(rechazo.getMessage().contains("compañero"));
    }

    @Test
    @DisplayName("con un solo rival en pie el objetivo se resuelve solo; con varios hay que decirlo")
    void objetivoRequerido() {
        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("ATAQUE_BASICO", "a", null, false, List.of(
                        heroe("a", "Guerrero Armas", null), heroe("b", "Guerrero Tanque", null),
                        heroe("c", "Mago Fuego", null))), new AzarGuionado()));
        assertEquals(MotivoDeRechazo.OBJETIVO_REQUERIDO, rechazo.motivo());

        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "a", null, true,
                new AzarGuionado().dados(1).filas(1000).dados(1),
                heroe("a", "Guerrero Armas", 1), heroe("b", "Guerrero Tanque", 1),
                heroe("c", "Mago Fuego", 2));
        assertEquals("c", r.objetivo(), "el unico rival: el del otro equipo");
        assertEquals(39, de(r, "c").vida());
    }

    @Test
    @DisplayName("a un caido no se le ataca, y un caido no actua")
    void caidos() {
        Contendiente caido = new Contendiente("b", null, "Guerrero Tanque", 1, null, 0, 10, 0, Map.of(),
                List.of(), List.of(), List.of(), null);
        AccionNoPermitida aUnCaido = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("ATAQUE_BASICO", "a", "b", false, List.of(
                        heroe("a", "Guerrero Armas", null), caido, heroe("c", "Mago Fuego", null))),
                new AzarGuionado()));
        assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, aUnCaido.motivo());

        AccionNoPermitida caidoQueActua = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("ATAQUE_BASICO", "b", null, false, List.of(
                        heroe("a", "Guerrero Armas", null), caido)), new AzarGuionado()));
        assertEquals(MotivoDeRechazo.EJECUTOR_CAIDO, caidoQueActua.motivo());
    }

    @Test
    @DisplayName("un sanador no ataca (§6.1.1): ni ataque basico ni epica de ataque")
    void sanadorNoAtaca() {
        AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("ATAQUE_BASICO", "chaman", null, false, List.of(
                        heroe("chaman", "Chamán", null), heroe("b", "Guerrero Tanque", null))),
                new AzarGuionado()));
        assertEquals(MotivoDeRechazo.SANADOR_NO_ATACA, rechazo.motivo());
    }

    @Test
    @DisplayName("una accion que no es del heroe, o que no ha aprendido, se rechaza (RC-01)")
    void accionAjenaOBloqueada() {
        AccionNoPermitida ajena = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Vulcano", "armas", null, false, List.of(
                        heroe("armas", "Guerrero Armas", null), heroe("b", "Guerrero Tanque", null))),
                new AzarGuionado()));
        assertEquals(MotivoDeRechazo.ACCION_DESCONOCIDA, ajena.motivo());

        AccionNoPermitida bloqueada = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                new SolicitudDeAccion("Golpe de tormenta", "armas", null, false, List.of(
                        heroe("armas", "Guerrero Armas", null, 4), heroe("b", "Guerrero Tanque", null))),
                new AzarGuionado()));
        assertEquals(MotivoDeRechazo.BLOQUEADA_POR_NIVEL, bloqueada.motivo());
        assertTrue(bloqueada.getMessage().contains("nivel 8"));
    }

    @Test
    @DisplayName("el ejecutor tiene que estar en la partida y los ids no se repiten")
    void peticionesMalFormadas() {
        assertThrows(IllegalArgumentException.class, () -> motor.resolver(new SolicitudDeAccion(
                "ATAQUE_BASICO", "nadie", null, false,
                List.of(heroe("a", "Guerrero Armas", null), heroe("b", "Guerrero Tanque", null))),
                new AzarGuionado()));
        assertThrows(IllegalArgumentException.class, () -> motor.resolver(new SolicitudDeAccion(
                "ATAQUE_BASICO", "a", null, false,
                List.of(heroe("a", "Guerrero Armas", null), heroe("a", "Guerrero Tanque", null))),
                new AzarGuionado()));
        assertThrows(IllegalArgumentException.class, () -> motor.resolver(new SolicitudDeAccion(
                "ATAQUE_BASICO", "a", null, false, List.of(heroe("a", "Guerrero Armas", null))),
                new AzarGuionado()));
    }

    @Test
    @DisplayName("el nivel escala las estadisticas y multiplica los bonos de la Tabla 7 (§6.1.2)")
    void multiplicadorDeNivel() {
        // Tanque de nivel 4: ataque 40 + 1d6; Golpe con escudo +2 x 4 = +8.
        ResultadoDeAccion r = jugar("Golpe con escudo", "tanque", null,
                new AzarGuionado().dados(1).filas(1000).dados(2),
                heroe("tanque", "Guerrero Tanque", null, 4), heroe("armas", "Guerrero Armas", null, 4));
        assertEquals(49, r.ataque().ataqueResuelto());
        assertEquals(44, r.ataque().defensaObjetivo(), "defensa 11 x 4");
        assertEquals(2, r.ataque().danoAplicado(), "la base del dano (0) escala, el dado no");
        assertEquals(38, de(r, "tanque").poder(), "40 - 2");
    }

    @Test
    @DisplayName("las estadisticas que manda quien lleva la partida (con equipo) mandan sobre las del catalogo")
    void estadisticasConEquipo() {
        Estadisticas conEspada = new Estadisticas(8, 46, 13, new Formula(11, 1, 6), new Formula(2, 1, 6), null);
        Contendiente armado = new Contendiente("armas", null, "Guerrero Armas", 1, conEspada, 46, 8, 0,
                Map.of(), List.of(), List.of(), List.of(), null);
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", null,
                new AzarGuionado().dados(1).filas(1000).dados(1),
                armado, heroe("tanque", "Guerrero Tanque", null));
        assertEquals(12, r.ataque().ataqueResuelto());
        assertEquals(3, r.ataque().danoAplicado());
        assertEquals(46, de(r, "armas").vidaMaxima());
    }

    @Test
    @DisplayName("la vida y el poder que llegan se acotan a los maximos del heroe")
    void acotaVidaYPoder() {
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", null, new AzarGuionado().dados(1),
                new Contendiente("armas", null, "Guerrero Armas", 1, null, 999, 999, 0, Map.of(), List.of(),
                        List.of(), List.of(), null),
                heroe("tanque", "Guerrero Tanque", null));
        assertEquals(44, de(r, "armas").vida());
        assertEquals(8, de(r, "armas").poder());
    }
}
