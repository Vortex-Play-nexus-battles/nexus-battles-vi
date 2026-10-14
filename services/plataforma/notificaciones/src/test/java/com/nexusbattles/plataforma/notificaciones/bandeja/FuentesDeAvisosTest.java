package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SA-11 — las fuentes de avisos se intentan todas, en orden, aunque una rompa su contrato. */
@DisplayName("Notificaciones · fuentes de avisos juntas")
class FuentesDeAvisosTest {

    @Test
    @DisplayName("el catalogo y despues las politicas; si una lanza, la otra se intenta igual")
    void todas() {
        List<String> llamadas = new CopyOnWriteArrayList<>();
        AvisosPorIncorporar catalogo = usuarioId -> {
            llamadas.add("catalogo:" + usuarioId);
            throw new IllegalStateException("rompio su contrato");
        };
        AvisosPorIncorporar politicas = usuarioId -> llamadas.add("politicas:" + usuarioId);

        AvisosPorIncorporar juntas = new FuentesDeAvisos().avisosPorIncorporar(catalogo, politicas);

        assertDoesNotThrow(() -> juntas.incorporar("jugador-1"));
        assertEquals(List.of("catalogo:jugador-1", "politicas:jugador-1"), llamadas);
        assertDoesNotThrow(() -> AvisosPorIncorporar.todas(List.of()).incorporar("jugador-1"));
    }
}
