package com.nexusbattles.ms_subastas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClient;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientFake;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientHttp;
import com.nexusbattles.ms_subastas.subastas.port.InventarioClientResiliente;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        InventarioClient client = config.inventarioClientHttp("http://localhost:8080", 5000, new ObjectMapper(),
                conCredencial());

        assertNotNull(client);
        assertInstanceOf(InventarioClientResiliente.class, client);
        assertFalse(client instanceof InventarioClientHttp,
                "sin envolver, una caida de inventario se lleva por delante las pujas de la subasta");
    }

    /**
     * ADR-005 / #451: contra el inventario real hace falta la credencial de
     * servicio. Sin ella el servicio no debe arrancar en modo http.
     */
    @Test
    void elClienteHttpExigeCredencialDeServicio() {
        @SuppressWarnings("unchecked")
        ObjectProvider<TokenDeServicio> sinCredencial = mock(ObjectProvider.class);
        when(sinCredencial.getIfAvailable()).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> config.inventarioClientHttp("http://localhost:8080", 5000, new ObjectMapper(), sinCredencial));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<TokenDeServicio> conCredencial() {
        ObjectProvider<TokenDeServicio> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(() -> "token-de-prueba");
        return proveedor;
    }
}
