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
        abandonar = new AbandonarSala(almacen, canal);
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
}
