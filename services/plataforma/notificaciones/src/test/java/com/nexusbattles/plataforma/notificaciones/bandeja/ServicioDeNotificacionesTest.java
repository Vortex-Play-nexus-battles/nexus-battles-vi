package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexusbattles.plataforma.notificaciones.BandejaDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Pruebas de la orquestacion de HU-NOT-006 sobre el dominio ya probado.
 *
 * Las reglas de la bandeja tienen sus propias pruebas; aqui se verifica lo que
 * agrega esta capa: que la bandeja se carga de la base antes de decidir, que lo
 * decidido se guarda, y que el canal solo se toca cuando algo si quedo guardado.
 * Los tres escenarios del archivo de aceptacion tienen su caso.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeNotificacionesTest {

    private static final Instant AYER = Instant.parse("2026-08-30T15:00:00Z");
    private static final String JUGADOR = "jugador-1";

    @Mock
    private RepositorioDeBandejas repositorio;

    @Mock
    private CanalDeNotificaciones canal;

    @InjectMocks
    private ServicioDeNotificaciones servicio;

    private static Notificacion aviso(String id) {
        return new Notificacion(id, "subasta", "Tu puja fue superada",
                "Alguien pujo mas alto por la Espada del Alba.", AYER);
    }

    @Test
    @DisplayName("el aviso llega a las tres sesiones abiertas del jugador")
    void entregaATodasLasSesionesAbiertas() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.reconstituir(
                JUGADOR, List.of(), Set.of(), Set.of("movil", "escritorio", "tablet"), Map.of());
        when(repositorio.existeAviso(JUGADOR, "evt-1")).thenReturn(false);
        when(repositorio.cargar(JUGADOR)).thenReturn(bandeja);

        Set<String> notificadas = servicio.emitir(JUGADOR, aviso("evt-1"));

        assertEquals(Set.of("movil", "escritorio", "tablet"), notificadas);
        verify(repositorio).guardarAviso(eq(JUGADOR), any(Notificacion.class));
        verify(repositorio).registrarEntregas(JUGADOR, "evt-1", notificadas);
        verify(canal).avisar(eq(JUGADOR), any(Notificacion.class), anyInt());
    }

    @Test
    @DisplayName("sin sesiones abiertas el aviso se guarda igual y queda pendiente")
    void guardaElAvisoAunqueNoHayaSesiones() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.reconstituir(
                JUGADOR, List.of(), Set.of(), Set.of(), Map.of());
        when(repositorio.existeAviso(JUGADOR, "evt-2")).thenReturn(false);
        when(repositorio.cargar(JUGADOR)).thenReturn(bandeja);

        Set<String> notificadas = servicio.emitir(JUGADOR, aviso("evt-2"));

        assertTrue(notificadas.isEmpty());
        verify(repositorio).guardarAviso(eq(JUGADOR), any(Notificacion.class));
    }

    @Test
    @DisplayName("al reconectar, la sesion recibe unicamente lo que se perdio")
    void alReconectarEntregaSoloLoPerdido() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.reconstituir(
                JUGADOR,
                List.of(aviso("visto"), aviso("perdido")),
                Set.of(),
                Set.of(),
                Map.of("movil", Set.of("visto")));
        when(repositorio.cargar(JUGADOR)).thenReturn(bandeja);

        List<Notificacion> entregados = servicio.registrarSesion(JUGADOR, "movil");

        assertEquals(1, entregados.size());
        assertEquals("perdido", entregados.get(0).id());
        verify(repositorio).abrirSesion(JUGADOR, "movil");
        verify(repositorio).registrarEntregas(JUGADOR, "perdido", Set.of("movil"));
    }

    @Test
    @DisplayName("marcar leido baja la cuenta y avisa a todas las sesiones")
    void marcarLeidaBajaLaCuenta() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.reconstituir(
                JUGADOR, List.of(aviso("evt-3"), aviso("evt-4")), Set.of(), Set.of(), Map.of());
        when(repositorio.cargar(JUGADOR)).thenReturn(bandeja);

        int noLeidas = servicio.marcarLeida(JUGADOR, "evt-3");

        assertEquals(1, noLeidas);
        verify(repositorio).marcarLeida(JUGADOR, "evt-3");
        verify(canal).actualizarContador(JUGADOR, 1);
    }

    @Test
    @DisplayName("marcar un aviso que no existe no toca la base ni el canal")
    void marcarUnAvisoInexistenteFalla() {
        BandejaDeNotificaciones bandeja = BandejaDeNotificaciones.reconstituir(
                JUGADOR, List.of(aviso("evt-5")), Set.of(), Set.of(), Map.of());
        when(repositorio.cargar(JUGADOR)).thenReturn(bandeja);

        assertThrows(AvisoNoEncontrado.class, () -> servicio.marcarLeida(JUGADOR, "no-existe"));

        verify(repositorio, never()).marcarLeida(anyString(), anyString());
        verify(canal, never()).actualizarContador(anyString(), anyInt());
    }

    @Test
    @DisplayName("el mismo evento repetido se rechaza y no se guarda dos veces")
    void elEventoRepetidoSeRechaza() {
        when(repositorio.existeAviso(JUGADOR, "evt-6")).thenReturn(true);

        assertThrows(AvisoDuplicado.class, () -> servicio.emitir(JUGADOR, aviso("evt-6")));

        verify(repositorio, never()).guardarAviso(anyString(), any(Notificacion.class));
        verify(canal, never()).avisar(anyString(), any(Notificacion.class), anyInt());
    }
}
