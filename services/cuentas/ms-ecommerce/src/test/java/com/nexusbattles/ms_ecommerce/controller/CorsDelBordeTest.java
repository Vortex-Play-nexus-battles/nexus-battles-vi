package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.AgregarItemRequest;
import com.nexusbattles.ms_ecommerce.dto.CarritoDto;
import com.nexusbattles.ms_ecommerce.seguridad.ConfiguracionCors;
import com.nexusbattles.ms_ecommerce.seguridad.SeguridadConfig;
import com.nexusbattles.ms_ecommerce.seguridad.TokensDePrueba;
import com.nexusbattles.ms_ecommerce.service.CarritoService;
import com.nexusbattles.ms_ecommerce.service.VitrinaDelCatalogoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Añadir» desde la pantalla servida por el borde. Esa llamada lleva
 * {@code Origin} y el mismo token con el que la API ya respondia 200 (R16).
 *
 * <p>Con los {@code @CrossOrigin} fijos en {@code localhost:8080}, el
 * {@code POST} del navegador se rechazaba con 403 aunque el token fuera bueno.
 * El origen del borde se configura por variable de entorno y cualquier otro
 * sigue fuera.
 */
@WebMvcTest(controllers = {CarritoController.class, VitrinaDelCatalogoController.class})
@Import({SeguridadConfig.class, ConfiguracionCors.class, TokensDePrueba.Decodificador.class})
@TestPropertySource(properties = "app.cors.origenes-permitidos=http://borde.test")
@DisplayName("CORS: el borde puede añadir al carrito; un origen ajeno no")
class CorsDelBordeTest {

    private static final UUID UID = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String PRODUCTO = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";
    private static final String CUERPO = "{\"productoId\":\"" + PRODUCTO + "\",\"cantidad\":1}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CarritoService carritoService;

    @MockitoBean
    private VitrinaDelCatalogoService vitrina;

    private static String token() {
        return "Bearer " + TokensDePrueba.deJugador("lyra", UID);
    }

    @Test
    @DisplayName("desde el origen del borde, el POST del navegador llega: 200 y la cabecera CORS")
    void elBordePuedeAnadir() throws Exception {
        when(carritoService.agregarProducto(eq(UID.toString()), any(AgregarItemRequest.class)))
                .thenReturn(new CarritoDto(1L, UID.toString(), List.of(), BigDecimal.ZERO, null));

        mvc.perform(post("/api/v1/carrito/items")
                        .header(HttpHeaders.ORIGIN, "http://borde.test")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://borde.test"));
    }

    @Test
    @DisplayName("el preflight del borde declara POST y la cabecera Authorization")
    void preflightDelBorde() throws Exception {
        mvc.perform(options("/api/v1/carrito/items")
                        .header(HttpHeaders.ORIGIN, "http://borde.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://borde.test"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")));
    }

    @Test
    @DisplayName("desde un origen que no esta en la lista, 403 y el carrito no se toca, aunque el token sea bueno")
    void unOrigenAjenoNo() throws Exception {
        mvc.perform(post("/api/v1/carrito/items")
                        .header(HttpHeaders.ORIGIN, "http://pagina-ajena.test")
                        .header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(carritoService);
    }
}
