package com.nexusbattles.plataforma.adminparametros.parametros;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * B12 — el cliente de auditoria sale con tiempos de espera.
 *
 * <p>La auditoria es fail-open (si ms-cumplimiento no responde, el cambio
 * queda en el historial local y en la bitacora), pero sin tiempo de lectura
 * "no responde" nunca llegaba: con ms-cumplimiento aceptando la conexion y
 * sin contestar, el cambio de un parametro se quedaba colgado dentro de la
 * peticion del administrador hasta que el borde cortaba a los 60 s.
 */
@DisplayName("Configuracion de parametros: la auditoria no deja colgado un cambio (B12)")
class ConfiguracionDeParametrosTest {

    private static final ObjectProvider<InterceptorDePortadorDeServicio> SIN_CREDENCIAL =
            new DefaultListableBeanFactory().getBeanProvider(InterceptorDePortadorDeServicio.class);

    @Test
    @DisplayName("con la auditoria colgada, registrar vuelve dentro de su tiempo de respuesta (fail-open)")
    void auditoriaColgadaNoCuelgaElCambio() throws Exception {
        UUID admin = UUID.randomUUID();
        Version v = new Version(1L, "chat.historial.tamano", 2, "50", "100", "Mas historial", admin,
                OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC));

        // Acepta la conexion (la completa el nucleo) y no contesta nunca.
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Auditoria auditoria = new ConfiguracionDeParametros().auditoria(
                    "http://127.0.0.1:" + mudo.getLocalPort() + "/api/v1/admin/auditoria/eventos",
                    500, 300, SIN_CREDENCIAL);

            assertThat(auditoria).isInstanceOf(ClienteAuditoria.class);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> auditoria.registrar(v));
        }
    }

    @Test
    @DisplayName("sin URL no hay cliente HTTP: el cambio queda en la bitacora")
    void sinUrlBitacora() {
        assertThat(new ConfiguracionDeParametros().auditoria("", 2000, 5000, SIN_CREDENCIAL))
                .isInstanceOf(AuditoriaEnBitacora.class);
    }
}
