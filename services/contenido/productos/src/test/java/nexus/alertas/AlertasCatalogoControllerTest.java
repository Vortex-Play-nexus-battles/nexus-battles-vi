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
}
