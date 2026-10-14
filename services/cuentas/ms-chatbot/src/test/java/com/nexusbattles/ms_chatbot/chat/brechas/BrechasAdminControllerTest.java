package com.nexusbattles.ms_chatbot.chat.brechas;

import com.nexusbattles.ms_chatbot.chat.analitica.PeriodoDelPanel;
import com.nexusbattles.ms_chatbot.chat.model.BrechaConocimiento;
import com.nexusbattles.ms_chatbot.chat.model.EstadoBrecha;
import com.nexusbattles.ms_chatbot.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ms-chatbot.yaml 1.7.0: /chatbot/admin/brechas, con la seguridad real del panel.
@WebMvcTest(BrechasAdminController.class)
@Import(SecurityConfig.class)
class BrechasAdminControllerTest {

    private static final String RUTA = "/chatbot/admin/brechas";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrechasAdminService servicio;

    private static RequestPostProcessor administrador() {
        return jwt().jwt(j -> j.claim("uid", "uid-del-admin").subject("apodo-admin"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static BrechaConocimiento brecha() {
        BrechaConocimiento brecha = new BrechaConocimiento("como gano gemas", "¿Cómo gano gemas?");
        brecha.registrarEscalamiento();
        brecha.registrarEscalamiento();
        brecha.registrarNoUtil();
        return brecha;
    }

    @Test
    void listarDevuelveLaPaginaConElTotalYSinElTextoNormalizado() throws Exception {
        when(servicio.listar(eq(new PeriodoDelPanel(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))),
            eq(EstadoBrecha.PENDIENTE), eq(1), eq(16)))
            .thenReturn(new PageImpl<>(List.of(brecha()), PageRequest.of(1, 16), 17));

        mockMvc.perform(get(RUTA).param("desde", "2026-09-01").param("hasta", "2026-09-30")
                .param("estado", "PENDIENTE").param("pagina", "1").param("tamano", "16").with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElementos").value(17))
            .andExpect(jsonPath("$.pagina").value(1))
            .andExpect(jsonPath("$.tamano").value(16))
            .andExpect(jsonPath("$.contenido[0].pregunta").value("¿Cómo gano gemas?"))
            .andExpect(jsonPath("$.contenido[0].escalamientos").value(2))
            .andExpect(jsonPath("$.contenido[0].noUtiles").value(1))
            .andExpect(jsonPath("$.contenido[0].total").value(3))
            .andExpect(jsonPath("$.contenido[0].estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.contenido[0].reaperturas").value(0))
            .andExpect(jsonPath("$.contenido[0].textoNormalizado").doesNotExist());
    }

    // Sin fechas, los ultimos 30 dias; sin estado, todas.
    @Test
    void sinParametros_losUltimosTreintaDiasDeTodosLosEstados() throws Exception {
        when(servicio.listar(any(), isNull(), eq(0), eq(20))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(RUTA).with(administrador()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido").isEmpty());

        verify(servicio).listar(eq(PeriodoDelPanel.resolver(null, null)), isNull(), eq(0), eq(20));
    }

    @Test
    void unEstadoDesconocidoOUnTamanoFueraDeRangoEs400() throws Exception {
        mockMvc.perform(get(RUTA).param("estado", "RESUELTA").with(administrador()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(RUTA).param("tamano", "101").with(administrador()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(RUTA).param("desde", "ayer").with(administrador()))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    // Quien marca sale del claim `uid` del token, nunca del cuerpo.
    @Test
    void cambiarEstado_tomaQuienDelToken() throws Exception {
        UUID id = UUID.randomUUID();
        BrechaConocimiento atendida = brecha();
        atendida.cambiarEstado(EstadoBrecha.ATENDIDA, "uid-del-admin", Instant.parse("2026-10-14T15:00:00Z"));
        when(servicio.cambiarEstado(id, EstadoBrecha.ATENDIDA, "uid-del-admin")).thenReturn(atendida);

        mockMvc.perform(patch(RUTA + "/" + id).with(administrador())
                .contentType("application/json")
                .content("{\"estado\":\"ATENDIDA\",\"estadoCambiadoPor\":\"otro\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("ATENDIDA"))
            .andExpect(jsonPath("$.estadoCambiadoPor").value("uid-del-admin"))
            .andExpect(jsonPath("$.estadoCambiadoEn").value("2026-10-14T15:00:00Z"));
    }

    @Test
    void cambiarEstadoSinEstadoEs400() throws Exception {
        mockMvc.perform(patch(RUTA + "/" + UUID.randomUUID()).with(administrador())
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(servicio);
    }

    @Test
    void cambiarEstadoDeUnaQueNoExisteEs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(servicio.cambiarEstado(eq(id), any(), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe."));

        mockMvc.perform(patch(RUTA + "/" + id).with(administrador())
                .contentType("application/json").content("{\"estado\":\"DESCARTADA\"}"))
            .andExpect(status().isNotFound());
    }

    // El panel no degrada a visitante: un jugador recibe 403 y sin token, 401.
    @Test
    void unJugadorRecibe403YSinTokenEs401() throws Exception {
        mockMvc.perform(get(RUTA).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_JUGADOR"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(patch(RUTA + "/" + UUID.randomUUID())
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MODERADOR")))
                .contentType("application/json").content("{\"estado\":\"ATENDIDA\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test
    void elSuperAdministradorTambienEntra() throws Exception {
        when(servicio.listar(any(), any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(RUTA).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMINISTRADOR"))))
            .andExpect(status().isOk());
    }

    // Un token sin `uid` usa su `sub`; sin autenticacion, nadie.
    @Test
    void quienAdministraSinUid_usaElSub() {
        var conUid = new JwtAuthenticationToken(
            Jwt.withTokenValue("t").header("alg", "none")
                .claim("uid", "uid-1").subject("apodo").build());
        var sinUid = new JwtAuthenticationToken(
            Jwt.withTokenValue("t").header("alg", "none")
                .claim("uid", " ").subject("apodo").build());

        assertThat(BrechasAdminController.quienAdministra(conUid)).isEqualTo("uid-1");
        assertThat(BrechasAdminController.quienAdministra(sinUid)).isEqualTo("apodo");
        assertThat(BrechasAdminController.quienAdministra(
            new UsernamePasswordAuthenticationToken("otro", "x"))).isEqualTo("otro");
        assertThat(BrechasAdminController.quienAdministra(null)).isNull();
    }
}
