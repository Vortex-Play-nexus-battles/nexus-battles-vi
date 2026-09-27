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
import nexus.inventario.dominio.ConflictoDeEscrituraException;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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

    @Test
    @DisplayName("B4: otra escritura que llego antes (version) es un conflicto, no un fallo generico")
    void versionDesactualizadaEsConflicto() {
        when(documentos.save(any())).thenThrow(new OptimisticLockingFailureException("version 3 != 4"));

        assertThrows(ConflictoDeEscrituraException.class, () -> repositorio.guardar(
                new Inventario("inventario-1", "jugador-A", List.of(), List.of(), List.of(), 3L)));
    }

    @Test
    @DisplayName("B4: dos inventarios nuevos del mismo jugador a la vez tambien son un conflicto (se relee y reintenta)")
    void propietarioDuplicadoEsConflicto() {
        when(documentos.save(any())).thenThrow(new DuplicateKeyException("propietarioId"));

        assertThrows(ConflictoDeEscrituraException.class, () -> repositorio.guardar(Inventario.vacio("jugador-A")));
    }

    @Test
    @DisplayName("B4: un documento anterior sin version recibe la 0 antes de su primer guardado versionado")
    void documentoAnteriorRecibeVersion() {
        when(documentos.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(mongo.getCollectionName(InventarioDocumento.class)).thenReturn("inventarios");

        Inventario guardado = repositorio.guardar(
                new Inventario("inventario-1", "jugador-A", List.of(), List.of(), List.of(), null));

        assertEquals(0L, guardado.version());
        ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
        verify(mongo).updateFirst(consulta.capture(), any(Update.class), eq("inventarios"));
        assertEquals("inventario-1", consulta.getValue().getQueryObject().getString("_id"));
        assertTrue(consulta.getValue().getQueryObject().containsKey("version"));
    }

    @Test
    @DisplayName("B4: un inventario nuevo o ya versionado se guarda sin tocar la version a mano")
    void sinParcheDeVersion() {
        when(documentos.save(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        repositorio.guardar(Inventario.vacio("jugador-A"));
        repositorio.guardar(new Inventario("inventario-1", "jugador-A", List.of(), List.of(), List.of(), 7L));

        org.mockito.Mockito.verifyNoInteractions(mongo);
    }
}
