package com.nexusbattles.plataforma.salaspartidas.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nexusbattles.plataforma.salaspartidas.chat.FiltroDeContenido.Veredicto;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Criterios de aceptacion de HU-JUE-015 sobre el caso de uso, sin Spring:
 * CA-01 camino feliz, CA-02 logro de mision, CA-03 contenido prohibido o
 * jugador silenciado. Mas la regla de que nada sale sin verificar.
 */
class EnviarMensajeTest {

    private static final Instant AHORA = Instant.parse("2026-09-02T10:00:00Z");
    private static final Autor ANA = new Autor(UUID.randomUUID(), "Ana");
    private static final Canal SALA = Canal.deSala(UUID.randomUUID());

    private final HistorialEnMemoria historial = new HistorialEnMemoria();
    private final List<MensajeDeChat> publicados = new ArrayList<>();
    private final Set<UUID> silenciados = new HashSet<>();
    private Veredicto veredicto = Veredicto.LIMPIO;

    private EnviarMensaje casoDeUso() {
        return new EnviarMensaje(historial, texto -> veredicto, silenciados::contains,
                publicados::add, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("CA-01: un mensaje limpio llega al canal y queda en el historial")
    void mensajeLimpioSaleYQueda() {
        MensajeDeChat m = casoDeUso().enviar(SALA, ANA, "  vamos a la sala 3  ", null);

        assertEquals("vamos a la sala 3", m.texto());
        assertEquals(Tipo.MENSAJE, m.tipo());
        assertEquals(AHORA, m.enviadoEn());
        assertNull(m.logro());
        assertEquals(List.of(m), publicados);
        assertEquals(List.of(m), historial.ultimos(SALA, 10));
    }

    @Test
    @DisplayName("CA-02: compartir un logro sale como mensaje de tipo logro con su detalle")
    void compartirUnLogro() {
        LogroCompartido logro = new LogroCompartido("mision-7", "Cazador de dragones");

        MensajeDeChat m = casoDeUso().enviar(Canal.general(), ANA, "lo logre", logro);

        assertEquals(Tipo.LOGRO, m.tipo());
        assertEquals(logro, m.logro());
        assertTrue(m.canal().esGeneral());
    }

    @Test
    @DisplayName("CA-03: un jugador silenciado no escribe y el canal ni se entera")
    void silenciadoNoEscribe() {
        silenciados.add(ANA.id());

        assertThrows(JugadorSilenciado.class, () -> casoDeUso().enviar(SALA, ANA, "hola", null));
        assertTrue(publicados.isEmpty());
        assertTrue(historial.ultimos(SALA, 10).isEmpty());
    }

    @Test
    @DisplayName("CA-03: contenido de la lista negra se bloquea sin entregarse")
    void contenidoProhibidoSeBloquea() {
        veredicto = Veredicto.SENALADO;

        assertThrows(ContenidoBloqueado.class, () -> casoDeUso().enviar(SALA, ANA, "groseria", null));
        assertTrue(publicados.isEmpty());
    }

    @Test
    @DisplayName("si el filtro no responde, el mensaje no sale y se pide reintentar")
    void sinFiltroNoSale() {
        veredicto = Veredicto.SIN_VERIFICAR;

        assertThrows(FiltroNoDisponible.class, () -> casoDeUso().enviar(SALA, ANA, "hola", null));
        assertTrue(publicados.isEmpty());
        assertTrue(historial.ultimos(SALA, 10).isEmpty());
    }

    @Test
    @DisplayName("un mensaje vacio o mas largo que el contrato se rechaza antes del filtro")
    void mensajeInvalido() {
        assertThrows(MensajeInvalido.class, () -> casoDeUso().enviar(SALA, ANA, "   ", null));
        assertThrows(MensajeInvalido.class, () -> casoDeUso().enviar(SALA, ANA, "x".repeat(501), null));
        assertTrue(publicados.isEmpty());
    }

    /** Historial en memoria, el mismo papel que RepositorioDeSalasEnMemoria. */
    static class HistorialEnMemoria implements HistorialDeChat {
        private final List<MensajeDeChat> mensajes = new ArrayList<>();

        @Override
        public void guardar(MensajeDeChat mensaje) {
            mensajes.add(mensaje);
        }

        @Override
        public List<MensajeDeChat> ultimos(Canal canal, int cantidad) {
            List<MensajeDeChat> del = mensajes.stream().filter(m -> m.canal().equals(canal)).toList();
            return del.subList(Math.max(0, del.size() - cantidad), del.size());
        }
    }
}
