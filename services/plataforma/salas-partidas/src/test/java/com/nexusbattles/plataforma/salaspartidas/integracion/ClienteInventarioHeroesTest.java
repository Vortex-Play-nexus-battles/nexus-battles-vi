package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.JugadorAutenticado;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoDelHeroe;
import com.nexusbattles.plataforma.salaspartidas.dominio.InventarioNoDisponible;
import com.nexusbattles.plataforma.salaspartidas.dominio.ResultadoVerificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Traduccion del inventario al veredicto de HU-SAL-003.
 *
 * <p>Aqui vive la unica regla que este servicio aplica sobre datos ajenos:
 * <i>equipado es llevar algo puesto</i>. Todo lo demas —quien esta disponible,
 * que retiene a un heroe— lo decide el proveedor y esta prueba solo comprueba
 * que se respeta su respuesta, incluida la que no gusta.
 */
@DisplayName("ClienteInventarioHeroes · verificacion contra inventario (HU-SAL-003)")
class ClienteInventarioHeroesTest {

    private static final String BASE = "http://inventario:8080";
    private static final String VITRINA = BASE + "/api/v1/inventario/elementos?pagina=0";
    private static final String EQUIPAMIENTO = BASE + "/api/v1/inventario/heroes/h-1/equipamiento";
    private static final String ESTADISTICAS = BASE + "/api/v1/inventario/heroes/h-1/estadisticas";

    private static final JugadorAutenticado JUGADOR =
            new JugadorAutenticado(UUID.fromString("11111111-1111-1111-1111-111111111111"), "vael");

    private MockRestServiceServer servidor;
    private ClienteInventarioHeroes cliente;

    @BeforeEach
    void montarInventarioSimulado() {
        RestClient.Builder constructor = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(constructor).build();
        cliente = new ClienteInventarioHeroes(constructor.build(), BASE);
    }

    private static String vitrinaCon(String elementos) {
        return "{\"elementos\":[" + elementos + "],\"numero\":0,\"tamanio\":16,"
                + "\"totalElementos\":1,\"totalPaginas\":1,\"ultima\":true}";
    }

    private static String heroe(String id, String nombre, boolean disponible, String subastaId) {
        return "{\"id\":\"" + id + "\",\"productoId\":\"p-1\",\"tipo\":\"HEROE\",\"nombrePropio\":\""
                + nombre + "\",\"disponible\":" + disponible + ",\"subastaId\":"
                + (subastaId == null ? "null" : "\"" + subastaId + "\"") + "}";
    }

    private void esperarVitrina(String cuerpo) {
        servidor.expect(requestTo(VITRINA))
                .andExpect(header("X-User-Name", "vael"))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("un heroe disponible y equipado admite la participacion (CA-2)")
    void heroeEquipadoAdmite() {
        esperarVitrina(vitrinaCon(heroe("h-1", "Sombra de Vael", true, null)));
        servidor.expect(requestTo(EQUIPAMIENTO)).andRespond(withSuccess(
                "{\"heroeId\":\"h-1\",\"armas\":[\"a-1\"],\"armaduras\":{},\"items\":[]}",
                MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(ESTADISTICAS)).andRespond(withSuccess(
                "{\"heroeId\":\"h-1\",\"poder\":10,\"vida\":140,\"defensa\":4}",
                MediaType.APPLICATION_JSON));

        EstadoDelHeroe estado = cliente.consultar(JUGADOR);

        assertAll(
                () -> assertEquals(ResultadoVerificacion.DISPONIBLE, estado.resultado()),
                () -> assertEquals("Sombra de Vael", estado.heroe().nombre()),
                () -> assertEquals(140, estado.heroe().vidaMaxima()),
                () -> assertEquals(140, estado.heroe().vidaActual()));
        servidor.verify();
    }

    @Test
    @DisplayName("un heroe sin nada puesto se rechaza indicando el motivo (CA-1)")
    void heroeDesnudoSeRechaza() {
        esperarVitrina(vitrinaCon(heroe("h-1", "Sombra de Vael", true, null)));
        servidor.expect(requestTo(EQUIPAMIENTO)).andRespond(withSuccess(
                "{\"heroeId\":\"h-1\",\"armas\":[],\"armaduras\":{},\"items\":[]}",
                MediaType.APPLICATION_JSON));

        EstadoDelHeroe estado = cliente.consultar(JUGADOR);

        assertAll(
                () -> assertEquals(ResultadoVerificacion.SIN_HEROE_EQUIPADO, estado.resultado()),
                () -> assertNull(estado.heroe()));
    }

    @Test
    @DisplayName("sin ningun heroe en la vitrina tampoco se puede combatir")
    void vitrinaSinHeroes() {
        esperarVitrina(vitrinaCon("{\"id\":\"e-9\",\"productoId\":\"p-9\",\"tipo\":\"ARMA\","
                + "\"nombrePropio\":\"Filo\",\"disponible\":true,\"subastaId\":null}"));

        assertEquals(ResultadoVerificacion.SIN_HEROE_EQUIPADO, cliente.consultar(JUGADOR).resultado());
    }

    @Test
    @DisplayName("un heroe equipado pero no disponible sale como ocupado, con su motivo")
    void heroeBloqueadoSaleOcupado() {
        esperarVitrina(vitrinaCon(heroe("h-1", "Sombra de Vael", false,
                "22222222-2222-2222-2222-222222222222")));
        // El equipamiento se consulta una sola vez: la primera pasada descarta
        // al heroe por no estar disponible sin llegar a preguntar por el.
        servidor.expect(requestTo(EQUIPAMIENTO)).andRespond(withSuccess(
                "{\"heroeId\":\"h-1\",\"armas\":[],\"armaduras\":{\"CASCO\":\"c-1\"},\"items\":[]}",
                MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(ESTADISTICAS)).andRespond(withSuccess(
                "{\"heroeId\":\"h-1\",\"poder\":10,\"vida\":90,\"defensa\":4}",
                MediaType.APPLICATION_JSON));

        EstadoDelHeroe estado = cliente.consultar(JUGADOR);

        assertAll(
                () -> assertEquals(ResultadoVerificacion.HEROE_OCUPADO, estado.resultado()),
                () -> assertEquals("una subasta en curso", estado.ocupadoPor()),
                () -> assertEquals("Sombra de Vael", estado.heroe().nombre()));
    }

    @Test
    @DisplayName("si el inventario falla, 503: no se responde un veredicto inventado")
    void inventarioCaidoNoInventaVeredicto() {
        servidor.expect(requestTo(VITRINA)).andRespond(withServerError());

        assertThrows(InventarioNoDisponible.class, () -> cliente.consultar(JUGADOR));
    }

    @Test
    @DisplayName("sin estadisticas el heroe sigue siendo utilizable: la vida solo adorna")
    void sinEstadisticasElHeroeSirve() {
        esperarVitrina(vitrinaCon(heroe("h-1", "Sombra de Vael", true, null)));
        servidor.expect(requestTo(EQUIPAMIENTO)).andRespond(withSuccess(
                "{\"heroeId\":\"h-1\",\"armas\":[\"a-1\"],\"armaduras\":{},\"items\":[]}",
                MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(ESTADISTICAS)).andRespond(withSuccess(
                "{\"heroeId\":\"h-1\",\"poder\":10,\"defensa\":4}", MediaType.APPLICATION_JSON));

        EstadoDelHeroe estado = cliente.consultar(JUGADOR);

        assertAll(
                () -> assertEquals(ResultadoVerificacion.DISPONIBLE, estado.resultado()),
                () -> assertEquals(1, estado.heroe().vidaMaxima()));
    }
}
