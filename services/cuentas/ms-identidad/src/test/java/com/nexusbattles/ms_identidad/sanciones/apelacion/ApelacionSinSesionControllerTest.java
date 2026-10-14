package com.nexusbattles.ms_identidad.sanciones.apelacion;

import com.nexusbattles.ms_identidad.auth.validation.ModeracionNoDisponibleException;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.ApelacionDeLaCuenta;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.EstadoDeApelacion;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.SancionQueImpideEntrar;
import com.nexusbattles.ms_identidad.sanciones.SancionRechazadaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/v1/auth/apelacion} (ms-identidad-auth.yaml 2.4.0): publico, lo
 * autoriza el codigo; siempre problem details y sin cache.
 */
@DisplayName("ApelacionSinSesionController (publico, con el codigo del login)")
class ApelacionSinSesionControllerTest {

    private static final String TIPOS = "https://nexusbattles.upb.edu.co/errors/";
    private static final UUID SANCION = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2026-10-14T15:00:00Z");

    private MockMvc mockMvc;
    private ApelacionSinSesionService servicio;

    @BeforeEach
    void preparar() {
        servicio = mock(ApelacionSinSesionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ApelacionSinSesionController(servicio)).build();
    }

    @Test
    @DisplayName("consulta: la sancion, hasta cuando se apela y la ultima apelacion, sin cache")
    void consulta() throws Exception {
        when(servicio.consultar("valor")).thenReturn(new EstadoDeApelacion(
                new SancionQueImpideEntrar(SANCION, "BANEO", "FRAUDE", "Duplico creditos", "Juego limpio", AHORA, null),
                AHORA.plusDays(30), true, null, null));

        mockMvc.perform(post("/api/v1/auth/apelacion/consulta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.sancion.tipo").value("BANEO"))
                .andExpect(jsonPath("$.sancion.causal").value("FRAUDE"))
                .andExpect(jsonPath("$.sancion.emitidaPor").doesNotExist())
                .andExpect(jsonPath("$.puedeApelar").value(true));
    }

    @Test
    @DisplayName("apelar: 201 con la apelacion y su plazo de decision")
    void apelar() throws Exception {
        when(servicio.apelar(eq("valor"), eq(SANCION), eq("No fui yo"))).thenReturn(new ApelacionDeLaCuenta(
                UUID.randomUUID(), SANCION, "PENDIENTE", "No fui yo", AHORA, AHORA.plusDays(21), false, null, null,
                null));

        mockMvc.perform(post("/api/v1/auth/apelacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\",\"sancionId\":\"" + SANCION + "\",\"argumento\":\"No fui yo\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.decisionAntesDe").exists());
    }

    @Test
    @DisplayName("codigo que ya no sirve: 401 codigo-de-apelacion-invalido")
    void codigoInvalido() throws Exception {
        when(servicio.consultar(any())).thenThrow(new CodigoDeApelacionInvalidoException());

        mockMvc.perform(post("/api/v1/auth/apelacion/consulta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(TIPOS + "codigo-de-apelacion-invalido"))
                .andExpect(jsonPath("$.detail").value(CodigoDeApelacionInvalidoException.MENSAJE));
    }

    @Test
    @DisplayName("moderacion dice que no procede: el mismo 422 como apelacion-no-procede")
    void noProcede() throws Exception {
        when(servicio.apelar(any(), any(), any())).thenThrow(new SancionRechazadaException(422,
                "ya hay una apelacion abierta sobre esta sancion"));

        mockMvc.perform(post("/api/v1/auth/apelacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\",\"sancionId\":\"" + SANCION + "\",\"argumento\":\"otra\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(TIPOS + "apelacion-no-procede"));
    }

    @Test
    @DisplayName("un 401/403 de moderacion es un fallo de la credencial del servicio: 503, no culpa de la persona")
    void credencialDelServicio() throws Exception {
        when(servicio.apelar(any(), any(), any())).thenThrow(new SancionRechazadaException(403, "solo ms-identidad"));

        mockMvc.perform(post("/api/v1/auth/apelacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\",\"sancionId\":\"" + SANCION + "\",\"argumento\":\"x\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value(TIPOS + "moderacion-no-disponible"))
                .andExpect(jsonPath("$.detail").value(ApelacionSinSesionController.NO_REGISTRADA));
    }

    @Test
    @DisplayName("moderacion caida: 503 con Retry-After y la apelacion no registrada")
    void moderacionCaida() throws Exception {
        when(servicio.consultar(any())).thenThrow(new ModeracionNoDisponibleException("sin respuesta"));

        mockMvc.perform(post("/api/v1/auth/apelacion/consulta").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("sin codigo, sin sancion, sin argumento o con uno de mas de 2000: 400 sin llamar al servicio")
    void malFormada() throws Exception {
        mockMvc.perform(post("/api/v1/auth/apelacion/consulta").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(TIPOS + "datos-invalidos"));
        mockMvc.perform(post("/api/v1/auth/apelacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\",\"argumento\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/apelacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\",\"sancionId\":\"" + SANCION + "\",\"argumento\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/apelacion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"valor\",\"sancionId\":\"" + SANCION + "\",\"argumento\":\""
                                + "x".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/apelacion").contentType(MediaType.APPLICATION_JSON)
                        .content("no es json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }
}
