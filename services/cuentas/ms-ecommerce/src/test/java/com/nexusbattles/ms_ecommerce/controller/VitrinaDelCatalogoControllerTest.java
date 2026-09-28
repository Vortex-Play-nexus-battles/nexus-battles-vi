package com.nexusbattles.ms_ecommerce.controller;

import com.nexusbattles.ms_ecommerce.dto.ConsultaDeVitrina;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.dto.PaginaDeVitrina;
import com.nexusbattles.ms_ecommerce.dto.ProductoEnVentaDto;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.MonedaNoDisponibleException;
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
        when(vitrina.pagina(ConsultaDeVitrina.de(0, 16, null), null)).thenReturn(paginaConLaEspada());

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

        verify(vitrina).pagina(ConsultaDeVitrina.de(0, 16, null), null);
    }

    @Test
    @DisplayName("con token tambien responde, y el uid del token llega al servicio para las marcas (B5)")
    void conTokenTambien() throws Exception {
        UUID uid = UUID.randomUUID();
        when(vitrina.pagina(ConsultaDeVitrina.de(0, 16, null), uid.toString())).thenReturn(paginaConLaEspada());

        mvc.perform(get("/api/v1/vitrina")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deJugador("lyra", uid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ESPADA));

        verify(vitrina).pagina(ConsultaDeVitrina.de(0, 16, null), uid.toString());
    }

    @Test
    @DisplayName("un token de servicio no es un jugador: vitrina sin marcas (B5)")
    void tokenDeServicioSinMarcas() throws Exception {
        when(vitrina.pagina(ConsultaDeVitrina.de(0, 16, null), null)).thenReturn(paginaConLaEspada());

        mvc.perform(get("/api/v1/vitrina")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.deServicio("salas-partidas")))
                .andExpect(status().isOk());

        verify(vitrina).pagina(ConsultaDeVitrina.de(0, 16, null), null);
    }

    @Test
    @DisplayName("un token caducado responde 401: la vitrina es publica, pero un token roto no se ignora")
    void tokenCaducado() throws Exception {
        mvc.perform(get("/api/v1/vitrina")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.caducado("lyra", UUID.randomUUID())))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(vitrina);
    }

    @Test
    @DisplayName("pasa pagina, tamano y tipo al servicio")
    void pasaLosParametros() throws Exception {
        when(vitrina.pagina(ConsultaDeVitrina.de(2, 8, "ARMA"), null)).thenReturn(PaginaDeVitrina.de(List.of(), 2, 8));

        mvc.perform(get("/api/v1/vitrina").param("page", "2").param("size", "8").param("tipo", "ARMA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(2))
                .andExpect(jsonPath("$.size").value(8))
                .andExpect(jsonPath("$.content").isEmpty());

        verify(vitrina).pagina(ConsultaDeVitrina.de(2, 8, "ARMA"), null);
    }

    @Test
    @DisplayName("moneda, filtros y busqueda llegan al servicio tal cual (B5, contrato 1.4.0)")
    void pasaMonedaFiltrosYBusqueda() throws Exception {
        ConsultaDeVitrina esperada = new ConsultaDeVitrina(1, 16, "ARMA", Moneda.USD, new BigDecimal("5"),
                new BigDecimal("20.50"), true, "espada fuego");
        when(vitrina.pagina(esperada, null)).thenReturn(PaginaDeVitrina.de(List.of(), 1, 16));

        mvc.perform(get("/api/v1/vitrina").param("page", "1").param("tipo", "ARMA").param("moneda", "USD")
                        .param("precioMinimo", "5").param("precioMaximo", "20.50").param("enPromocion", "true")
                        .param("busqueda", "espada fuego"))
                .andExpect(status().isOk());

        verify(vitrina).pagina(esperada, null);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"moneda=GBP", "moneda=usd", "precioMinimo=-1", "precioMaximo=-0.5"})
    @DisplayName("una moneda que no es COP, USD ni EUR, o un precio negativo: 400 sin consultar nada")
    void monedaOPrecioInvalidos(String consulta) throws Exception {
        mvc.perform(get("/api/v1/vitrina?" + consulta))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vitrina);
    }

    @Test
    @DisplayName("USD sin tasa: 422 moneda-no-disponible con las monedas que si")
    void monedaSinTasaEs422() throws Exception {
        when(vitrina.pagina(any(ConsultaDeVitrina.class), any()))
                .thenThrow(new MonedaNoDisponibleException(Moneda.USD, java.util.Set.of(Moneda.COP)));

        mvc.perform(get("/api/v1/vitrina").param("moneda", "USD"))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:nexus:problema:moneda-no-disponible"))
                .andExpect(jsonPath("$.monedasDisponibles[0]").value("COP"));
    }

    @Test
    @DisplayName("50 por pagina es el maximo admitido")
    void cincuentaEsElMaximo() throws Exception {
        when(vitrina.pagina(ConsultaDeVitrina.de(0, 50, null), null)).thenReturn(PaginaDeVitrina.de(List.of(), 0, 50));

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
        when(vitrina.pagina(any(ConsultaDeVitrina.class), any()))
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
