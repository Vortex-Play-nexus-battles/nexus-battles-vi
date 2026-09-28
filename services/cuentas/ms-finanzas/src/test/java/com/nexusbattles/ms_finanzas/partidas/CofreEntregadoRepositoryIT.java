package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integración de los cofres contra PostgreSQL real: el mapeo JPA, la migración
 * V5 (contenido, sorteo, entrega, {@code @Version}) y las consultas del tope
 * semanal y de las entregas pendientes (cofres.yaml 1.1.0, B7).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class CofreEntregadoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final TablaDeCofre TABLA = TablaDeCofre.desde("PRUEBA-1",
            "1647b2ea-096d-37e7-b580-0172e4c62313=1;37154b84-2ea0-3076-ae7b-656f7b4ebf11=1");

    @Autowired
    private CofreEntregadoRepository repositorio;

    @Autowired
    private ContadorDeCofresRepository contadores;

    private CofreEntregado nuevo(String uid, String semana, Instant ganadoEn) {
        return CofreEntregado.sorteado(uid, semana, ganadoEn, ganadoEn.toEpochMilli(), TABLA);
    }

    @Test
    void elIdLoPoneElServicioYElCofreVuelveConSuContenidoYSuSorteo() {
        CofreEntregado cofre = nuevo("uid-1", "2026-W38", Instant.parse("2026-09-16T10:00:00Z"));
        UUID id = cofre.getId();

        repositorio.save(cofre);
        CofreEntregado leido = repositorio.findById(id).orElseThrow();

        assertThat(leido.getId()).isEqualTo(id);
        assertThat(leido.isNew()).isFalse();
        assertThat(leido.getPremios()).containsExactly(new PremioDeCofre(cofre.getContenido(), 1));
        assertThat(leido.getSemilla()).isEqualTo(cofre.getSemilla());
        assertThat(leido.getTablaVersion()).isEqualTo("PRUEBA-1");
        assertThat(leido.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.PENDIENTE);
        assertThat(leido.getVersion()).isNotNull();
        // Con la semilla y la tabla guardadas el sorteo se repite.
        assertThat(TABLA.sortear(leido.getSemilla()).productoId()).isEqualTo(leido.getContenido());
    }

    @Test
    void findByUidJugadorOrderByEntregadoEnDesc_devuelveEnOrdenCorrectoYFiltraOtros() {
        repositorio.save(nuevo("uid-lista", "2026-W38", Instant.parse("2026-09-16T10:00:00Z")));
        repositorio.save(nuevo("uid-lista", "2026-W38", Instant.parse("2026-09-16T12:00:00Z")));
        repositorio.save(nuevo("uid-lista", "2026-W38", Instant.parse("2026-09-16T11:00:00Z")));
        repositorio.save(nuevo("uid-otro", "2026-W38", Instant.parse("2026-09-16T13:00:00Z")));

        Page<CofreEntregado> pagina = repositorio.findByUidJugadorOrderByEntregadoEnDesc(
                "uid-lista", PageRequest.of(0, 20));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).extracting(c -> c.getEntregadoEn().toString())
                .containsExactly(
                        "2026-09-16T12:00:00Z",
                        "2026-09-16T11:00:00Z",
                        "2026-09-16T10:00:00Z");
    }

    @Test
    void elTopeSemanalCuentaPorJugadorYSemana() {
        repositorio.save(nuevo("uid-tope", "2026-W38", Instant.parse("2026-09-15T10:00:00Z")));
        repositorio.save(nuevo("uid-tope", "2026-W38", Instant.parse("2026-09-16T10:00:00Z")));
        repositorio.save(nuevo("uid-tope", "2026-W39", Instant.parse("2026-09-22T10:00:00Z")));
        repositorio.save(nuevo("uid-ajeno", "2026-W38", Instant.parse("2026-09-16T10:00:00Z")));

        assertThat(repositorio.countByUidJugadorAndSemanaIso("uid-tope", "2026-W38")).isEqualTo(2);
        assertThat(repositorio.countByUidJugadorAndSemanaIso("uid-tope", "2026-W39")).isEqualTo(1);
        assertThat(repositorio.countByUidJugadorAndSemanaIso("uid-tope", "2026-W40")).isZero();
    }

    @Test
    void lasEntregasPendientesSalenPorSuProximoIntentoYSinLasEntregadas() {
        Instant ahora = Instant.parse("2026-10-01T10:00:00Z");
        CofreEntregado vieja = nuevo("uid-pend", "2026-W40", ahora.minusSeconds(600));
        CofreEntregado reciente = nuevo("uid-pend", "2026-W40", ahora.minusSeconds(60));
        CofreEntregado futura = nuevo("uid-pend", "2026-W40", ahora);
        futura.entregaFallida("inventario caido", ahora, 30);
        CofreEntregado entregada = nuevo("uid-pend", "2026-W40", ahora.minusSeconds(900));
        entregada.entregado("entrega-1");
        List.of(reciente, vieja, futura, entregada).forEach(repositorio::save);

        List<UUID> pendientes = repositorio
                .findTop20ByEstadoEntregaAndProximoIntentoEnLessThanEqualOrderByProximoIntentoEnAsc(
                        CofreEntregado.EstadoEntrega.PENDIENTE, ahora)
                .stream().map(CofreEntregado::getId).toList();

        assertThat(pendientes).containsSubsequence(vieja.getId(), reciente.getId());
        assertThat(pendientes).doesNotContain(futura.getId(), entregada.getId());
    }

    @Test
    void unaEscrituraSobreUnaLecturaViejaDelCofreNoPisaNada() {
        CofreEntregado cofre = nuevo("uid-ver", "2026-W38", Instant.parse("2026-09-16T10:00:00Z"));
        repositorio.save(cofre);
        CofreEntregado unaLectura = repositorio.findById(cofre.getId()).orElseThrow();
        CofreEntregado otraLectura = repositorio.findById(cofre.getId()).orElseThrow();

        unaLectura.entregado("entrega-buena");
        repositorio.save(unaLectura);
        otraLectura.entregaFallida("tarde", Instant.parse("2026-09-16T10:01:00Z"), 30);

        assertThatThrownBy(() -> repositorio.save(otraLectura)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repositorio.findById(cofre.getId()).orElseThrow().getEstadoEntrega())
                .isEqualTo(CofreEntregado.EstadoEntrega.ENTREGADO);
    }

    @Test
    void elContadorLlevaBloqueoOptimista() {
        ContadorDeCofres contador = ContadorDeCofres.nuevo("uid-contador");
        contador.sumar(16, Instant.parse("2026-09-16T10:00:00Z"));
        contadores.save(contador);
        ContadorDeCofres unaLectura = contadores.findById("uid-contador").orElseThrow();
        ContadorDeCofres otraLectura = contadores.findById("uid-contador").orElseThrow();

        unaLectura.sumar(2, Instant.parse("2026-09-16T10:01:00Z"));
        contadores.save(unaLectura);
        otraLectura.sumar(4, Instant.parse("2026-09-16T10:01:00Z"));

        assertThatThrownBy(() -> contadores.save(otraLectura)).isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(contadores.findById("uid-contador").orElseThrow().getCreditosGanados()).isEqualTo(18);
    }
}
