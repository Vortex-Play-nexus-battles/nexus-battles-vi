package com.nexusbattles.plataforma.salaspartidas.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
