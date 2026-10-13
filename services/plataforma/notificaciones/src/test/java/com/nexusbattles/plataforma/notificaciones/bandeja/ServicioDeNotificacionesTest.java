package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
    @DisplayName("HU-NOT-001 CA-02: marcar todas es una sola actualizacion en bloque, recuenta despues y avisa a todas las sesiones")
    void marcarTodasEnBloque() {
        when(repositorio.marcarTodasLeidas(JUGADOR)).thenReturn(3);
        when(repositorio.contarNoLeidas(JUGADOR)).thenReturn(0);

        ServicioDeNotificaciones.Lectura lectura = servicio.marcarTodasLeidas(JUGADOR);

        assertEquals(3, lectura.marcadas());
        assertEquals(0, lectura.noLeidas());
        verify(repositorio).marcarTodasLeidas(JUGADOR);
        verify(repositorio, never()).marcarLeida(anyString(), anyString());
        // La cuenta es la que queda en la base despues del UPDATE, y es la que viaja.
        InOrder orden = inOrder(repositorio, canal);
        orden.verify(repositorio).marcarTodasLeidas(JUGADOR);
        orden.verify(repositorio).contarNoLeidas(JUGADOR);
        orden.verify(canal).actualizarContador(JUGADOR, 0);
    }

    @Test
    @DisplayName("marcar todas con la bandeja vacia no falla: marca 0 y reenvia el contador igual")
    void marcarTodasConLaBandejaVacia() {
        when(repositorio.marcarTodasLeidas(JUGADOR)).thenReturn(0);
        when(repositorio.contarNoLeidas(JUGADOR)).thenReturn(0);

        assertEquals(new ServicioDeNotificaciones.Lectura(0, 0), servicio.marcarTodasLeidas(JUGADOR));

        verify(canal).actualizarContador(JUGADOR, 0);
    }

    @Test
    @DisplayName("marcar todas corre en una transaccion de escritura y el contador sale despues del commit, no antes")
    void marcarTodasPublicaTrasElCommit() throws NoSuchMethodException {
        // El UPDATE y el recuento van en la misma transaccion: o queda todo o nada.
        Transactional transaccion = ServicioDeNotificaciones.class
                .getMethod("marcarTodasLeidas", String.class).getAnnotation(Transactional.class);
        assertNotNull(transaccion, "marcarTodasLeidas tiene que ser transaccional");
        assertFalse(transaccion.readOnly());

        when(repositorio.marcarTodasLeidas(JUGADOR)).thenReturn(2);
        when(repositorio.contarNoLeidas(JUGADOR)).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();

        servicio.marcarTodasLeidas(JUGADOR);
        verify(canal, never()).actualizarContador(anyString(), anyInt());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(canal).actualizarContador(JUGADOR, 0);
    }

    @Test
    @DisplayName("si la transaccion de marcar todas se deshace, no se anuncia nada: ninguna sesion ve un cero que no quedo guardado")
    void marcarTodasNoPublicaSiSeDeshace() {
        when(repositorio.marcarTodasLeidas(JUGADOR)).thenReturn(2);
        when(repositorio.contarNoLeidas(JUGADOR)).thenReturn(0);
        TransactionSynchronizationManager.initSynchronization();

        servicio.marcarTodasLeidas(JUGADOR);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

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
