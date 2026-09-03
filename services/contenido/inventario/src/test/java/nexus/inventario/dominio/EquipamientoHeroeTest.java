package nexus.inventario.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EquipamientoHeroeTest {

    @Test
    @DisplayName("un heroe puede llevar como maximo dos armas")
    void limiteDeArmas() {
        EquipamientoHeroe equipo = EquipamientoHeroe.vacio("heroe-1")
                .equipar(arma("arma-1"))
                .equipar(arma("arma-2"));

        assertEquals(List.of("arma-1", "arma-2"), equipo.armas());
        assertThrows(LimiteEquipamientoException.class,
                () -> equipo.equipar(arma("arma-3")));
    }

    @Test
    @DisplayName("un heroe admite una pieza en cada una de las seis ranuras de armadura")
    void seisRanurasDeArmadura() {
        EquipamientoHeroe equipo = EquipamientoHeroe.vacio("heroe-1");
        for (ParteArmadura parte : ParteArmadura.values()) {
            equipo = equipo.equipar(armadura("armadura-" + parte.name(), parte));
        }

        assertEquals(6, equipo.armaduras().size());
        assertEquals(ParteArmadura.values().length, equipo.armaduras().keySet().size());
    }

    @Test
    @DisplayName("dos piezas de armadura no pueden ocupar la misma ranura")
    void ranuraDeArmaduraUnica() {
        EquipamientoHeroe equipo = EquipamientoHeroe.vacio("heroe-1")
                .equipar(armadura("casco-1", ParteArmadura.CASCO));

        assertThrows(LimiteEquipamientoException.class,
                () -> equipo.equipar(armadura("casco-2", ParteArmadura.CASCO)));
    }

    @Test
    @DisplayName("una armadura sin parte de catalogo no se puede equipar")
    void armaduraSinRanura() {
        ElementoInventario armadura = new ElementoInventario(
                "armadura-1", "producto-1", TipoElementoInventario.ARMADURA, "Armadura", null);

        assertThrows(ElementoNoEquipableException.class,
                () -> EquipamientoHeroe.vacio("heroe-1").equipar(armadura));
    }

    @Test
    @DisplayName("un heroe puede llevar como maximo dos items")
    void limiteDeItems() {
        EquipamientoHeroe equipo = EquipamientoHeroe.vacio("heroe-1")
                .equipar(item("item-1"))
                .equipar(item("item-2"));

        assertEquals(List.of("item-1", "item-2"), equipo.items());
        assertThrows(LimiteEquipamientoException.class,
                () -> equipo.equipar(item("item-3")));
    }

    @Test
    @DisplayName("heroes habilidades y epicas no ocupan ranuras de equipo")
    void tiposNoEquipables() {
        for (TipoElementoInventario tipo : List.of(
                TipoElementoInventario.HEROE,
                TipoElementoInventario.HABILIDAD,
                TipoElementoInventario.EPICA)) {
            ElementoInventario elemento = new ElementoInventario(
                    "elemento-" + tipo, "producto-1", tipo, "No equipable");
            assertThrows(ElementoNoEquipableException.class,
                    () -> EquipamientoHeroe.vacio("heroe-1").equipar(elemento));
        }
    }

    @Test
    @DisplayName("desequipar libera la ranura para otro elemento")
    void desequiparLiberaRanura() {
        EquipamientoHeroe equipo = EquipamientoHeroe.vacio("heroe-1")
                .equipar(armadura("casco-1", ParteArmadura.CASCO))
                .desequipar("casco-1")
                .equipar(armadura("casco-2", ParteArmadura.CASCO));

        assertFalse(equipo.contiene("casco-1"));
        assertEquals("casco-2", equipo.armaduras().get(ParteArmadura.CASCO));
    }

    private ElementoInventario arma(String id) {
        return new ElementoInventario(id, "producto-" + id, TipoElementoInventario.ARMA, id);
    }

    private ElementoInventario armadura(String id, ParteArmadura parte) {
        return new ElementoInventario(
                id, "producto-" + id, TipoElementoInventario.ARMADURA, id, parte);
    }

    private ElementoInventario item(String id) {
        return new ElementoInventario(id, "producto-" + id, TipoElementoInventario.ITEM, id);
    }
}
