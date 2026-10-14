package com.nexusbattles.ms_identidad.rbac.service;

import com.nexusbattles.ms_identidad.auditoria.client.AuditoriaNoDisponibleException;
import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.rbac.model.Role;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * El cambio de rol solo sigue adelante si ms-cumplimiento lo registra
 * (HU-RBAC-003 / 7.3.5). Contra un servidor HTTP de verdad en un puerto
 * libre: se afirma lo que recibe cumplimiento y lo que pasa cuando no lo
 * acepta o no contesta.
 */
@DisplayName("AuditoriaCambioRolClient: el cambio de rol queda auditado o no ocurre")
class AuditoriaCambioRolClientTest {

    private HttpServer cumplimiento;
    private final AtomicReference<String> autorizacionRecibida = new AtomicReference<>();
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private volatile int respuesta = 201;

    @BeforeEach
    void levantarCumplimiento() throws IOException {
        cumplimiento = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        cumplimiento.createContext("/api/v1/admin/auditoria/eventos", intercambio -> {
            autorizacionRecibida.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            try (InputStream cuerpo = intercambio.getRequestBody()) {
                cuerpoRecibido.set(new String(cuerpo.readAllBytes(), StandardCharsets.UTF_8));
            }
            intercambio.sendResponseHeaders(respuesta, -1);
            intercambio.close();
        });
        cumplimiento.start();
    }

    @AfterEach
    void apagarCumplimiento() {
        cumplimiento.stop(0);
    }

    private String url() {
        return "http://" + cumplimiento.getAddress().getHostString() + ":" + cumplimiento.getAddress().getPort()
                + "/api/v1/admin/auditoria/eventos";
    }

    private static CredencialPropia credencialQuePone(String token) {
        CredencialPropia credencial = mock(CredencialPropia.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(token).when(credencial).portador();
        return credencial;
    }

    private AuditoriaCambioRolClient cliente() {
        return new AuditoriaCambioRolClient(url(), credencialQuePone("token-de-servicio"));
    }

    @Test
    @DisplayName("llega con la credencial, la cuenta por uid, los dos roles, el motivo y la ip")
    void registraConMotivoYUid() {
        cliente().registrarCambioRol("super_1", "uid-ana", Role.JUGADOR, Role.MODERADOR,
                "  Refuerzo del equipo de moderación  ", "10.0.0.5");

        assertThat(autorizacionRecibida.get()).isEqualTo("Bearer token-de-servicio");
        assertThat(cuerpoRecibido.get())
                .contains("\"tipoAccion\":\"CAMBIO_ROL\"")
                .contains("\"administradorId\":\"super_1\"")
                .contains("\"afectado\":\"uid-ana\"")
                .contains("\"valorAnterior\":\"JUGADOR\"")
                .contains("\"valorNuevo\":\"MODERADOR\"")
                .contains("\"motivo\":\"Refuerzo del equipo de moderación\"")
                .contains("\"ipOrigen\":\"10.0.0.5\"");
    }

    @Test
    @DisplayName("la firma antigua (id numerico, sin motivo) sigue auditando, con motivo e ip por omision")
    void firmaAntigua() {
        cliente().registrarCambioRol("super_1", 42L, Role.MODERADOR, Role.ADMINISTRADOR, null);

        assertThat(cuerpoRecibido.get())
                .contains("\"afectado\":\"42\"")
                .contains("\"motivo\":\"Asignación administrativa de rol\"")
                .contains("\"ipOrigen\":\"DESCONOCIDA\"");
    }

    @Test
    @DisplayName("un motivo en blanco y una ip en blanco no viajan vacios")
    void enBlanco() {
        cliente().registrarCambioRol("super_1", "uid-ana", Role.JUGADOR, Role.MODERADOR, "   ", " ");

        assertThat(cuerpoRecibido.get())
                .contains("\"motivo\":\"Asignación administrativa de rol\"")
                .contains("\"ipOrigen\":\"DESCONOCIDA\"");
    }

    @Test
    @DisplayName("si cumplimiento no lo acepta (200 en vez de 201), no hay cambio de rol: 503")
    void noAceptado() {
        respuesta = 200;

        assertThatThrownBy(() -> cliente().registrarCambioRol("super_1", "uid-ana", Role.JUGADOR,
                Role.MODERADOR, "motivo", "10.0.0.5"))
                .isInstanceOf(AuditoriaNoDisponibleException.class)
                .hasMessageContaining("no lo aceptó");
    }

    @Test
    @DisplayName("si cumplimiento rechaza (500) o no contesta, tampoco: 503 con «inténtalo de nuevo»")
    void sinRespuesta() {
        respuesta = 500;
        AuditoriaCambioRolClient cliente = cliente();
        assertThatThrownBy(() -> cliente.registrarCambioRol("super_1", "uid-ana", Role.JUGADOR,
                Role.MODERADOR, "motivo", "10.0.0.5"))
                .isInstanceOf(AuditoriaNoDisponibleException.class)
                .hasMessageContaining("no respondió");

        cumplimiento.stop(0);
        assertThatThrownBy(() -> cliente.registrarCambioRol("super_1", "uid-ana", Role.JUGADOR,
                Role.MODERADOR, "motivo", "10.0.0.5"))
                .isInstanceOf(AuditoriaNoDisponibleException.class);
    }

    @Test
    @DisplayName("con 201 no se lanza nada")
    void aceptado() {
        assertThatCode(() -> cliente().registrarCambioRol("super_1", "uid-ana", Role.JUGADOR,
                Role.MODERADOR, "motivo", "10.0.0.5")).doesNotThrowAnyException();
    }
}
