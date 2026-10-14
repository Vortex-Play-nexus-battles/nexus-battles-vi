package com.nexusbattles.ms_identidad.admin.controller;

import com.nexusbattles.ms_identidad.admin.service.AdminGestionUsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SA-21 — la suspension de la consola, por HTTP con el conversor JSON de
 * Spring: la fecha fin con zona llega al servicio como el mismo instante en la
 * hora del servidor (antes, la hora de Colombia se leia como hora del servidor
 * y la suspension terminaba cinco horas antes). El permiso lo prueban las
 * pruebas del interceptor; aqui solo el cuerpo.
 */
@DisplayName("La fecha fin de una suspension por HTTP, con su zona (SA-21)")
class SuspensionConZonaHttpTest {

    private AdminGestionUsuarioService servicio;
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        servicio = mock(AdminGestionUsuarioService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminGestionUsuarioController(servicio)).build();
    }

    private LocalDateTime recibida(String fecha) throws Exception {
        mvc.perform(put("/api/v1/admin/usuarios/15/suspender")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suspendidoHasta\":\"" + fecha + "\",\"motivo\":\"Lenguaje ofensivo reiterado\","
                                + "\"causal\":\"LENGUAJE_OFENSIVO\"}"))
                .andExpect(status().isNoContent());
        ArgumentCaptor<LocalDateTime> hasta = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(servicio).suspenderCuenta(eq(15L), hasta.capture(), any(), any(), any(), any(), any(), any(), any());
        return hasta.getValue();
    }

    @Test
    @DisplayName("lo que manda la consola (toISOString) es el mismo instante en la hora del servidor")
    void instanteDeLaConsola() throws Exception {
        assertThat(recibida("2099-10-15T15:00:00.000Z")).isEqualTo(OffsetDateTime.parse("2099-10-15T15:00:00Z")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
    }

    @Test
    @DisplayName("con la zona de Colombia: el mismo instante (sin el conversor de SA-21 seria un 400)")
    void horaDeColombia() throws Exception {
        assertThat(recibida("2099-10-15T10:00:00-05:00")).isEqualTo(OffsetDateTime.parse("2099-10-15T15:00:00Z")
                .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
    }

    @Test
    @DisplayName("una hora sin zona se sigue leyendo como hora del servidor")
    void horaSinZona() throws Exception {
        assertThat(recibida("2099-10-15T10:00")).isEqualTo(LocalDateTime.of(2099, 10, 15, 10, 0));
    }
}
