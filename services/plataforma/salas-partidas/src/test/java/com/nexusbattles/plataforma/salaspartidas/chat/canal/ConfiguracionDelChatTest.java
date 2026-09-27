package com.nexusbattles.plataforma.salaspartidas.chat.canal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.nexusbattles.plataforma.salaspartidas.configuracion.ConfiguracionDeResiliencia;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * B12 — el cliente del chat hacia lista negra y sanciones sale con tiempos de
 * espera, como el resto de las llamadas salientes de este servicio.
 *
 * <p>Hasta B12 era el unico cliente de salas-partidas construido sin la fabrica
 * de {@link ConfiguracionDeResiliencia}: con moderacion-sanciones aceptando la
 * conexion y sin contestar, cada mensaje de chat, cada alta de sala y cada
 * ingreso (todos consultan la sancion activa) se quedaba colgado hasta que el
 * sistema operativo se rindiera, y el borde contestaba un 504 a los 60 s.
 */
class ConfiguracionDelChatTest {

    @Test
    @DisplayName("con moderacion-sanciones colgada, la consulta falla dentro de su tiempo de respuesta y no se queda esperando")
    void noSeQuedaColgado() throws Exception {
        ClientHttpRequestFactory fabrica =
                new ConfiguracionDeResiliencia().fabricaDePeticionesConTiempos(500, 300);
        RestClient cliente = new ConfiguracionDelChat().restClientChat(fabrica);

        // Un servidor que acepta la conexion (la completa el nucleo en la cola
        // de espera) y no lee ni contesta nunca: lo que hace un servicio colgado.
        try (ServerSocket mudo = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            String url = "http://127.0.0.1:" + mudo.getLocalPort() + "/api/v1/sanciones/usuarios/u-1/activa";

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThrows(ResourceAccessException.class,
                            () -> cliente.get().uri(url).retrieve().toBodilessEntity()));
        }
    }
}
