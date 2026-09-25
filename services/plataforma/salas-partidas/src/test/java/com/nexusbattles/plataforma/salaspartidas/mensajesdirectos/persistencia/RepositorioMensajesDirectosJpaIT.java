package com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.persistencia;

import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.Conversacion;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.MensajeDirecto;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.RepositorioDeMensajesDirectos;
import com.nexusbattles.plataforma.salaspartidas.mensajesdirectos.RepositorioDeMensajesDirectos.Guardado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los mensajes privados contra una PostgreSQL de verdad — B6 (V13).
 *
 * <p>Con {@code ddl-auto=validate}, como las demas IT de persistencia del
 * servicio: si la entidad y la migracion dejan de coincidir, falla aqui.
 *
 * <p>Sin la transaccion por prueba de {@code @DataJpaTest}
 * ({@code NOT_SUPPORTED}): la idempotencia depende de que el insert repetido
 * choque con el indice unico y de leer al ganador en OTRA transaccion, que es
 * como corre en el servicio. Dentro de una transaccion de prueba el choque la
 * dejaria inservible y la prueba mentiria.
 */
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(RepositorioMensajesDirectosJpa.class)
@DisplayName("RepositorioMensajesDirectosJpa · la tabla mensajes_directos (B6)")
class RepositorioMensajesDirectosJpaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant T0 = Instant.parse("2026-09-25T18:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private RepositorioDeMensajesDirectos repositorio;

    @Autowired
    private MensajesDirectosSpringData almacen;

    @BeforeEach
    void vaciar() {
        almacen.deleteAll();
    }

    private static MensajeDirecto mensaje(UUID de, UUID a, String texto, long segundo, String idCliente) {
        return new MensajeDirecto(UUID.randomUUID(), Conversacion.entre(de, a), de, "apodo-" + de.toString().charAt(0),
                a, "apodo-" + a.toString().charAt(0), texto, T0.plusSeconds(segundo), null, idCliente);
    }

    @Test
    @DisplayName("un mensaje vuelve entero, con su conversacion canonica")
    void vuelveEntero() {
        MensajeDirecto guardado = mensaje(BRUNO, ANA, "hola ana", 1, "cli-1");

        Guardado resultado = repositorio.guardar(guardado);
        MensajeDirecto leido = repositorio.historial(Conversacion.entre(ANA, BRUNO), null, 10).get(0);

        assertAll(
                () -> assertTrue(resultado.nuevo()),
                () -> assertEquals(guardado, leido),
                () -> assertEquals("dm:" + ANA + ":" + BRUNO, leido.conversacion().clave()),
                () -> assertNull(leido.leidoEn()));
    }

    @Test
    @DisplayName("el historial es solo de esa pareja, en orden de lectura, y se pagina hacia atras")
    void historial() {
        for (int i = 1; i <= 5; i++) {
            repositorio.guardar(mensaje(i % 2 == 0 ? ANA : BRUNO, i % 2 == 0 ? BRUNO : ANA, "m" + i, i, null));
        }
        repositorio.guardar(mensaje(CARLA, ANA, "de carla", 3, null));

        Conversacion anaBruno = Conversacion.entre(ANA, BRUNO);
        List<String> ultimos = repositorio.historial(anaBruno, null, 3).stream().map(MensajeDirecto::texto).toList();
        List<String> antes = repositorio.historial(anaBruno, T0.plusSeconds(3), 10).stream()
                .map(MensajeDirecto::texto).toList();

        assertAll(
                () -> assertEquals(List.of("m3", "m4", "m5"), ultimos),
                () -> assertEquals(List.of("m1", "m2"), antes));
    }

    @Test
    @DisplayName("el ultimo de cada conversacion del jugador, y ninguna ajena")
    void ultimosPorConversacion() {
        repositorio.guardar(mensaje(ANA, BRUNO, "a bruno 1", 1, null));
        repositorio.guardar(mensaje(BRUNO, ANA, "a ana 2", 2, null));
        repositorio.guardar(mensaje(CARLA, ANA, "de carla", 5, null));
        repositorio.guardar(mensaje(BRUNO, CARLA, "no es de ana", 9, null));

        List<MensajeDirecto> deAna = repositorio.ultimosPorConversacion(ANA);

        assertAll(
                () -> assertEquals(2, deAna.size()),
                () -> assertTrue(deAna.stream().anyMatch(m -> m.texto().equals("a ana 2"))),
                () -> assertTrue(deAna.stream().anyMatch(m -> m.texto().equals("de carla"))),
                () -> assertTrue(deAna.stream().noneMatch(m -> m.texto().equals("no es de ana"))));
    }

    @Test
    @DisplayName("si dos mensajes de la conversacion comparten instante, sale uno solo")
    void empateDeInstante() {
        repositorio.guardar(mensaje(ANA, BRUNO, "uno", 7, null));
        repositorio.guardar(mensaje(BRUNO, ANA, "otro", 7, null));

        assertEquals(1, repositorio.ultimosPorConversacion(ANA).size());
    }

    @Test
    @DisplayName("no leidos por remitente, y marcarlos es idempotente")
    void noLeidos() {
        repositorio.guardar(mensaje(BRUNO, ANA, "1", 1, null));
        repositorio.guardar(mensaje(BRUNO, ANA, "2", 2, null));
        repositorio.guardar(mensaje(CARLA, ANA, "3", 3, null));
        repositorio.guardar(mensaje(ANA, BRUNO, "mio", 4, null));

        Map<UUID, Long> antes = repositorio.noLeidosPorRemitente(ANA);
        int primeraVez = repositorio.marcarLeidos(ANA, BRUNO, T0.plusSeconds(100));
        int segundaVez = repositorio.marcarLeidos(ANA, BRUNO, T0.plusSeconds(200));

        assertAll(
                () -> assertEquals(Map.of(BRUNO, 2L, CARLA, 1L), antes),
                () -> assertEquals(2, primeraVez),
                () -> assertEquals(0, segundaVez),
                () -> assertEquals(0, repositorio.noLeidos(ANA, BRUNO)),
                () -> assertEquals(1, repositorio.noLeidos(ANA, CARLA)),
                () -> assertEquals(1, repositorio.noLeidos(BRUNO, ANA), "lo de ana a bruno no se toca"),
                () -> assertEquals(T0.plusSeconds(100), repositorio.historial(Conversacion.entre(ANA, BRUNO), null, 10)
                        .get(0).leidoEn()));
    }

    @Test
    @DisplayName("el mismo idCliente del mismo remitente no se guarda dos veces: devuelve el primero")
    void idempotenciaPorIdCliente() {
        MensajeDirecto primero = mensaje(ANA, BRUNO, "hola", 1, "cli-9");
        MensajeDirecto reintento = mensaje(ANA, BRUNO, "hola", 2, "cli-9");
        MensajeDirecto deOtro = mensaje(CARLA, BRUNO, "hola", 3, "cli-9");

        Guardado uno = repositorio.guardar(primero);
        Guardado dos = repositorio.guardar(reintento);
        Guardado tres = repositorio.guardar(deOtro);

        assertAll(
                () -> assertTrue(uno.nuevo()),
                () -> assertFalse(dos.nuevo()),
                () -> assertEquals(primero.id(), dos.mensaje().id()),
                () -> assertTrue(tres.nuevo(), "el idCliente es de cada remitente"),
                () -> assertEquals(primero.id(), repositorio.buscarPorIdCliente(ANA, "cli-9").orElseThrow().id()),
                () -> assertEquals(2, almacen.count(), "el primero y el de carla; el reintento no"));
    }

    @Test
    @DisplayName("una restriccion que no es la del idCliente no se disfraza de reintento: sube")
    void otraRestriccionSube() {
        MensajeDirecto vacioSinId = mensaje(ANA, BRUNO, "", 1, null);
        MensajeDirecto vacioConId = mensaje(ANA, BRUNO, "", 2, "cli-7");

        assertAll(
                () -> assertThrows(DataIntegrityViolationException.class, () -> repositorio.guardar(vacioSinId)),
                () -> assertThrows(DataIntegrityViolationException.class, () -> repositorio.guardar(vacioConId)),
                () -> assertEquals(0, almacen.count()));
    }

    @Test
    @DisplayName("la base tambien se niega a un mensaje a uno mismo o vacio")
    void restricciones() {
        MensajeDirectoEntidad aSiMismo = new MensajeDirectoEntidad(UUID.randomUUID(),
                "dm:" + ANA + ":" + BRUNO, ANA, "a", ANA, "a", "hola", T0, null, null);
        MensajeDirectoEntidad vacio = new MensajeDirectoEntidad(UUID.randomUUID(),
                "dm:" + ANA + ":" + BRUNO, ANA, "a", BRUNO, "b", "", T0, null, null);

        assertAll(
                () -> assertThrows(DataIntegrityViolationException.class, () -> almacen.saveAndFlush(aSiMismo)),
                () -> assertThrows(DataIntegrityViolationException.class, () -> almacen.saveAndFlush(vacio)));
    }
}
