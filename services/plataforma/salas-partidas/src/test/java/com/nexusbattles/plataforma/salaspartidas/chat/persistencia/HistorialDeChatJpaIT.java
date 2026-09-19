package com.nexusbattles.plataforma.salaspartidas.chat.persistencia;

import com.nexusbattles.plataforma.salaspartidas.chat.Canal;
import com.nexusbattles.plataforma.salaspartidas.chat.HistorialDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Autor;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.LogroCompartido;
import com.nexusbattles.plataforma.salaspartidas.chat.MensajeDeChat.Tipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El historial del chat contra una PostgreSQL de verdad — HU-JUE-015.
 *
 * <p><b>Por que existe.</b> Era el mayor agujero del servicio:
 * {@code MensajeDeChatEntidad} tenia sus veintidos lineas sin cubrir y
 * {@code HistorialDeChatJpa} dieciseis de diecinueve. O sea que el unico codigo
 * que guarda y recupera lo que se escribe en el chat no lo habia ejecutado
 * ninguna prueba: se comprobaba el caso de uso con un historial en memoria y el
 * adaptador real viajaba a produccion sin mirar.
 *
 * <p>Con {@code ddl-auto=validate} a proposito, igual que
 * {@code RepositorioSalasJpaIT}: asi Hibernate compara el mapeo de la entidad
 * contra las columnas que creo Flyway. Si la migracion y la entidad dejan de
 * coincidir, falla aqui y no en el servidor.
 *
 * <p>Sin {@code disabledWithoutDocker}: una prueba de integracion omitida no es
 * una prueba que pasa.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import(HistorialDeChatJpa.class)
@DisplayName("HistorialDeChatJpa · lo que se escribe en el chat sobrevive")
class HistorialDeChatJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID SALA = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Truncado a milisegundos: PostgreSQL guarda microsegundos y Java nanos. */
    private static final Instant AHORA =
            Instant.parse("2026-09-19T18:00:00Z").truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private HistorialDeChat historial;

    private static MensajeDeChat mensaje(Canal canal, String texto, Instant cuando) {
        return new MensajeDeChat(UUID.randomUUID(), canal, new Autor(ANA, "Ana"),
                Tipo.MENSAJE, texto, null, cuando);
    }

    // =====================================================================

    @Test
    @DisplayName("un mensaje vuelve entero: autor, tipo, texto y momento")
    void elMensajeVuelveEntero() {
        Canal canal = Canal.deSala(SALA);
        MensajeDeChat guardado = mensaje(canal, "Vamos por el jefe", AHORA);

        historial.guardar(guardado);
        MensajeDeChat recuperado = historial.ultimos(canal, 10).get(0);

        assertAll(
                () -> assertEquals(guardado.id(), recuperado.id()),
                () -> assertEquals(canal, recuperado.canal()),
                () -> assertEquals(ANA, recuperado.autor().id()),
                () -> assertEquals("Ana", recuperado.autor().apodo()),
                () -> assertEquals(Tipo.MENSAJE, recuperado.tipo()),
                () -> assertEquals("Vamos por el jefe", recuperado.texto()),
                () -> assertEquals(AHORA, recuperado.enviadoEn()),
                () -> assertNull(recuperado.logro(), "un mensaje normal no lleva logro"));
    }

    @Test
    @DisplayName("un logro compartido conserva su mision y su titulo")
    void elLogroVuelveEntero() {
        Canal canal = Canal.deSala(SALA);
        MensajeDeChat conLogro = new MensajeDeChat(UUID.randomUUID(), canal,
                new Autor(ANA, "Ana"), Tipo.LOGRO, "He terminado la mision",
                new LogroCompartido("Cazador de sombras", "Sombra menor"), AHORA);

        historial.guardar(conLogro);
        MensajeDeChat recuperado = historial.ultimos(canal, 10).get(0);

        assertAll(
                () -> assertEquals(Tipo.LOGRO, recuperado.tipo()),
                () -> assertEquals("Cazador de sombras", recuperado.logro().mision()),
                () -> assertEquals("Sombra menor", recuperado.logro().titulo()));
    }

    @Test
    @DisplayName("el canal general y el de una sala no se mezclan")
    void losCanalesNoSeMezclan() {
        // Son claves distintas en la misma tabla ("general" y "sala:<uuid>"): si
        // la consulta no filtrara, lo que se dice en una sala aparecería en el
        // chat general de todo el mundo.
        historial.guardar(mensaje(Canal.general(), "Hola a todos", AHORA));
        historial.guardar(mensaje(Canal.deSala(SALA), "Solo para la sala", AHORA));

        List<MensajeDeChat> general = historial.ultimos(Canal.general(), 10);
        List<MensajeDeChat> deSala = historial.ultimos(Canal.deSala(SALA), 10);

        assertAll(
                () -> assertEquals(1, general.size()),
                () -> assertEquals("Hola a todos", general.get(0).texto()),
                () -> assertEquals(1, deSala.size()),
                () -> assertEquals("Solo para la sala", deSala.get(0).texto()));
    }

    @Test
    @DisplayName("el historial llega en orden de lectura: el mas antiguo primero")
    void elOrdenEsElDeLectura() {
        // La consulta pide los ULTIMOS por fecha descendente y despues les da la
        // vuelta. Es el detalle que nadie comprobaba: sin ese giro, quien entra a
        // una sala ve la conversacion del reves.
        Canal canal = Canal.deSala(SALA);
        historial.guardar(mensaje(canal, "primero", AHORA));
        historial.guardar(mensaje(canal, "segundo", AHORA.plusSeconds(60)));
        historial.guardar(mensaje(canal, "tercero", AHORA.plusSeconds(120)));

        List<String> textos = historial.ultimos(canal, 10).stream()
                .map(MensajeDeChat::texto).toList();

        assertEquals(List.of("primero", "segundo", "tercero"), textos);
    }

    @Test
    @DisplayName("se devuelven los N ultimos, y son los ultimos de verdad")
    void seDevuelvenLosUltimos() {
        Canal canal = Canal.deSala(SALA);
        for (int i = 1; i <= 5; i++) {
            historial.guardar(mensaje(canal, "mensaje " + i, AHORA.plusSeconds(i * 60L)));
        }

        List<String> textos = historial.ultimos(canal, 2).stream()
                .map(MensajeDeChat::texto).toList();

        assertEquals(List.of("mensaje 4", "mensaje 5"), textos);
    }

    @Test
    @DisplayName("un canal sin nada devuelve una lista vacia, no un nulo")
    void elCanalVacioDevuelveListaVacia() {
        List<MensajeDeChat> ninguno =
                historial.ultimos(Canal.deSala(UUID.randomUUID()), 10);

        assertAll(
                () -> assertTrue(ninguno.isEmpty()),
                () -> assertEquals(List.of(), ninguno));
    }
}
