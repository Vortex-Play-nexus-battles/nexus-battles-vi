package nexus.alertas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * HU-PRD-014 — la consulta de alertas pendientes contra MongoDB real.
 *
 * <p>Las pruebas de servicio y de API usan el repositorio simulado de
 * {@code ConfiguracionAlertasParaPruebas}; por eso la consulta de #752 llego a
 * DEV rota sin que nada fallara. Aqui el repositorio es el que arma Spring
 * Data, y se fijan los bordes que el servicio da por hechos: {@code desde}
 * exclusivo (lo ya entregado no se repite), {@code hasta} inclusivo (lo
 * implementado justo al iniciar sesion si se entrega) y orden ascendente (el
 * servicio toma la ultima como marca de lo entregado).
 */
@DataMongoTest
@Testcontainers
class AlertaCatalogoRepositoryMongoTest {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    private static final Instant DESDE = Instant.parse("2026-09-27T15:30:00Z");
    private static final Instant HASTA = Instant.parse("2026-09-30T23:40:00Z");

    @Autowired
    private AlertaCatalogoRepository repositorio;

    @BeforeEach
    void limpiar() {
        repositorio.deleteAll();
    }

    @Test
    @DisplayName("usa el repositorio real de Spring Data y no el simulado de las pruebas de API")
    void elRepositorioEsElReal() {
        assertFalse(Mockito.mockingDetails(repositorio).isMock());
    }

    @Test
    @DisplayName("entrega (desde, hasta]: deja fuera el borde inferior, incluye el superior, en orden ascendente")
    void entregaElIntervaloAbiertoACerradoEnOrdenAscendente() {
        repositorio.saveAll(List.of(
                alerta("despues-de-hasta", HASTA.plusMillis(1)),
                alerta("en-hasta", HASTA),
                alerta("antes-de-desde", DESDE.minusSeconds(60)),
                alerta("en-medio", DESDE.plusSeconds(3600)),
                alerta("en-desde", DESDE),
                alerta("justo-despues-de-desde", DESDE.plusMillis(1))));

        List<String> ids = repositorio.buscarImplementadasEntre(DESDE, HASTA)
                .stream()
                .map(AlertaCatalogo::id)
                .toList();

        assertEquals(List.of("justo-despues-de-desde", "en-medio", "en-hasta"), ids);
    }

    @Test
    @DisplayName("sin alertas en el intervalo devuelve una lista vacia, no un error")
    void sinAlertasDevuelveListaVacia() {
        repositorio.save(alerta("vieja", DESDE.minusSeconds(1)));

        assertEquals(List.of(), repositorio.buscarImplementadasEntre(DESDE, HASTA));
    }

    @Test
    @DisplayName("desde la epoca, como en el primer ingreso, entrega todo el historial hasta ahora")
    void primerIngresoEntregaTodoElHistorial() {
        repositorio.saveAll(List.of(
                alerta("segunda", DESDE.plusSeconds(10)),
                alerta("primera", DESDE)));

        List<String> ids = repositorio.buscarImplementadasEntre(Instant.EPOCH, HASTA)
                .stream()
                .map(AlertaCatalogo::id)
                .toList();

        assertEquals(List.of("primera", "segunda"), ids);
    }

    private static AlertaCatalogo alerta(String id, Instant implementadaEn) {
        return new AlertaCatalogo(
                id,
                "producto-1",
                "Espada solar",
                TipoCambioCatalogo.CAMBIO_BALANCE,
                "Se actualizo el balance de Espada solar.",
                implementadaEn);
    }
}
