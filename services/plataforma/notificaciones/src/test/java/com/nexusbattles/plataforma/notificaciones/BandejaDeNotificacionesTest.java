package com.nexusbattles.plataforma.notificaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de HU-NOT-006 - Notificaciones en tiempo real.
 *
 * Fuente: Proyecto Integrador II, seccion 7.7.13, p. 57 y seccion 7.8.12, p. 65.
 * La entrega debe ser en tiempo real y el estado de lectura tiene que quedar
 * sincronizado entre todas las sesiones simultaneas del mismo usuario.
 */
class BandejaDeNotificacionesTest {

    private static Notificacion aviso(String id) {
        return new Notificacion(id, "Subasta", "Te superaron la puja",
                "Alguien ofrecio mas por el objeto que seguias.",
                Instant.parse("2026-08-26T14:00:00Z"));
    }

    @Test
    @DisplayName("el aviso llega a las tres sesiones abiertas y la lectura es la misma en todas")
    void entregaATodasLasSesionesYSincronizaLaLectura() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.de("jugador-1");
        bandeja.abrirSesion("computador");
        bandeja.abrirSesion("celular");
        bandeja.abrirSesion("tableta");

        Set<String> destinos = bandeja.recibir(aviso("aviso-1"));

        assertEquals(Set.of("computador", "celular", "tableta"), destinos);
        assertEquals(1, bandeja.noLeidas());

        bandeja.marcarLeida("aviso-1");

        assertTrue(bandeja.estaLeida("aviso-1"));
        assertEquals(0, bandeja.noLeidas());
        assertTrue(bandeja.pendientesDeEntrega("computador").isEmpty());
        assertTrue(bandeja.pendientesDeEntrega("celular").isEmpty());
        assertTrue(bandeja.pendientesDeEntrega("tableta").isEmpty());
    }

    @Test
    @DisplayName("sin sesiones abiertas el aviso queda pendiente y se entrega en el siguiente ingreso")
    void guardaElAvisoCuandoNoHaySesionesYLoEntregaAlVolver() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.de("jugador-2");

        Set<String> destinos = bandeja.recibir(aviso("aviso-1"));

        assertTrue(destinos.isEmpty());
        assertEquals(1, bandeja.noLeidas());

        bandeja.abrirSesion("computador");
        List<Notificacion> entregados = bandeja.entregarPendientes("computador");

        assertEquals(List.of(aviso("aviso-1")), entregados);
        assertTrue(bandeja.pendientesDeEntrega("computador").isEmpty());
    }

    @Test
    @DisplayName("tras una caida la sesion recupera lo que se perdio y su cuenta vuelve a coincidir")
    void reconciliaElEstadoCuandoLaSesionRecuperaLaConexion() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.de("jugador-3");
        bandeja.abrirSesion("computador");
        bandeja.abrirSesion("celular");

        bandeja.recibir(aviso("aviso-1"));
        bandeja.marcarLeida("aviso-1");

        bandeja.cerrarSesion("celular");
        bandeja.recibir(aviso("aviso-2"));
        bandeja.recibir(aviso("aviso-3"));

        assertFalse(bandeja.sesionesActivas().contains("celular"));
        assertEquals(List.of(aviso("aviso-2"), aviso("aviso-3")),
                bandeja.pendientesDeEntrega("celular"));

        bandeja.abrirSesion("celular");
        List<Notificacion> recuperados = bandeja.entregarPendientes("celular");

        assertEquals(List.of(aviso("aviso-2"), aviso("aviso-3")), recuperados);
        assertTrue(bandeja.pendientesDeEntrega("celular").isEmpty());
        assertEquals(2, bandeja.noLeidas());
    }

    @Test
    @DisplayName("la bandeja rechaza usuarios, sesiones y avisos invalidos o repetidos")
    void rechazaEntradasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> BandejaDeNotificaciones.de(" "));

        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.de("jugador-4");
        assertThrows(IllegalArgumentException.class, () -> bandeja.abrirSesion(""));
        assertThrows(IllegalArgumentException.class,
                () -> bandeja.marcarLeida("aviso-inexistente"));

        bandeja.recibir(aviso("aviso-1"));
        assertThrows(IllegalArgumentException.class, () -> bandeja.recibir(aviso("aviso-1")));

        assertThrows(IllegalArgumentException.class,
                () -> new Notificacion("aviso-2", "Subasta", " ", "cuerpo", Instant.now()));
        assertThrows(UnsupportedOperationException.class,
                () -> bandeja.historial().add(aviso("intruso")));
    }
}
