package com.nexusbattles.ms_chatbot.chat.soporte;

import com.nexusbattles.ms_chatbot.chat.identidad.IdentidadDelChat;
import com.nexusbattles.ms_chatbot.chat.moderacion.ModeracionDeContenido;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ms-chatbot.yaml 1.3.9 — tickets de soporte contra PostgreSQL real, con V6
 * y su indice unico parcial: un jugador solo puede tener un ticket ABIERTO o
 * EN_PROCESO. Reabrir uno cuando ya tiene otro abierto es 409, no un 500.
 * Solo ese indice es 409: otro error de la base (un texto que no cabe en su
 * columna) se relanza tal cual.
 */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@DisplayName("Chatbot · tickets de soporte contra PostgreSQL (1.3.9)")
class TicketsDeSoporteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TicketSoporteRepository tickets;

    @Autowired
    private TicketSoporteService servicio;

    @Autowired
    private TicketSoporteAdminService servicioAdmin;

    // La lista negra es otro servicio: aqui aprueba todo.
    @MockitoBean
    private ModeracionDeContenido moderacion;

    private static RequestPostProcessor administrador() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static TicketSoporte nuevo(String uid, String asunto) {
        return TicketSoporte.abrir(uid, Categoria.SOPORTE_TECNICO, asunto, "Detalle", List.of(), Instant.now());
    }

    private TicketSoporte resuelto(String uid) {
        TicketSoporte ticket = nuevo(uid, "Primero");
        ticket.atender(EstadoTicket.RESUELTO, "Ya quedo", null, false, Instant.now());
        return tickets.saveAndFlush(ticket);
    }

    @Test
    @DisplayName("la base no deja que un jugador tenga dos tickets abiertos, y el error se reconoce como el de V6")
    void elIndiceUnicoImpideDosAbiertos() {
        String uid = UUID.randomUUID().toString();
        tickets.saveAndFlush(nuevo(uid, "Uno"));

        assertThatThrownBy(() -> tickets.saveAndFlush(nuevo(uid, "Otro")))
            .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                error -> assertThat(IndiceDeTicketAbierto.loIncumple(error)).isTrue());
    }

    // 7.4.8: con el redactor real de plataforma (chat.privacidad), lo que se
    // guarda ya no lleva ni la contrasena ni la tarjeta.
    @Test
    @DisplayName("el ticket se guarda con la contrasena y la tarjeta tapadas por el redactor real")
    void elTicketSeGuardaRedactado() {
        IdentidadDelChat jugador = IdentidadDelChat.usuario(UUID.randomUUID(), "token");

        TicketSoporte abierto = servicio.abrir(jugador, Categoria.SOPORTE_TECNICO, "No puedo entrar",
            "Mi contraseña: abc123 y la tarjeta 4111 1111 1111 1111");

        assertThat(tickets.findById(abierto.getId())).hasValueSatisfying(guardado ->
            assertThat(guardado.getMensaje()).isEqualTo("Mi contraseña: *** y la tarjeta [tarjeta]"));
    }

    // Revision de plataforma: un texto que no cabe en su columna tambien es un
    // error de integridad, pero no es "ya tienes un ticket abierto".
    @Test
    @DisplayName("un asunto mas largo que su columna no sale como TICKET_ABIERTO")
    void unAsuntoMuyLargoNoEsTicketAbierto() {
        IdentidadDelChat jugador = IdentidadDelChat.usuario(UUID.randomUUID(), "token");
        String asuntoDemasiadoLargo = "x".repeat(151);

        assertThatThrownBy(() -> servicio.abrir(jugador, Categoria.SOPORTE_TECNICO, asuntoDemasiadoLargo, "Detalle"))
            .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                error -> assertThat(IndiceDeTicketAbierto.loIncumple(error)).isFalse());
    }

    @Test
    @DisplayName("una respuesta mas larga que su columna no sale como OTRO_TICKET_ABIERTO")
    void unaRespuestaMuyLargaNoEsOtroTicketAbierto() {
        TicketSoporte abierto = tickets.saveAndFlush(nuevo(UUID.randomUUID().toString(), "Abierto"));
        String respuestaDemasiadoLarga = "x".repeat(2001);

        assertThatThrownBy(() -> servicioAdmin.atender(abierto.getId(), null, respuestaDemasiadoLarga, null, false))
            .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                error -> assertThat(IndiceDeTicketAbierto.loIncumple(error)).isFalse());
    }

    @Test
    @DisplayName("reabrir con otro ticket abierto responde 409 OTRO_TICKET_ABIERTO y no cambia nada")
    void reabrirConOtroAbiertoEs409() throws Exception {
        String uid = UUID.randomUUID().toString();
        TicketSoporte primero = resuelto(uid);
        tickets.saveAndFlush(nuevo(uid, "Segundo"));

        mvc.perform(patch("/chatbot/admin/tickets/" + primero.getId()).with(administrador())
                .contentType("application/json")
                .content("{\"estado\":\"EN_PROCESO\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.motivo").value("OTRO_TICKET_ABIERTO"));

        assertThat(tickets.findById(primero.getId()))
            .hasValueSatisfying(t -> assertThat(t.getEstado()).isEqualTo(EstadoTicket.RESUELTO));
    }

    @Test
    @DisplayName("reabrir sin otro ticket abierto funciona")
    void reabrirSinOtroAbiertoFunciona() throws Exception {
        TicketSoporte primero = resuelto(UUID.randomUUID().toString());

        mvc.perform(patch("/chatbot/admin/tickets/" + primero.getId()).with(administrador())
                .contentType("application/json")
                .content("{\"estado\":\"EN_PROCESO\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("EN_PROCESO"));
    }
}
