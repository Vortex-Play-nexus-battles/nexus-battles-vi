package nexus.inventario.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;

class RepositorioInventariosMongoTest {

    private RepositorioInventariosSpringData documentos;
    private MongoOperations mongo;
    private RepositorioInventariosMongo repositorio;

    @BeforeEach
    void preparar() {
        documentos = mock(RepositorioInventariosSpringData.class);
        mongo = mock(MongoOperations.class);
        repositorio = new RepositorioInventariosMongo(documentos, mongo);
    }

    @Test
    @DisplayName("guardar delega en Spring Data y devuelve el identificador generado")
    void guardar() {
        Inventario sinId = Inventario.vacio("jugador-A");
        when(documentos.save(any())).thenReturn(
                new InventarioDocumento("inventario-1", "jugador-A", List.of()));

        Inventario guardado = repositorio.guardar(sinId);

        assertEquals("inventario-1", guardado.id());
        assertEquals("jugador-A", guardado.propietarioId());
    }

    @Test
    @DisplayName("un fallo de Spring Data se traduce sin exponer detalles de Mongo")
    void traducirFalloDeEscritura() {
        when(documentos.save(any())).thenThrow(new DataAccessResourceFailureException("Mongo no disponible"));

        assertThrows(FalloPersistenciaInventarioException.class,
                () -> repositorio.guardar(Inventario.vacio("jugador-A")));
    }

    @Test
    @DisplayName("buscar convierte el documento encontrado al dominio")
    void buscarExistente() {
        when(documentos.findByPropietarioId("jugador-A")).thenReturn(Optional.of(
                new InventarioDocumento("inventario-1", "jugador-A", List.of())));

        Inventario encontrado = repositorio.buscarPorPropietario("jugador-A").orElseThrow();

        assertEquals("inventario-1", encontrado.id());
    }

    @Test
    @DisplayName("buscar conserva la ausencia informada por Spring Data")
    void buscarInexistente() {
        when(documentos.findByPropietarioId("jugador-A")).thenReturn(Optional.empty());

        assertTrue(repositorio.buscarPorPropietario("jugador-A").isEmpty());
    }

    @Test
    @DisplayName("buscar elementos combina el indice de texto con el propietario")
    void buscarElementosIndexadosDelPropietario() {
        when(mongo.findOne(any(Query.class), eq(InventarioDocumento.class))).thenReturn(
                new InventarioDocumento("inventario-1", "jugador-A", List.of(
                        new ElementoDocumento(
                                "elemento-1", "producto-bruma", TipoElementoInventario.ITEM,
                                "Amuleto de Bruma", null),
                        new ElementoDocumento(
                                "elemento-2", "producto-solar", TipoElementoInventario.ARMA,
                                "Espada Solar", null))));

        var encontrados = repositorio.buscarElementos("jugador-A", "bruma");

        assertEquals(List.of("elemento-1"), encontrados.stream().map(e -> e.id()).toList());
        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findOne(consulta.capture(), eq(InventarioDocumento.class));
        assertEquals("jugador-A", consulta.getValue().getQueryObject().getString("propietarioId"));
        assertTrue(consulta.getValue().getQueryObject().containsKey("$text"));
    }
}
