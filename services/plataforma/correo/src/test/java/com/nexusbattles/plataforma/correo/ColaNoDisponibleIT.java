package com.nexusbattles.plataforma.correo;

import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La otra mitad de «202 = guardado»: con la base caida, la aplicacion entera
 * responde 503 y no un 202 de algo que nadie va a enviar, que es exactamente
 * lo que pasaba antes de B1 cuando el SMTP fallaba.
 *
 * <p>La base apunta a un puerto donde no escucha nadie. Flyway no corre (no
 * hay contra que) y el trabajador no esta programado: lo que se prueba es la
 * peticion.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ColaNoDisponibleIT {

    @DynamicPropertySource
    static void baseCaida(DynamicPropertyRegistry registro) {
        int puertoCerrado = puertoSinNadieEscuchando();
        registro.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:" + puertoCerrado + "/plataformadb");
        registro.add("spring.datasource.hikari.connection-timeout", () -> "1000");
        registro.add("spring.flyway.enabled", () -> "false");
        registro.add("correo.entrega.activa", () -> "false");
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    private static int puertoSinNadieEscuchando() {
        try (ServerSocket libre = new ServerSocket(0)) {
            return libre.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sin base de datos, pedir un correo responde 503 en problem details y nunca 202")
    void sinBaseDeDatosNoHay202() throws Exception {
        mockMvc.perform(post("/api/v1/correos/recuperacion-clave")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio("ms-identidad"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jugador@ejemplo.com","apodo":"ElGuerrero",
                                 "codigo":"482915","minutosVigencia":15}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/cola-de-correo-no-disponible"))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    @DisplayName("la evidencia de entrega tampoco inventa contadores sin base: 503")
    void laEvidenciaSinBaseTambienEs503() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/correos/envios")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + EmisorDeTokensDePrueba.emisor().tokenDeServicio("metricas-plataforma")))
                .andExpect(status().isServiceUnavailable());
    }
}
