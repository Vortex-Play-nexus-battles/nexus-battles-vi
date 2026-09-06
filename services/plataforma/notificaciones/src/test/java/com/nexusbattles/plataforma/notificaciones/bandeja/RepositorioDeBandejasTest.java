package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexusbattles.plataforma.notificaciones.BandejaDeNotificaciones;
import com.nexusbattles.plataforma.notificaciones.Notificacion;

/**
 * Pruebas de la traduccion entre la bandeja del dominio y las tres tablas.
 *
 * Aqui no hay base de datos: los repositorios de Spring Data se sustituyen por
 * dobles. Lo que se verifica es la conversion en los dos sentidos y que el
 * guardado sea por diferencia, sin reescribir lo que otra sesion acaba de
 * dejar, porque de eso depende que dos sesiones del mismo jugador no se pisen.
 */
@ExtendWith(MockitoExtension.class)
class RepositorioDeBandejasTest {

    private static final Instant AYER = Instant.parse("2026-08-30T15:00:00Z");
    private static final String JUGADOR = "jugador-1";

    @Mock
    private NotificacionRepository avisos;

    @Mock
    private EntregaRepository entregas;

    @Mock
    private SesionRepository sesiones;

    @InjectMocks
    private RepositorioDeBandejas repositorio;

    private static RegistroDeNotificacion fila(String avisoId, boolean leida) {
        return new RegistroDeNotificacion(JUGADOR, avisoId, "subasta",
                "Tu puja fue superada", "Alguien pujo mas alto.", AYER, leida);
    }

    @Test
    @DisplayName("cargar arma la bandeja con historial, lecturas, sesiones y entregas")
    void cargarReconstruyeLaBandejaCompleta() {
        when(avisos.findByUsuarioIdOrderByCreadaEnAsc(JUGADOR))
                .thenReturn(List.of(fila("evt-1", true), fila("evt-2", false)));
        when(sesiones.findByUsuarioId(JUGADOR))
                .thenReturn(List.of(new RegistroDeSesion(JUGADOR, "movil", AYER)));
        when(entregas.findByUsuarioId(JUGADOR))
                .thenReturn(List.of(new RegistroDeEntrega(JUGADOR, "evt-1", "movil")));

        BandejaDeNotificaciones bandeja = repositorio.cargar(JUGADOR);

        assertEquals(2, bandeja.historial().size());
        assertEquals(1, bandeja.noLeidas());
        assertTrue(bandeja.estaLeida("evt-1"));
        assertFalse(bandeja.estaLeida("evt-2"));
        assertEquals(Set.of("movil"), bandeja.sesionesActivas());
        assertEquals(Set.of("evt-1"), bandeja.entregadasA("movil"));
    }

    @Test
    @DisplayName("la sesion cargada solo tiene pendiente lo que todavia no recibio")
    void cargarDejaSoloLoPerdidoComoPendiente() {
        when(avisos.findByUsuarioIdOrderByCreadaEnAsc(JUGADOR))
                .thenReturn(List.of(fila("visto", false), fila("perdido", false)));
        when(sesiones.findByUsuarioId(JUGADOR))
                .thenReturn(List.of(new RegistroDeSesion(JUGADOR, "movil", AYER)));
        when(entregas.findByUsuarioId(JUGADOR))
                .thenReturn(List.of(new RegistroDeEntrega(JUGADOR, "visto", "movil")));

        List<Notificacion> pendientes = repositorio.cargar(JUGADOR).pendientesDeEntrega("movil");

        assertEquals(1, pendientes.size());
        assertEquals("perdido", pendientes.get(0).id());
    }

    @Test
    @DisplayName("cargar un jugador sin nada devuelve una bandeja vacia y utilizable")
    void cargarSinDatosDevuelveBandejaVacia() {
        when(avisos.findByUsuarioIdOrderByCreadaEnAsc(JUGADOR)).thenReturn(List.of());
        when(sesiones.findByUsuarioId(JUGADOR)).thenReturn(List.of());
        when(entregas.findByUsuarioId(JUGADOR)).thenReturn(List.of());

        BandejaDeNotificaciones bandeja = repositorio.cargar(JUGADOR);

        assertEquals(JUGADOR, bandeja.usuarioId());
        assertTrue(bandeja.historial().isEmpty());
        assertEquals(0, bandeja.noLeidas());
    }

    @Test
    @DisplayName("guardar un aviso conserva sus datos y lo deja sin leer")
    void guardarAvisoConservaLosDatos() {
        repositorio.guardarAviso(JUGADOR, new Notificacion(
                "evt-9", "mision", "Mision completada", "Ganaste 300 creditos.", AYER));

        ArgumentCaptor<RegistroDeNotificacion> captor =
                ArgumentCaptor.forClass(RegistroDeNotificacion.class);
        verify(avisos).save(captor.capture());

        RegistroDeNotificacion guardado = captor.getValue();
        assertEquals(JUGADOR, guardado.getUsuarioId());
        assertEquals("evt-9", guardado.getAvisoId());
        assertEquals("mision", guardado.getTipo());
        assertEquals("Mision completada", guardado.getTitulo());
        assertEquals(AYER, guardado.getCreadaEn());
        assertFalse(guardado.isLeida());
    }

    @Test
    @DisplayName("una entrega ya registrada no se vuelve a insertar")
    void noDuplicaEntregasYaRegistradas() {
        when(entregas.existsByUsuarioIdAndAvisoIdAndSesionId(JUGADOR, "evt-1", "movil"))
                .thenReturn(true);
        when(entregas.existsByUsuarioIdAndAvisoIdAndSesionId(JUGADOR, "evt-1", "tablet"))
                .thenReturn(false);

        repositorio.registrarEntregas(JUGADOR, "evt-1", Set.of("movil", "tablet"));

        ArgumentCaptor<RegistroDeEntrega> captor =
                ArgumentCaptor.forClass(RegistroDeEntrega.class);
        verify(entregas).save(captor.capture());
        assertEquals("tablet", captor.getValue().getSesionId());
    }

    @Test
    @DisplayName("una sesion ya conocida no se registra de nuevo")
    void noDuplicaSesionesYaAbiertas() {
        when(sesiones.existsByUsuarioIdAndSesionId(JUGADOR, "movil")).thenReturn(true);

        repositorio.abrirSesion(JUGADOR, "movil");

        verify(sesiones, never()).save(any(RegistroDeSesion.class));
    }

    @Test
    @DisplayName("una sesion nueva se registra con su identificador estable")
    void registraLaSesionNueva() {
        when(sesiones.existsByUsuarioIdAndSesionId(JUGADOR, "tablet")).thenReturn(false);

        repositorio.abrirSesion(JUGADOR, "tablet");

        ArgumentCaptor<RegistroDeSesion> captor =
                ArgumentCaptor.forClass(RegistroDeSesion.class);
        verify(sesiones).save(captor.capture());
        assertEquals("tablet", captor.getValue().getSesionId());
    }

    @Test
    @DisplayName("marcar leido cambia la fila del aviso y la vuelve a guardar")
    void marcarLeidaActualizaLaFila() {
        RegistroDeNotificacion existente = fila("evt-1", false);
        when(avisos.findByUsuarioIdAndAvisoId(JUGADOR, "evt-1"))
                .thenReturn(Optional.of(existente));

        repositorio.marcarLeida(JUGADOR, "evt-1");

        assertTrue(existente.isLeida());
        verify(avisos).save(existente);
    }

    @Test
    @DisplayName("marcar leido un aviso que no esta en la base no escribe nada")
    void marcarLeidaSinFilaNoEscribe() {
        when(avisos.findByUsuarioIdAndAvisoId(JUGADOR, "fantasma"))
                .thenReturn(Optional.empty());

        repositorio.marcarLeida(JUGADOR, "fantasma");

        verify(avisos, never()).save(any(RegistroDeNotificacion.class));
    }

    @Test
    @DisplayName("existeAviso responde con lo que dice la base")
    void existeAvisoConsultaLaBase() {
        when(avisos.existsByUsuarioIdAndAvisoId(JUGADOR, "evt-1")).thenReturn(true);

        assertTrue(repositorio.existeAviso(JUGADOR, "evt-1"));
    }
}
