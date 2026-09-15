package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConsultarElementoInventarioTest {

    private static final UUID PROPIETARIO_UID = UUID.fromString("ae8df97e-9ab9-4af5-bd2a-25715919e5f1");
    private static final UUID PRODUCTO_ID = UUID.fromString("113609ca-3c15-42f5-b427-d452ce06f9a8");

    private RepositorioInventariosEnMemoria repositorio;
    private ConsultarElementoInventario consulta;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        consulta = new ConsultarElementoInventario(repositorio);
    }

    @Test
    @DisplayName("consulta una unidad con propietario UUID y estado de uso")
    void consultarUnidadEquipada() {
        ElementoInventario heroe = elemento("heroe-1", UUID.randomUUID(), TipoElementoInventario.HEROE);
        ElementoInventario item = elemento("item-1", PRODUCTO_ID, TipoElementoInventario.ITEM);
        Inventario inventario = new Inventario(
                null, PROPIETARIO_UID.toString(), List.of(heroe, item)).equipar(heroe.id(), item.id());
        repositorio.guardar(inventario);

        DetalleElementoInventario detalle = consulta.consultar(item.id());

        assertEquals(item.id(), detalle.elementoId());
        assertEquals(PRODUCTO_ID, detalle.productoId());
        assertEquals(PROPIETARIO_UID, detalle.propietarioUid());
        assertTrue(detalle.enUso());
        assertTrue(detalle.disponible());
        assertNull(detalle.subastaId());
    }

    @Test
    @DisplayName("incluye la subasta que mantiene bloqueada la unidad")
    void consultarUnidadBloqueada() {
        UUID subastaId = UUID.randomUUID();
        Inventario inventario = Inventario.vacio(PROPIETARIO_UID.toString())
                .agregar(elemento("item-1", PRODUCTO_ID, TipoElementoInventario.ITEM))
                .bloquearEnSubasta("item-1", subastaId.toString());
        repositorio.guardar(inventario);

        DetalleElementoInventario detalle = consulta.consultar("item-1");

        assertFalse(detalle.disponible());
        assertEquals(subastaId, detalle.subastaId());
    }

    @Test
    @DisplayName("un elemento inexistente responde como recurso no encontrado")
    void elementoInexistente() {
        assertThrows(ElementoNoEncontradoException.class,
                () -> consulta.consultar("elemento-inexistente"));
    }

    @Test
    @DisplayName("no publica apodos historicos como si fueran UUID")
    void propietarioHistorico() {
        repositorio.guardar(Inventario.vacio("jugador-antiguo")
                .agregar(elemento("item-1", PRODUCTO_ID, TipoElementoInventario.ITEM)));

        assertThrows(IdentificadorHistoricoException.class,
                () -> consulta.consultar("item-1"));
    }

    private ElementoInventario elemento(String id, UUID productoId, TipoElementoInventario tipo) {
        return new ElementoInventario(id, productoId.toString(), tipo, id);
    }
}
