package com.nexusbattles.ms_finanzas.pagos.correo;

import com.nexusbattles.comun.seguridad.servicio.InterceptorDePortadorDeServicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * B12 — el cliente hacia el servicio de correo sale con tiempos de espera.
 *
 * <p>La confirmacion de compra tiene reintento y cortacircuitos
 * (resilience4j), pero los dos cuentan fallos, y sin tiempo de lectura un
 * correo que acepta la conexion y no contesta nunca FALLA: el pago ya
 * aprobado se quedaba esperando dentro de la peticion del jugador.
 */
@DisplayName("Cliente de correo de pagos: con tiempos de espera (B12)")
class ConfiguracionClientesHttpPagosTest {

    private static final ObjectProvider<InterceptorDePortadorDeServicio> SIN_CREDENCIAL =
            new DefaultListableBeanFactory().getBeanProvider(InterceptorDePortadorDeServicio.class);

    @Test
    @DisplayName("con el correo colgado, la llamada falla dentro de su tiempo de respuesta y el reintento puede actuar")
    void correoColgadoNoCuelgaElPago() throws Exception {
        RestClient cliente = new ConfiguracionClientesHttpPagos()
                .restClientCorreo(RestClient.builder(), SIN_CREDENCIAL, 500, 300);

        // Acepta la conexion (la completa el nucleo) y no contesta nunca.
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            String url = "http://127.0.0.1:" + mudo.getLocalPort() + "/api/v1/correos/confirmacion-compra";

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThrows(ResourceAccessException.class,
                            () -> cliente.post().uri(url).body("{}").retrieve().toBodilessEntity()));
        }
    }
}
