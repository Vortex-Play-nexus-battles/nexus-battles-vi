package com.nexusbattles.plataforma.salaspartidas.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de creacion de sala — HU-SAL-001.
 *
 * <p>Cada prueba cita el requisito que la obliga. Si una prueba no puede citar un
 * requisito, sobra: seria alcance inventado. Aqui vivio un tiempo la validacion
 * del nombre de sala, hasta que se comprobo que RF-JUE-001 no lo pide.
 *
 * <p>Tampoco se prueba el saldo: con reserva atomica eso no lo decide el dominio
 * sino el modulo de creditos, y se prueba en {@code CrearSalaTest}.
 */
@DisplayName("Sala · creacion (HU-SAL-001)")
class SalaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static ParametrosDeSala validos() {
        return new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 400, false, false, null);
    }

    @Nested
    @DisplayName("RF-JUE-001 · parametros de creacion")
    class Creacion {

        @Test
        @DisplayName("una sala valida nace abierta, con el anfitrion dentro")
        void salaValida() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertAll(
                    () -> assertEquals(EstadoSala.ABIERTA, sala.estado()),
                    () -> assertEquals(4, sala.maximoParticipantes()),
                    () -> assertEquals(400, sala.recompensaCreditos()),
                    () -> assertEquals(ANFITRION, sala.idAnfitrion()),
                    () -> assertEquals(1, sala.ocupacion(), "solo el anfitrion al crearla"),
                    () -> assertNotNull(sala.id(), "toda sala nace con identificador"),
                    () -> assertNotNull(sala.creadaEn(), "toda sala nace con fecha"));
        }

        @Test
        @DisplayName("guarda si se incluye un heroe controlado por la IA")
        void heroeDeIa() {
            ParametrosDeSala con = new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, true, false, null);

            assertEquals(true, Sala.crear(con, ANFITRION).incluirHeroeIA());
        }

        @Test
        @DisplayName("una sala privada nace en estado PRIVADA, fuera del listado publico")
        void salaPrivada() {
            ParametrosDeSala privada = new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, true, null);

            assertEquals(EstadoSala.PRIVADA, Sala.crear(privada, ANFITRION).estado());
        }

        @Test
        @DisplayName("sin parametros no hay sala que crear")
        void exigeParametros() {
            assertThrows(NullPointerException.class, () -> Sala.crear(null, ANFITRION));
        }

        @Test
        @DisplayName("sin anfitrion la sala no tendria dueno")
        void exigeAnfitrion() {
            assertThrows(NullPointerException.class, () -> Sala.crear(validos(), null));
        }

        @Test
        @DisplayName("sin modalidad no se sabe cuantos jugadores caben")
        void exigeModalidad() {
            ParametrosDeSala sinModalidad = new ParametrosDeSala(4, null, 0, false, false, null);

            assertThrows(NullPointerException.class, () -> Sala.crear(sinModalidad, ANFITRION));
        }
    }

    @Nested
    @DisplayName("RF-JUE-004 · modalidades y limites de participantes")
    class Modalidades {

        @Test
        @DisplayName("menos de dos participantes no es una partida")
        void menosDeDos() {
            ParametrosDeSala uno = new ParametrosDeSala(1, Modalidad.HASTA_SEIS, 0, false, false, null);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(uno, ANFITRION));
            assertEquals("maximoParticipantes", error.errores().get(0).campo());
        }

        @Test
        @DisplayName("el maximo del juego son seis participantes")
        void masDeSeis() {
            ParametrosDeSala siete = new ParametrosDeSala(7, Modalidad.HASTA_SEIS, 0, false, false, null);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(siete, ANFITRION));
        }

        @Test
        @DisplayName("uno contra uno son exactamente dos")
        void duelo() {
            ParametrosDeSala cuatroEnDuelo =
                    new ParametrosDeSala(4, Modalidad.UNO_CONTRA_UNO, 0, false, false, null);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(cuatroEnDuelo, ANFITRION));
        }

        @Test
        @DisplayName("contra la IA tambien son exactamente dos")
        void contraIa() {
            ParametrosDeSala seisContraIa =
                    new ParametrosDeSala(6, Modalidad.CONTRA_IA, 0, false, false, null);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(seisContraIa, ANFITRION));
        }

        @Test
        @DisplayName("los equipos no pasan de tres integrantes")
        void equipoDemasiadoGrande() {
            ParametrosDeSala equipoDeCuatro =
                    new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, 4);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(equipoDeCuatro, ANFITRION));
            assertEquals("tamanoEquipo", error.errores().get(0).campo());
        }

        @Test
        @DisplayName("solo la modalidad de hasta seis admite equipos")
        void equipoEnDuelo() {
            ParametrosDeSala dueloConEquipo =
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, 2);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(dueloConEquipo, ANFITRION));
        }

        @Test
        @DisplayName("un duelo bien formado se crea sin problema")
        void dueloValido() {
            ParametrosDeSala duelo =
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null);

            assertEquals(Modalidad.UNO_CONTRA_UNO, Sala.crear(duelo, ANFITRION).modalidad());
        }
    }

    @Nested
    @DisplayName("RF-JUE-014 · recompensa como parametro")
    class Recompensa {

        @Test
        @DisplayName("la recompensa no puede ser negativa")
        void recompensaNegativa() {
            ParametrosDeSala negativa =
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, -1, false, false, null);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(negativa, ANFITRION));
            assertEquals("recompensaCreditos", error.errores().get(0).campo());
        }

        @Test
        @DisplayName("una sala sin recompensa es valida: apostar es libre")
        void sinRecompensa() {
            ParametrosDeSala gratis =
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null);

            assertEquals(0, Sala.crear(gratis, ANFITRION).recompensaCreditos());
        }
    }

    /**
     * HU-SAL-002 · RF-JUE-002 — ingreso a una sala existente.
     *
     * <p>Los tres criterios de aceptacion del issue #30:
     * <ol>
     *   <li>«El sistema verifica cupos y condiciones antes de admitir.»</li>
     *   <li>«El estado de la sala se actualiza para todos los participantes.»
     *       — la difusion es del canal; aqui se prueba que el ESTADO cambie.</li>
     *   <li>«Sala llena, cerrada o iniciada rechaza el ingreso.»</li>
     * </ol>
     *
     * <p>Se modelan participantes y no solo un contador porque el esquema
     * {@code Sala} del contrato exige el arreglo {@code participantes}, y
     * porque sin identidad no se puede impedir que el mismo jugador ocupe dos
     * cupos.
     *
     * <p>NO se prueba aqui el heroe equipado (HU-SAL-003, depende del modulo de
     * Thomas) ni los creditos de la apuesta (depende del modulo de Santiago).
     */
    @Nested
    @DisplayName("RF-JUE-002 · ingreso a una sala existente")
    class Ingreso {

        private static final UUID VISITANTE =
                UUID.fromString("55555555-5555-5555-5555-555555555555");
        private static final UUID OTRO =
                UUID.fromString("66666666-6666-6666-6666-666666666666");

        /**
         * Sala de dos cupos en el estado que pida la prueba.
         *
         * <p>El anfitrion lo anade el propio constructor, asi que aqui solo se
         * rellenan los cupos que falten hasta la ocupacion pedida.
         */
        private Sala salaEn(EstadoSala estado, int ocupacion) {
            Set<UUID> dentro = new LinkedHashSet<>();
            for (int i = 1; i < ocupacion; i++) {
                dentro.add(UUID.randomUUID());
            }
            return Sala.rehidratar(UUID.randomUUID(), estado, Modalidad.UNO_CONTRA_UNO,
                    2, 0, false, false, null, ANFITRION, dentro, Instant.now());
        }

        @Test
        @DisplayName("el anfitrion cuenta como participante desde que se crea la sala")
        void elAnfitrionYaEstaDentro() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertTrue(sala.participantes().contains(ANFITRION));
        }

        @Test
        @DisplayName("un jugador entra y la sala pasa a tener dos ocupantes")
        void entraUnJugador() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            sala.unirse(VISITANTE);

            assertAll(
                    () -> assertEquals(2, sala.ocupacion()),
                    () -> assertTrue(sala.participantes().contains(VISITANTE)),
                    () -> assertEquals(EstadoSala.ABIERTA, sala.estado(), "aun quedan cupos"));
        }

        @Test
        @DisplayName("al ocuparse el ultimo cupo la sala pasa a LLENA")
        void seLlena() {
            ParametrosDeSala duelo =
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null);
            Sala sala = Sala.crear(duelo, ANFITRION);

            sala.unirse(VISITANTE);

            assertEquals(EstadoSala.LLENA, sala.estado());
        }

        @Test
        @DisplayName("una sala llena rechaza el ingreso")
        void salaLlena() {
            Sala sala = salaEn(EstadoSala.LLENA, 2);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("una partida ya iniciada rechaza el ingreso")
        void partidaIniciada() {
            Sala sala = salaEn(EstadoSala.EN_JUEGO, 2);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("una sala cancelada rechaza el ingreso")
        void salaCancelada() {
            Sala sala = salaEn(EstadoSala.CANCELADA, 1);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("una sala finalizada rechaza el ingreso")
        void salaFinalizada() {
            Sala sala = salaEn(EstadoSala.FINALIZADA, 2);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("el mismo jugador no puede ocupar dos cupos")
        void ingresoRepetido() {
            Sala sala = Sala.crear(validos(), ANFITRION);
            sala.unirse(VISITANTE);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("el anfitrion no puede volver a entrar en su propia sala")
        void elAnfitrionNoSeUneDosVeces() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(ANFITRION));
        }

        @Test
        @DisplayName("el rechazo es un conflicto: 409, como fija el contrato")
        void elRechazoEs409() {
            Sala sala = salaEn(EstadoSala.LLENA, 2);

            IngresoNoPermitido error =
                    assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
            assertEquals(409, error.estado());
        }

        @Test
        @DisplayName("una sala privada rechaza con 403, no con el 409 de los conflictos")
        void salaPrivada() {
            Sala sala = salaEn(EstadoSala.PRIVADA, 1);

            SalaPrivadaSinInvitacion error =
                    assertThrows(SalaPrivadaSinInvitacion.class, () -> sala.unirse(OTRO));

            assertAll(
                    () -> assertEquals(403, error.estado(), "lo fija el contrato"),
                    () -> assertTrue(error.detalle().toLowerCase().contains("invitacion"),
                            "el motivo tiene que nombrar la invitacion, no dejar adivinar"));
        }

        @Test
        @DisplayName("sin jugador identificado no hay ingreso")
        void exigeJugador() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertThrows(NullPointerException.class, () -> sala.unirse(null));
        }
    }
}
