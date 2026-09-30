package nexus.combate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcesadorPerdidaEquipoTest {

    @Test
    @DisplayName("pierde unicamente el objeto equipado con mayor tasa de caida")
    void seleccionaMayorTasaEquipada() {
        InventarioFalso inventario = new InventarioFalso();
        inventario.registrar("jugador-perdedor", List.of(
                candidato("arma-menor", "producto-arma", OrigenBotin.EQUIPADO),
                candidato("item-mayor", "producto-item", OrigenBotin.EQUIPADO),
                candidato("almacenado", "producto-almacenado", OrigenBotin.ALMACENADO)));
        CatalogoFalso catalogo = new CatalogoFalso(
                producto("producto-arma", 30),
                producto("producto-item", 80),
                producto("producto-almacenado", 100));
        TransferidorFalso transferidor = new TransferidorFalso();
        ProcesadorPerdidaEquipo procesador = procesador(
                participantesUnoContraUno(), inventario, catalogo, transferidor, cantidad -> 0);

        procesador.procesar(resultado("equipo-ganador"));

        assertEquals(1, transferidor.llamadas);
        assertEquals(1, transferidor.transferencias.size());
        assertEquals("item-mayor", transferidor.transferencias.getFirst().elementoId());
        assertEquals("jugador-ganador", transferidor.transferencias.getFirst().propietarioDestinoId());
        assertEquals(1, procesador.resultado().asignaciones().size());
        assertEquals(BigDecimal.valueOf(80), procesador.resultado().asignaciones().getFirst().tasaDeCaida());
    }

    @Test
    @DisplayName("reparte en una sola operacion los objetos de varios derrotados")
    void repartePorLoteEntreGanadores() {
        InventarioFalso inventario = new InventarioFalso();
        inventario.registrar("perdedor-1", List.of(
                candidato("objeto-1", "producto-1", OrigenBotin.EQUIPADO)));
        inventario.registrar("perdedor-2", List.of(
                candidato("objeto-2", "producto-2", OrigenBotin.EQUIPADO)));
        CatalogoFalso catalogo = new CatalogoFalso(
                producto("producto-1", 40),
                producto("producto-2", 70));
        TransferidorFalso transferidor = new TransferidorFalso();
        List<ParticipantePerdidaEquipo> participantes = List.of(
                participante("ganador-1", "equipo-a", "ganador-1"),
                participante("ganador-2", "equipo-a", "ganador-2"),
                participante("perdedor-1", "equipo-b", "perdedor-1"),
                participante("perdedor-2", "equipo-b", "perdedor-2"));
        SecuenciaSelector selector = new SecuenciaSelector(1, 0);
        ProcesadorPerdidaEquipo procesador = procesador(
                participantes, inventario, catalogo, transferidor, selector);

        procesador.procesar(resultado("equipo-a"));

        assertEquals(1, transferidor.llamadas);
        assertEquals("operacion-partida", transferidor.operacionId);
        assertEquals(2, transferidor.transferencias.size());
        assertEquals("ganador-2", transferidor.transferencias.get(0).propietarioDestinoId());
        assertEquals("ganador-1", transferidor.transferencias.get(1).propietarioDestinoId());
    }

    @Test
    @DisplayName("el cierre real de la partida activa la perdida de equipo")
    void seIntegraConCierreDePartida() {
        InventarioFalso inventario = new InventarioFalso();
        inventario.registrar("jugador-perdedor", List.of(
                candidato("objeto-1", "producto-1", OrigenBotin.EQUIPADO)));
        TransferidorFalso transferidor = new TransferidorFalso();
        ProcesadorPerdidaEquipo procesador = procesador(
                participantesUnoContraUno(),
                inventario,
                new CatalogoFalso(producto("producto-1", 50)),
                transferidor,
                cantidad -> 0);
        Partida partida = Partida.iniciar(
                List.of(
                        Combatiente.nuevo("combatiente-ganador", "equipo-ganador", 10),
                        Combatiente.nuevo("combatiente-perdedor", "equipo-perdedor", 10)),
                procesador);

        partida.aplicarDanio("combatiente-perdedor", 10);

        assertTrue(partida.finalizada());
        assertEquals("equipo-ganador", partida.resultado().orElseThrow().ganadorEquipoId());
        assertEquals("objeto-1", transferidor.transferencias.getFirst().elementoId());
    }

    @Test
    @DisplayName("no pierde objetos almacenados ni ejecuta una transferencia vacia")
    void conservaInventarioAlmacenado() {
        InventarioFalso inventario = new InventarioFalso();
        inventario.registrar("jugador-perdedor", List.of(
                candidato("objeto-guardado", "producto-1", OrigenBotin.ALMACENADO)));
        TransferidorFalso transferidor = new TransferidorFalso();
        ProcesadorPerdidaEquipo procesador = procesador(
                participantesUnoContraUno(),
                inventario,
                new CatalogoFalso(producto("producto-1", 100)),
                transferidor,
                cantidad -> 0);

        procesador.procesar(resultado("equipo-ganador"));

        assertEquals(0, transferidor.llamadas);
        assertTrue(procesador.resultado().asignaciones().isEmpty());
    }

    @Test
    @DisplayName("procesar dos veces el mismo cierre no duplica transferencias")
    void cierreIdempotente() {
        InventarioFalso inventario = new InventarioFalso();
        inventario.registrar("jugador-perdedor", List.of(
                candidato("objeto-1", "producto-1", OrigenBotin.EQUIPADO)));
        TransferidorFalso transferidor = new TransferidorFalso();
        ProcesadorPerdidaEquipo procesador = procesador(
                participantesUnoContraUno(),
                inventario,
                new CatalogoFalso(producto("producto-1", 50)),
                transferidor,
                cantidad -> 0);

        procesador.procesar(resultado("equipo-ganador"));
        procesador.procesar(resultado("equipo-ganador"));

        assertEquals(1, transferidor.llamadas);
    }

    @Test
    @DisplayName("rechaza un selector que no corresponde a ningun ganador")
    void selectorInvalido() {
        ProcesadorPerdidaEquipo procesador = procesador(
                participantesUnoContraUno(),
                inventarioConObjeto(),
                new CatalogoFalso(producto("producto-1", 50)),
                new TransferidorFalso(),
                cantidad -> cantidad);

        assertThrows(IllegalStateException.class,
                () -> procesador.procesar(resultado("equipo-ganador")));
    }

    @Test
    @DisplayName("la fabrica conecta las dependencias y crea un procesador por partida")
    void fabricaProcesadorPorPartida() {
        InventarioFalso inventario = inventarioConObjeto();
        TransferidorFalso transferidor = new TransferidorFalso();
        FabricaProcesadorPerdidaEquipo fabrica = new FabricaProcesadorPerdidaEquipo(
                inventario,
                new CatalogoFalso(producto("producto-1", 50)),
                transferidor,
                cantidad -> 0);

        ProcesadorPerdidaEquipo procesador = fabrica.crear(
                "partida-fabrica", participantesUnoContraUno());
        assertThrows(IllegalStateException.class, procesador::resultado);
        procesador.procesar(resultado("equipo-ganador"));

        assertEquals("partida-fabrica", transferidor.operacionId);
        assertEquals("objeto-1", procesador.resultado().asignaciones().getFirst().elementoId());
    }

    @Test
    @DisplayName("valida la composicion de participantes antes de consultar integraciones")
    void validaParticipantes() {
        InventarioFalso inventario = new InventarioFalso();
        CatalogoFalso catalogo = new CatalogoFalso();
        TransferidorFalso transferidor = new TransferidorFalso();

        assertThrows(IllegalArgumentException.class, () -> procesador(
                List.of(participante("unico", "equipo-a", "jugador")),
                inventario, catalogo, transferidor, cantidad -> 0));
        assertThrows(IllegalArgumentException.class, () -> procesador(
                List.of(
                        participante("repetido", "equipo-a", "jugador-a"),
                        participante("repetido", "equipo-b", "jugador-b")),
                inventario, catalogo, transferidor, cantidad -> 0));
        assertThrows(IllegalArgumentException.class, () -> procesador(
                List.of(
                        participante("uno", "equipo-a", "jugador-a"),
                        participante("dos", "equipo-a", "jugador-b")),
                inventario, catalogo, transferidor, cantidad -> 0));
    }

    private static ProcesadorPerdidaEquipo procesador(
            List<ParticipantePerdidaEquipo> participantes,
            InventarioFalso inventario,
            CatalogoFalso catalogo,
            TransferidorFalso transferidor,
            SelectorGanador selector) {
        return new ProcesadorPerdidaEquipo(
                "operacion-partida", participantes, inventario, catalogo, transferidor, selector);
    }

    private static InventarioFalso inventarioConObjeto() {
        InventarioFalso inventario = new InventarioFalso();
        inventario.registrar("jugador-perdedor", List.of(
                candidato("objeto-1", "producto-1", OrigenBotin.EQUIPADO)));
        return inventario;
    }

    private static List<ParticipantePerdidaEquipo> participantesUnoContraUno() {
        return List.of(
                participante("combatiente-ganador", "equipo-ganador", "jugador-ganador"),
                participante("combatiente-perdedor", "equipo-perdedor", "jugador-perdedor"));
    }

    private static ParticipantePerdidaEquipo participante(
            String combatienteId,
            String equipoId,
            String propietarioId) {
        return new ParticipantePerdidaEquipo(
                combatienteId, equipoId, propietarioId, "heroe-" + combatienteId);
    }

    private static ResultadoPartida resultado(String equipoGanador) {
        return new ResultadoPartida(equipoGanador, MotivoFinPartida.SUPERVIVENCIA);
    }

    private static ElementoCandidatoBotin candidato(
            String elementoId,
            String productoId,
            OrigenBotin origen) {
        return new ElementoCandidatoBotin(
                elementoId, productoId, TipoBotin.ITEM, "Objeto", null, origen);
    }

    private static ProductoBotin producto(String id, int tasa) {
        return new ProductoBotin(
                id, "Producto", TipoBotin.ITEM, null, BigDecimal.valueOf(tasa));
    }

    private static final class InventarioFalso implements InventarioBotin {

        private final Map<String, List<ElementoCandidatoBotin>> elementos = new HashMap<>();

        void registrar(String propietarioId, List<ElementoCandidatoBotin> candidatos) {
            elementos.put(propietarioId, candidatos);
        }

        @Override
        public List<ElementoCandidatoBotin> listarCandidatos(String propietarioId, String heroeId) {
            return elementos.getOrDefault(propietarioId, List.of());
        }

        @Override
        public void otorgar(String jugadorId, ElementoCandidatoBotin elemento) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CatalogoFalso implements CatalogoBotin {

        private final Map<String, ProductoBotin> productos = new HashMap<>();

        CatalogoFalso(ProductoBotin... productos) {
            for (ProductoBotin producto : productos) {
                this.productos.put(producto.id(), producto);
            }
        }

        @Override
        public ProductoBotin consultar(String productoId) {
            return productos.get(productoId);
        }
    }

    private static final class TransferidorFalso implements TransferidorEquipo {

        private int llamadas;
        private String operacionId;
        private List<TransferenciaEquipo> transferencias = List.of();

        @Override
        public void transferir(String operacionId, List<TransferenciaEquipo> transferencias) {
            llamadas++;
            this.operacionId = operacionId;
            this.transferencias = List.copyOf(transferencias);
        }
    }

    private static final class SecuenciaSelector implements SelectorGanador {

        private final List<Integer> indices;
        private int posicion;

        SecuenciaSelector(Integer... indices) {
            this.indices = new ArrayList<>(List.of(indices));
        }

        @Override
        public int seleccionar(int cantidadGanadores) {
            return indices.get(posicion++);
        }
    }
}
