package com.nexusbattles.ms_subastas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientFake;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientHttp;
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

    @Test
    void creaInstanciaHttpCuandoSeConfigura() {
        InventarioClient client = config.inventarioClientHttp("http://localhost:8080", 5000, new ObjectMapper());
        assertNotNull(client);
        assertInstanceOf(InventarioClientHttp.class, client);
    }
}
