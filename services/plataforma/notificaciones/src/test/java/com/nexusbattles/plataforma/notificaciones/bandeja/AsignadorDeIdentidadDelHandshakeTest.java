package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;

/**
 * Pruebas de la identidad provisional del handshake.
 *
 * De esto depende que la cola privada exista: sin Principal el broker no
 * tiene a quien entregarle, y sin el atributo de sesion la desconexion no
 * sabe que cerrar.
 */
class AsignadorDeIdentidadDelHandshakeTest {

    private final AsignadorDeIdentidadDelHandshake asignador =
            new AsignadorDeIdentidadDelHandshake();

    private ServerHttpRequest solicitud(String uri) {
        ServerHttpRequest solicitud = mock(ServerHttpRequest.class);
        when(solicitud.getURI()).thenReturn(URI.create(uri));
        return solicitud;
    }

    @Test
    @DisplayName("con usuario y sesion en la URL hay principal y atributos completos")
    void conUsuarioYSesionHayPrincipalYAtributos() {
        Map<String, Object> atributos = new HashMap<>();

        Principal principal = asignador.determineUser(
                solicitud("ws://localhost:8085/ws?usuario=jugador-1&sesion=movil"),
                null, atributos);

        assertEquals("jugador-1", principal.getName());
        assertEquals("jugador-1", atributos.get(AsignadorDeIdentidadDelHandshake.ATRIBUTO_USUARIO));
        assertEquals("movil", atributos.get(AsignadorDeIdentidadDelHandshake.ATRIBUTO_SESION));
    }

    @Test
    @DisplayName("sin usuario no hay principal ni atributos, la conexion queda anonima")
    void sinUsuarioNoHayPrincipal() {
        Map<String, Object> atributos = new HashMap<>();

        Principal principal = asignador.determineUser(
                solicitud("ws://localhost:8085/ws"), null, atributos);

        assertNull(principal);
        assertTrue(atributos.isEmpty());
    }

    @Test
    @DisplayName("con usuario pero sin sesion hay principal y solo el atributo del usuario")
    void sinSesionSoloQuedaElUsuario() {
        Map<String, Object> atributos = new HashMap<>();

        Principal principal = asignador.determineUser(
                solicitud("ws://localhost:8085/ws?usuario=jugador-1"), null, atributos);

        assertEquals("jugador-1", principal.getName());
        assertEquals(1, atributos.size());
    }
}
