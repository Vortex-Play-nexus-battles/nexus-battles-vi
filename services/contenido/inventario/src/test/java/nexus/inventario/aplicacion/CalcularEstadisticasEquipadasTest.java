package nexus.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.FormulaDetalle;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de HU-INV-006, criterios 1 y 2, contra dobles en memoria de los
 * dos puertos externos (ResolutorDeProducto, ResolutorDeEstadisticasHeroe).
 * No dependen del GET /api/v1/productos/{id} que todavia no existe.
 *
 * <p>Cadena confirmada por Nicolay: heroeId es un id de ElementoInventario
 * tipo HEROE; su productoId se resuelve dentro del propio Inventario.
 */
class CalcularEstadisticasEquipadasTest {

    private static final String JUGADOR_ID = "jugador-1";
    private static final String HEROE_ELEMENTO_ID = "elemento-mi-guerrero-tanque";
    private static final String HEROE_PRODUCTO_ID = "producto-guerrero-tanque";
    private static final String ESPADA_ELEMENTO_ID = "elemento-mi-espada";
    private static final String ESPADA_PRODUCTO_ID = "producto-espada-una-mano";
    private static final String MAGMA_ELEMENTO_ID = "elemento-mi-magma";
    private static final String MAGMA_PRODUCTO_ID = "producto-magma-ardiente";

    private ResolutorDeProductoEnMemoria productos;
    private ResolutorDeEstadisticasHeroeEnMemoria heroes;
    private CalcularEstadisticasEquipadas calculadora;
    private Inventario inventarioBase;

    @BeforeEach
    void preparar() {
        productos = new ResolutorDeProductoEnMemoria();
        productos.registrar(HEROE_PRODUCTO_ID,
                new ResolutorDeProducto.DetalleProducto(null, "HEROE", "Guerrero Tanque"));
        productos.registrar(ESPADA_PRODUCTO_ID,
                new ResolutorDeProducto.DetalleProducto("Espada de una mano", "ARMA", null));
        productos.registrar(MAGMA_PRODUCTO_ID,
                new ResolutorDeProducto.DetalleProducto("Magma Ardiente", "ARMADURA", null));

        heroes = new ResolutorDeEstadisticasHeroeEnMemoria();
        heroes.registrar("Guerrero Tanque", new EstadisticasHeroe(
                10, 44, 11,
                new FormulaDetalle(10, 1, 6), // ataque: 10 + 1d6, Tabla 6
                new FormulaDetalle(0, 1, 4),  // daño: 1d4
                null));                       // sin sanar: no es sanador

        calculadora = new CalcularEstadisticasEquipadas(productos, heroes);

        inventarioBase = Inventario.vacio(JUGADOR_ID)
                .agregar(new ElementoInventario(
                        HEROE_ELEMENTO_ID, HEROE_PRODUCTO_ID,
                        TipoElementoInventario.HEROE, "Mi Guerrero Tanque"))
                .agregar(new ElementoInventario(
                        ESPADA_ELEMENTO_ID, ESPADA_PRODUCTO_ID,
                        TipoElementoInventario.ARMA, "mi espada"))
                .agregar(new ElementoInventario(
                        MAGMA_ELEMENTO_ID, MAGMA_PRODUCTO_ID,
                        TipoElementoInventario.ARMADURA, "mi casco", ParteArmadura.CASCO));
    }

    @Test
    void criterio1_armaEquipada_aplicaSuEfectoALasEstadisticas() {
        Inventario inventario = inventarioBase.equipar(HEROE_ELEMENTO_ID, ESPADA_ELEMENTO_ID);

        EstadisticasHeroe resultado = calculadora.calcular(inventario, HEROE_ELEMENTO_ID);

        // Espada de una mano: +1 ataque -> base 10 pasa a 11
        assertThat(resultado.ataqueDetalle().base()).isEqualTo(11);
        assertThat(resultado.ataqueDetalle().cantidadDados()).isEqualTo(1);
        assertThat(resultado.ataqueDetalle().caras()).isEqualTo(6);
        assertThat(resultado.vida()).isEqualTo(44);
        assertThat(resultado.defensa()).isEqualTo(11);
    }

    @Test
    void criterio1_armaduraEquipada_aplicaSuEfecto() {
        Inventario inventario = inventarioBase.equipar(HEROE_ELEMENTO_ID, MAGMA_ELEMENTO_ID);

        EstadisticasHeroe resultado = calculadora.calcular(inventario, HEROE_ELEMENTO_ID);

        // Magma Ardiente: +2 defensa, +1 vida
        assertThat(resultado.defensa()).isEqualTo(13);
        assertThat(resultado.vida()).isEqualTo(45);
    }

    @Test
    void criterio2_alDesequipar_elModificadorDejaDeAplicarse() {
        Inventario conArma = inventarioBase.equipar(HEROE_ELEMENTO_ID, ESPADA_ELEMENTO_ID);
        Inventario sinArma = conArma.desequipar(HEROE_ELEMENTO_ID, ESPADA_ELEMENTO_ID);

        EstadisticasHeroe resultado = calculadora.calcular(sinArma, HEROE_ELEMENTO_ID);

        assertThat(resultado.ataqueDetalle().base()).isEqualTo(10); // vuelve al base sin modificar
    }

    @Test
    void variosObjetosEquipados_susModificadoresSeSuman() {
        Inventario inventario = inventarioBase
                .equipar(HEROE_ELEMENTO_ID, ESPADA_ELEMENTO_ID)
                .equipar(HEROE_ELEMENTO_ID, MAGMA_ELEMENTO_ID);

        EstadisticasHeroe resultado = calculadora.calcular(inventario, HEROE_ELEMENTO_ID);

        assertThat(resultado.ataqueDetalle().base()).isEqualTo(11); // espada
        assertThat(resultado.defensa()).isEqualTo(13);              // magma ardiente
        assertThat(resultado.vida()).isEqualTo(45);                 // magma ardiente
    }

    // --- Dobles en memoria ---

    private static class ResolutorDeProductoEnMemoria implements ResolutorDeProducto {
        private final Map<String, DetalleProducto> productos = new HashMap<>();

        void registrar(String id, DetalleProducto detalle) {
            productos.put(id, detalle);
        }

        @Override
        public DetalleProducto resolver(String productoId) {
            DetalleProducto detalle = productos.get(productoId);
            if (detalle == null) {
                throw new IllegalStateException("Producto no registrado en el doble: " + productoId);
            }
            return detalle;
        }
    }

    private static class ResolutorDeEstadisticasHeroeEnMemoria implements ResolutorDeEstadisticasHeroe {
        private final Map<String, EstadisticasHeroe> estadisticas = new HashMap<>();

        void registrar(String prototipo, EstadisticasHeroe valores) {
            estadisticas.put(prototipo, valores);
        }

        @Override
        public EstadisticasHeroe resolver(String prototipo) {
            EstadisticasHeroe valores = estadisticas.get(prototipo);
            if (valores == null) {
                throw new IllegalStateException("Prototipo no registrado en el doble: " + prototipo);
            }
            return valores;
        }
    }
}
