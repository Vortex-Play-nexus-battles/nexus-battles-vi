package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoVerificacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La puerta de heroe — HU-SAL-003, RF-JUE-003, SCRUM-1074.
 *
 * <p>La verificacion previa (`VerificarHeroe`) ya estaba probada: avisa, pero no
 * impide nada. Esto prueba la puerta con efectos, en los tres sitios donde tiene
 * que estar cerrada, porque una sola de las tres abierta basta para meter en
 * combate a alguien sin heroe.
 *
 * <p>Se prueba a traves de los casos de uso reales y no de la clase auxiliar: lo
 * que importa no es que exista un metodo, sino que <b>los tres caminos</b> lo
 * usen y que el rechazo no deje efectos a medias.
 */
@DisplayName("Puerta de heroe · nadie entra ni empieza sin heroe (SCRUM-1074)")
class PuertaDeHeroeTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VISITANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-09-18T10:00:00Z");

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDeSalaEspia canalDeSala = new CanalDeSalaEspia();
    private final CanalDePartidaEspia canalDePartida = new CanalDePartidaEspia();
    private final CreditosAnotados creditos = new CreditosAnotados();

    /**
     * Creditos que siempre alcanzan y anotan cada reserva. Lo que se comprueba
     * con el es que la puerta va ANTES: si rechaza, esta lista queda vacia.
     */
    private static final class CreditosAnotados
            implements com.nexusbattles.plataforma.salaspartidas.aplicacion.CreditosDelJugador {

        private final java.util.List<UUID> reservas = new java.util.ArrayList<>();

        @Override
        public ReservaDeCreditos reservar(UUID idJugador, int creditos, UUID idSala) {
            reservas.add(idSala);
            return new ReservaDeCreditos(UUID.randomUUID(), creditos);
        }

        @Override
        public void liberar(UUID idReserva) {
            // Nada que devolver: en estas pruebas o se reserva o no se llega.
        }
    }

    private static JugadorAutenticado como(UUID id) {
        return new JugadorAutenticado(id, "jugador-" + id.toString().substring(0, 8));
    }

    private static ParametrosDeSala parametros() {
        return new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null);
    }

    /** Sala abierta ya guardada, creada sin pasar por el caso de uso. */
    private Sala salaAbierta() {
        return salas.guardar(Sala.crear(parametros(), ANFITRION));
    }

    private CrearSala crear(InventarioEnMemoria inventario) {
        return new CrearSala(salas, creditos, inventario);
    }

    private IngresarASala ingresar(InventarioEnMemoria inventario) {
        return new IngresarASala(salas, canalDeSala, inventario);
    }

    private IniciarPartida iniciar(InventarioEnMemoria inventario) {
        return new IniciarPartida(salas, partidas, canalDePartida, inventario,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    // =====================================================================
    // Las tres puertas rechazan
    // =====================================================================

    @Nested
    @DisplayName("sin heroe equipado")
    class SinHeroeEquipado {

        private final InventarioEnMemoria inventario = InventarioEnMemoria.sinHeroe();

        @Test
        @DisplayName("no se puede crear una sala")
        void noPuedeCrear() {
            HeroeNoDisponible error = assertThrows(HeroeNoDisponible.class,
                    () -> crear(inventario).ejecutar(parametros(), como(ANFITRION)));

            assertAll(
                    () -> assertEquals(ResultadoVerificacion.SIN_HEROE_EQUIPADO, error.resultado()),
                    // Los dos tipos y el 422 salen del contrato, no del codigo.
                    () -> assertEquals(HeroeNoDisponible.SIN_EQUIPAR, error.tipo()),
                    () -> assertEquals(422, error.estado()));
        }

        @Test
        @DisplayName("no se puede entrar a una sala")
        void noPuedeEntrar() {
            Sala sala = salaAbierta();

            assertThrows(HeroeNoDisponible.class,
                    () -> ingresar(inventario).ejecutar(sala.id(), como(VISITANTE)));
        }

        @Test
        @DisplayName("no se puede arrancar el combate")
        void noPuedeEmpezar() {
            Sala sala = salaAbierta();
            sala.unirse(VISITANTE);
            salas.guardar(sala);

            assertThrows(HeroeNoDisponible.class,
                    () -> iniciar(inventario).ejecutar(sala.id(), como(ANFITRION)));
        }
    }

    @Nested
    @DisplayName("con el heroe ya comprometido en otra batalla")
    class HeroeOcupado {

        private final InventarioEnMemoria inventario =
                InventarioEnMemoria.conHeroeOcupado("Torre del Alba");

        @Test
        @DisplayName("el rechazo nombra donde esta el heroe, para que se pueda arreglar")
        void diceDondeEsta() {
            Sala sala = salaAbierta();

            HeroeNoDisponible error = assertThrows(HeroeNoDisponible.class,
                    () -> ingresar(inventario).ejecutar(sala.id(), como(VISITANTE)));

            assertAll(
                    () -> assertEquals(ResultadoVerificacion.HEROE_OCUPADO, error.resultado()),
                    () -> assertEquals(HeroeNoDisponible.OCUPADO, error.tipo()),
                    () -> assertEquals(422, error.estado()),
                    () -> assertTrue(error.detalle().contains("Torre del Alba"), error.detalle()));
        }
    }

    // =====================================================================
    // Un rechazo no deja nada a medias
    // =====================================================================

    @Test
    @DisplayName("rechazar el ingreso no mete al jugador ni anuncia nada")
    void elRechazoNoDejaEfectos() {
        Sala sala = salaAbierta();

        assertThrows(HeroeNoDisponible.class,
                () -> ingresar(InventarioEnMemoria.sinHeroe()).ejecutar(sala.id(), como(VISITANTE)));

        assertAll(
                () -> assertEquals(1, salas.buscarPorId(sala.id()).orElseThrow().ocupacion()),
                () -> assertTrue(canalDeSala.anuncios().isEmpty(), "no se anuncia un ingreso que no paso"));
    }

    @Test
    @DisplayName("rechazar la creacion no reserva creditos: devolverlos podria fallar")
    void elRechazoNoReservaCreditos() {
        ParametrosDeSala conApuesta =
                new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 320, false, false, null);

        assertThrows(HeroeNoDisponible.class,
                () -> crear(InventarioEnMemoria.sinHeroe()).ejecutar(conApuesta, como(ANFITRION)));

        assertTrue(creditos.reservas.isEmpty(), "la puerta va antes que la reserva");
    }

    @Test
    @DisplayName("rechazar el arranque no deja la sala en juego ni crea partida")
    void elRechazoNoArrancaNada() {
        Sala sala = salaAbierta();
        sala.unirse(VISITANTE);
        salas.guardar(sala);

        assertThrows(HeroeNoDisponible.class,
                () -> iniciar(InventarioEnMemoria.sinHeroe()).ejecutar(sala.id(), como(ANFITRION)));

        assertAll(
                () -> assertTrue(partidas.buscarPorSala(sala.id()).isEmpty()),
                () -> assertTrue(canalDePartida.anuncios.isEmpty()));
    }

    // =====================================================================
    // Un inventario caido NO abre la puerta
    // =====================================================================

    @Test
    @DisplayName("si el inventario no contesta, nadie entra: el fallo sube tal cual")
    void inventarioCaidoNoAbreLaPuerta() {
        Sala sala = salaAbierta();

        assertAll(
                () -> assertThrows(InventarioNoDisponible.class,
                        () -> crear(InventarioEnMemoria.caido()).ejecutar(parametros(), como(ANFITRION))),
                () -> assertThrows(InventarioNoDisponible.class,
                        () -> ingresar(InventarioEnMemoria.caido()).ejecutar(sala.id(), como(VISITANTE))));
    }

    // =====================================================================
    // Se pregunta por el jugador del token, y lo justo
    // =====================================================================

    @Test
    @DisplayName("se pregunta por quien pide entrar, nunca por otro")
    void preguntaPorElJugadorDelToken() {
        InventarioEnMemoria inventario = InventarioEnMemoria.conHeroe();
        Sala sala = salaAbierta();

        ingresar(inventario).ejecutar(sala.id(), como(VISITANTE));

        assertEquals(VISITANTE, inventario.consultados.get(0).id());
    }

    @Test
    @DisplayName("se consulta una sola vez por ingreso, aunque haya carrera por el cupo")
    void noSeMolestaAlInventarioDeMas() {
        InventarioEnMemoria inventario = InventarioEnMemoria.conHeroe();
        Sala sala = salaAbierta();

        ingresar(inventario).ejecutar(sala.id(), como(VISITANTE));

        assertEquals(1, inventario.vecesConsultado());
    }

    @Test
    @DisplayName("pulsar «empezar» dos veces no vuelve a preguntar al inventario")
    void laSegundaPulsacionNoConsulta() {
        // La segunda llamada devuelve la partida que ya existe. Volver a
        // preguntar la haria fallar: el heroe esta ocupado justamente en el
        // combate que acaba de arrancar.
        InventarioEnMemoria inventario = InventarioEnMemoria.conHeroe();
        Sala sala = salaAbierta();
        sala.unirse(VISITANTE);
        salas.guardar(sala);
        IniciarPartida casoDeUso = iniciar(inventario);

        casoDeUso.ejecutar(sala.id(), como(ANFITRION));
        casoDeUso.ejecutar(sala.id(), como(ANFITRION));

        assertEquals(1, inventario.vecesConsultado());
    }
}
