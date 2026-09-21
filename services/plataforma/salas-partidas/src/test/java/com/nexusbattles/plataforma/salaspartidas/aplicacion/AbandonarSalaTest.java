package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalidaNoPermitida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caso de uso de salida de sala — operacion {@code abandonarSala} del contrato.
 *
 * <p>Espejo de {@code IngresarASalaTest}: aqui no se repiten las reglas de quien
 * puede salir —eso es {@code SalaTest}— sino la coordinacion. Lo que se prueba
 * es que se guarda antes de anunciar, que un rechazo no deja rastro en ninguna
 * de las dos partes, y que la salida queda escrita de verdad.
 */
@DisplayName("AbandonarSala · caso de uso")
class AbandonarSalaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITANTE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID AJENO = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private RepositorioDeSalasEnMemoria almacen;
    private CanalDeSalaEspia canal;
    private AbandonarSala abandonar;

    @BeforeEach
    void preparar() {
        almacen = new RepositorioDeSalasEnMemoria();
        canal = new CanalDeSalaEspia();
        abandonar = new AbandonarSala(almacen, canal, new CreditosEnMemoria());
    }

    /** Sala de dos cupos con el anfitrion y un visitante dentro. Queda LLENA. */
    private Sala salaConDos() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null),
                ANFITRION);
        sala.unirse(VISITANTE);
        return almacen.guardar(sala);
    }

    @Test
    @DisplayName("la salida se guarda y el cupo vuelve a estar libre")
    void guardaLaSalida() {
        Sala sala = salaConDos();

        abandonar.ejecutar(sala.id(), VISITANTE);

        Sala enAlmacen = almacen.buscarPorId(sala.id()).orElseThrow();
        assertAll(
                () -> assertEquals(1, enAlmacen.ocupacion()),
                () -> assertTrue(!enAlmacen.participantes().contains(VISITANTE)),
                () -> assertEquals(EstadoSala.ABIERTA, enAlmacen.estado(),
                        "deja de estar llena, si no el cupo libre seria invisible"));
    }

    @Test
    @DisplayName("anuncia la salida por el canal, con la ocupacion ya actualizada")
    void anunciaLaSalida() {
        Sala sala = salaConDos();

        abandonar.ejecutar(sala.id(), VISITANTE);

        assertEquals(
                java.util.List.of(new CanalDeSalaEspia.Anuncio(
                        CanalDeSalaEspia.SALIDA, sala.id(), VISITANTE, 1)),
                canal.anuncios());
    }

    @Test
    @DisplayName("una sala que no existe responde 404 y no anuncia nada")
    void salaInexistente() {
        assertThrows(SalaNoEncontrada.class,
                () -> abandonar.ejecutar(UUID.randomUUID(), VISITANTE));

        assertTrue(canal.noAnuncioNada());
    }

    @Test
    @DisplayName("quien no esta dentro no sale, y el canal no dice nada")
    void ajenoNoSale() {
        Sala sala = salaConDos();

        assertThrows(SalidaNoPermitida.class, () -> abandonar.ejecutar(sala.id(), AJENO));

        assertAll(
                () -> assertTrue(canal.noAnuncioNada()),
                () -> assertEquals(2, almacen.buscarPorId(sala.id()).orElseThrow().ocupacion(),
                        "un rechazo no cambia el aforo"));
    }

    @Test
    @DisplayName("el anfitrion recibe 409: su camino es cancelar")
    void elAnfitrionNoAbandona() {
        Sala sala = salaConDos();

        SalidaNoPermitida error = assertThrows(SalidaNoPermitida.class,
                () -> abandonar.ejecutar(sala.id(), ANFITRION));

        assertAll(
                () -> assertEquals(409, error.estado()),
                () -> assertTrue(canal.noAnuncioNada()));
    }

    @Test
    @DisplayName("exige sala y jugador")
    void exigeArgumentos() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> abandonar.ejecutar(null, VISITANTE)),
                () -> assertThrows(NullPointerException.class,
                        () -> abandonar.ejecutar(UUID.randomUUID(), null)));
    }

    /* HU-JUE-014, CA-03: quien se va antes de empezar recupera su apuesta. */
    @org.junit.jupiter.api.Nested
    @DisplayName("con apuesta (HU-JUE-014)")
    class ConApuesta {

        private CreditosEnMemoria creditos;

        @BeforeEach
        void conLibro() {
            creditos = new CreditosEnMemoria().conSaldo(VISITANTE, 500);
            abandonar = new AbandonarSala(almacen, canal, creditos);
        }

        /** Sala con apuesta, el visitante dentro y su reserva viva en el libro. */
        private Sala salaApostada() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 150, false, false, null), ANFITRION);
            UUID reserva = creditos.reservar(VISITANTE, 150, sala.id(), 0).id();
            sala.unirse(VISITANTE, new com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante(
                    "Visitante", InventarioEnMemoria.SOMBRA, reserva), null);
            return almacen.guardar(sala);
        }

        @Test
        @DisplayName("al salir, la reserva se libera y el saldo vuelve a estar disponible")
        void devuelveLaReserva() {
            Sala sala = salaApostada();
            assertEquals(350, creditos.disponibleDe(VISITANTE), "antes: comprometido");

            abandonar.ejecutar(sala.id(), VISITANTE);

            assertAll(
                    () -> assertEquals(500, creditos.disponibleDe(VISITANTE)),
                    () -> assertEquals(0, creditos.reservadoDe(VISITANTE)),
                    () -> assertTrue(!almacen.buscarPorId(sala.id()).orElseThrow().participantes().contains(VISITANTE)),
                    () -> assertEquals(1, canal.anuncios().size(), "la salida se anuncia igual"));
        }

        @Test
        @DisplayName("si el libro no responde, la salida se sostiene y la deuda queda en la bitacora")
        void elFalloAlLiberarNoDeshaceLaSalida() {
            Sala sala = salaApostada();
            creditos.fallaAlLiberar = true;

            abandonar.ejecutar(sala.id(), VISITANTE);

            assertAll(
                    () -> assertTrue(!almacen.buscarPorId(sala.id()).orElseThrow().participantes().contains(VISITANTE),
                            "quien salio no puede acabar creyendo que sigue dentro"),
                    () -> assertEquals(150, creditos.reservadoDe(VISITANTE), "la reserva sigue viva, para devolverla a mano"),
                    () -> assertEquals(1, canal.anuncios().size()));
        }

        @Test
        @DisplayName("un rechazo no toca el libro")
        void unRechazoNoLibera() {
            Sala sala = salaApostada();

            assertThrows(SalidaNoPermitida.class, () -> abandonar.ejecutar(sala.id(), AJENO));

            assertAll(
                    () -> assertEquals(150, creditos.reservadoDe(VISITANTE)),
                    () -> assertTrue(creditos.liberadas.isEmpty()));
        }
    }
}
