package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * De donde salen las claves con las que se firman los tokens (ADR-002).
 *
 * <p>Importa distinguir los dos modos: con la clave configurada, un reinicio
 * o una segunda instancia siguen validando los tokens ya emitidos; sin ella,
 * cada arranque genera un par nuevo y los tokens anteriores dejan de servir.
 */
class ClavesDeFirmaTest {

    @Test
    @DisplayName("sin clave configurada genera un par utilizable")
    void generaParEfimero() {
        ClavesDeFirma claves = new ClavesDeFirma("");

        assertAll(
                () -> assertNotNull(claves.privada()),
                () -> assertNotNull(claves.publica()),
                () -> assertEquals("RSA", claves.publica().getAlgorithm()),
                () -> assertNotNull(claves.identificador())
        );
    }

    @Test
    @DisplayName("dos arranques sin clave configurada no comparten par")
    void elParEfimeroNoSobreviveAlReinicio() {
        // Esta es la consecuencia que el servicio avisa por bitacora: sin
        // app.jwt.clave-privada, reiniciar invalida los tokens emitidos.
        assertNotEquals(new ClavesDeFirma("").identificador(), new ClavesDeFirma("").identificador());
    }

    @Test
    @DisplayName("con la clave configurada, el par es siempre el mismo")
    void usaLaClaveConfigurada() throws Exception {
        String clave = clavePrivadaEnBase64();

        ClavesDeFirma primera = new ClavesDeFirma(clave);
        ClavesDeFirma segunda = new ClavesDeFirma(clave);

        assertAll(
                () -> assertEquals(primera.identificador(), segunda.identificador()),
                () -> assertEquals(primera.publica().getModulus(), segunda.publica().getModulus()),
                () -> assertEquals(primera.publica().getPublicExponent(), segunda.publica().getPublicExponent())
        );
    }

    @Test
    @DisplayName("admite la clave con cabeceras PEM y saltos de linea")
    void admiteFormatoPem() throws Exception {
        String base64 = clavePrivadaEnBase64();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + base64.replaceAll("(.{64})", "$1\n")
                + "\n-----END PRIVATE KEY-----\n";

        assertEquals(new ClavesDeFirma(base64).identificador(), new ClavesDeFirma(pem).identificador());
    }

    @Test
    @DisplayName("una clave ilegible falla al arrancar, con instrucciones")
    void claveInvalida() {
        // Falla al construir el bean, no en la primera peticion: un servicio
        // que no puede firmar no debe quedarse arriba aparentando que si.
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> new ClavesDeFirma("esto-no-es-una-clave"));

        assertTrue(error.getMessage().contains("openssl"),
                "el mensaje debe decir como generar una clave valida: " + error.getMessage());
    }

    private static String clavePrivadaEnBase64() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        return Base64.getEncoder().encodeToString(generador.generateKeyPair().getPrivate().getEncoded());
    }
}
