package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * La IP de origen del asiento de moderacion — B3: el primer valor de
 * {@code X-Forwarded-For}, o la remota; y nunca algo que no parezca una IP.
 */
class OrigenDeLaPeticionTest {

    private static MockHttpServletRequest peticion(String reenviada, String remota) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        if (reenviada != null) {
            peticion.addHeader("X-Forwarded-For", reenviada);
        }
        peticion.setRemoteAddr(remota);
        return peticion;
    }

    @Test
    @DisplayName("detras del borde, el primer valor de X-Forwarded-For")
    void primerValor() {
        assertEquals("198.51.100.23", OrigenDeLaPeticion.ipDe(peticion("198.51.100.23, 172.18.0.5", "172.18.0.9")));
        assertEquals("2001:db8::1", OrigenDeLaPeticion.ipDe(peticion(" 2001:db8::1 ", "172.18.0.9")));
    }

    @Test
    @DisplayName("sin la cabecera, o con basura en ella, la remota")
    void remota() {
        assertEquals("192.0.2.9", OrigenDeLaPeticion.ipDe(peticion(null, "192.0.2.9")));
        assertEquals("192.0.2.9", OrigenDeLaPeticion.ipDe(peticion("<script>", "192.0.2.9")));
        assertEquals("192.0.2.9", OrigenDeLaPeticion.ipDe(peticion("", "192.0.2.9")));
        assertEquals("192.0.2.9", OrigenDeLaPeticion.ipDe(peticion("1".repeat(60), "192.0.2.9")));
    }

    @Test
    @DisplayName("si ni la cabecera ni la remota lo parecen, no se inventa una")
    void ninguna() {
        assertNull(OrigenDeLaPeticion.ipDe(peticion("nombre-de-host", "nombre-de-host")));
    }

    @Test
    @DisplayName("solo ASCII: digitos de otros alfabetos o letras de ancho completo no son una IP")
    void soloAscii() {
        assertTrue(OrigenDeLaPeticion.pareceUnaIp("10.0.0.1"));
        assertTrue(OrigenDeLaPeticion.pareceUnaIp("fe80::ABCD"));
        assertFalse(OrigenDeLaPeticion.pareceUnaIp("١٠.٠.٠.١"));
        assertFalse(OrigenDeLaPeticion.pareceUnaIp("ＡＢ::１"));
        assertFalse(OrigenDeLaPeticion.pareceUnaIp("fe80::1%eth0"));
        assertFalse(OrigenDeLaPeticion.pareceUnaIp(null));
    }
}
