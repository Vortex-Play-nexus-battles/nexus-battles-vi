package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaRequest.ParticipantePartidaRequest;

/**
 * El cofre de punta a punta dentro de ms-finanzas, contra PostgreSQL real
 * (cofres.yaml 1.1.0, B7): del resultado de la partida al cofre ganado, su
 * entrega al inventario al confirmar y lo que ve el jugador en "Mis cofres".
 * El único doble es inventario ({@link InventarioDeCofres}), que es otro
 * servicio.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Cofres de punta a punta en ms-finanzas (B7)")
class CofresIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private RegistroDeResultados registro;

    @Autowired
    private ContadorDeCofresRepository contadores;

    @Autowired
    private CofreEntregadoRepository cofres;

    @Autowired
    private MisCofresConsultaService misCofres;

    @Autowired
    private EntregaDeCofres entregas;

    @MockitoBean
    private InventarioDeCofres inventario;

    private static ResultadoPartidaRequest ganaUnoContraUno(String ganador, String perdedor) {
        return new ResultadoPartidaRequest("partida-" + UUID.randomUUID(), TipoPartida.UNO_A_UNO, ganador,
                List.of(new ParticipantePartidaRequest(ganador, false), new ParticipantePartidaRequest(perdedor, false)));
    }

    private void conCreditosAcumulados(String uid, int creditos) {
        ContadorDeCofres contador = ContadorDeCofres.nuevo(uid);
        contador.sumar(creditos, Instant.now());
        contadores.save(contador);
    }

    /**
     * La entrega va en otro hilo tras confirmar: se espera a que el cofre
     * cumpla la condicion, con un tope, en vez de dormir un tiempo fijo.
     */
    private CofreEntregado esperarCofre(UUID idCofre, java.util.function.Predicate<CofreEntregado> hasta)
            throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        CofreEntregado cofre = cofres.findById(idCofre).orElseThrow();
        while (!hasta.test(cofre) && System.nanoTime() < limite) {
            Thread.sleep(50);
            cofre = cofres.findById(idCofre).orElseThrow();
        }
        return cofre;
    }

    @Test
    @DisplayName("la victoria que completa los 20 da un cofre, se entrega al confirmar y sale en Mis cofres")
    void cofreDePuntaAPunta() throws InterruptedException {
        String ana = "ana-" + UUID.randomUUID();
        conCreditosAcumulados(ana, 18);
        when(inventario.entregar(any())).thenAnswer(inv -> "entrega-" + ((CofreEntregado) inv.getArgument(0)).getId());

        ResultadoPartidaResponse respuesta = registro.registrar(ganaUnoContraUno(ana, "bruno-" + UUID.randomUUID()));

        UUID idCofre = respuesta.acreditaciones().get(0).cofreId();
        assertThat(idCofre).isNotNull();
        CofreEntregado cofre = esperarCofre(idCofre, c -> !c.pendienteDeEntrega());
        assertThat(cofre.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.ENTREGADO);
        assertThat(cofre.getEntregaId()).isEqualTo("entrega-" + idCofre);
        assertThat(contadores.findById(ana).orElseThrow().getCreditosGanados()).isZero();

        ResumenCofre resumen = misCofres.listarPorUsuario(ana, PageRequest.of(0, 20)).getContent().get(0);
        assertThat(resumen.estadoEntrega()).isEqualTo("ENTREGADO");
        assertThat(resumen.premios()).hasSize(1);
        assertThat(resumen.premios().get(0).productoId()).isEqualTo(cofre.getContenido());
        assertThat(resumen.sorteo().tablaVersion()).isEqualTo("PROVISIONAL-DEV-1");
        assertThat(resumen.semanaIso()).matches("\\d{4}-W\\d{2}");
    }

    @Test
    @DisplayName("participar no cuenta para el cofre: solo los créditos de la victoria")
    void participarNoCuenta() {
        String perdedor = "perdedor-" + UUID.randomUUID();
        conCreditosAcumulados(perdedor, 19);

        ResultadoPartidaResponse respuesta = registro.registrar(ganaUnoContraUno("otro-" + UUID.randomUUID(),
                perdedor));

        assertThat(respuesta.acreditaciones().get(1).monto()).isEqualTo(1);
        assertThat(respuesta.acreditaciones().get(1).cofreId()).isNull();
        assertThat(contadores.findById(perdedor).orElseThrow().getCreditosGanados()).isEqualTo(19);
    }

    @Test
    @DisplayName("si inventario no responde el cofre queda PENDIENTE; el reintento lo entrega con la misma clave")
    void entregaPendienteYReintento() throws InterruptedException {
        String carla = "carla-" + UUID.randomUUID();
        conCreditosAcumulados(carla, 18);
        List<String> claves = new ArrayList<>();
        when(inventario.entregar(any())).thenAnswer(inv -> {
            CofreEntregado cofre = inv.getArgument(0);
            claves.add(cofre.claveDeEntrega());
            if (claves.size() == 1) {
                throw new InventarioDeCofres.EntregaNoRealizada("inventario respondio 404");
            }
            return "entrega-" + cofre.getId();
        });

        UUID idCofre = registro.registrar(ganaUnoContraUno(carla, "dario-" + UUID.randomUUID()))
                .acreditaciones().get(0).cofreId();
        CofreEntregado pendiente = esperarCofre(idCofre, c -> c.getIntentosEntrega() > 0);
        assertThat(pendiente.getEstadoEntrega()).isEqualTo(CofreEntregado.EstadoEntrega.PENDIENTE);
        assertThat(pendiente.getUltimoError()).contains("404");

        assertThat(entregas.entregar(idCofre)).isTrue();

        assertThat(cofres.findById(idCofre).orElseThrow().getEstadoEntrega())
                .isEqualTo(CofreEntregado.EstadoEntrega.ENTREGADO);
        assertThat(claves).containsExactly("cofre-" + idCofre, "cofre-" + idCofre);
    }

    /**
     * Varias partidas del mismo ganador a la vez: sin bloqueo optimista dos de
     * ellas sumarían sobre la misma lectura y se perderían créditos o se darían
     * dos cofres por una cuota. Con él, lo acumulado siempre cuadra:
     * {@code 16 + 2·partidas = 20·cofres + contador}.
     */
    @Test
    @DisplayName("cuatro victorias simultáneas del mismo jugador: ni se pierden créditos ni sobran cofres")
    void victoriasSimultaneas() throws Exception {
        String eva = "eva-" + UUID.randomUUID();
        // Una primera victoria en solitario crea la cuenta y el contador.
        registro.registrar(ganaUnoContraUno(eva, "rival-" + UUID.randomUUID()));
        ContadorDeCofres contador = contadores.findById(eva).orElseThrow();
        assertThat(contador.getCreditosGanados()).isEqualTo(2);
        when(inventario.entregar(any())).thenReturn("entrega");

        int hilos = 4;
        CyclicBarrier salida = new CyclicBarrier(hilos);
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < hilos; i++) {
                resultados.add(ejecutor.submit(() -> {
                    salida.await(20, TimeUnit.SECONDS);
                    try {
                        registro.registrar(ganaUnoContraUno(eva, "rival-" + UUID.randomUUID()));
                        return true;
                    } catch (RuntimeException conflictoAgotado) {
                        return false;
                    }
                }));
            }
            long exitosas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(60, TimeUnit.SECONDS)) {
                    exitosas++;
                }
            }

            int enElContador = contadores.findById(eva).orElseThrow().getCreditosGanados();
            long cofresDeEva = cofres.findByUidJugadorOrderByEntregadoEnDesc(eva, PageRequest.of(0, 20))
                    .getTotalElements();
            assertThat(exitosas).isGreaterThan(0);
            assertThat(2 + 2 * exitosas).isEqualTo(20 * cofresDeEva + enElContador);
        } finally {
            ejecutor.shutdownNow();
        }
    }
}
