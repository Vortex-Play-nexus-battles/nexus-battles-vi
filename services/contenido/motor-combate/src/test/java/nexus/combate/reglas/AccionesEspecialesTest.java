package nexus.combate.reglas;

import nexus.combate.IndiceNormal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static nexus.combate.reglas.CatalogoDePrueba.heroe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las 24 acciones de la Tabla 7, cada una con su coste, su efecto y el
 * multiplicador de nivel (§6.1.2). La segunda accion de cada heroe se prueba en
 * nivel 4 y la tercera en nivel 8, que es cuando se aprenden (RC-01): asi cada
 * prueba ve tambien el multiplicador trabajando.
 */
class AccionesEspecialesTest {

    private final MotorDeAcciones motor = new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision());

    private ResultadoDeAccion jugar(String accion, String ejecutor, String objetivo, boolean porEquipos,
                                    AzarGuionado azar, Contendiente... combatientes) {
        ResultadoDeAccion r = motor.resolver(
                new SolicitudDeAccion(accion, ejecutor, objetivo, porEquipos, List.of(combatientes)), azar);
        assertTrue(azar.agotado(), "la regla no gasto todo el azar guionado");
        return r;
    }

    private ResultadoDeAccion jugar(String accion, String ejecutor, AzarGuionado azar, Contendiente... combatientes) {
        return jugar(accion, ejecutor, null, false, azar, combatientes);
    }

    private ResultadoDeTurno empezarTurno(String id, Contendiente... combatientes) {
        return motor.iniciarTurno(new SolicitudDeTurno(id, false, List.of(combatientes)), new AzarGuionado());
    }

    private static Contendiente de(ResultadoDeAccion r, String id) {
        return MotorDeAccionesTest.de(r, id);
    }

    private static Contendiente de(ResultadoDeTurno r, String id) {
        return r.combatientes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static Contendiente conVida(Contendiente c, int vida) {
        return new Contendiente(c.id(), c.equipo(), c.prototipo(), c.nivel(), c.estadisticas(), vida,
                c.poder(), c.turnosJugados(), c.cargas(), c.efectos(), c.equipamiento(), c.epicas(),
                c.ultimoDanoRecibido());
    }

    @Nested
    @DisplayName("Guerrero Tanque")
    class GuerreroTanque {

        @Test
        @DisplayName("Golpe con escudo (2): +2 al ataque")
        void golpeConEscudo() {
            ResultadoDeAccion r = jugar("Golpe con escudo", "tanque",
                    new AzarGuionado().dados(1).filas(1000).dados(3),
                    heroe("tanque", "Guerrero Tanque", null), heroe("armas", "Guerrero Armas", null));
            assertEquals(13, r.ataque().ataqueResuelto(), "10 + 1 + 2");
            assertEquals(3, r.ataque().danoAplicado());
            assertEquals(8, de(r, "tanque").poder());
        }

        @Test
        @DisplayName("Mano de piedra (4): +12 x nivel a la defensa hasta su proximo turno")
        void manoDePiedra() {
            ResultadoDeAccion defensa = jugar("Mano de piedra", "tanque", new AzarGuionado(),
                    heroe("tanque", "Guerrero Tanque", null, 4), heroe("armas", "Guerrero Armas", null, 4));
            Contendiente tanque = de(defensa, "tanque");
            assertEquals(36, tanque.poder(), "40 - 4");
            assertEquals(48, tanque.sumaDe(TipoDeEfecto.BONO_DEFENSA), "12 x 4");
            assertEquals(TipoDeAccion.DEFENSA, defensa.tipo());
            assertEquals(null, defensa.ataque());

            // El Armas saca el maximo (40 + 6 = 46): contra 44 + 48 no llega.
            ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(6),
                    tanque, heroe("armas", "Guerrero Armas", null, 4));
            assertEquals(92, golpe.ataque().defensaObjetivo());
            assertFalse(golpe.ataque().acierta());

            // Al empezar su siguiente turno, la proteccion termina.
            ResultadoDeTurno turno = empezarTurno("tanque", de(golpe, "tanque"), de(golpe, "armas"));
            assertEquals(0, de(turno, "tanque").sumaDe(TipoDeEfecto.BONO_DEFENSA));
        }

        @Test
        @DisplayName("Defensa feroz (6): inmune al dano fisico y resta (3d6) x nivel al magico")
        void defensaFeroz() {
            ResultadoDeAccion r = jugar("Defensa feroz", "tanque", new AzarGuionado().dados(2, 3, 4),
                    heroe("tanque", "Guerrero Tanque", null, 8), heroe("fuego", "Mago Fuego", null, 8));
            Contendiente tanque = de(r, "tanque");
            assertEquals(74, tanque.poder(), "80 - 6");
            assertTrue(tanque.tiene(TipoDeEfecto.INMUNE_FISICO));
            assertEquals(72, tanque.sumaDe(TipoDeEfecto.REDUCE_MAGICO), "(2+3+4) x 8");
        }

        @Test
        @DisplayName("Defensa feroz frente a un golpe fisico: ni un punto")
        void defensaFerozFisico() {
            Contendiente tanque = heroe("tanque", "Guerrero Tanque", null).conEfecto(new EfectoActivo(
                    "DEFENSA_FEROZ_FISICO", "Defensa feroz", TipoDeEfecto.INMUNE_FISICO, 0, 1, "tanque"));
            ResultadoDeAccion r = jugar("ATAQUE_BASICO", "machete", new AzarGuionado().dados(10).filas(1000).dados(8),
                    tanque, heroe("machete", "Pícaro Machete", null));
            assertEquals(0, r.ataque().danoAplicado());
            assertEquals(44, de(r, "tanque").vida());
            assertTrue(r.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.PROTEGIDO));
        }

        @Test
        @DisplayName("Defensa feroz frente a un golpe magico: resta lo que resto la tirada")
        void defensaFerozMagico() {
            Contendiente tanque = heroe("tanque", "Guerrero Tanque", null).conEfecto(new EfectoActivo(
                    "DEFENSA_FEROZ_MAGICO", "Defensa feroz", TipoDeEfecto.REDUCE_MAGICO, 3, 1, "tanque"));
            // Misiles de magma: 10 + 8 + 1 = 19 acierta; dano 8 + 2 = 10, menos 3.
            ResultadoDeAccion r = jugar("Misiles de magma", "fuego", new AzarGuionado().dados(8).filas(1000).dados(8),
                    tanque, heroe("fuego", "Mago Fuego", null));
            assertEquals(7, r.ataque().danoAplicado());
            assertEquals(37, de(r, "tanque").vida());
        }
    }

    @Nested
    @DisplayName("Guerrero Armas")
    class GuerreroArmas {

        @Test
        @DisplayName("Lanza de los dioses (4): +2 x nivel al dano")
        void lanzaDeLosDioses() {
            ResultadoDeAccion r = jugar("Lanza de los dioses", "armas", new AzarGuionado().dados(5).filas(1000).dados(1),
                    heroe("armas", "Guerrero Armas", null, 4), heroe("tanque", "Guerrero Tanque", null, 4));
            assertEquals(45, r.ataque().ataqueResuelto());
            assertEquals(9, r.ataque().danoAplicado(), "1 + 2 x 4");
            assertEquals(28, de(r, "armas").poder());
        }

        @Test
        @DisplayName("Golpe de tormenta (6): +(3d6) x nivel al ataque y +2 x nivel al dano")
        void golpeDeTormenta() {
            ResultadoDeAccion r = jugar("Golpe de tormenta", "armas",
                    new AzarGuionado().dados(1, 1, 1, 1).filas(1000).dados(2),
                    heroe("armas", "Guerrero Armas", null, 8), heroe("tanque", "Guerrero Tanque", null, 8));
            assertEquals(105, r.ataque().ataqueResuelto(), "80 + 1 + (3) x 8");
            assertEquals(18, r.ataque().danoAplicado(), "2 + 2 x 8");
            assertEquals(58, de(r, "armas").poder());
        }
    }

    @Nested
    @DisplayName("Mago Fuego")
    class MagoFuego {

        @Test
        @DisplayName("Misiles de magma (2): +1 al ataque +2 de dano")
        void misilesDeMagma() {
            ResultadoDeAccion r = jugar("Misiles de magma", "fuego", new AzarGuionado().dados(1).filas(1000).dados(4),
                    heroe("fuego", "Mago Fuego", null), heroe("tanque", "Guerrero Tanque", null));
            assertEquals(12, r.ataque().ataqueResuelto());
            assertEquals(6, r.ataque().danoAplicado());
            assertEquals(6, de(r, "fuego").poder());
        }

        @Test
        @DisplayName("Vulcano (6): +3 x nivel al ataque +(3d9) x nivel al dano")
        void vulcano() {
            ResultadoDeAccion r = jugar("Vulcano", "fuego", new AzarGuionado().dados(1).filas(1000).dados(1, 1, 1, 1),
                    heroe("fuego", "Mago Fuego", null, 4), heroe("tanque", "Guerrero Tanque", null, 4));
            assertEquals(53, r.ataque().ataqueResuelto(), "40 + 1 + 12");
            assertEquals(13, r.ataque().danoAplicado(), "1 + (3) x 4");
            assertEquals(26, de(r, "fuego").poder());
        }

        @Test
        @DisplayName("Pare de fuego (4): +1 x nivel al ataque y retorna de 0 a lo que el objetivo le hizo (0dx)")
        void pareDeFuego() {
            Contendiente fuego = heroe("fuego", "Mago Fuego", null, 8).conUltimoDanoRecibido(new DanoRecibido("tanque", 7));
            ResultadoDeAccion r = jugar("Pare de fuego", "fuego", new AzarGuionado().dados(8).filas(1000).dados(1).enteros(5),
                    fuego, heroe("tanque", "Guerrero Tanque", null, 8));
            assertEquals(96, r.ataque().ataqueResuelto(), "80 + 8 + 8");
            assertEquals(6, r.ataque().danoAplicado(), "1 + 5 de los 7 recibidos");
            assertEquals(60, de(r, "fuego").poder());
        }

        @Test
        @DisplayName("Pare de fuego no retorna nada contra quien no le golpeo")
        void pareDeFuegoSinGolpePrevio() {
            Contendiente fuego = heroe("fuego", "Mago Fuego", null, 8).conUltimoDanoRecibido(new DanoRecibido("otro", 7));
            ResultadoDeAccion r = jugar("Pare de fuego", "fuego", new AzarGuionado().dados(8).filas(1000).dados(1),
                    fuego, heroe("tanque", "Guerrero Tanque", null, 8));
            assertEquals(1, r.ataque().danoAplicado());
        }
    }

    @Nested
    @DisplayName("Mago Hielo")
    class MagoHielo {

        @Test
        @DisplayName("Lluvia de hielo (2): +2 al ataque +2 de dano")
        void lluviaDeHielo() {
            ResultadoDeAccion r = jugar("Lluvia de hielo", "hielo", new AzarGuionado().dados(1).filas(1000).dados(1),
                    heroe("hielo", "Mago Hielo", null), heroe("tanque", "Guerrero Tanque", null));
            assertEquals(13, r.ataque().ataqueResuelto());
            assertEquals(3, r.ataque().danoAplicado());
            assertEquals(8, de(r, "hielo").poder());
        }

        @Test
        @DisplayName("Cono de hielo (6): +2 x nivel al dano y resta (1d3) x nivel al ataque del enemigo dos turnos")
        void conoDeHielo() {
            ResultadoDeAccion r = jugar("Cono de hielo", "hielo", new AzarGuionado().dados(8).filas(1000).dados(2).dados(3),
                    heroe("hielo", "Mago Hielo", null, 4), heroe("armas", "Guerrero Armas", null, 4));
            assertEquals(10, r.ataque().danoAplicado(), "2 + 2 x 4");
            Contendiente armas = de(r, "armas");
            assertEquals(12, armas.sumaDe(TipoDeEfecto.PENALIZA_ATAQUE), "3 x 4");

            // Primer turno del enemigo: 40 + 6 - 12 = 34.
            ResultadoDeAccion primero = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(6),
                    armas, de(r, "hielo"));
            assertEquals(34, primero.ataque().ataqueResuelto());
            Contendiente trasUno = de(primero, "armas");
            assertEquals(12, trasUno.sumaDe(TipoDeEfecto.PENALIZA_ATAQUE), "le queda un turno");

            ResultadoDeAccion segundo = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(6),
                    trasUno, de(primero, "hielo"));
            assertEquals(34, segundo.ataque().ataqueResuelto());
            assertEquals(0, de(segundo, "armas").sumaDe(TipoDeEfecto.PENALIZA_ATAQUE), "ya no le afecta");
        }

        @Test
        @DisplayName("Bola de hielo (4): +2 x nivel al ataque y resta (0d4) x nivel al dano del enemigo")
        void bolaDeHielo() {
            ResultadoDeAccion r = jugar("Bola de hielo", "hielo", new AzarGuionado().dados(1).filas(1000).dados(1).enteros(4),
                    heroe("hielo", "Mago Hielo", null, 8), heroe("armas", "Guerrero Armas", null, 8));
            assertEquals(97, r.ataque().ataqueResuelto(), "80 + 1 + 16");
            Contendiente armas = de(r, "armas");
            assertEquals(32, armas.sumaDe(TipoDeEfecto.PENALIZA_DANO), "4 x 8");

            // El siguiente golpe del Armas pierde esos 32 puntos de dano (sin bajar de cero).
            ResultadoDeAccion golpe = jugar("ATAQUE_BASICO", "armas", new AzarGuionado().dados(6).filas(1000).dados(6),
                    armas, de(r, "hielo"));
            assertEquals(0, golpe.ataque().danoBase(), "6 - 32 no baja de cero");
            assertEquals(0, de(golpe, "armas").sumaDe(TipoDeEfecto.PENALIZA_DANO));
        }
    }

    @Nested
    @DisplayName("Picaro Veneno")
    class PicaroVeneno {

        @Test
        @DisplayName("Flor de loto (2): +(4d8) al dano")
        void florDeLoto() {
            ResultadoDeAccion r = jugar("Flor de loto", "veneno", new AzarGuionado().dados(5).filas(1000).dados(1, 1, 1, 1, 1),
                    heroe("veneno", "Pícaro Veneno", null), heroe("tanque", "Guerrero Tanque", null));
            assertEquals(5, r.ataque().danoAplicado(), "1 + (4)");
            assertEquals(6, de(r, "veneno").poder());
        }

        @Test
        @DisplayName("Agonia (4): +(2d9) x nivel de dano")
        void agonia() {
            ResultadoDeAccion r = jugar("Agonía", "veneno", new AzarGuionado().dados(10).filas(1000).dados(1, 1, 1),
                    heroe("veneno", "Pícaro Veneno", null, 4), heroe("tanque", "Guerrero Tanque", null, 4));
            assertEquals(9, r.ataque().danoAplicado(), "1 + (2) x 4");
            assertEquals(28, de(r, "veneno").poder());
        }

        @Test
        @DisplayName("Piquete (4): +1 al ataque por dos turnos, +2 al dano por uno")
        void piquete() {
            ResultadoDeAccion r = jugar("Piquete", "veneno", new AzarGuionado().dados(1).filas(1000).dados(1),
                    heroe("veneno", "Pícaro Veneno", null, 8), heroe("tanque", "Guerrero Tanque", null, 8));
            assertEquals(89, r.ataque().ataqueResuelto(), "80 + 1 + 8");
            assertEquals(17, r.ataque().danoAplicado(), "1 + 16");
            Contendiente veneno = de(r, "veneno");
            assertEquals(8, veneno.sumaDe(TipoDeEfecto.BONO_ATAQUE), "el segundo turno del +1 x 8");

            ResultadoDeAccion siguiente = jugar("ATAQUE_BASICO", "veneno", new AzarGuionado().dados(1).filas(1000).dados(1),
                    veneno, de(r, "tanque"));
            assertEquals(89, siguiente.ataque().ataqueResuelto(), "80 + 1 + 8 del segundo turno");
            assertEquals(0, de(siguiente, "veneno").sumaDe(TipoDeEfecto.BONO_ATAQUE));
        }
    }

    @Nested
    @DisplayName("Picaro Machete")
    class PicaroMachete {

        @Test
        @DisplayName("Cortada (2): +2 al dano por dos turnos")
        void cortada() {
            ResultadoDeAccion r = jugar("Cortada", "machete", new AzarGuionado().dados(2).filas(1000).dados(1),
                    heroe("machete", "Pícaro Machete", null), heroe("tanque", "Guerrero Tanque", null));
            assertEquals(3, r.ataque().danoAplicado(), "1 + 2");
            Contendiente machete = de(r, "machete");
            assertEquals(2, machete.sumaDe(TipoDeEfecto.BONO_DANO));

            ResultadoDeAccion siguiente = jugar("ATAQUE_BASICO", "machete", new AzarGuionado().dados(2).filas(1000).dados(1),
                    machete, de(r, "tanque"));
            assertEquals(3, siguiente.ataque().danoAplicado(), "el segundo turno de la Cortada");
            assertEquals(0, de(siguiente, "machete").sumaDe(TipoDeEfecto.BONO_DANO));
        }

        @Test
        @DisplayName("Machetazo (4): +(2d8) x nivel al dano +1 x nivel al ataque")
        void machetazo() {
            ResultadoDeAccion r = jugar("Machetazo", "machete", new AzarGuionado().dados(1).filas(1000).dados(1, 1, 1),
                    heroe("machete", "Pícaro Machete", null, 4), heroe("tanque", "Guerrero Tanque", null, 4));
            assertEquals(45, r.ataque().ataqueResuelto());
            assertEquals(9, r.ataque().danoAplicado(), "1 + (2) x 4");
        }

        @Test
        @DisplayName("Planazo (4): +(2d8) x nivel al ataque +1 x nivel al dano")
        void planazo() {
            ResultadoDeAccion r = jugar("Planazo", "machete", new AzarGuionado().dados(1, 1, 1).filas(1000).dados(1),
                    heroe("machete", "Pícaro Machete", null, 8), heroe("tanque", "Guerrero Tanque", null, 8));
            assertEquals(97, r.ataque().ataqueResuelto(), "80 + 1 + (2) x 8");
            assertEquals(9, r.ataque().danoAplicado(), "1 + 8");
        }
    }

    @Nested
    @DisplayName("Chaman")
    class Chaman {

        @Test
        @DisplayName("Toque de la Vida (2): Sanar + 2 a un companero")
        void toqueDeLaVida() {
            ResultadoDeAccion r = jugar("Toque de la Vida", "chaman", "armas", true, new AzarGuionado().dados(3),
                    heroe("chaman", "Chamán", 1), conVida(heroe("armas", "Guerrero Armas", 1), 10),
                    heroe("tanque", "Guerrero Tanque", 2));
            assertEquals(21, de(r, "armas").vida(), "10 + 6 + 3 + 2");
            assertEquals(8, de(r, "chaman").poder());
            assertEquals(List.of(new Afectado("armas", 10, 21)), r.afectados());
        }

        @Test
        @DisplayName("Toque de la Vida no cura a un rival")
        void noSeCuraAUnRival() {
            AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                    new SolicitudDeAccion("Toque de la Vida", "chaman", "tanque", true, List.of(
                            heroe("chaman", "Chamán", 1), heroe("tanque", "Guerrero Tanque", 2))),
                    new AzarGuionado()));
            assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, rechazo.motivo());
        }

        @Test
        @DisplayName("Vinculo Natural (4): +2 x nivel de sanacion por dos turnos")
        void vinculoNatural() {
            Contendiente chaman = conVida(heroe("chaman", "Chamán", null, 4), 50);
            ResultadoDeAccion r = jugar("Vínculo Natural", "chaman", new AzarGuionado().dados(1),
                    chaman, heroe("tanque", "Guerrero Tanque", null));
            assertEquals(83, de(r, "chaman").vida(), "50 + 24 + 1 + 8");
            assertEquals(8, de(r, "chaman").sumaDe(TipoDeEfecto.BONO_SANACION));
        }

        @Test
        @DisplayName("Canto del Bosque (6): sana a todo el grupo Sanar + (2d6) x nivel, y otra vez al empezar su turno")
        void cantoDelBosque() {
            ResultadoDeAccion r = jugar("Canto del Bosque", "chaman", null, true,
                    new AzarGuionado().dados(1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                    conVida(heroe("chaman", "Chamán", 1, 8), 100), conVida(heroe("armas", "Guerrero Armas", 1), 20),
                    conVida(heroe("tanque", "Guerrero Tanque", 2), 20));
            assertEquals(165, de(r, "chaman").vida(), "100 + 48 + 1 + (2) x 8");
            assertEquals(44, de(r, "armas").vida(), "20 + 65, hasta su maximo");
            assertEquals(20, de(r, "tanque").vida(), "el rival no");
            assertEquals(16, de(r, "armas").sumaDe(TipoDeEfecto.SANACION_POR_TURNO));
            assertEquals(74, de(r, "chaman").poder());

            ResultadoDeTurno turno = empezarTurno("armas", conVida(de(r, "armas"), 30), de(r, "chaman"), de(r, "tanque"));
            assertEquals(44, de(turno, "armas").vida(), "30 + 16 hasta el maximo");
            assertEquals(0, de(turno, "armas").sumaDe(TipoDeEfecto.SANACION_POR_TURNO), "dos turnos: ya acabo");
        }
    }

    @Nested
    @DisplayName("Medico")
    class Medico {

        @Test
        @DisplayName("Curacion Directa (2): Sanar + 2")
        void curacionDirecta() {
            ResultadoDeAccion r = jugar("Curación Directa", "medico", "armas", true, new AzarGuionado().dados(8),
                    heroe("medico", "Médico", 1), conVida(heroe("armas", "Guerrero Armas", 1), 10),
                    heroe("tanque", "Guerrero Tanque", 2));
            assertEquals(24, de(r, "armas").vida(), "10 + 4 + 8 + 2");
        }

        @Test
        @DisplayName("Neutralizacion de Efectos (4): +2 y +(2d4), por nivel")
        void neutralizacionDeEfectos() {
            ResultadoDeAccion r = jugar("Neutralización de Efectos", "medico", "armas", true,
                    new AzarGuionado().dados(1, 1, 1),
                    heroe("medico", "Médico", 1, 4), conVida(heroe("armas", "Guerrero Armas", 1, 4), 10),
                    heroe("tanque", "Guerrero Tanque", 2));
            assertEquals(43, de(r, "armas").vida(), "10 + 16 + 1 + (2 + 2) x 4");
            assertEquals(36, de(r, "medico").poder());
        }

        @Test
        @DisplayName("Reanimacion (todo el poder): toda la vida a un companero, tambien caido")
        void reanimacion() {
            ResultadoDeAccion r = jugar("Reanimación", "medico", "armas", true, new AzarGuionado(),
                    heroe("medico", "Médico", 1, 8), conVida(heroe("armas", "Guerrero Armas", 1), 0),
                    heroe("tanque", "Guerrero Tanque", 2));
            assertEquals(44, de(r, "armas").vida());
            assertEquals(0, de(r, "medico").poder(), "todos los puntos de poder");
            assertTrue(r.eventos().stream().anyMatch(e -> e.tipo() == TipoDeEvento.REANIMACION));
        }

        @Test
        @DisplayName("Reanimacion no es para uno mismo: «del companero»")
        void reanimacionNoEsParaUnoMismo() {
            AccionNoPermitida rechazo = assertThrows(AccionNoPermitida.class, () -> motor.resolver(
                    new SolicitudDeAccion("Reanimación", "medico", "medico", true, List.of(
                            heroe("medico", "Médico", 1, 8), heroe("armas", "Guerrero Armas", 1),
                            heroe("tanque", "Guerrero Tanque", 2))), new AzarGuionado()));
            assertEquals(MotivoDeRechazo.OBJETIVO_INVALIDO, rechazo.motivo());
        }
    }

    @Test
    @DisplayName("las veinticuatro acciones de la Tabla 7 tienen regla")
    void veinticuatroAcciones() {
        Reglamento reglamento = motor.reglamento();
        List<String> todas = List.of("Golpe con escudo", "Mano de piedra", "Defensa feroz",
                "Embate sangriento", "Lanza de los dioses", "Golpe de tormenta",
                "Misiles de magma", "Vulcano", "Pare de fuego",
                "Lluvia de hielo", "Cono de hielo", "Bola de hielo",
                "Flor de loto", "Agonía", "Piquete",
                "Cortada", "Machetazo", "Planazo",
                "Toque de la Vida", "Vínculo Natural", "Canto del Bosque",
                "Curación Directa", "Neutralización de Efectos", "Reanimación");
        assertEquals(24, todas.size());
        todas.forEach(nombre -> assertTrue(reglamento.conoce(nombre), nombre));
        assertFalse(reglamento.conoce("Rayo laser"));
    }
}
