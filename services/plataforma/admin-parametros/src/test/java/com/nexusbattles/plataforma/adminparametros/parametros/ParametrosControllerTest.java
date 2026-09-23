package com.nexusbattles.plataforma.adminparametros.parametros;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.adminparametros.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ParametrosController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class, ManejadorErroresParametros.class})
@DisplayName("Parametros · API con tokens de ms-identidad")
class ParametrosControllerTest {

    private static final UUID ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ParametrosService servicio;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private static ParametrosService.Vigente vigente() {
        Parametro p = Parametro.editable("sanciones.suspension.maxima-dias", Parametro.Tipo.ENTERO, "30",
                BigDecimal.ONE, BigDecimal.valueOf(365), null, "D-20");
        return new ParametrosService.Vigente(p, "30");
    }

    @Test
    @DisplayName("leer es publico: el catalogo, un parametro y su valor ligero")
    void lectura() throws Exception {
        when(servicio.listar()).thenReturn(List.of(vigente()));
        when(servicio.obtener("sanciones.suspension.maxima-dias")).thenReturn(vigente());
        mvc.perform(get("/api/v1/parametros"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clave").value("sanciones.suspension.maxima-dias"))
                .andExpect(jsonPath("$[0].maximo").value(365))
                .andExpect(jsonPath("$[0].inalterable").value(false));
        mvc.perform(get("/api/v1/parametros/sanciones.suspension.maxima-dias/valor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("30"))
                .andExpect(jsonPath("$.tipo").value("ENTERO"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("cambiar exige token (401) y el actor sale del token; los rechazos salen como problem details")
    void cambiar() throws Exception {
        mvc.perform(put("/api/v1/parametros/x").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        when(servicio.cambiar(any(), eq("sanciones.suspension.maxima-dias"), any())).thenReturn(vigente());
        mvc.perform(put("/api/v1/parametros/sanciones.suspension.maxima-dias")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("admin", ADMIN, "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"15\",\"motivo\":\"Sprint Review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valor").value("30"));
        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<ParametrosService.Cambio> cambio = ArgumentCaptor.forClass(ParametrosService.Cambio.class);
        verify(servicio).cambiar(actor.capture(), eq("sanciones.suspension.maxima-dias"), cambio.capture());
        assertThat(actor.getValue().id()).isEqualTo(ADMIN);
        assertThat(actor.getValue().rol()).isEqualTo("ADMINISTRADOR");
        assertThat(cambio.getValue().valor()).isEqualTo("15");

        when(servicio.cambiar(any(), eq("torneos.cupos"), any()))
                .thenThrow(new ParametroRechazado(ParametroRechazado.Motivo.INALTERABLE, "Charter"));
        mvc.perform(put("/api/v1/parametros/torneos.cupos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("admin", ADMIN, "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"16\",\"motivo\":\"mas\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/inalterable"))
                .andExpect(jsonPath("$.motivo").value("INALTERABLE"));

        when(servicio.cambiar(any(), eq("chat.historial.tamano"), any()))
                .thenThrow(new ParametroRechazado(ParametroRechazado.Motivo.VALOR_INVALIDO, "fuera de rango"));
        mvc.perform(put("/api/v1/parametros/chat.historial.tamano")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("admin", ADMIN, "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"9999\",\"motivo\":\"mas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.motivo").value("VALOR_INVALIDO"));
    }

    @Test
    @DisplayName("un token de servicio llega al controlador como actor sin uid, y el servicio lo rechaza con 403")
    void servicioNoConfigura() throws Exception {
        when(servicio.cambiar(any(), any(), any()))
                .thenThrow(new ParametroRechazado(ParametroRechazado.Motivo.PERMISO_INSUFICIENTE, "solo administracion"));
        mvc.perform(put("/api/v1/parametros/chat.historial.tamano")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("salas-partidas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":\"10\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(servicio).cambiar(actor.capture(), any(), any());
        assertThat(actor.getValue().id()).isNull();
        assertThat(actor.getValue().puedeConfigurar()).isFalse();
    }
}
