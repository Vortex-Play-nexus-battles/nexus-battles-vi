package nexus.combate.reglas;

import nexus.combate.CategoriaEfecto;
import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los efectos de combate de armas e items (Tablas 8 a 19) que el motor aplica
 * con {@code aplicarEquipamiento}: critico (Tabla 23), lo que afecta al
 * oponente y lo que dura varios turnos. Los efectos planos llegan ya sumados en
 * las estadisticas que calcula inventario.
 */
class EquipoEnCombateTest {

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());

    private static Contendiente armado(String id, String prototipo, Integer equipo, String... objetos) {
        return new Contendiente(id, equipo, prototipo, 1, null, Integer.MAX_VALUE, Integer.MAX_VALUE, 0,
                Map.of(), List.of(), List.of(objetos), List.of(), null);
    }

    private ResultadoDeAccion jugar(String accion, String ejecutor, AzarGuionado azar, Contendiente... combatientes) {
        ResultadoDeAccion r = motor.resolver(new SolicitudDeAccion(accion, ejecutor, null, false,
                List.of(combatientes)), azar);
        assertTrue(azar.agotado(), "la regla no gasto todo el azar guionado");
        return r;
    }

    private static Contendiente de(ResultadoDeAccion r, String id) {
        return MotorDeAccionesTest.de(r, id);
    }

    @Test
    @DisplayName("Tabla 23: la Espada de dos manos (+3 % de critico) convierte en critico una fila que era evadir")
    void criticoDelArma() {
        // Guerrero Armas: critico 4801-5200 y evadir 5201-5440. Con +3 %: critico
        // hasta 5440. La fila 5300 pasa de evadir a critico.
        ResultadoDeAccion sinEspada = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(6).filas(5300).dados(5),
                armado("armas", "Guerrero Armas", null), armado("tanque", "Guerrero Tanque", null));
        assertEquals(CategoriaEfecto.EVADIR_EL_GOLPE, sinEspada.ataque().categoria());

        ResultadoDeAccion conEspada = jugar("ATAQUE_BASICO", "armas",
                new AzarGuionado().dados(6).filas(5300).enteros(0).dados(5),
                armado("armas", "Guerrero Armas", null, "Espada de dos manos"), armado("tanque", "Guerrero Tanque", null));
        assertEquals(CategoriaEfecto.CAUSAR_DANO_CRITICO, conEspada.ataque().categoria());
    }

    @Test
    @DisplayName("Baculo de Permafrost: -1 al dano y -2 % de critico de quien ataca al que lo lleva")
    void baculoDePermafrost() {
        // Critico del Armas 5 - 2 = 3 %: filas 4801-5040. La 5100 ya es evadir.
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(6).filas(5100).dados(6),
                armado("armas", "Guerrero Armas", null), armado("hielo", "Mago Hielo", null, "Báculo de Permafrost"));
        assertEquals(CategoriaEfecto.EVADIR_EL_GOLPE, r.ataque().categoria());
        assertEquals(5, r.ataque().danoBase(), "6 - 1");
        assertEquals(4, r.ataque().danoAplicado(), "80 % de 5");
    }

    @Test
    @DisplayName("Vision borrosa: -1 al ataque de quien ataca al que la lleva")
    void visionBorrosa() {
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(1),
                armado("armas", "Guerrero Armas", null), armado("tanque", "Guerrero Tanque", null, "Visión borrosa"));
        assertEquals(10, r.ataque().ataqueResuelto(), "10 + 1 - 1");
        assertFalse(r.ataque().acierta(), "10 no supera 11");
    }

    @Test
    @DisplayName("Pinchos de escudo: si el ataque es MENOR que la defensa, el atacante recibe 1")
    void pinchosDeEscudo() {
        // Un Mago de nivel 1 contra un Tanque de nivel 2 (defensa 22): 10 + 1 = 11 < 22.
        Contendiente tanque = new Contendiente("tanque", null, "Guerrero Tanque", 2, null, Integer.MAX_VALUE,
                Integer.MAX_VALUE, 0, Map.of(), List.of(), List.of("Pinchos de escudo"), List.of(), null);
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "fuego", new AzarGuionado().dados(1),
                armado("fuego", "Mago Fuego", null), tanque);
        assertFalse(r.ataque().acierta());
        assertEquals(39, de(r, "fuego").vida());

        ResultadoDeAccion igual = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(1),
                armado("armas", "Guerrero Armas", null), armado("tanque", "Guerrero Tanque", null, "Pinchos de escudo"));
        assertEquals(44, de(igual, "armas").vida(), "11 contra 11 no es menor");
    }

    @Test
    @DisplayName("Cierra sangrienta: sangrado de 2 durante dos turnos; con la Mancuerna yugular, de 4")
    void cierraSangrientaYMancuerna() {
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "machete", new AzarGuionado().dados(5).filas(1000).dados(1),
                armado("machete", "Pícaro Machete", null, "Cierra sangrienta"), armado("tanque", "Guerrero Tanque", null));
        assertEquals(2, de(r, "tanque").sumaDe(TipoDeEfecto.DANO_POR_TURNO));

        ResultadoDeAccion doble = jugar("ATAQUE_BASICO", "machete", new AzarGuionado().dados(5).filas(1000).dados(1),
                armado("machete", "Pícaro Machete", null, "Cierra sangrienta", "Mancuerna yugular"),
                armado("tanque", "Guerrero Tanque", null));
        Contendiente tanque = de(doble, "tanque");
        assertEquals(4, tanque.sumaDe(TipoDeEfecto.DANO_POR_TURNO));

        ResultadoDeTurno primero = motor.iniciarTurno(new SolicitudDeTurno("tanque", false,
                List.of(tanque, de(doble, "machete"))), new AzarGuionado());
        Contendiente trasUno = primero.combatientes().stream().filter(c -> c.id().equals("tanque")).findFirst().orElseThrow();
        assertEquals(39, trasUno.vida(), "43 - 4");
        assertTrue(primero.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.DANO_POR_TURNO));

        ResultadoDeTurno segundo = motor.iniciarTurno(new SolicitudDeTurno("tanque", false,
                primero.combatientes()), new AzarGuionado());
        Contendiente trasDos = segundo.combatientes().stream().filter(c -> c.id().equals("tanque")).findFirst().orElseThrow();
        assertEquals(35, trasDos.vida());
        assertFalse(trasDos.tiene(TipoDeEfecto.DANO_POR_TURNO), "dos turnos: acabo");
    }

    @Test
    @DisplayName("un sangrado que se renueva no se acumula: dos golpes de la misma arma no sangran el doble")
    void elSangradoSeRenueva() {
        ResultadoDeAccion primero = jugar("ATAQUE_BASICO", "machete", new AzarGuionado().dados(5).filas(1000).dados(1),
                armado("machete", "Pícaro Machete", null, "Cierra sangrienta"), armado("tanque", "Guerrero Tanque", null));
        ResultadoDeAccion segundo = jugar("ATAQUE_BASICO", "machete", new AzarGuionado().dados(5).filas(1000).dados(1),
                de(primero, "machete"), de(primero, "tanque"));
        assertEquals(2, de(segundo, "tanque").sumaDe(TipoDeEfecto.DANO_POR_TURNO));
    }

    @Test
    @DisplayName("Daga purulenta: +3 % de critico y sangrado de 1 durante dos turnos")
    void dagaPurulenta() {
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "veneno", new AzarGuionado().dados(5).filas(1000).dados(1),
                armado("veneno", "Pícaro Veneno", null, "Daga purulenta"), armado("tanque", "Guerrero Tanque", null));
        assertEquals(1, de(r, "tanque").sumaDe(TipoDeEfecto.DANO_POR_TURNO));
    }

    @Test
    @DisplayName("un golpe que sale «no causar dano» no deja efectos al acertar")
    void sinEfectoNoDejaEfectos() {
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "machete", new AzarGuionado().dados(5).filas(7000).dados(1),
                armado("machete", "Pícaro Machete", null, "Cierra sangrienta"), armado("tanque", "Guerrero Tanque", null));
        assertFalse(de(r, "tanque").tiene(TipoDeEfecto.DANO_POR_TURNO));
    }

    @Test
    @DisplayName("Empunadura de Furia: sangrado de 1 al objetivo y el guerrero pierde 1 en los mismos turnos")
    void empunaduraDeFuria() {
        ResultadoDeAccion r = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(5).filas(1000).dados(1),
                armado("armas", "Guerrero Armas", null, "Empuñadura de Furia"), armado("tanque", "Guerrero Tanque", null));
        assertEquals(1, de(r, "tanque").sumaDe(TipoDeEfecto.DANO_POR_TURNO));
        assertEquals(1, de(r, "armas").sumaDe(TipoDeEfecto.DANO_POR_TURNO));
    }

    @Test
    @DisplayName("Veneno lacerante: -1 al poder del oponente solo cada dos turnos (los pares del portador)")
    void venenoLacerante() {
        ResultadoDeAccion impar = jugar("ATAQUE_BASICO", "veneno", new AzarGuionado().dados(5).filas(1000).dados(1),
                armado("veneno", "Pícaro Veneno", null, "Veneno lacerante"), armado("tanque", "Guerrero Tanque", null));
        assertEquals(10, de(impar, "tanque").poder(), "primer turno propio: no");

        ResultadoDeAccion par = jugar("ATAQUE_BASICO", "veneno", new AzarGuionado().dados(5).filas(1000).dados(1),
                de(impar, "veneno"), de(impar, "tanque"));
        assertEquals(9, de(par, "tanque").poder(), "segundo turno propio: si");
    }

    @Test
    @DisplayName("Pluma sanadora: la sanacion se multiplica por dos; Benditas deja +1 durante tres turnos")
    void plumaYBenditas() {
        Contendiente chaman = new Contendiente("chaman", null, "Chamán", 1, null, 10, 10, 0, Map.of(), List.of(),
                List.of("Pluma sanadora"), List.of(), null);
        ResultadoDeAccion pluma = jugar("SANACION_BASICA", "chaman", new AzarGuionado().dados(1),
                chaman, armado("tanque", "Guerrero Tanque", null));
        assertEquals(24, de(pluma, "chaman").vida(), "10 + (6 + 1) x 2");

        Contendiente medico = new Contendiente("medico", null, "Médico", 1, null, 10, 10, 0, Map.of(), List.of(),
                List.of("Benditas"), List.of(), null);
        ResultadoDeAccion benditas = jugar("SANACION_BASICA", "medico", new AzarGuionado().dados(1),
                medico, armado("tanque", "Guerrero Tanque", null));
        EfectoActivo hot = de(benditas, "medico").efectos().stream()
                .filter(e -> e.tipo() == TipoDeEfecto.SANACION_POR_TURNO).findFirst().orElseThrow();
        assertEquals(1, hot.valor());
        assertEquals(3, hot.turnos());
    }

    @Test
    @DisplayName("Yerbabuena: la sanacion del Chaman deja +2 durante dos turnos")
    void yerbabuena() {
        Contendiente chaman = new Contendiente("chaman", null, "Chamán", 1, null, 10, 10, 0, Map.of(), List.of(),
                List.of("Yerbabuena"), List.of(), null);
        ResultadoDeAccion r = jugar("Toque de la Vida", "chaman", new AzarGuionado().dados(1),
                chaman, armado("tanque", "Guerrero Tanque", null));
        EfectoActivo hot = de(r, "chaman").efectos().stream()
                .filter(e -> e.tipo() == TipoDeEfecto.SANACION_POR_TURNO).findFirst().orElseThrow();
        assertEquals(2, hot.valor());
        assertEquals(2, hot.turnos());
    }

    @Test
    @DisplayName("los objetos con efectos solo planos no anaden nada de combate")
    void objetosPlanos() {
        EquipoDeCombate.EfectosDeEquipo efectos = EquipoDeCombate.de(List.of("Escudo de dragón",
                "Defensa del enfurecido", "Anillo para Piro-explosión", "Objeto inventado"));
        assertEquals(EquipoDeCombate.EfectosDeEquipo.NINGUNO, efectos);
        assertEquals(EquipoDeCombate.EfectosDeEquipo.NINGUNO, EquipoDeCombate.de(null));
    }
}
