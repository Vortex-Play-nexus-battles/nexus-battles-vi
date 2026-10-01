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
 * jugador silenciado. Mas la regla de que nada sale sin verificar, y la de
 * HU-COM-007: el logro compartido pasa por el mismo filtro que el texto.
 */
class EnviarMensajeTest {

    private static final Instant AHORA = Instant.parse("2026-09-02T10:00:00Z");
    private static final Autor ANA = new Autor(UUID.randomUUID(), "Ana");
    private static final Canal SALA = Canal.deSala(UUID.randomUUID());

    private final HistorialEnMemoria historial = new HistorialEnMemoria();
    private final List<MensajeDeChat> publicados = new ArrayList<>();
    private final Set<UUID> silenciados = new HashSet<>();
    private Veredicto veredicto = Veredicto.LIMPIO;
    /** Un texto para el que el filtro no contesta, aunque los demas si. */
    private String sinRespuestaPara;
    /** Lo que se le pidio verificar al filtro, y en que canal. */
    private final List<String> verificados = new ArrayList<>();
    private final List<Canal> canalesVerificados = new ArrayList<>();

    /** Doble del filtro: senala lo que contiene «prohibida»; el resto, segun {@code veredicto}. */
    private EnviarMensaje casoDeUso() {
        FiltroDeContenido filtro = (texto, canal) -> {
            verificados.add(texto);
            canalesVerificados.add(canal);
            if (texto.equals(sinRespuestaPara)) {
                return Veredicto.SIN_VERIFICAR;
            }
            return texto.contains("prohibida") ? Veredicto.SENALADO : veredicto;
        };
        return new EnviarMensaje(historial, filtro, silenciados::contains,
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

    // --- HU-COM-007 (RF-COM-007): el logro tambien se publica, asi que tambien se filtra ---

    @Test
    @DisplayName("HU-COM-007: un logro con un termino prohibido en el titulo se bloquea y no queda en el historial")
    void logroConTituloProhibidoSeBloquea() {
        LogroCompartido logro = new LogroCompartido("mision-7", "palabra prohibida");

        assertThrows(ContenidoBloqueado.class, () -> casoDeUso().enviar(SALA, ANA, "lo logre", logro));
        assertTrue(publicados.isEmpty());
        assertTrue(historial.ultimos(SALA, 10).isEmpty());
    }

    @Test
    @DisplayName("HU-COM-007: la mision del logro tambien pasa por el filtro")
    void logroConMisionProhibidaSeBloquea() {
        LogroCompartido logro = new LogroCompartido("mision prohibida", "Cazador de dragones");

        assertThrows(ContenidoBloqueado.class, () -> casoDeUso().enviar(Canal.general(), ANA, "lo logre", logro));
        assertTrue(publicados.isEmpty());
    }

    @Test
    @DisplayName("HU-COM-007: el texto y cada campo del logro se verifican por separado, con el canal del mensaje")
    void cadaTextoSeVerificaConSuCanal() {
        casoDeUso().enviar(SALA, ANA, "lo logre", new LogroCompartido("mision-7", "Cazador de dragones"));

        assertEquals(List.of("lo logre", "mision-7", "Cazador de dragones"), verificados);
        assertEquals(List.of(SALA, SALA, SALA), canalesVerificados);
    }

    @Test
    @DisplayName("HU-COM-007: un campo vacio del logro no se manda a verificar")
    void campoVacioDelLogroNoSeVerifica() {
        casoDeUso().enviar(Canal.general(), ANA, "lo logre", new LogroCompartido("mision-7", null));

        assertEquals(List.of("lo logre", "mision-7"), verificados);
        assertEquals(List.of(Canal.general(), Canal.general()), canalesVerificados);
    }

    @Test
    @DisplayName("HU-COM-007: si el filtro no responde para el logro, el mensaje no sale y se pide reintentar")
    void logroSinVerificarNoSale() {
        sinRespuestaPara = "Cazador de dragones";

        assertThrows(FiltroNoDisponible.class, () -> casoDeUso().enviar(SALA, ANA, "lo logre",
                new LogroCompartido("mision-7", "Cazador de dragones")));
        assertTrue(publicados.isEmpty());
        assertTrue(historial.ultimos(SALA, 10).isEmpty());
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
