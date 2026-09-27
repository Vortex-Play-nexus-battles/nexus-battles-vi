package nexus.inventario.aplicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoDisponibleException;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.LimiteEquipamientoException;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GestionarEquipamientoTest {

    private RepositorioInventariosEnMemoria repositorio;
    private CatalogoDeProductosEnMemoria catalogo;
    private GestionarEquipamiento gestion;

    @BeforeEach
    void preparar() {
        repositorio = new RepositorioInventariosEnMemoria();
        catalogo = new CatalogoDeProductosEnMemoria();
        gestion = new GestionarEquipamiento(repositorio, catalogo);
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

    @Test
    @DisplayName("un producto bloqueado por subasta no se puede equipar")
    void rechazarProductoBloqueado() {
        guardarInventario("jugador-A", "heroe-A", "arma-A");
        Inventario inventario = repositorio.buscarPorPropietario("jugador-A").orElseThrow();
        repositorio.guardar(inventario.bloquearEnSubasta("arma-A", "subasta-1"));

        assertThrows(ElementoNoDisponibleException.class,
                () -> gestion.equipar("jugador-A", "heroe-A", "arma-A"));
    }

    @Test
    @DisplayName("B4: un heroe comprometido en una subasta no cambia de equipo")
    void rechazarHeroeEnSubasta() {
        guardarInventario("jugador-A", "heroe-A", "arma-A");
        Inventario inventario = repositorio.buscarPorPropietario("jugador-A").orElseThrow();
        repositorio.guardar(inventario.bloquearEnSubasta("heroe-A", "subasta-1"));

        ElementoNoDisponibleException error = assertThrows(ElementoNoDisponibleException.class,
                () -> gestion.equipar("jugador-A", "heroe-A", "arma-A"));

        assertEquals("El heroe esta bloqueado por una subasta vigente y no se puede equipar.", error.getMessage());
        assertEquals(List.of(), repositorio.buscarPorPropietario("jugador-A").orElseThrow()
                .equipamiento("heroe-A").armas());
    }

    @Test
    @DisplayName("B4: la ranura de una armadura es la del catalogo, no la que guardo el elemento")
    void laParteLaDecideElCatalogo() {
        catalogo.registrarArmadura("producto-peto", ParteArmadura.PECHO);
        guardarConArmadura("jugador-A", "heroe-A", "peto-A", "producto-peto", ParteArmadura.CASCO);

        EquipamientoHeroe equipado = gestion.equipar("jugador-A", "heroe-A", "peto-A");

        assertEquals("peto-A", equipado.armaduras().get(ParteArmadura.PECHO));
        assertNull(equipado.armaduras().get(ParteArmadura.CASCO));
        assertEquals(ParteArmadura.PECHO, repositorio.buscarPorPropietario("jugador-A").orElseThrow()
                .elemento("peto-A").parteArmadura(), "la parte guardada se corrige en la misma escritura");
    }

    @Test
    @DisplayName("B4: dos armaduras de la misma parte segun el catalogo no caben, aunque se guardaran con otra")
    void dosArmadurasDeLaMismaParte() {
        catalogo.registrarArmadura("producto-casco", ParteArmadura.CASCO);
        catalogo.registrarArmadura("producto-casco-2", ParteArmadura.CASCO);
        guardarConArmadura("jugador-A", "heroe-A", "casco-A", "producto-casco", ParteArmadura.CASCO);
        Inventario inventario = repositorio.buscarPorPropietario("jugador-A").orElseThrow();
        repositorio.guardar(inventario.agregar(new ElementoInventario(
                "casco-B", "producto-casco-2", TipoElementoInventario.ARMADURA, "otro casco", ParteArmadura.ZAPATOS)));

        gestion.equipar("jugador-A", "heroe-A", "casco-A");

        assertThrows(LimiteEquipamientoException.class, () -> gestion.equipar("jugador-A", "heroe-A", "casco-B"));
    }

    @Test
    @DisplayName("B4: si el catalogo no declara la parte, vale la guardada")
    void sinParteEnElCatalogoValeLaGuardada() {
        catalogo.registrarArmadura("producto-viejo", (ParteArmadura) null);
        guardarConArmadura("jugador-A", "heroe-A", "botas-A", "producto-viejo", ParteArmadura.ZAPATOS);

        assertEquals("botas-A", gestion.equipar("jugador-A", "heroe-A", "botas-A")
                .armaduras().get(ParteArmadura.ZAPATOS));
    }

    @Test
    @DisplayName("B4: equipar una armadura con el catalogo caido es 503 y no toca nada")
    void catalogoCaidoAlEquiparArmadura() {
        catalogo.registrarArmadura("producto-peto", ParteArmadura.PECHO);
        guardarConArmadura("jugador-A", "heroe-A", "peto-A", "producto-peto", ParteArmadura.PECHO);
        catalogo.caer();
        int guardadosAntes = repositorio.guardados();

        assertThrows(CatalogoNoDisponibleException.class, () -> gestion.equipar("jugador-A", "heroe-A", "peto-A"));

        assertEquals(guardadosAntes, repositorio.guardados());
    }

    @Test
    @DisplayName("B4: una armadura cuyo producto ya no existe no se equipa (404)")
    void armaduraDeProductoInexistente() {
        guardarConArmadura("jugador-A", "heroe-A", "peto-A", "producto-borrado", ParteArmadura.PECHO);

        assertThrows(ProductoNoEncontradoException.class, () -> gestion.equipar("jugador-A", "heroe-A", "peto-A"));
    }

    @Test
    @DisplayName("B4: un arma no consulta el catalogo: equipar sigue funcionando con el catalogo caido")
    void unArmaNoConsultaElCatalogo() {
        guardarInventario("jugador-A", "heroe-A", "arma-A");
        catalogo.caer();

        assertEquals(List.of("arma-A"), gestion.equipar("jugador-A", "heroe-A", "arma-A").armas());
        assertEquals(0, catalogo.consultas());
    }

    private void guardarInventario(String propietario, String heroeId, String armaId) {
        repositorio.guardar(Inventario.vacio(propietario)
                .agregar(new ElementoInventario(
                        heroeId, "producto-" + heroeId, TipoElementoInventario.HEROE, heroeId))
                .agregar(new ElementoInventario(
                        armaId, "producto-" + armaId, TipoElementoInventario.ARMA, armaId)));
    }

    private void guardarConArmadura(
            String propietario, String heroeId, String armaduraId, String productoId, ParteArmadura parteGuardada) {
        repositorio.guardar(Inventario.vacio(propietario)
                .agregar(new ElementoInventario(
                        heroeId, "producto-" + heroeId, TipoElementoInventario.HEROE, heroeId))
                .agregar(new ElementoInventario(
                        armaduraId, productoId, TipoElementoInventario.ARMADURA, armaduraId, parteGuardada)));
    }
}
