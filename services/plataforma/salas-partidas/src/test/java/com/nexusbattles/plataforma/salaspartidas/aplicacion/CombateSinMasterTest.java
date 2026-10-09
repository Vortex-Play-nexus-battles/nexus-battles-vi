package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParticipanteDePartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PerfilDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.integracion.ClienteCatalogoDeHeroes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * HU-SIM-005, criterio 3: «dado cualquier modo de juego distinto de misiones,
 * cuando se procesa, entonces los enemigos Master no aparecen».
 *
 * <p>Este servicio no tiene ninguna nocion de Master, y eso es lo que se fija:
 * un Master es un rival con una epica propia, y el unico rival que arma este
 * servicio es la maquina de D-B7-11, un heroe del catalogo de prototipos, sin
 * equipo y SIN epicas, o su sustituto {@code comoRivalDeLaMaquina()} cuando el
 * catalogo no responde. La otra mitad del criterio (que el plan de una mision
 * solo lleva Master cuando la tirada los saca) esta en
 * {@code MasterSoloEnMisionesTest}, en el servicio de misiones.
 *
 * <p>Un encuentro de torneo no tiene camino propio: es una sala vinculada a un
 * encuentro que pasa por el mismo {@link IniciarPartida}, con personas en todos
 * los cupos y ningun cupo de la maquina. La tercera prueba lo cubre.
 */
@DisplayName("HU-SIM-005 C3 · fuera de las misiones no aparecen enemigos Master")
class CombateSinMasterTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID INVITADO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-10-05T20:00:00Z");
    private static final String HEROES = "http://heroes:8080";

    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final CanalDePartidaEspia canal = new CanalDePartidaEspia();
    private final InventarioEnMemoria inventario = InventarioEnMemoria.conHeroe();

    private static JugadorAutenticado como(UUID id) {
        return new JugadorAutenticado(id, "jugador-" + id.toString().substring(0, 8));
    }

    /** Un heroe del jugador con equipo y dos epicas de la Tabla 20: lo que un Master lleva y la maquina no. */
    private static HeroeDeCombate heroeConEpicas() {
        return new HeroeDeCombate("h-ana", "Aquiles", "Guerrero Armas", "https://retratos/aquiles.png", 5, 120, 120,
                11, new PerfilDeCombate(5, null, List.of("Espada de prueba", "Escudo de prueba"),
                        List.of("Velo de Sombras", "Segundo impulso")));
    }

    private IniciarPartida iniciar(HeroesDeLaMaquina maquina) {
        return new IniciarPartida(salas, partidas, canal, inventario, Clock.fixed(AHORA, ZoneOffset.UTC),
                null, maquina, () -> 3L, () -> null, idPartida -> { });
    }

    private Sala salaContraLaMaquina() {
        return salas.guardar(Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION,
                new FichaDeParticipante("Ana", heroeConEpicas())));
    }

    private static List<ParticipanteDePartida> deLaMaquina(Partida partida) {
        return partida.participantes().stream().filter(ParticipanteDePartida::esIA).toList();
    }

    private static void comoRivalDelCatalogo(HeroeDeCombate rival) {
        assertAll(
                () -> assertTrue(rival.perfil().epicas().isEmpty(), "un rival de la maquina no lleva epicas"),
                () -> assertTrue(rival.perfil().equipamiento().isEmpty(), "ni equipo"),
                () -> assertNull(rival.perfil().estadisticas(), "ni estadisticas propias: las del catalogo"),
                () -> assertNull(rival.retratoUrl(), "ni el retrato de un jugador"));
    }

    @Test
    @DisplayName("criterio3_laMaquinaDeUnaPartidaEsUnHeroeDelCatalogoSinEpicasNiEquipo")
    void criterio3_laMaquinaDeUnaPartidaEsUnHeroeDelCatalogoSinEpicasNiEquipo() {
        // El catalogo de verdad (ClienteCatalogoDeHeroes) contra un servidor de heroes simulado.
        MockRestServiceServer servidor;
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        servidor.expect(requestTo(HEROES + "/api/v1/heroes")).andRespond(withSuccess(
                "[ { \"nombre\": \"Mago Hielo\", \"tipo\": \"Mago\", \"esSanador\": false } ]",
                MediaType.APPLICATION_JSON));
        servidor.expect(ExpectedCount.manyTimes(), requestTo(HEROES + "/api/v1/heroes/Mago%20Hielo/niveles/5"))
                .andRespond(withSuccess("{\"nombre\":\"x\",\"nivel\":5,\"estadisticas\":{\"poder\":8,"
                        + "\"vida\":150,\"defensa\":30}}", MediaType.APPLICATION_JSON));
        Sala sala = salaContraLaMaquina();

        Partida partida = iniciar(new ClienteCatalogoDeHeroes(constructor.build(), HEROES))
                .ejecutar(sala.id(), como(ANFITRION));

        List<ParticipanteDePartida> maquina = deLaMaquina(partida);
        ParticipanteDePartida anfitrion = partida.participante(ANFITRION).orElseThrow();
        assertAll(
                () -> assertEquals(1, maquina.size(), "un cupo de la maquina, un rival"),
                () -> assertEquals("Mago Hielo", maquina.getFirst().heroe().prototipo()),
                () -> assertEquals("Mago Hielo", maquina.getFirst().heroe().nombre(),
                        "se llama como su prototipo: no tiene nombre propio ni epica con nombre"),
                () -> comoRivalDelCatalogo(maquina.getFirst().heroe()),
                // Control: lo que la maquina no tiene, el jugador si lo conserva. Sin esto, la prueba
                // pasaria igual si el servicio le quitara las epicas a todo el mundo.
                () -> assertEquals(List.of("Velo de Sombras", "Segundo impulso"),
                        anfitrion.heroe().perfil().epicas()));
    }

    @Test
    @DisplayName("criterio3_sinCatalogoElRivalDeLaMaquinaNoHeredaLasEpicasNiElEquipoDelAnfitrion")
    void criterio3_sinCatalogoElRivalDeLaMaquinaNoHeredaLasEpicasNiElEquipoDelAnfitrion() {
        Sala sala = salaContraLaMaquina();
        HeroesDeLaMaquina caido = (cuantos, nivel) -> {
            throw new IllegalStateException("catalogo apagado");
        };

        Partida conCatalogoCaido = iniciar(caido).ejecutar(sala.id(), como(ANFITRION));

        // Y sin el puerto del catalogo configurado, que es el mismo respaldo.
        Sala otra = salaContraLaMaquina();
        Partida sinPuerto = iniciar(null).ejecutar(otra.id(), como(ANFITRION));

        for (Partida partida : List.of(conCatalogoCaido, sinPuerto)) {
            HeroeDeCombate rival = deLaMaquina(partida).getFirst().heroe();
            assertAll(
                    () -> assertNotEquals("h-ana", rival.id(), "no es una copia del heroe del anfitrion"),
                    () -> assertEquals("Guerrero Armas", rival.nombre(), "se llama como su prototipo"),
                    () -> comoRivalDelCatalogo(rival));
        }
    }

    @Test
    @DisplayName("criterio3_unaSalaSinCuposDeLaMaquinaNoLlevaMasOponentesQueLasPersonas")
    void criterio3_unaSalaSinCuposDeLaMaquinaNoLlevaMasOponentesQueLasPersonas() {
        // PvP y encuentro de torneo: personas en todos los cupos y heroesIA = 0; ni siquiera el
        // catalogo de la maquina se consulta, asi que nadie puede meter un rival extra.
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.HASTA_SEIS, 0, false, false, null), ANFITRION,
                new FichaDeParticipante("Ana", heroeConEpicas()));
        sala.unirse(INVITADO, new FichaDeParticipante("Bruno", heroeConEpicas()), null);
        salas.guardar(sala);
        boolean[] seConsultoLaMaquina = {false};
        HeroesDeLaMaquina maquina = (cuantos, nivel) -> {
            seConsultoLaMaquina[0] = true;
            return List.of();
        };

        Partida partida = iniciar(maquina).ejecutar(sala.id(), como(ANFITRION));

        assertAll(
                () -> assertEquals(List.of(ANFITRION, INVITADO).stream().sorted().toList(),
                        partida.participantes().stream().map(ParticipanteDePartida::idJugador).sorted().toList(),
                        "los dos jugadores y nadie mas"),
                () -> assertTrue(deLaMaquina(partida).isEmpty(), "ningun cupo de la maquina"),
                () -> assertFalse(seConsultoLaMaquina[0], "y no se sortea ningun heroe del catalogo"));
    }
}
