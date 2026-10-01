package nexus.alertas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
@AutoConfigureMockMvc
class AlertasCatalogoApiTest {

    private static final String RUTA = "/api/v1/productos/alertas/inicio-sesion";

    @Autowired
    private MockMvc mvc;

    @Autowired
    @Qualifier("alertasCatalogoSimuladas")
    private AlertaCatalogoRepository alertas;

    @Autowired
    @Qualifier("consultasAlertasSimuladas")
    private ConsultaAlertasJugadorRepository consultas;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void preparar() {
        when(consultas.findById("jugador-7")).thenReturn(Optional.empty());
        when(alertas.buscarImplementadasEntre(
                any(),
                any()))
                .thenReturn(List.of(new AlertaCatalogo(
                        "alerta-1",
                        "producto-1",
                        "Espada solar",
                        TipoCambioCatalogo.CAMBIO_BALANCE,
                        "Se actualizo el balance de Espada solar.",
                        Instant.parse("2026-09-27T15:30:00Z"))));
    }

    @Test
    @DisplayName("rechaza la consulta de alertas sin token")
    void requiereAutenticacion() throws Exception {
        mvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el jugador autenticado recibe sus alertas pendientes")
    void entregaAlertasAlJugador() throws Exception {
        mvc.perform(get(RUTA).with(jwt().jwt(token -> token.subject("jugador-7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productoId").value("producto-1"))
                .andExpect(jsonPath("$[0].tipo").value("CAMBIO_BALANCE"))
                .andExpect(jsonPath("$[0].implementadaEn").value("2026-09-27T15:30:00Z"));
    }
}
