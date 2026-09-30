package nexus.inventario.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import nexus.inventario.aplicacion.RepositorioInventariosEnMemoria;
import nexus.inventario.aplicacion.TransferirEquipoPorCombate;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferenciaCombateControllerTest {

    @Test
    @DisplayName("la API aplica el lote y conserva la clave de la partida")
    void aplicaTransferencia() {
        RepositorioInventariosEnMemoria repositorio = new RepositorioInventariosEnMemoria();
        Inventario origen = Inventario.vacio("perdedor")
                .agregar(elemento("heroe-1", TipoElementoInventario.HEROE))
                .agregar(elemento("objeto-1", TipoElementoInventario.ITEM))
                .equipar("heroe-1", "objeto-1");
        repositorio.guardar(origen);
        TransferenciaCombateController controller = new TransferenciaCombateController(
                new TransferirEquipoPorCombate(repositorio));
        TransferirEquipoCombateRequest solicitud = new TransferirEquipoCombateRequest(List.of(
                new MovimientoCombateRequest("objeto-1", "perdedor", "heroe-1", "ganador")));

        TransferenciaCombateResponse respuesta = controller.transferir("partida-44", solicitud);

        assertEquals("partida-44", respuesta.operacionId());
        assertEquals(List.of("objeto-1"), respuesta.elementosTransferidos());
        assertEquals("ganador", repositorio.buscarPorElementoId("objeto-1").orElseThrow().propietarioId());
    }

    private static ElementoInventario elemento(String id, TipoElementoInventario tipo) {
        return new ElementoInventario(id, "producto-" + id, tipo, "Elemento " + id);
    }
}
