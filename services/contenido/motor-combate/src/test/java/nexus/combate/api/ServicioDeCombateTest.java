package nexus.combate.api;

import nexus.combate.IndiceNormal;
import nexus.combate.reglas.AccionNoPermitida;
import nexus.combate.reglas.CatalogoDePrueba;
import nexus.combate.reglas.EstadoDeAccion;
import nexus.combate.reglas.MotivoDeRechazo;
import nexus.combate.reglas.MotorDeAcciones;
import nexus.combate.reglas.Reglamento;
import nexus.combate.reglas.TipoDeEfecto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /combate/acciones} y {@code /turnos} sin HTTP: la traduccion del
 * contrato, los valores por omision, la validacion de forma y el generador.
 * Las reglas del combate ya estan probadas en {@code nexus.combate.reglas}.
 */
@DisplayName("ServicioDeCombate · contrato 1.2.0 de acciones y turnos")
class ServicioDeCombateTest {

    private final AtomicInteger generadoresDeProduccion = new AtomicInteger();
    private final ServicioDeCombate servicio = new ServicioDeCombate(
            new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision()),
            () -> {
                generadoresDeProduccion.incrementAndGet();
                return new Random(123);
            });

    /** Un combatiente como lo manda salas al empezar: solo lo imprescindible. */
    private static EstadoDeCombatiente nuevo(String id, String prototipo, int vida) {
        return new EstadoDeCombatiente(id, null, prototipo, null, null, vida, null, null, null, null, null, null,
                null, null, null);
    }

    private static PeticionDeAccion accion(String accion, String ejecutor, String objetivo, Long semilla,
                                           EstadoDeCombatiente... combatientes) {
        return new PeticionDeAccion(accion, ejecutor, objetivo, null, List.of(combatientes), semilla);
    }

    private static EstadoDeCombatiente de(RespuestaDeAccion r, String id) {
        return r.combatientes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static EstadoDeAccion la(EstadoDeCombatiente c, String codigo) {
        return c.acciones().stream().filter(a -> a.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("un combatiente minimo sale completo: nivel 1, poder maximo, estadisticas y acciones")
    void valoresPorOmision() {
        RespuestaDeAccion r = servicio.resolver(accion(Reglamento.ATAQUE_BASICO, "armas", "tanque", 7L,
                nuevo("armas", "Guerrero Armas", 44), nuevo("tanque", "Guerrero Tanque", 44)));

        EstadoDeCombatiente armas = de(r, "armas");
        assertAll(
                () -> assertEquals(Reglamento.ATAQUE_BASICO, r.accionEjecutada()),
                () -> assertFalse(r.enValorBase()),
                () -> assertEquals("tanque", r.objetivo()),
                () -> assertEquals(1, armas.nivel()),
                () -> assertEquals(8, armas.estadisticas().poder(), "Tabla 6: poder del Guerrero Armas"),
                () -> assertEquals(8, armas.poderActual(), "nulo es el maximo; el basico no cuesta"),
                () -> assertEquals(1, armas.turnosJugados()),
                () -> assertEquals(10, armas.estadisticas().ataque().base()),
                () -> assertTrue(la(armas, Reglamento.ATAQUE_BASICO).disponible()),
                () -> assertTrue(armas.recargas().isEmpty()),
                () -> assertTrue(de(r, "tanque").vidaActual() <= 44));
    }

    @Test
    @DisplayName("una accion especial paga su poder, entra en carga y lo dice en recargas y acciones")
    void accionEspecial() {
        RespuestaDeAccion r = servicio.resolver(accion("Golpe con escudo", "tanque", null, 3L,
                nuevo("tanque", "Guerrero Tanque", 44), nuevo("armas", "Guerrero Armas", 44)));

        EstadoDeCombatiente tanque = de(r, "tanque");
        assertAll(
                () -> assertEquals("Golpe con escudo", r.accionEjecutada()),
                () -> assertEquals(8, tanque.poderActual(), "10 - 2"),
                () -> assertEquals(Map.of("Golpe con escudo", 0), tanque.cargas()),
                () -> assertEquals(Map.of("Golpe con escudo", 1), tanque.recargas()),
                () -> assertEquals("En carga: 1 turno.", la(tanque, "Golpe con escudo").motivo()),
                () -> assertTrue(r.ataque() != null && r.ataque().ataqueResuelto() > 0));
    }

    @Test
    @DisplayName("el estado completo va y vuelve: estadisticas, efectos, cargas, equipo, epicas y ultimo golpe")
    void estadoCompletoIdaYVuelta() {
        EstadoDeCombatiente tanque = new EstadoDeCombatiente("tanque", 1, "Guerrero Tanque", 4,
                new EstadoDeCombatiente.EstadisticasDeCombate(40, 176, 45,
                        new EstadoDeCombatiente.FormulaDetalle(40, 1, 6),
                        new EstadoDeCombatiente.FormulaDetalle(0, 1, 4), null),
                100, 10, 3,
                Map.of("Golpe con escudo", 2),
                List.of(new EstadoDeCombatiente.Efecto("CORTADA", "Cortada", TipoDeEfecto.BONO_DANO, 2, 1, null,
                        "tanque")),
                List.of("Espada de una mano"), List.of("Golpe de defensa"),
                new EstadoDeCombatiente.GolpeRecibido("armas", 5), null, null);

        RespuestaDeAccion r = servicio.resolver(new PeticionDeAccion("Mano de piedra", "tanque", null, true,
                List.of(tanque, nuevo("armas", "Guerrero Armas", 44)), 1L));

        EstadoDeCombatiente despues = de(r, "tanque");
        EstadoDeCombatiente.Efecto mano = despues.efectos().stream()
                .filter(e -> e.codigo().equals("MANO_DE_PIEDRA")).findFirst().orElseThrow();
        assertAll(
                () -> assertEquals("tanque", r.objetivo(), "una defensa va a uno mismo"),
                () -> assertNull(r.ataque(), "una defensa no golpea"),
                () -> assertEquals(45, despues.estadisticas().defensa(), "las estadisticas enviadas mandan"),
                () -> assertEquals(1, despues.equipo()),
                () -> assertEquals(6, despues.poderActual(), "10 - 4"),
                () -> assertEquals(4, despues.turnosJugados()),
                () -> assertEquals(48, mano.valor(), "+12 por el multiplicador de nivel 4 (§6.1.2)"),
                () -> assertTrue(mano.hastaSuTurno(), "una proteccion dura hasta su proximo turno"),
                () -> assertTrue(despues.efectos().stream().noneMatch(e -> e.codigo().equals("CORTADA")),
                        "el bono propio se gasto con la accion"),
                () -> assertEquals(List.of("Espada de una mano"), despues.equipamiento()),
                () -> assertEquals(List.of("Golpe de defensa"), despues.epicas()),
                () -> assertEquals("armas", despues.ultimoDanoRecibido().de()),
                () -> assertEquals(5, despues.ultimoDanoRecibido().cantidad()),
                () -> assertEquals(Map.of("Golpe con escudo", 2, "Mano de piedra", 3), despues.cargas()),
                () -> assertTrue(la(despues, "Golpe de defensa").esEpica()),
                () -> assertTrue(r.afectados().isEmpty(), "nadie perdio ni gano vida"));
    }

    @Test
    @DisplayName("una accion que no se puede jugar sube como AccionNoPermitida con su motivo")
    void rechazo() {
        AccionNoPermitida error = assertThrows(AccionNoPermitida.class, () -> servicio.resolver(
                accion("Mano de piedra", "tanque", null, 1L,
                        nuevo("tanque", "Guerrero Tanque", 44), nuevo("armas", "Guerrero Armas", 44))));
        assertEquals(MotivoDeRechazo.BLOQUEADA_POR_NIVEL, error.motivo());
    }

    @Test
    @DisplayName("la misma semilla da el mismo combate; sin semilla se usa el generador de produccion")
    void generador() {
        PeticionDeAccion conSemilla = accion(Reglamento.ATAQUE_BASICO, "armas", null, 42L,
                nuevo("armas", "Guerrero Armas", 44), nuevo("tanque", "Guerrero Tanque", 44));
        assertEquals(servicio.resolver(conSemilla), servicio.resolver(conSemilla));
        assertEquals(0, generadoresDeProduccion.get(), "con semilla no se toca el de produccion");

        servicio.resolver(accion(Reglamento.ATAQUE_BASICO, "armas", null, null,
                nuevo("armas", "Guerrero Armas", 44), nuevo("tanque", "Guerrero Tanque", 44)));
        assertEquals(1, generadoresDeProduccion.get());
    }

    @Test
    @DisplayName("un objetivo en blanco es «sin objetivo»: con un solo rival, ese")
    void objetivoEnBlanco() {
        RespuestaDeAccion r = servicio.resolver(accion(Reglamento.ATAQUE_BASICO, "armas", "  ", 5L,
                nuevo("armas", "Guerrero Armas", 44), nuevo("tanque", "Guerrero Tanque", 44)));
        assertEquals("tanque", r.objetivo());
    }

    @Test
    @DisplayName("el constructor publico usa SecureRandom y resuelve igual")
    void constructorDeProduccion() {
        ServicioDeCombate deProduccion = new ServicioDeCombate(
                new MotorDeAcciones(new CatalogoDePrueba(), IndiceNormal.porOmision()));
        RespuestaDeAccion r = deProduccion.resolver(accion(Reglamento.ATAQUE_BASICO, "armas", null, null,
                nuevo("armas", "Guerrero Armas", 44), nuevo("tanque", "Guerrero Tanque", 44)));
        assertTrue(r.ataque().ataqueResuelto() >= 11 && r.ataque().ataqueResuelto() <= 16);
    }

    @Nested
    @DisplayName("El comienzo de un turno")
    class Turnos {

        @Test
        @DisplayName("recupera dos de poder y devuelve el estado de todos")
        void recuperaPoder() {
            EstadoDeCombatiente tanque = new EstadoDeCombatiente("tanque", null, "Guerrero Tanque", 1, null, 44, 2,
                    1, null, null, null, null, null, null, null);
            RespuestaDeTurno r = servicio.iniciarTurno(new PeticionDeTurno("tanque", false,
                    List.of(tanque, nuevo("armas", "Guerrero Armas", 44)), null));

            assertEquals("tanque", r.combatiente());
            EstadoDeCombatiente despues = r.combatientes().get(0);
            assertEquals(4, despues.poderActual());
            assertTrue(la(despues, "Golpe con escudo").disponible());
            assertTrue(r.afectados().isEmpty());
            assertEquals(1, generadoresDeProduccion.get());
        }

        @Test
        @DisplayName("un solo combatiente basta para empezar un turno")
        void unoBasta() {
            RespuestaDeTurno r = servicio.iniciarTurno(new PeticionDeTurno("tanque", null,
                    List.of(nuevo("tanque", "Guerrero Tanque", 44)), 9L));
            assertEquals(1, r.combatientes().size());
        }

        @Test
        @DisplayName("una peticion de turno a medias es PeticionInvalida")
        void aMedias() {
            assertAll(
                    () -> assertThrows(PeticionInvalida.class, () -> servicio.iniciarTurno(null)),
                    () -> assertThrows(PeticionInvalida.class, () -> servicio.iniciarTurno(
                            new PeticionDeTurno(" ", null, List.of(nuevo("t", "Guerrero Tanque", 44)), null))),
                    () -> assertThrows(PeticionInvalida.class, () -> servicio.iniciarTurno(
                            new PeticionDeTurno("t", null, List.of(), null))));
        }
    }

    @Nested
    @DisplayName("La forma de la peticion se valida antes de las reglas")
    class Validacion {

        private final EstadoDeCombatiente tanque = nuevo("tanque", "Guerrero Tanque", 44);

        private void rechaza(Consumer<List<EstadoDeCombatiente>> ajuste, String mensaje) {
            List<EstadoDeCombatiente> combatientes = new ArrayList<>(List.of(nuevo("armas", "Guerrero Armas", 44),
                    tanque));
            ajuste.accept(combatientes);
            assertThrows(PeticionInvalida.class, () -> servicio.resolver(new PeticionDeAccion(
                    Reglamento.ATAQUE_BASICO, "armas", null, false, combatientes, 1L)), mensaje);
        }

        private static EstadoDeCombatiente con(EstadoDeCombatiente.EstadisticasDeCombate estadisticas,
                                               Map<String, Integer> cargas,
                                               List<EstadoDeCombatiente.Efecto> efectos,
                                               List<String> equipamiento,
                                               EstadoDeCombatiente.GolpeRecibido golpe) {
            return new EstadoDeCombatiente("armas", null, "Guerrero Armas", 1, estadisticas, 44, null, null, cargas,
                    efectos, equipamiento, null, golpe, null, null);
        }

        @Test
        @DisplayName("sin cuerpo, sin accion o sin ejecutor")
        void cabecera() {
            List<EstadoDeCombatiente> dos = List.of(nuevo("armas", "Guerrero Armas", 44), tanque);
            assertAll(
                    () -> assertThrows(PeticionInvalida.class, () -> servicio.resolver(null)),
                    () -> assertThrows(PeticionInvalida.class, () -> servicio.resolver(
                            new PeticionDeAccion(" ", "armas", null, null, dos, 1L))),
                    () -> assertThrows(PeticionInvalida.class, () -> servicio.resolver(
                            new PeticionDeAccion(Reglamento.ATAQUE_BASICO, null, null, null, dos, 1L))));
        }

        @Test
        @DisplayName("menos de dos, mas de seis o un hueco en la lista")
        void cuantos() {
            assertAll(
                    () -> rechaza(l -> l.remove(1), "uno solo no combate"),
                    () -> rechaza(l -> {
                        for (int i = 0; i < 5; i++) {
                            l.add(nuevo("extra" + i, "Guerrero Tanque", 44));
                        }
                    }, "siete son demasiados (§6.1.3: hasta seis)"),
                    () -> rechaza(l -> l.add(null), "un combatiente vacio"),
                    () -> assertThrows(PeticionInvalida.class, () -> servicio.resolver(new PeticionDeAccion(
                            Reglamento.ATAQUE_BASICO, "armas", null, false, null, 1L))));
        }

        @Test
        @DisplayName("un combatiente sin vida actual, o con piezas incompletas")
        void piezas() {
            Map<String, Integer> cargaNula = new HashMap<>();
            cargaNula.put("Embate sangriento", null);
            List<String> equipoConHueco = new ArrayList<>();
            equipoConHueco.add(" ");
            assertAll(
                    () -> rechaza(l -> l.set(0, new EstadoDeCombatiente("armas", null, "Guerrero Armas", 1, null,
                            null, null, null, null, null, null, null, null, null, null)), "sin vida actual"),
                    () -> rechaza(l -> l.set(0, con(new EstadoDeCombatiente.EstadisticasDeCombate(8, null, 11,
                            null, null, null), null, null, null, null)), "estadisticas sin vida"),
                    () -> rechaza(l -> l.set(0, con(new EstadoDeCombatiente.EstadisticasDeCombate(8, 44, 11,
                            new EstadoDeCombatiente.FormulaDetalle(10, null, 6), null, null), null, null, null,
                            null)), "formula a medias"),
                    () -> rechaza(l -> l.set(0, con(null, cargaNula, null, null, null)), "carga nula"),
                    () -> rechaza(l -> l.set(0, con(null, Map.of("Embate sangriento", -1), null, null, null)),
                            "carga negativa"),
                    () -> rechaza(l -> l.set(0, con(null, null, List.of(new EstadoDeCombatiente.Efecto("X", "X",
                            null, 1, 1, null, null)), null, null)), "efecto sin tipo"),
                    () -> rechaza(l -> l.set(0, con(null, null, Collections.singletonList(null), null, null)),
                            "efecto vacio"),
                    () -> rechaza(l -> l.set(0, con(null, null, null, equipoConHueco, null)), "equipo vacio"),
                    () -> rechaza(l -> l.set(0, con(null, null, null, null,
                            new EstadoDeCombatiente.GolpeRecibido("tanque", null))), "golpe sin cantidad"));
        }

        @Test
        @DisplayName("las cotas del juego las valida el dominio: nivel 9 o ids repetidos")
        void cotasDelDominio() {
            EstadoDeCombatiente nivelNueve = new EstadoDeCombatiente("armas", null, "Guerrero Armas", 9, null, 44,
                    null, null, null, null, null, null, null, null, null);
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> servicio.resolver(
                            accion(Reglamento.ATAQUE_BASICO, "armas", null, 1L, nivelNueve, tanque))),
                    () -> assertThrows(IllegalArgumentException.class, () -> servicio.resolver(
                            accion(Reglamento.ATAQUE_BASICO, "tanque", null, 1L, tanque, tanque))));
        }
    }
}
