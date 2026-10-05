package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.dominio.SalaNoEncontrada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Caso de uso de consulta de una sala — operacion {@code obtenerSala}.
 *
 * <p>Es el mas simple de los cuatro, y aun asi tiene una decision que probar:
 * una sala privada se devuelve igual que una publica. Ver una sala no es entrar
 * en ella, y el listado ya las muestra todas.
 */
@DisplayName("ObtenerSala · caso de uso")
class ObtenerSalaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private RepositorioDeSalasEnMemoria almacen;
    private RepositorioDePartidasEnMemoria partidas;
    private ObtenerSala obtener;

    @BeforeEach
    void preparar() {
        almacen = new RepositorioDeSalasEnMemoria();
        partidas = new RepositorioDePartidasEnMemoria();
        obtener = new ObtenerSala(almacen, partidas);
    }

    @Test
    @DisplayName("R18 · una sala en juego dice cual es su partida: recargar vuelve al combate")
    void laSalaEnJuegoDiceSuPartida() {
        Sala conIa = almacen.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION));
        Partida partida = new IniciarPartida(
                        almacen,
                        partidas,
                        new CanalDePartidaEspia(),
                        InventarioEnMemoria.conHeroe(),
                        Clock.fixed(Instant.parse("2026-09-24T17:00:00Z"), ZoneOffset.UTC))
                .ejecutar(conIa.id(), new JugadorAutenticado(ANFITRION, "anfitrion"));

        Sala enJuego = obtener.ejecutar(conIa.id());

        assertAll(
                () -> assertEquals(EstadoSala.EN_JUEGO, enJuego.estado()),
                () -> assertEquals(Optional.of(partida.id()), obtener.partidaDe(enJuego)));
    }

    @Test
    @DisplayName("R18 · una sala que no ha empezado no tiene partida, y ni se busca")
    void sinEmpezarNoHayPartida() {
        Sala abierta = almacen.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null), ANFITRION));

        assertEquals(Optional.empty(), obtener.partidaDe(abierta));
    }

    @Test
    @DisplayName("devuelve la sala tal como esta almacenada")
    void devuelveLaSala() {
        Sala guardada = almacen.guardar(Sala.crear(
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 320, true, false, null), ANFITRION));

        Sala sala = obtener.ejecutar(guardada.id());

        assertAll(
                () -> assertEquals(guardada.id(), sala.id()),
                () -> assertEquals(320, sala.recompensaCreditos()),
                // Anfitrion + la maquina: desde HU-SAL-004 la IA ocupa cupo.
                () -> assertEquals(2, sala.ocupacion()));
    }

    @Test
    @DisplayName("una sala privada tambien se puede consultar: lo que se protege es el ingreso")
    void laPrivadaSeConsulta() {
        Sala guardada = almacen.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, true, null), ANFITRION));

        Sala sala = obtener.ejecutar(guardada.id());

        assertAll(
                () -> assertEquals(guardada.id(), sala.id()),
                () -> assertNotNull(sala.codigoInvitacion(),
                        "el caso de uso no filtra el codigo; eso lo decide la API con el token"));
    }

    @Test
    @DisplayName("una sala que no existe responde 404 con su tipo")
    void salaInexistente() {
        SalaNoEncontrada error = assertThrows(SalaNoEncontrada.class,
                () -> obtener.ejecutar(UUID.randomUUID()));

        assertEquals(404, error.estado());
    }

    @Test
    @DisplayName("exige un identificador de sala")
    void exigeIdentificador() {
        assertThrows(NullPointerException.class, () -> obtener.ejecutar(null));
    }
}
