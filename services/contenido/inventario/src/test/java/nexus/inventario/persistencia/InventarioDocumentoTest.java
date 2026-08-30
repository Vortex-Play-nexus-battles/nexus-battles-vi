package nexus.inventario.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventarioDocumentoTest {

    @Test
    @DisplayName("el documento Mongo conserva el agregado sin duplicar datos del catalogo")
    void conversionCompleta() {
        ElementoInventario heroe = new ElementoInventario(
                "heroe-1", "producto-heroe", TipoElementoInventario.HEROE, "Heroe propio");
        ElementoInventario casco = new ElementoInventario(
                "casco-1", "producto-casco", TipoElementoInventario.ARMADURA,
                "Casco propio", ParteArmadura.CASCO);
        Inventario inventario = new Inventario(
                "inventario-1", "jugador-A", java.util.List.of(heroe, casco))
                .equipar(heroe.id(), casco.id());

        InventarioDocumento documento = InventarioDocumento.de(inventario);
        Inventario restaurado = documento.aDominio();

        assertEquals(inventario, restaurado);
    }

    @Test
    @DisplayName("un documento anterior sin equipamiento se migra como lista vacia")
    void documentoAnteriorSinEquipamiento() {
        InventarioDocumento documento = new InventarioDocumento(
                "inventario-1", "jugador-A", java.util.List.of(), null);

        assertEquals(java.util.List.of(), documento.aDominio().equipamientos());
    }
}
