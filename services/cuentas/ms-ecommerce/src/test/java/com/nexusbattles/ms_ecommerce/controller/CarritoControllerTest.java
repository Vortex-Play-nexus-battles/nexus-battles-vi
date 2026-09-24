package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ItemCarritoDto;
import com.nexusbattles.ms_ecommerce.dto.ProductoDelItemDto;
import com.nexusbattles.ms_ecommerce.dto.ProductoVitrinaDto;
import com.nexusbattles.ms_ecommerce.seguridad.SeguridadConfig;
import com.nexusbattles.ms_ecommerce.seguridad.TokensDePrueba;
import com.nexusbattles.ms_ecommerce.service.CarritoService;
import com.nexusbattles.ms_ecommerce.service.VitrinaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El carrito es del uid del token, con la cadena de seguridad real y tokens
 * firmados de verdad. Antes {@code X-User-Id} era la identidad sin verificar:
 * con el identificador de otro se veia y se modificaba su carrito.
 *
 * <p>Las reglas de venta contra el catalogo (422/409/503) se prueban con el
 * servicio real en {@code CarritoConCatalogoMaestroTest}; aqui el servicio es
 * un doble y lo que se prueba es la identidad, la validacion y la forma del
 * JSON.
 */
@WebMvcTest(controllers = {CarritoController.class, VitrinaController.class})
@Import({SeguridadConfig.class, TokensDePrueba.Decodificador.class})
@DisplayName("Carrito: es del uid del token, no de X-User-Id")
@SuppressWarnings("deprecation") // VitrinaController: la vitrina legada sigue publica y se prueba
class CarritoControllerTest {

    private static final UUID UID = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String PRODUCTO = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CarritoService carritoService;

    @MockitoBean
    private VitrinaService vitrinaService;

    private static CarritoDto carritoDe(String usuarioId) {
        return new CarritoDto(1L, usuarioId, List.of(), BigDecimal.ZERO, null);
    }

    @Test
    @DisplayName("sin token, 401: X-User-Id ya no identifica a nadie")
    void sinTokenEs401() throws Exception {
        mvc.perform(get("/api/v1/carrito").header("X-User-Id", "usr_12345"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/carrito/items").header("X-User-Id", "usr_12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":\"" + PRODUCTO + "\",\"cantidad\":2}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/carrito/items/7"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(carritoService);
    }

    @Test
    @DisplayName("el carrito que se obtiene es el del uid del token, aunque la cabecera diga otro")
    void elCarritoEsDelUidDelToken() throws Exception {
        when(carritoService.obtenerOCrearCarrito(UID.toString())).thenReturn(carritoDe(UID.toString()));

        mvc.perform(get("/api/v1/carrito")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deJugador("lyra", UID))
                        .header("X-User-Id", "usuario-suplantado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(UID.toString()));

        verify(carritoService).obtenerOCrearCarrito(UID.toString());
    }

    @Test
    @DisplayName("agregar y eliminar items operan sobre el carrito del uid del token")
    void agregarYEliminarSobreElPropio() throws Exception {
        when(carritoService.agregarProducto(eq(UID.toString()), any(AgregarItemRequest.class)))
                .thenReturn(carritoDe(UID.toString()));
        when(carritoService.eliminarItem(UID.toString(), 7L)).thenReturn(carritoDe(UID.toString()));
        String token = "Bearer " + TokensDePrueba.deJugador("lyra", UID);

        mvc.perform(post("/api/v1/carrito/items").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":\"" + PRODUCTO + "\",\"cantidad\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(UID.toString()));
        mvc.perform(delete("/api/v1/carrito/items/7").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        verify(carritoService).agregarProducto(eq(UID.toString()), any(AgregarItemRequest.class));
        verify(carritoService).eliminarItem(UID.toString(), 7L);
    }

    @Test
    @DisplayName("el carrito sale como DTO: producto del catalogo con su UUID, importes y moneda")
    void formaDelJson() throws Exception {
        ItemCarritoDto item = new ItemCarritoDto(10L, new ProductoDelItemDto(PRODUCTO, "Espada de fuego", "COP"),
                1, new BigDecimal("6000.00"), new BigDecimal("6000.00"));
        when(carritoService.obtenerOCrearCarrito(UID.toString()))
                .thenReturn(new CarritoDto(1L, UID.toString(), List.of(item), new BigDecimal("6000.00"), "COP"));

        mvc.perform(get("/api/v1/carrito")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deJugador("lyra", UID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.usuarioId").value(UID.toString()))
                .andExpect(jsonPath("$.items[0].id").value(10))
                .andExpect(jsonPath("$.items[0].producto.id").value(PRODUCTO))
                .andExpect(jsonPath("$.items[0].producto.nombre").value("Espada de fuego"))
                .andExpect(jsonPath("$.items[0].producto.moneda").value("COP"))
                .andExpect(jsonPath("$.items[0].cantidad").value(1))
                .andExpect(jsonPath("$.items[0].precioUnitario").value(6000.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(6000.00))
                .andExpect(jsonPath("$.total").value(6000.00))
                .andExpect(jsonPath("$.moneda").value("COP"))
                // Nada de la entidad se cuela: ni la relacion con el carrito ni columnas internas.
                .andExpect(jsonPath("$.items[0].carrito").doesNotExist())
                .andExpect(jsonPath("$.items[0].productoRef").doesNotExist());
    }

    @Test
    @DisplayName("un cuerpo invalido es 400 con problem details, y no llega al servicio")
    void cuerpoInvalidoEs400() throws Exception {
        String token = "Bearer " + TokensDePrueba.deJugador("lyra", UID);

        for (String cuerpo : List.of(
                "{\"productoId\":\"\",\"cantidad\":1}",
                "{\"productoId\":\"   \",\"cantidad\":1}",
                "{\"cantidad\":1}",
                "{\"productoId\":\"" + PRODUCTO + "\",\"cantidad\":0}",
                "{\"productoId\":\"" + PRODUCTO + "\"}",
                "{\"productoId\":")) {
            mvc.perform(post("/api/v1/carrito/items").header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        verifyNoInteractions(carritoService);
    }

    @Test
    @DisplayName("un token de servicio no tiene carrito: 403")
    void tokenDeServicioEs403() throws Exception {
        mvc.perform(get("/api/v1/carrito")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deServicio("ms-subastas")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(carritoService);
    }

    @Test
    @DisplayName("un token caducado o firmado por otro es 401")
    void tokenInvalidoEs401() throws Exception {
        mvc.perform(get("/api/v1/carrito")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.caducado("lyra", UID)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/carrito")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.firmadoPorOtro("lyra", UID)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(carritoService);
    }

    @Test
    @DisplayName("con la forma de Keycloak el carrito es del sujeto estable")
    void tokenDeKeycloak() throws Exception {
        UUID sujeto = UUID.randomUUID();
        when(carritoService.obtenerOCrearCarrito(sujeto.toString())).thenReturn(carritoDe(sujeto.toString()));

        mvc.perform(get("/api/v1/carrito")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + TokensDePrueba.deKeycloak(sujeto, "ana", List.of("JUGADOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(sujeto.toString()));
    }

    @Test
    @DisplayName("la vitrina legada sigue publica y sin cambios: el catalogo lo ve cualquiera")
    void laVitrinaEsPublica() throws Exception {
        Page<ProductoVitrinaDto> vacia = new PageImpl<>(List.of());
        when(vitrinaService.obtenerProductosVitrina(any(Pageable.class), anyString())).thenReturn(vacia);

        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk());
    }
}
