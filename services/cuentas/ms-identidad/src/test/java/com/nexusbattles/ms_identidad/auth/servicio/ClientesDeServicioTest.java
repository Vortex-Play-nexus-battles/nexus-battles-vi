package com.nexusbattles.ms_identidad.auth.servicio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ClientesDeServicio · los clientes de servicio salen de la configuracion (ADR-005)")
class ClientesDeServicioTest {

    private static final String SECRETO = "un-secreto-de-al-menos-dieciseis";

    @Test
    @DisplayName("lee varias entradas cliente=secreto separadas por ; o ,")
    void leeVariasEntradas() {
        ClientesDeServicio clientes = new ClientesDeServicio(
                "salas-partidas=" + SECRETO + "; ms-subastas=" + SECRETO + "b ,comentarios=" + SECRETO + "c");

        assertEquals(java.util.Set.of("salas-partidas", "ms-subastas", "comentarios"), clientes.clientes());
        assertTrue(clientes.autentica("salas-partidas", SECRETO));
        assertTrue(clientes.autentica("ms-subastas", SECRETO + "b"));
    }

    @Test
    @DisplayName("un secreto equivocado, un cliente desconocido o un nulo no autentican")
    void rechaza() {
        ClientesDeServicio clientes = new ClientesDeServicio("salas-partidas=" + SECRETO);

        assertFalse(clientes.autentica("salas-partidas", SECRETO + "x"));
        assertFalse(clientes.autentica("salas-partidas", ""));
        assertFalse(clientes.autentica("otro", SECRETO));
        assertFalse(clientes.autentica(null, SECRETO));
        assertFalse(clientes.autentica("salas-partidas", null));
    }

    @Test
    @DisplayName("sin configuracion no hay clientes: nadie obtiene credencial")
    void sinConfiguracion() {
        assertTrue(new ClientesDeServicio("").clientes().isEmpty());
        assertTrue(new ClientesDeServicio(null).clientes().isEmpty());
        assertFalse(new ClientesDeServicio("").autentica("salas-partidas", SECRETO));
    }

    @Test
    @DisplayName("una entrada mal formada o un secreto corto no arrancan: mejor fallar al construir")
    void configuracionInvalida() {
        assertThrows(IllegalStateException.class, () -> new ClientesDeServicio("salas-partidas"));
        assertThrows(IllegalStateException.class, () -> new ClientesDeServicio("=secreto-largo-de-verdad-si"));
        assertThrows(IllegalStateException.class, () -> new ClientesDeServicio("salas-partidas="));
        assertThrows(IllegalStateException.class, () -> new ClientesDeServicio("salas-partidas=corto"));
    }
}
