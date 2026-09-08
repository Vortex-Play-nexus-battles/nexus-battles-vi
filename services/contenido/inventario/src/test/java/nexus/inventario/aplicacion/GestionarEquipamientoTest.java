package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GestionarEquipamientoTest {

    private RepositorioInventariosEnMemoria repositorio;
    private GestionarEquipamiento gestion;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        gestion = new GestionarEquipamiento(repositorio);
    }

    @Test
    @DisplayName("el propietario equipa y desequipa un elemento de su inventario")
    void equiparYDesequiparPropio() {
        guardarInventario("jugador-A", "heroe-A", "arma-A");

        EquipamientoHeroe equipado = gestion.equipar("jugador-A", "heroe-A", "arma-A");
        EquipamientoHeroe vacio = gestion.desequipar("jugador-A", "heroe-A", "arma-A");

        assertEquals(java.util.List.of("arma-A"), equipado.armas());
        assertEquals(java.util.List.of(), vacio.armas());
        assertEquals(vacio, repositorio.buscarPorPropietario("jugador-A")
                .orElseThrow().equipamiento("heroe-A"));
    }

    @Test
    @DisplayName("un jugador no puede equipar sobre el heroe de otro")
    void rechazarHeroeAjeno() {
        guardarInventario("jugador-B", "heroe-B", "arma-B");

        assertThrows(InventarioAjenoException.class,
                () -> gestion.equipar("jugador-A", "heroe-B", "arma-B"));
    }

    @Test
    @DisplayName("un jugador no puede equipar un elemento de otro inventario")
    void rechazarElementoAjeno() {
        guardarInventario("jugador-A", "heroe-A", "arma-A");
        guardarInventario("jugador-B", "heroe-B", "arma-B");

        assertThrows(InventarioAjenoException.class,
                () -> gestion.equipar("jugador-A", "heroe-A", "arma-B"));
    }

    @Test
    @DisplayName("consultar el equipo exige la identidad del propietario")
    void consultarEquipoPropio() {
        guardarInventario("jugador-A", "heroe-A", "arma-A");

        assertEquals("heroe-A", gestion.consultar("jugador-A", "heroe-A").heroeId());
        assertThrows(IdentidadRequeridaException.class,
                () -> gestion.consultar(null, "heroe-A"));
    }

    private void guardarInventario(String propietario, String heroeId, String armaId) {
        repositorio.guardar(Inventario.vacio(propietario)
                .agregar(new ElementoInventario(
                        heroeId, "producto-" + heroeId, TipoElementoInventario.HEROE, heroeId))
                .agregar(new ElementoInventario(
                        armaId, "producto-" + armaId, TipoElementoInventario.ARMA, armaId)));
    }
}
