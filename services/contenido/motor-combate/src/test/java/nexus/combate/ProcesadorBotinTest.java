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

class ProcesadorBotinTest {

    @Test
    @DisplayName("otorga un arma equipada cuando cumple su tasa de caida")
    void otorgaArmaEquipada() {
        ElementoCandidatoBotin arma = candidato(
                "elemento-arma", "producto-arma", TipoBotin.ARMA, OrigenBotin.EQUIPADO);
        InventarioEnMemoria inventario = new InventarioEnMemoria(List.of(arma));
        CatalogoEnMemoria catalogo = new CatalogoEnMemoria(producto(
                "producto-arma", "Espada corta", TipoBotin.ARMA, BigDecimal.valueOf(100)));
        ProcesadorBotin procesador = new ProcesadorBotin(
                inventario, catalogo, new EvaluadorCaida(() -> 0.99));

        ResultadoBotin resultado = procesador.procesar(derrota("accion-1"));

        assertEquals(EstadoResultadoBotin.OTORGADO, resultado.estado());
        assertEquals(OrigenBotin.EQUIPADO, resultado.botines().getFirst().origen());
        assertEquals(List.of("jugador-ganador:elemento-arma"), inventario.otorgados);
    }

    @Test
    @DisplayName("evalua independientemente cada objeto equipado o almacenado")
    void evaluaCadaObjetoConSuPropiaTasa() {
        ElementoCandidatoBotin arma = candidato(
                "elemento-arma", "producto-arma", TipoBotin.ARMA, OrigenBotin.EQUIPADO);
        ElementoCandidatoBotin item = candidato(
                "elemento-item", "producto-item", TipoBotin.ITEM, OrigenBotin.ALMACENADO);
        ElementoCandidatoBotin itemPosterior = candidato(
                "elemento-item-2", "producto-item-2", TipoBotin.ITEM, OrigenBotin.ALMACENADO);
        InventarioEnMemoria inventario = new InventarioEnMemoria(List.of(arma, item, itemPosterior));
        CatalogoEnMemoria catalogo = new CatalogoEnMemoria(
                producto("producto-arma", "Espada", TipoBotin.ARMA, BigDecimal.ZERO),
                producto("producto-item", "Pocion", TipoBotin.ITEM, BigDecimal.valueOf(100)),
                producto("producto-item-2", "Antidoto", TipoBotin.ITEM, BigDecimal.valueOf(100)));
        ProcesadorBotin procesador = new ProcesadorBotin(
                inventario, catalogo, new EvaluadorCaida(() -> 0.5));

        ResultadoBotin resultado = procesador.procesar(derrota("accion-2"));

        assertEquals(EstadoResultadoBotin.OTORGADO, resultado.estado());
        assertEquals(2, resultado.botines().size());
        assertEquals("producto-item", resultado.botines().get(0).productoId());
        assertEquals(OrigenBotin.ALMACENADO, resultado.botines().get(0).origen());
        assertEquals("producto-item-2", resultado.botines().get(1).productoId());
        assertEquals(2, inventario.otorgados.size());
        assertEquals(3, catalogo.consultas);
    }

    @Test
    @DisplayName("no entrega objetos cuando ninguna probabilidad se cumple")
    void noEntregaCuandoNoCumpleLaProbabilidad() {
        ElementoCandidatoBotin item = candidato(
                "elemento-item", "producto-item", TipoBotin.ITEM, OrigenBotin.ALMACENADO);
        InventarioEnMemoria inventario = new InventarioEnMemoria(List.of(item));
        CatalogoEnMemoria catalogo = new CatalogoEnMemoria(producto(
                "producto-item", "Pocion", TipoBotin.ITEM, BigDecimal.valueOf(10)));
        ProcesadorBotin procesador = new ProcesadorBotin(
                inventario, catalogo, new EvaluadorCaida(() -> 0.10));

        ResultadoBotin resultado = procesador.procesar(derrota("accion-3"));

        assertEquals(EstadoResultadoBotin.SIN_BOTIN, resultado.estado());
        assertTrue(inventario.otorgados.isEmpty());
    }

    @Test
    @DisplayName("una misma accion no puede volver a otorgar el botin")
    void evitaPremioDuplicadoPorLaMismaAccion() {
        ElementoCandidatoBotin item = candidato(
                "elemento-item", "producto-item", TipoBotin.ITEM, OrigenBotin.ALMACENADO);
        InventarioEnMemoria inventario = new InventarioEnMemoria(List.of(item));
        CatalogoEnMemoria catalogo = new CatalogoEnMemoria(producto(
                "producto-item", "Pocion", TipoBotin.ITEM, BigDecimal.valueOf(100)));
        ProcesadorBotin procesador = new ProcesadorBotin(
                inventario, catalogo, new EvaluadorCaida(() -> 0.5));
        DerrotaEnemigo derrota = derrota("accion-repetida");

        ResultadoBotin primera = procesador.procesar(derrota);
        ResultadoBotin repetida = procesador.procesar(derrota);

        assertEquals(EstadoResultadoBotin.OTORGADO, primera.estado());
        assertEquals(EstadoResultadoBotin.YA_PROCESADO, repetida.estado());
        assertEquals(1, inventario.otorgados.size());
    }

    @Test
    @DisplayName("rechaza diferencias entre inventario y catalogo")
    void rechazaTipoInconsistenteEntreServicios() {
        ElementoCandidatoBotin arma = candidato(
                "elemento-arma", "producto-arma", TipoBotin.ARMA, OrigenBotin.EQUIPADO);
        InventarioEnMemoria inventario = new InventarioEnMemoria(List.of(arma));
        CatalogoEnMemoria catalogo = new CatalogoEnMemoria(producto(
                "producto-arma", "Pocion", TipoBotin.ITEM, BigDecimal.valueOf(100)));
        ProcesadorBotin procesador = new ProcesadorBotin(
                inventario, catalogo, new EvaluadorCaida(() -> 0.5));

        assertThrows(
                IntegracionBotinException.class,
                () -> procesador.procesar(derrota("accion-inconsistente")));
        assertTrue(inventario.otorgados.isEmpty());
    }

    private static DerrotaEnemigo derrota(String accionId) {
        return new DerrotaEnemigo(
                accionId,
                "jugador-ganador",
                "jugador-enemigo",
                "heroe-enemigo");
    }

    private static ElementoCandidatoBotin candidato(
            String elementoId,
            String productoId,
            TipoBotin tipo,
            OrigenBotin origen) {
        return new ElementoCandidatoBotin(
                elementoId,
                productoId,
                tipo,
                "Objeto enemigo",
                null,
                origen);
    }

    private static ProductoBotin producto(
            String id,
            String nombre,
            TipoBotin tipo,
            BigDecimal tasa) {
        return new ProductoBotin(id, nombre, tipo, null, tasa);
    }

    private static final class InventarioEnMemoria implements InventarioBotin {

        private final List<ElementoCandidatoBotin> candidatos;
        private final List<String> otorgados = new ArrayList<>();

        private InventarioEnMemoria(List<ElementoCandidatoBotin> candidatos) {
            this.candidatos = candidatos;
        }

        @Override
        public List<ElementoCandidatoBotin> listarCandidatos(
                String propietarioEnemigoId,
                String heroeEnemigoId) {
            return candidatos;
        }

        @Override
        public void otorgar(String jugadorId, ElementoCandidatoBotin elemento) {
            otorgados.add(jugadorId + ":" + elemento.elementoId());
        }
    }

    private static final class CatalogoEnMemoria implements CatalogoBotin {

        private final Map<String, ProductoBotin> productos = new HashMap<>();
        private int consultas;

        private CatalogoEnMemoria(ProductoBotin... productos) {
            for (ProductoBotin producto : productos) {
                this.productos.put(producto.id(), producto);
            }
        }

        @Override
        public ProductoBotin consultar(String productoId) {
            consultas++;
            return productos.get(productoId);
        }
    }
}
