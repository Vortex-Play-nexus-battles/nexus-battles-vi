package com.nexusbattles.ms_subastas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientFake;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientHttp;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientResiliente;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventarioClientConfigTest {

    private final InventarioClientConfig config = new InventarioClientConfig();

    @Test
    void creaInstanciaFakePorDefecto() {
        InventarioClient client = config.inventarioClientFake();
        assertNotNull(client);
        assertInstanceOf(InventarioClientFake.class, client);
    }

    /**
     * El cliente real va envuelto en el cortacircuitos, no desnudo. Importa
     * comprobarlo: estas llamadas ocurren dentro del lock pesimista de la
     * subasta, asi que un inventario lento sin cortar congela todas las pujas
     * de esa subasta. Si alguien quita el envoltorio, esto lo detecta.
     */
    @Test
    void elClienteHttpVaEnvueltoEnElCortacircuitos() {
        InventarioClient client = config.inventarioClientHttp("http://localhost:8080", 5000, new ObjectMapper());

        assertNotNull(client);
        assertInstanceOf(InventarioClientResiliente.class, client);
        assertFalse(client instanceof InventarioClientHttp,
                "sin envolver, una caida de inventario se lleva por delante las pujas de la subasta");
    }
}
