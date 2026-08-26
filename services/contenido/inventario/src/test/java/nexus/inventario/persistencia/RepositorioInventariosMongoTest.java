package nexus.inventario.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import nexus.inventario.dominio.Inventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepositorioInventariosMongoTest {

    private RepositorioInventariosSpringData documentos;
    private RepositorioInventariosMongo repositorio;

    @BeforeEach
    void preparar() {
        documentos = mock(RepositorioInventariosSpringData.class);
        repositorio = new RepositorioInventariosMongo(documentos);
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
}
