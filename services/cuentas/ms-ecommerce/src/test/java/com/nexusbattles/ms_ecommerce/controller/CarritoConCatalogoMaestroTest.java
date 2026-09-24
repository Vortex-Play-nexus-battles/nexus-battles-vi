package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestroDePrueba;
import com.nexusbattles.ms_ecommerce.model.Carrito;
import com.nexusbattles.ms_ecommerce.repository.CarritoRepository;
import com.nexusbattles.ms_ecommerce.seguridad.SeguridadConfig;
import com.nexusbattles.ms_ecommerce.seguridad.TokensDePrueba;
import com.nexusbattles.ms_ecommerce.service.CarritoMapper;
import com.nexusbattles.ms_ecommerce.service.CarritoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESPADA;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.json;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agregar al carrito de punta a punta dentro del servicio: controlador,
 * seguridad, {@link CarritoService} y {@link CatalogoMaestro} reales. Solo son
 * dobles la base de datos (el repositorio) y el servicio productos (un
 * servidor HTTP simulado), asi que lo que se afirma es lo que ve el cliente:
 * el codigo HTTP y el {@code type} de problem details de cada regla.
 */
@WebMvcTest(CarritoController.class)
@Import({SeguridadConfig.class, TokensDePrueba.Decodificador.class, CarritoService.class, CarritoMapper.class,
        CarritoConCatalogoMaestroTest.CatalogoSimulado.class})
@DisplayName("Agregar al carrito contra el catalogo maestro: codigos y problem details")
class CarritoConCatalogoMaestroTest {

    private static final UUID UID = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String PRODUCTO_EN_CATALOGO = CatalogoMaestroDePrueba.BASE + "/api/v1/productos/";

    /** El catalogo maestro real, sobre un servidor HTTP simulado. */
    @TestConfiguration(proxyBeanMethods = false)
    static class CatalogoSimulado {

        private final CatalogoMaestroDePrueba catalogo = new CatalogoMaestroDePrueba();

        @Bean
        MockRestServiceServer servidorDelCatalogo() {
            return catalogo.servidor();
        }

        @Bean
        CatalogoMaestro catalogoMaestro() {
            return catalogo.cliente();
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MockRestServiceServer servidor;

    @MockitoBean
    private CarritoRepository carritoRepository;

    @MockitoBean
    private PlatformTransactionManager transacciones;

    private final String token = "Bearer " + TokensDePrueba.deJugador("lyra", UID);

    @BeforeEach
    void carritoVacio() {
        Carrito carrito = new Carrito();
        carrito.setId(1L);
        carrito.setUsuarioId(UID.toString());
        carrito.setItems(new ArrayList<>());
        when(carritoRepository.findByUsuarioId(UID.toString())).thenReturn(Optional.of(carrito));
        when(carritoRepository.save(any(Carrito.class))).thenAnswer(i -> i.getArguments()[0]);
    }

    @AfterEach
    void catalogoSinSorpresas() {
        servidor.verify();
        servidor.reset();
    }

    private ResultActions agregar(String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/carrito/items")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private void catalogoResponde(String id, String productoJson) {
        servidor.expect(requestTo(PRODUCTO_EN_CATALOGO + id))
                .andRespond(withSuccess(productoJson, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("un productoId numerico se acepta y se responde 422 producto-inexistente: no hay tal UUID")
    void idNumericoEsInexistente() throws Exception {
        servidor.expect(requestTo(PRODUCTO_EN_CATALOGO + "1")).andRespond(withResourceNotFound());

        agregar("{\"productoId\":1,\"cantidad\":1}")
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:nexus:problema:producto-inexistente"))
                .andExpect(jsonPath("$.title").value("Producto inexistente"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value("/api/v1/carrito/items"));
    }

    @Test
    @DisplayName("un producto suspendido: 409 producto-no-disponible (RN-PRD-004)")
    void suspendidoEs409() throws Exception {
        catalogoResponde(ESPADA, json(ESPADA, "Espada", "ARMA", "SUSPENDIDO", -1, "6000"));

        agregar("{\"productoId\":\"" + ESPADA + "\",\"cantidad\":1}")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:nexus:problema:producto-no-disponible"))
                .andExpect(jsonPath("$.title").value("Producto no disponible"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("un producto agotado: 409 producto-agotado (RF-CAR-007)")
    void agotadoEs409() throws Exception {
        catalogoResponde(ESPADA, json(ESPADA, "Espada", "ARMA", "ACTIVO", 0, "6000"));

        agregar("{\"productoId\":\"" + ESPADA + "\",\"cantidad\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:nexus:problema:producto-agotado"))
                .andExpect(jsonPath("$.title").value("Producto agotado"));
    }

    @Test
    @DisplayName("un producto sin precio en moneda real: 422 producto-sin-precio-en-moneda-real")
    void sinPrecioEnMonedaRealEs422() throws Exception {
        catalogoResponde(ESPADA, json(ESPADA, "Espada", "ARMA", "ACTIVO", -1, null));

        agregar("{\"productoId\":\"" + ESPADA + "\",\"cantidad\":1}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value("urn:nexus:problema:producto-sin-precio-en-moneda-real"))
                .andExpect(jsonPath("$.title").value("Producto sin precio en moneda real"));
    }

    @Test
    @DisplayName("el catalogo caido: 503 catalogo-no-disponible con Retry-After, y la base ni se toca")
    void catalogoCaidoEs503() throws Exception {
        servidor.expect(requestTo(PRODUCTO_EN_CATALOGO + ESPADA)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        agregar("{\"productoId\":\"" + ESPADA + "\",\"cantidad\":1}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.type").value("urn:nexus:problema:catalogo-no-disponible"));

        verifyNoInteractions(transacciones);
    }

    @Test
    @DisplayName("un producto en venta entra: 200 con el DTO del carrito")
    void productoEnVentaEntra() throws Exception {
        catalogoResponde(ESPADA, json(ESPADA, "Espada de fuego", "ARMA", "ACTIVO", 5, "6000.00"));

        agregar("{\"productoId\":\"" + ESPADA + "\",\"cantidad\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(UID.toString()))
                .andExpect(jsonPath("$.items[0].producto.id").value(ESPADA))
                .andExpect(jsonPath("$.items[0].producto.nombre").value("Espada de fuego"))
                .andExpect(jsonPath("$.items[0].producto.moneda").value("COP"))
                .andExpect(jsonPath("$.items[0].cantidad").value(2))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(6000.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(12000.00))
                .andExpect(jsonPath("$.total").value(12000.00))
                .andExpect(jsonPath("$.moneda").value("COP"));
    }

    @Test
    @DisplayName("sin token: 401, y ni el catalogo ni la base se consultan")
    void sinTokenEs401() throws Exception {
        mvc.perform(post("/api/v1/carrito/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":\"" + ESPADA + "\",\"cantidad\":1}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(carritoRepository);
    }
}
