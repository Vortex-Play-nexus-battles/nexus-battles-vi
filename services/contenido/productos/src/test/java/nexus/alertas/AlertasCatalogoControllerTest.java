package nexus.alertas;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AlertasCatalogoControllerTest {

    @Test
    @DisplayName("el endpoint usa la identidad autenticada y devuelve descripcion y fecha")
    void consultaAlertasDelJugadorAutenticado() throws Exception {
        AlertasCatalogoServicio servicio = mock(AlertasCatalogoServicio.class);
        Instant fecha = Instant.parse("2026-09-11T15:30:00Z");
        when(servicio.consultarAlIniciarSesion("jugador-7"))
                .thenReturn(List.of(new AlertaCatalogo(
                        "alerta-1",
                        "producto-1",
                        "Espada solar",
                        TipoCambioCatalogo.PRODUCTO_SUSPENDIDO,
                        "El producto Espada solar fue suspendido del catalogo.",
                        fecha)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AlertasCatalogoController(servicio)).build();
        Principal jugador = () -> "jugador-7";

        mvc.perform(get("/api/v1/productos/alertas/inicio-sesion")
                        .principal(jugador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoNombre").value("Espada solar"))
                .andExpect(jsonPath("$[0].tipo").value("PRODUCTO_SUSPENDIDO"))
                .andExpect(jsonPath("$[0].descripcion").isNotEmpty())
                .andExpect(jsonPath("$[0].implementadaEn")
                        .value("2026-09-11T15:30:00Z"));
    }

    @Test
    @DisplayName("HU-NOT-001: los cambios para otro servicio pasan desde y limite al servicio y responden el lote")
    void consultaCambiosParaOtroServicio() throws Exception {
        AlertasCatalogoServicio servicio = mock(AlertasCatalogoServicio.class);
        Instant desde = Instant.parse("2026-09-10T12:00:00Z");
        Instant fecha = Instant.parse("2026-09-11T15:30:00Z");
        when(servicio.consultarCambios(desde, 10))
                .thenReturn(new LoteDeAlertasCatalogo(fecha, false, List.of(new AlertaCatalogo(
                        "alerta-1",
                        "producto-1",
                        "Espada solar",
                        TipoCambioCatalogo.PRODUCTO_MODIFICADO,
                        "El producto Espada solar fue modificado.",
                        fecha))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AlertasCatalogoController(servicio)).build();

        mvc.perform(get("/api/v1/productos/alertas/cambios")
                        .param("desde", "2026-09-10T12:00:00Z")
                        .param("limite", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasta").value("2026-09-11T15:30:00Z"))
                .andExpect(jsonPath("$.completo").value(false))
                .andExpect(jsonPath("$.alertas[0].id").value("alerta-1"))
                .andExpect(jsonPath("$.alertas[0].tipo").value("PRODUCTO_MODIFICADO"))
                .andExpect(jsonPath("$.alertas[0].descripcion")
                        .value("El producto Espada solar fue modificado."))
                .andExpect(jsonPath("$.alertas[0].implementadaEn").value("2026-09-11T15:30:00Z"));
    }

    @Test
    @DisplayName("HU-NOT-001: sin parametros pide la linea base con el limite por omision (50)")
    void sinParametrosPideLaLineaBase() throws Exception {
        AlertasCatalogoServicio servicio = mock(AlertasCatalogoServicio.class);
        Instant ahora = Instant.parse("2026-09-11T15:30:00Z");
        when(servicio.consultarCambios(null, 50))
                .thenReturn(new LoteDeAlertasCatalogo(ahora, true, List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new AlertasCatalogoController(servicio)).build();

        mvc.perform(get("/api/v1/productos/alertas/cambios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasta").value("2026-09-11T15:30:00Z"))
                .andExpect(jsonPath("$.completo").value(true))
                .andExpect(jsonPath("$.alertas.length()").value(0));
    }
}
