package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TransferirEquipoPorCombateTest {

    private RepositorioInventariosEnMemoria repositorio;
    private TransferirEquipoPorCombate servicio;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        servicio = new TransferirEquipoPorCombate(repositorio);
    }

    @Test
    @DisplayName("mueve la misma instancia equipada y libera su ranura")
    void transfiereLaInstanciaSinDuplicarla() {
        repositorio.guardar(inventarioEquipado("perdedor", "heroe-1", "objeto-1"));

        List<ElementoInventario> resultado = servicio.transferir(
                "partida-1",
                List.of(transferencia("objeto-1", "perdedor", "heroe-1", "ganador")));

        assertEquals("objeto-1", resultado.getFirst().id());
        assertTrue(repositorio.buscarPorPropietario("perdedor").orElseThrow()
                .equipamiento("heroe-1").items().isEmpty());
        assertFalse(repositorio.buscarPorPropietario("perdedor").orElseThrow()
                .elementos().stream().anyMatch(elemento -> elemento.id().equals("objeto-1")));
        assertEquals("objeto-1", repositorio.buscarPorPropietario("ganador").orElseThrow()
                .elemento("objeto-1").id());
        assertEquals(1, repositorio.buscarTodosPorElementoId("objeto-1").size());
    }

    @Test
    @DisplayName("procesa varias perdidas como una sola operacion de aplicacion")
    void transfiereUnLoteCompleto() {
        repositorio.guardar(inventarioEquipado("perdedor-1", "heroe-1", "objeto-1"));
        repositorio.guardar(inventarioEquipado("perdedor-2", "heroe-2", "objeto-2"));

        List<ElementoInventario> resultado = servicio.transferir(
                "partida-2",
                List.of(
                        transferencia("objeto-1", "perdedor-1", "heroe-1", "ganador-1"),
                        transferencia("objeto-2", "perdedor-2", "heroe-2", "ganador-2")));

        assertEquals(List.of("objeto-1", "objeto-2"), resultado.stream()
                .map(ElementoInventario::id)
                .toList());
        assertEquals(1, repositorio.buscarTodosPorElementoId("objeto-1").size());
        assertEquals(1, repositorio.buscarTodosPorElementoId("objeto-2").size());
    }

    @Test
    @DisplayName("un reintento termina la transferencia sin duplicar el objeto")
    void reintentoConvergeDesdeDuplicado() {
        Inventario origen = inventarioEquipado("perdedor", "heroe-1", "objeto-1");
        repositorio.guardar(origen);
        repositorio.guardar(Inventario.vacio("ganador").agregar(origen.elemento("objeto-1")));

        servicio.transferir(
                "partida-3",
                List.of(transferencia("objeto-1", "perdedor", "heroe-1", "ganador")));

        assertEquals(1, repositorio.buscarTodosPorElementoId("objeto-1").size());
        assertEquals("ganador", repositorio.buscarPorElementoId("objeto-1").orElseThrow().propietarioId());
        assertTrue(repositorio.buscarPorPropietario("perdedor").orElseThrow()
                .equipamiento("heroe-1").items().isEmpty());
    }

    @Test
    @DisplayName("repetir una operacion ya completada no mueve ni duplica")
    void operacionCompletadaEsIdempotente() {
        repositorio.guardar(inventarioEquipado("perdedor", "heroe-1", "objeto-1"));
        TransferenciaCombate transferencia = transferencia(
                "objeto-1", "perdedor", "heroe-1", "ganador");

        servicio.transferir("partida-4", List.of(transferencia));
        servicio.transferir("partida-4", List.of(transferencia));

        assertEquals(1, repositorio.buscarTodosPorElementoId("objeto-1").size());
        assertEquals("ganador", repositorio.buscarPorElementoId("objeto-1").orElseThrow().propietarioId());
    }

    @Test
    @DisplayName("rechaza un objeto almacenado antes de modificar cualquier inventario")
    void conservaObjetosAlmacenados() {
        repositorio.guardar(inventarioAlmacenado("perdedor", "heroe-1", "objeto-1"));

        assertThrows(TransferenciaCombateInvalidaException.class, () -> servicio.transferir(
                "partida-5",
                List.of(transferencia("objeto-1", "perdedor", "heroe-1", "ganador"))));

        assertEquals("perdedor", repositorio.buscarPorElementoId("objeto-1").orElseThrow().propietarioId());
        assertTrue(repositorio.buscarPorPropietario("ganador").isEmpty());
    }

    @Test
    @DisplayName("un objeto epico no se pierde aunque figure en el equipamiento")
    void conservaObjetosEpicos() {
        ElementoInventario heroe = elemento("heroe-1", TipoElementoInventario.HEROE);
        ElementoInventario epico = elemento("epico-1", TipoElementoInventario.EPICA);
        Inventario inventario = new Inventario(
                null,
                "perdedor",
                List.of(heroe, epico),
                List.of(new EquipamientoHeroe("heroe-1", List.of(), java.util.Map.of(), List.of("epico-1"))));
        repositorio.guardar(inventario);

        assertThrows(TransferenciaCombateInvalidaException.class, () -> servicio.transferir(
                "partida-6",
                List.of(transferencia("epico-1", "perdedor", "heroe-1", "ganador"))));

        assertEquals("perdedor", repositorio.buscarPorElementoId("epico-1").orElseThrow().propietarioId());
    }

    @Test
    @DisplayName("valida todo el lote antes de iniciar para evitar una aplicacion parcial")
    void validaAntesDeEscribir() {
        repositorio.guardar(inventarioEquipado("perdedor-1", "heroe-1", "objeto-1"));
        repositorio.guardar(inventarioAlmacenado("perdedor-2", "heroe-2", "objeto-2"));

        assertThrows(TransferenciaCombateInvalidaException.class, () -> servicio.transferir(
                "partida-7",
                List.of(
                        transferencia("objeto-1", "perdedor-1", "heroe-1", "ganador"),
                        transferencia("objeto-2", "perdedor-2", "heroe-2", "ganador"))));

        assertEquals("perdedor-1", repositorio.buscarPorElementoId("objeto-1")
                .orElseThrow().propietarioId());
        assertTrue(repositorio.buscarPorPropietario("ganador").isEmpty());
    }

    private static Inventario inventarioEquipado(String propietario, String heroeId, String objetoId) {
        return inventarioAlmacenado(propietario, heroeId, objetoId).equipar(heroeId, objetoId);
    }

    private static Inventario inventarioAlmacenado(String propietario, String heroeId, String objetoId) {
        return Inventario.vacio(propietario)
                .agregar(elemento(heroeId, TipoElementoInventario.HEROE))
                .agregar(elemento(objetoId, TipoElementoInventario.ITEM));
    }

    private static ElementoInventario elemento(String id, TipoElementoInventario tipo) {
        return new ElementoInventario(id, "producto-" + id, tipo, "Elemento " + id);
    }

    private static TransferenciaCombate transferencia(
            String elementoId,
            String origen,
            String heroeId,
            String destino) {
        return new TransferenciaCombate(elementoId, origen, heroeId, destino);
    }
}
