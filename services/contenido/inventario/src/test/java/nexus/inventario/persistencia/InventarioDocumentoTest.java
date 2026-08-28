package nexus.inventario.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InventarioDocumentoTest {

    @Test
    @DisplayName("el documento Mongo conserva el agregado sin duplicar datos del catalogo")
    void conversionCompleta() {
        Inventario inventario = new Inventario("inventario-1", "jugador-A", java.util.List.of(
                new ElementoInventario(
                        "elemento-1", "producto-42", TipoElementoInventario.ARMA, "Espada propia")));

        InventarioDocumento documento = InventarioDocumento.de(inventario);
        Inventario restaurado = documento.aDominio();

        assertEquals(inventario, restaurado);
    }
}
