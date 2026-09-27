package nexus.inventario.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import nexus.inventario.aplicacion.CatalogoDeProductosEnMemoria;
import nexus.inventario.aplicacion.EntregarProductos;
import nexus.inventario.aplicacion.RepositorioDeEntregasEnMemoria;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.aplicacion.ResolutorDeProducto;
import nexus.inventario.configuracion.IdentidadDelLlamador;
import nexus.inventario.dominio.ParteArmadura;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * B4 — el contrato de {@code POST /api/v1/inventario/entregas} por HTTP: codigos,
 * cuerpo y problem details. Quien puede llamarla lo verifica
 * {@link SeguridadDeEntregasTest} con la cadena real.
 */
class EntregasApiTest {

    private static final String RUTA = "/api/v1/inventario/entregas";
    private static final String JUGADOR = "5d8e2a4b-1c3f-4e6a-8b7d-9f0a1b2c3d4e";

    private RepositorioInventariosEnMemoria inventarios;
    private CatalogoDeProductosEnMemoria catalogo;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        inventarios = new RepositorioInventariosEnMemoria();
        catalogo = new CatalogoDeProductosEnMemoria()
                .registrar("espada", new ResolutorDeProducto.DetalleProducto(
                        "Espada de una mano", "ARMA", null, "ACTIVO"))
                .registrar("retirado", new ResolutorDeProducto.DetalleProducto(
                        "Retirado", "ITEM", null, "SUSPENDIDO"))
                .registrarArmadura("peto", ParteArmadura.PECHO)
                .registrarArmadura("sin-parte", (ParteArmadura) null);
        EntregarProductos servicio = new EntregarProductos(new RepositorioDeEntregasEnMemoria(), inventarios, catalogo,
                Clock.fixed(Instant.parse("2026-09-25T15:00:00Z"), ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new EntregasController(servicio, new IdentidadDelLlamador()))
                .setControllerAdvice(new ManejadorDeErrores())
                .build();
    }

    private static String cuerpo(String productoId, int cantidad) {
        return """
                {"uid":"%s","origen":"COMPRA","referencia":"orden-77",
                 "productos":[{"productoId":"%s","cantidad":%d}]}
                """.formatted(JUGADOR, productoId, cantidad);
    }

    private ResultActions entregar(String clave, String cuerpo) throws Exception {
        var peticion = post(RUTA).with(ComoLlamador.servicio("ms-ecommerce"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
        if (clave != null) {
            peticion = peticion.header("Idempotency-Key", clave);
        }
        return mvc.perform(peticion);
    }

    @Test
    @DisplayName("201 con la entrega: elementos con su origen y su referencia")
    void entregaNueva() throws Exception {
        entregar("clave-1", cuerpo("peto", 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.uid").value(JUGADOR))
                .andExpect(jsonPath("$.origen").value("COMPRA"))
                .andExpect(jsonPath("$.referencia").value("orden-77"))
                .andExpect(jsonPath("$.entregadaEn").value("2026-09-25T15:00:00Z"))
                .andExpect(jsonPath("$.elementos.length()").value(2))
                .andExpect(jsonPath("$.elementos[0].tipo").value("ARMADURA"))
                .andExpect(jsonPath("$.elementos[0].parteArmadura").value("PECHO"))
                .andExpect(jsonPath("$.elementos[0].origen").value("COMPRA"))
                .andExpect(jsonPath("$.elementos[0].referencia").value("orden-77"))
                .andExpect(jsonPath("$.elementos[0].disponible").value(true))
                .andExpect(jsonPath("$.elementos[0].nivel").doesNotExist());

        assertEquals(2, inventarios.buscarPorPropietario(JUGADOR).orElseThrow().elementos().size());
    }

    @Test
    @DisplayName("la misma clave y el mismo cuerpo: 200 con la entrega original, sin duplicar")
    void repeticion() throws Exception {
        String primera = entregar("clave-1", cuerpo("espada", 1)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String segunda = entregar("clave-1", cuerpo("espada", 1)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertEquals((String) JsonPath.read(primera, "$.id"), JsonPath.read(segunda, "$.id"));
        assertEquals((String) JsonPath.read(primera, "$.elementos[0].id"), JsonPath.read(segunda, "$.elementos[0].id"));
        assertEquals(1, inventarios.buscarPorPropietario(JUGADOR).orElseThrow().elementos().size());
    }

    @Test
    @DisplayName("la misma clave con otro cuerpo: 409")
    void claveReutilizada() throws Exception {
        entregar("clave-1", cuerpo("espada", 1)).andExpect(status().isCreated());

        entregar("clave-1", cuerpo("espada", 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Clave de idempotencia reutilizada"));
    }

    @Test
    @DisplayName("sin Idempotency-Key, vacia o de mas de 100 caracteres: 400 y nada entregado")
    void sinClaveValida() throws Exception {
        entregar(null, cuerpo("espada", 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud invalida"));
        entregar("   ", cuerpo("espada", 1)).andExpect(status().isBadRequest());
        entregar("x".repeat(101), cuerpo("espada", 1)).andExpect(status().isBadRequest());

        assertEquals(0, catalogo.consultas());
    }

    @Test
    @DisplayName("un cuerpo fuera del contrato es 400: sin productos, cantidad 0 o 21, sin referencia, uid u origen invalidos")
    void cuerpoInvalido() throws Exception {
        entregar("c1", """
                {"uid":"%s","origen":"COMPRA","referencia":"r","productos":[]}""".formatted(JUGADOR))
                .andExpect(status().isBadRequest());
        entregar("c2", cuerpo("espada", 0)).andExpect(status().isBadRequest());
        entregar("c3", cuerpo("espada", 21)).andExpect(status().isBadRequest());
        entregar("c4", """
                {"uid":"%s","origen":"COMPRA","productos":[{"productoId":"espada","cantidad":1}]}"""
                .formatted(JUGADOR)).andExpect(status().isBadRequest());
        entregar("c5", """
                {"uid":"%s","origen":"COMPRA","referencia":"%s","productos":[{"productoId":"espada","cantidad":1}]}"""
                .formatted(JUGADOR, "r".repeat(121))).andExpect(status().isBadRequest());
        entregar("c6", """
                {"uid":"no-es-uuid","origen":"COMPRA","referencia":"r","productos":[{"productoId":"espada","cantidad":1}]}""")
                .andExpect(status().isBadRequest());
        entregar("c7", """
                {"uid":"%s","origen":"REGALO","referencia":"r","productos":[{"productoId":"espada","cantidad":1}]}"""
                .formatted(JUGADOR)).andExpect(status().isBadRequest());
        entregar("c8", """
                {"uid":"%s","origen":"COMPRA","referencia":"r","productos":[{"productoId":" ","cantidad":1}]}"""
                .formatted(JUGADOR)).andExpect(status().isBadRequest());

        assertEquals(0, catalogo.consultas());
    }

    @Test
    @DisplayName("un producto suspendido es 409, uno inexistente 422, una armadura sin parte 422")
    void productosQueNoSeEntregan() throws Exception {
        entregar("c1", cuerpo("retirado", 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Producto suspendido"));
        entregar("c2", cuerpo("espada-corta", 1)).andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Producto inexistente"));
        entregar("c3", cuerpo("sin-parte", 1)).andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Producto incompleto"));

        assertEquals(0, inventarios.guardados());
    }

    @Test
    @DisplayName("sin catalogo es 503 'Catalogo no disponible'; si falla la escritura, 503 'Inventario no disponible'")
    void dependenciasCaidas() throws Exception {
        catalogo.caer();
        entregar("c1", cuerpo("espada", 1)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Catalogo no disponible"));

        catalogo.levantar();
        inventarios.fallarSiguienteGuardado();
        entregar("c2", cuerpo("espada", 1)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Inventario no disponible"));

        // El reintento con la misma clave la termina: 201, una sola vez.
        entregar("c2", cuerpo("espada", 1)).andExpect(status().isCreated());
        assertEquals(1, inventarios.buscarPorPropietario(JUGADOR).orElseThrow().elementos().size());
    }

    @Test
    @DisplayName("un heroe entregado sale con nivel 1 y experiencia 0")
    void heroeEntregado() throws Exception {
        catalogo.registrar("guerrero", new ResolutorDeProducto.DetalleProducto(
                "Guerrero Tanque", "HEROE", "Guerrero Tanque", "ACTIVO"));

        entregar("c1", cuerpo("guerrero", 1)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.elementos[0].tipo").value("HEROE"))
                .andExpect(jsonPath("$.elementos[0].nivel").value(1))
                .andExpect(jsonPath("$.elementos[0].experiencia").value(0.0));
    }

    @Test
    @DisplayName("el uid del cuerpo es el que recibe; un uid distinto es otro inventario")
    void elUidDelCuerpoRecibe() throws Exception {
        String otro = UUID.randomUUID().toString();
        entregar("c1", """
                {"uid":"%s","origen":"PREMIO_TORNEO","referencia":"torneo-3",
                 "productos":[{"productoId":"espada","cantidad":1}]}""".formatted(otro))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("PREMIO_TORNEO"));

        assertEquals(1, inventarios.buscarPorPropietario(otro).orElseThrow().elementos().size());
        assertEquals(true, inventarios.buscarPorPropietario(JUGADOR).isEmpty());
    }
}
