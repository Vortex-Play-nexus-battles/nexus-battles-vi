package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.ProductoVitrinaDto;
import com.nexusbattles.ms_ecommerce.model.Carrito;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El carrito es del uid del token, con la cadena de seguridad real y tokens
 * firmados de verdad. Antes {@code X-User-Id} era la identidad sin verificar:
 * con el identificador de otro se veia y se modificaba su carrito.
 */
@WebMvcTest(controllers = {CarritoController.class, VitrinaController.class})
@Import({SeguridadConfig.class, TokensDePrueba.Decodificador.class})
@DisplayName("Carrito: es del uid del token, no de X-User-Id")
class CarritoControllerTest {

    private static final UUID UID = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CarritoService carritoService;

    @MockitoBean
    private VitrinaService vitrinaService;

    private static Carrito carritoDe(String usuarioId) {
        Carrito carrito = new Carrito();
        carrito.setUsuarioId(usuarioId);
        return carrito;
    }

    @Test
    @DisplayName("sin token, 401: X-User-Id ya no identifica a nadie")
    void sinTokenEs401() throws Exception {
        mvc.perform(get("/api/v1/carrito").header("X-User-Id", "usr_12345"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/carrito/items").header("X-User-Id", "usr_12345")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productoId\":1,\"cantidad\":2}"))
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
                        .content("{\"productoId\":1,\"cantidad\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(UID.toString()));
        mvc.perform(delete("/api/v1/carrito/items/7").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        verify(carritoService).agregarProducto(eq(UID.toString()), any(AgregarItemRequest.class));
        verify(carritoService).eliminarItem(UID.toString(), 7L);
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
    @DisplayName("la vitrina sigue publica: el catalogo lo ve cualquiera")
    void laVitrinaEsPublica() throws Exception {
        Page<ProductoVitrinaDto> vacia = new PageImpl<>(List.of());
        when(vitrinaService.obtenerProductosVitrina(any(Pageable.class), anyString())).thenReturn(vacia);

        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk());
    }
}
