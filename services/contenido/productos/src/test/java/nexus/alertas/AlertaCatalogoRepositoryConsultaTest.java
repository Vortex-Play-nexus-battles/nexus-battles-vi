package nexus.alertas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoClientSettings;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.DbCallback;
import org.springframework.data.mongodb.core.ExecutableFindOperation.ExecutableFind;
import org.springframework.data.mongodb.core.ExecutableFindOperation.FindWithQuery;
import org.springframework.data.mongodb.core.ExecutableFindOperation.TerminatingFind;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;

/**
 * HU-PRD-014 — la consulta de alertas pendientes, armada por Spring Data
 * MongoDB de verdad (no un mock del repositorio) pero sin servidor: las
 * operaciones de Mongo son simuladas y lo que se captura es el {@link Query}
 * que el repositorio le entrega a la base.
 *
 * <p>Existe porque la consulta derivada de #752
 * ({@code ...ImplementadaEnAfterAndImplementadaEnLessThanEqual...}) ponia dos
 * criterios sobre la misma clave y Spring Data la rechazaba al armarla, en cada
 * llamada: el endpoint respondia 500 siempre. Ninguna prueba lo vio porque
 * todas usaban el repositorio simulado. Esta corre sin Docker; la de bordes
 * contra MongoDB real esta en {@code AlertaCatalogoRepositoryMongoTest}.
 */
class AlertaCatalogoRepositoryConsultaTest {

    private static final Instant DESDE = Instant.parse("2026-09-27T15:30:00Z");
    private static final Instant HASTA = Instant.parse("2026-09-30T23:40:00Z");

    private MongoOperations operaciones;
    private FindWithQuery<AlertaCatalogo> busqueda;
    private AlertaCatalogoRepository repositorio;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        // Igual que arma MongoTemplate por omision: sin los tipos simples de
        // Mongo, Instant se trataria como entidad y el contexto no arrancaria.
        MongoCustomConversions conversiones = new MongoCustomConversions(List.of());
        MongoMappingContext contexto = new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversiones.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        MappingMongoConverter conversor = new MappingMongoConverter(
                NoOpDbRefResolver.INSTANCE,
                contexto);
        conversor.setCustomConversions(conversiones);
        conversor.afterPropertiesSet();

        operaciones = mock(MongoOperations.class);
        ExecutableFind<AlertaCatalogo> consulta = mock(ExecutableFind.class);
        busqueda = mock(FindWithQuery.class);
        TerminatingFind<AlertaCatalogo> resultado = mock(TerminatingFind.class);

        when(operaciones.getConverter()).thenReturn(conversor);
        when(operaciones.query(AlertaCatalogo.class)).thenReturn(consulta);
        when(operaciones.execute(any(DbCallback.class)))
                .thenReturn(MongoClientSettings.getDefaultCodecRegistry());
        when(consulta.as(any())).thenReturn((FindWithQuery) busqueda);
        when(busqueda.matching(any(Query.class))).thenReturn(resultado);
        when(resultado.all()).thenReturn(List.of());

        repositorio = new MongoRepositoryFactory(operaciones)
                .getRepository(AlertaCatalogoRepository.class);
    }

    @Test
    @DisplayName("arma un unico criterio sobre implementadaEn: desde exclusivo, hasta inclusivo, ascendente")
    void armaUnUnicoCriterioDeRangoOrdenadoAscendente() {
        repositorio.buscarImplementadasEntre(
                DESDE,
                HASTA);

        ArgumentCaptor<Query> enviada = ArgumentCaptor.forClass(Query.class);
        verify(busqueda).matching(enviada.capture());
        assertEquals(
                new Document("implementadaEn", new Document("$gt", Date.from(DESDE))
                        .append("$lte", Date.from(HASTA))),
                enviada.getValue().getQueryObject());
        assertEquals(
                new Document("implementadaEn", 1),
                enviada.getValue().getSortObject());
    }
}
