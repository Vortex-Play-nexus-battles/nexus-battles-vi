package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.dto.PaginaDeVitrina;
import com.nexusbattles.ms_ecommerce.dto.ProductoEnVentaDto;
import com.nexusbattles.ms_ecommerce.seguridad.SeguridadConfig;
import com.nexusbattles.ms_ecommerce.seguridad.TokensDePrueba;
import com.nexusbattles.ms_ecommerce.service.VitrinaDelCatalogoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/v1/vitrina} con la cadena de seguridad real: publica, con
 * los limites de paginacion validados y el catalogo caido como 503.
 */
@WebMvcTest(VitrinaDelCatalogoController.class)
@Import({SeguridadConfig.class, TokensDePrueba.Decodificador.class})
@DisplayName("Vitrina del catalogo maestro: GET /api/v1/vitrina")
class VitrinaDelCatalogoControllerTest {

    private static final String ESPADA = "5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VitrinaDelCatalogoService vitrina;

    private static PaginaDeVitrina paginaConLaEspada() {
        ProductoEnVentaDto espada = new ProductoEnVentaDto(ESPADA, "Espada de fuego", "img/espada.png",
                "Arde al golpear", "Golpe ardiente", "ARMA", new BigDecimal("6000.00"), new BigDecimal("6000.00"),
                "COP", false, null, false, false);
        return PaginaDeVitrina.de(List.of(espada), 0, 16);
    }

    @Test
    @DisplayName("es publica: sin token responde 200 con la pagina, con 16 por omision")
    void publicaSinToken() throws Exception {
        when(vitrina.pagina(0, 16, null)).thenReturn(paginaConLaEspada());

        mvc.perform(get("/api/v1/vitrina"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].id").value(ESPADA))
                .andExpect(jsonPath("$.content[0].nombre").value("Espada de fuego"))
                .andExpect(jsonPath("$.content[0].imagenUrl").value("img/espada.png"))
                .andExpect(jsonPath("$.content[0].descripcion").value("Arde al golpear"))
                .andExpect(jsonPath("$.content[0].habilidades").value("Golpe ardiente"))
                .andExpect(jsonPath("$.content[0].tipo").value("ARMA"))
                .andExpect(jsonPath("$.content[0].precioFinal").value(6000.00))
                .andExpect(jsonPath("$.content[0].precioOriginal").value(6000.00))
                .andExpect(jsonPath("$.content[0].moneda").value("COP"))
                .andExpect(jsonPath("$.content[0].enPromocion").value(false))
                .andExpect(jsonPath("$.content[0].porcentajeDescuento").isEmpty())
                .andExpect(jsonPath("$.content[0].esPropio").value(false))
                .andExpect(jsonPath("$.content[0].enListaDeseos").value(false))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(16))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.last").value(true));

        verify(vitrina).pagina(0, 16, null);
    }

    @Test
    @DisplayName("con token tambien responde: ser publica no es rechazar a quien tiene sesion")
    void conTokenTambien() throws Exception {
        when(vitrina.pagina(0, 16, null)).thenReturn(paginaConLaEspada());

        mvc.perform(get("/api/v1/vitrina")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deJugador("lyra", UUID.randomUUID())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("pasa pagina, tamano y tipo al servicio")
    void pasaLosParametros() throws Exception {
        when(vitrina.pagina(2, 8, "ARMA")).thenReturn(PaginaDeVitrina.de(List.of(), 2, 8));

        mvc.perform(get("/api/v1/vitrina").param("page", "2").param("size", "8").param("tipo", "ARMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(8))
                .andExpect(jsonPath("$.content").isEmpty());

        verify(vitrina).pagina(2, 8, "ARMA");
    }

    @Test
    @DisplayName("una moneda pedida no se repite: sin conversion implementada, la vitrina responde COP")
    void noRepiteLaMonedaPedida() throws Exception {
        when(vitrina.pagina(0, 16, null)).thenReturn(paginaConLaEspada());

        mvc.perform(get("/api/v1/vitrina").param("moneda", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].moneda").value("COP"));

        verify(vitrina).pagina(0, 16, null);
    }

    @Test
    @DisplayName("50 por pagina es el maximo admitido")
    void cincuentaEsElMaximo() throws Exception {
        when(vitrina.pagina(0, 50, null)).thenReturn(PaginaDeVitrina.de(List.of(), 0, 50));

        mvc.perform(get("/api/v1/vitrina").param("size", "50"))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"page=-1", "size=0", "size=51", "page=uno", "size=muchos"})
    @DisplayName("fuera de rango o de otro tipo: 400 con problem details, sin consultar el catalogo")
    void parametrosInvalidos(String consulta) throws Exception {
        mvc.perform(get("/api/v1/vitrina?" + consulta))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        verifyNoInteractions(vitrina);
    }

    @Test
    @DisplayName("catalogo caido: 503 problem+json con Retry-After de 30 s")
    void catalogoCaidoEs503() throws Exception {
        when(vitrina.pagina(anyInt(), anyInt(), any()))
                .thenThrow(new CatalogoNoDisponibleException("Connection refused"));

        mvc.perform(get("/api/v1/vitrina"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(jsonPath("$.type").value("urn:nexus:problema:catalogo-no-disponible"))
                .andExpect(jsonPath("$.title").value("Catálogo no disponible"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(
                        "El catálogo de productos no responde en este momento. Vuelve a intentarlo en unos segundos."))
                .andExpect(jsonPath("$.instance").value("/api/v1/vitrina"));
    }
}
