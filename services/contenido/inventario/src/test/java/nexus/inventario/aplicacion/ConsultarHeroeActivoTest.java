package nexus.inventario.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import nexus.inventario.aplicacion.ConsultarHeroeActivo.HeroeActivo;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EstadisticasHeroe;
import nexus.inventario.dominio.FormulaDetalle;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * HU-SAL-003: cero heroes, un heroe (reutilizando CalcularEstadisticasEquipadas
 * de verdad, no un doble, para confirmar que el calculo real se aplica), y
 * dos o mas heroes (falla explicito, no adivina).
 */
class ConsultarHeroeActivoTest {

    private static final String JUGADOR_ID = "jugador-1";

    private RepositorioInventariosEnMemoria repositorio;
    private ResolutorDeProductoEnMemoria productos;
    private ResolutorDeEstadisticasHeroeEnMemoria heroes;
    private ConsultarHeroeActivo consulta;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        productos = new ResolutorDeProductoEnMemoria();
        heroes = new ResolutorDeEstadisticasHeroeEnMemoria();
        CalcularEstadisticasEquipadas calculo = new CalcularEstadisticasEquipadas(productos, heroes);
        consulta = new ConsultarHeroeActivo(repositorio, calculo);
    }

    @Test
    @DisplayName("sin heroes devuelve vacio, no un error")
    void sinHeroesDevuelveVacio() {
        repositorio.guardar(Inventario.vacio(JUGADOR_ID));

        Optional<HeroeActivo> resultado = consulta.consultar(JUGADOR_ID);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("un heroe reutiliza CalcularEstadisticasEquipadas con el equipamiento actual")
    void unHeroeReutilizaElCalculoReal() {
        productos.registrar("producto-guerrero", new ResolutorDeProducto.DetalleProducto(null, "HEROE", "Guerrero Tanque"));
        productos.registrar("producto-espada", new ResolutorDeProducto.DetalleProducto("Espada de una mano", "ARMA", null));
        heroes.registrar("Guerrero Tanque", new EstadisticasHeroe(
                10, 44, 11, 1,
                new FormulaDetalle(10, 1, 6),
                new FormulaDetalle(0, 1, 4),
                null));

        Inventario inventario = Inventario.vacio(JUGADOR_ID)
                .agregar(new ElementoInventario(
                        "heroe-1", "producto-guerrero", TipoElementoInventario.HEROE, "Mi Guerrero"))
                .agregar(new ElementoInventario(
                        "espada-1", "producto-espada", TipoElementoInventario.ARMA, "mi espada"));
        repositorio.guardar(inventario.equipar("heroe-1", "espada-1"));

        Optional<HeroeActivo> resultado = consulta.consultar(JUGADOR_ID);

        assertThat(resultado).isPresent();
        HeroeActivo heroeActivo = resultado.get();
        assertThat(heroeActivo.nombre()).isEqualTo("Mi Guerrero");
        assertThat(heroeActivo.estadisticas().vida()).isEqualTo(44);
        assertThat(heroeActivo.estadisticas().nivel()).isEqualTo(1);
        // Espada de una mano: +1 ataque -> base 10 pasa a 11, confirma que
        // el equipamiento SI se aplico (no es solo la base del heroe).
        assertThat(heroeActivo.estadisticas().ataqueDetalle().base()).isEqualTo(11);
    }

    @Test
    @DisplayName("dos o mas heroes lanza excepcion propia, nunca adivina cual es el activo")
    void dosOMasHeroesLanzaExcepcion() {
        Inventario inventario = Inventario.vacio(JUGADOR_ID)
                .agregar(new ElementoInventario(
                        "heroe-1", "producto-guerrero", TipoElementoInventario.HEROE, "Mi Guerrero"))
                .agregar(new ElementoInventario(
                        "heroe-2", "producto-mago", TipoElementoInventario.HEROE, "Mi Mago"));
        repositorio.guardar(inventario);

        assertThatThrownBy(() -> consulta.consultar(JUGADOR_ID))
                .isInstanceOf(SeleccionDeHeroeActivoNoDefinidaException.class);
    }

    @Test
    @DisplayName("exige identidad")
    void exigeIdentidad() {
        assertThatThrownBy(() -> consulta.consultar(null))
                .isInstanceOf(IdentidadRequeridaException.class);
        assertThatThrownBy(() -> consulta.consultar("  "))
                .isInstanceOf(IdentidadRequeridaException.class);
    }

    // --- Dobles en memoria (mismo patron que CalcularEstadisticasEquipadasTest) ---

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
