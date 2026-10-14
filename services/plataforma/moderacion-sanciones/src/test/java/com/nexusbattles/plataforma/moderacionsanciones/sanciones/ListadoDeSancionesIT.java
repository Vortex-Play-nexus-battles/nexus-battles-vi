package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SA-4 — el listado global de sanciones (informe, punto 20) y la causal de
 * §7.3.2 contra PostgreSQL con {@code ddl-auto=validate}: V11 crea la columna
 * con su CHECK, y cada filtro de {@link FiltroDeSanciones} es una consulta de
 * verdad, no un filtro en memoria.
 *
 * <p>Cada prueba trabaja con cuentas nuevas y filtra por ellas, para no
 * depender de lo que dejaron las demas en la misma base.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "sanciones.avisos.reintento-ms=3600000"
})
@DisplayName("Listado global de sanciones · filtros contra PostgreSQL (SA-4)")
class ListadoDeSancionesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

    private static final Actor MODERADORA = new Actor(UUID.randomUUID(), "MODERADOR");
    private static final Actor ADMIN = new Actor(UUID.randomUUID(), "ADMINISTRADOR");

    @Autowired
    private SancionesService servicio;

    @Autowired
    private SancionRepository repositorio;

    @Autowired
    private JdbcTemplate jdbc;

    /** Aqui no hay ms-identidad: todas las cuentas de la prueba son jugadores (7.3.1). */
    @MockitoBean
    private RolesDeCuentas roles;

    @BeforeEach
    void todasSonJugadores() {
        org.mockito.Mockito.when(roles.rolDe(org.mockito.ArgumentMatchers.any())).thenReturn("JUGADOR");
    }

    private Sancion emitir(Actor actor, UUID jugador, Sancion.Tipo tipo, Causal causal, Long horas) {
        return servicio.emitir(actor, new SancionesService.SolicitudDeSancion(jugador, tipo, causal,
                "Hecho concreto de la prueba", "Normas de convivencia", null, horas, tipo == Sancion.Tipo.BANEO));
    }

    private Page<Sancion> buscar(FiltroDeSanciones filtro) {
        return servicio.buscar(MODERADORA, filtro, 0, 16);
    }

    @Test
    @DisplayName("la causal se guarda y se lee de la columna de V11")
    void laCausalSeGuarda() {
        UUID jugador = UUID.randomUUID();
        Sancion s = emitir(MODERADORA, jugador, Sancion.Tipo.ADVERTENCIA, Causal.SUPLANTACION_DE_IDENTIDAD, null);

        assertThat(repositorio.findById(s.id()).orElseThrow().causal()).isEqualTo(Causal.SUPLANTACION_DE_IDENTIDAD);
        assertThat(jdbc.queryForObject("select causal from moderacion_sanciones.sanciones where id = ?",
                String.class, s.id())).isEqualTo("SUPLANTACION_DE_IDENTIDAD");
    }

    @Test
    @DisplayName("el CHECK de V11 no admite una causal que no sea de las ocho; sin causal, si")
    void elCheckDeLaCausal() {
        String insercion = "insert into moderacion_sanciones.sanciones (id, usuario_id, tipo, motivo, causal,"
                + " emitida_por, rol_emisor, emitida_en) values (?, ?, 'ADVERTENCIA', 'x', ?, ?, 'MODERADOR', now())";

        assertThatThrownBy(() -> jdbc.update(insercion, UUID.randomUUID(), UUID.randomUUID(), "OTRA_CAUSAL",
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update(insercion, UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("por cuenta, tipo y causal; de la mas reciente a la mas antigua, con el total")
    void porTipoYCausal() {
        UUID jugador = UUID.randomUUID();
        Sancion primera = emitir(MODERADORA, jugador, Sancion.Tipo.ADVERTENCIA, Causal.LENGUAJE_OFENSIVO, null);
        Sancion segunda = emitir(MODERADORA, jugador, Sancion.Tipo.ADVERTENCIA, Causal.ACOSO, null);
        Sancion suspension = emitir(MODERADORA, jugador, Sancion.Tipo.SUSPENSION, Causal.ACOSO, 24L);
        emitir(MODERADORA, UUID.randomUUID(), Sancion.Tipo.SUSPENSION, Causal.ACOSO, 24L);

        Page<Sancion> todas = buscar(new FiltroDeSanciones(null, null, null, jugador, null, null));
        assertThat(todas.getTotalElements()).isEqualTo(3);
        assertThat(todas.getContent()).extracting(Sancion::id)
                .containsExactly(suspension.id(), segunda.id(), primera.id());

        assertThat(buscar(new FiltroDeSanciones(Sancion.Tipo.ADVERTENCIA, null, null, jugador, null, null))
                .getContent()).extracting(Sancion::id).containsExactly(segunda.id(), primera.id());
        assertThat(buscar(new FiltroDeSanciones(null, Causal.ACOSO, null, jugador, null, null))
                .getContent()).extracting(Sancion::id).containsExactly(suspension.id(), segunda.id());
        assertThat(buscar(new FiltroDeSanciones(Sancion.Tipo.SUSPENSION, Causal.ACOSO, null, jugador, null, null))
                .getContent()).extracting(Sancion::id).containsExactly(suspension.id());
    }

    @Test
    @DisplayName("por estado: vigente, cumplida (suspension vencida) o revertida, con la misma regla que la consulta")
    void porEstado() {
        UUID jugador = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        Sancion advertencia = emitir(MODERADORA, jugador, Sancion.Tipo.ADVERTENCIA, Causal.ACOSO, null);
        Sancion vigente = emitir(MODERADORA, jugador, Sancion.Tipo.SUSPENSION, Causal.ACOSO, 48L);
        Sancion levantada = emitir(MODERADORA, jugador, Sancion.Tipo.SUSPENSION, Causal.FRAUDE, 24L);
        servicio.levantar(ADMIN, levantada.id(), "Error de moderacion");
        // Una suspension que ya termino: se guarda con sus fechas, como la dejo el tiempo.
        Sancion cumplida = repositorio.save(new Sancion(UUID.randomUUID(), jugador, Sancion.Tipo.SUSPENSION,
                Causal.LENGUAJE_OFENSIVO, "Ya cumplio", null, null, MODERADORA.id(), "MODERADOR",
                ahora.minusDays(5), ahora.minusDays(3)));

        assertThat(buscar(new FiltroDeSanciones(null, null, FiltroDeSanciones.Estado.VIGENTE, jugador, null, null))
                .getContent()).extracting(Sancion::id).containsExactlyInAnyOrder(advertencia.id(), vigente.id());
        assertThat(buscar(new FiltroDeSanciones(null, null, FiltroDeSanciones.Estado.REVERTIDA, jugador, null, null))
                .getContent()).extracting(Sancion::id).containsExactly(levantada.id());
        assertThat(buscar(new FiltroDeSanciones(null, null, FiltroDeSanciones.Estado.CUMPLIDA, jugador, null, null))
                .getContent()).extracting(Sancion::id).containsExactly(cumplida.id());
    }

    @Test
    @DisplayName("por periodo de emision: desde incluido, hasta excluido")
    void porPeriodo() {
        UUID jugador = UUID.randomUUID();
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        Sancion antigua = repositorio.save(new Sancion(UUID.randomUUID(), jugador, Sancion.Tipo.ADVERTENCIA,
                Causal.ACOSO, "Hace diez dias", null, null, MODERADORA.id(), "MODERADOR", ahora.minusDays(10),
                null));
        Sancion reciente = emitir(MODERADORA, jugador, Sancion.Tipo.ADVERTENCIA, Causal.ACOSO, null);

        assertThat(buscar(new FiltroDeSanciones(null, null, null, jugador, ahora.minusDays(1), null))
                .getContent()).extracting(Sancion::id).containsExactly(reciente.id());
        assertThat(buscar(new FiltroDeSanciones(null, null, null, jugador, null, ahora.minusDays(1)))
                .getContent()).extracting(Sancion::id).containsExactly(antigua.id());
        assertThat(buscar(new FiltroDeSanciones(null, null, null, jugador, antigua.emitidaEn(),
                antigua.emitidaEn().plusSeconds(1))).getContent()).extracting(Sancion::id)
                .containsExactly(antigua.id());
    }

    @Test
    @DisplayName("paginado: dieciseis por pagina por omision, con el total y las paginas")
    void paginado() {
        UUID jugador = UUID.randomUUID();
        for (int i = 0; i < 17; i++) {
            emitir(MODERADORA, jugador, Sancion.Tipo.ADVERTENCIA, Causal.LENGUAJE_OFENSIVO, null);
        }
        FiltroDeSanciones delJugador = new FiltroDeSanciones(null, null, null, jugador, null, null);

        Page<Sancion> primera = servicio.buscar(MODERADORA, delJugador, 0, 16);
        Page<Sancion> segunda = servicio.buscar(MODERADORA, delJugador, 1, 16);

        assertThat(primera.getContent()).hasSize(16);
        assertThat(primera.getTotalElements()).isEqualTo(17);
        assertThat(primera.getTotalPages()).isEqualTo(2);
        assertThat(segunda.getContent()).hasSize(1);
        assertThat(primera.getContent().get(15).emitidaEn())
                .isAfterOrEqualTo(segunda.getContent().get(0).emitidaEn());
    }

    @Test
    @DisplayName("un baneo no se levanta a mano aunque este en la base: 409 y sigue vigente")
    void elBaneoSigueVigente() {
        UUID jugador = UUID.randomUUID();
        Sancion baneo = emitir(ADMIN, jugador, Sancion.Tipo.BANEO, Causal.FRAUDE, null);

        assertThatThrownBy(() -> servicio.levantar(new Actor(UUID.randomUUID(), "SUPER_ADMINISTRADOR"), baneo.id(),
                "Error de moderacion"))
                .extracting("motivo").isEqualTo(SancionRechazada.Motivo.BANEO_SOLO_POR_APELACION);
        assertThat(repositorio.findById(baneo.id()).orElseThrow().revertidaEn()).isNull();
        assertThat(buscar(new FiltroDeSanciones(Sancion.Tipo.BANEO, null, FiltroDeSanciones.Estado.VIGENTE, jugador,
                null, null)).getContent()).extracting(Sancion::id).containsExactly(baneo.id());
    }
}
