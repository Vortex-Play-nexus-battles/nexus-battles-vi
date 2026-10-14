package com.nexusbattles.plataforma.adminparametros.parametros;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * B12 — el cliente de auditoria sale con tiempos de espera; SA-5 — y ya no
 * corre dentro de la peticion del administrador.
 *
 * <p>Con ms-cumplimiento aceptando la conexion y sin contestar, la entrega
 * corta en su tiempo de respuesta y la version queda pendiente para la
 * siguiente vuelta.
 */
@DisplayName("Configuracion de parametros: la auditoria no se queda colgada (B12, SA-5)")
class ConfiguracionDeParametrosTest {

    private static final ObjectProvider<InterceptorDePortadorDeServicio> SIN_CREDENCIAL =
            new DefaultListableBeanFactory().getBeanProvider(InterceptorDePortadorDeServicio.class);

    @Test
    @DisplayName("con la auditoria colgada, la entrega corta dentro de su tiempo de respuesta y se reintenta despues")
    void auditoriaColgadaNoCuelga() throws Exception {
        Version v = new Version(1L, "chat.historial.tamano", 2, "50", "100", "Mas historial", UUID.randomUUID(),
                OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC));

        // Acepta la conexion (la completa el nucleo) y no contesta nunca.
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ClienteAuditoria cliente = ConfiguracionDeParametros.clienteAuditoria(
                    "http://127.0.0.1:" + mudo.getLocalPort() + "/api/v1/admin/auditoria/eventos",
                    500, 300, SIN_CREDENCIAL);

            assertThat(cliente).isNotNull();
            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThatThrownBy(() -> cliente.entregar(v)).isInstanceOf(RuntimeException.class));
        }
    }

    @Test
    @DisplayName("sin URL no hay cliente: las versiones esperan y cada cambio queda en la bitacora JSON")
    void sinUrl() {
        VersionRepository versiones = mock(VersionRepository.class);
        EnvioDeAuditoria envio = new ConfiguracionDeParametros().envioDeAuditoria(versiones, Clock.systemUTC(), " ",
                2000, 5000, 10000, 900000, SIN_CREDENCIAL);

        assertThat(envio.ejecutar()).isZero();
        verifyNoInteractions(versiones);
        assertThat(new ConfiguracionDeParametros().auditoria()).isInstanceOf(AuditoriaEnBitacora.class);
    }
}
