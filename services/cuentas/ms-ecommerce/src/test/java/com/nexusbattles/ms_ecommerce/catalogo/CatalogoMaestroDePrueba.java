package com.nexusbattles.ms_ecommerce.catalogo;

import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * El {@link CatalogoMaestro} real sobre un servidor HTTP simulado, para las
 * pruebas de otros paquetes. Usa el mismo constructor de cliente que el bean
 * desplegado ({@code ConfiguracionDelCatalogo.constructorDelCliente}); solo
 * cambia la fabrica de peticiones.
 */
public final class CatalogoMaestroDePrueba {

    public static final String BASE = "http://catalogo.test";

    private final RestClient.Builder constructor = ConfiguracionDelCatalogo.constructorDelCliente(
            new PropiedadesDelCatalogo(BASE, Duration.ofSeconds(2), Duration.ofSeconds(3)));
    private final MockRestServiceServer servidor = MockRestServiceServer.bindTo(constructor).build();
    private final CatalogoMaestro cliente = new CatalogoMaestro(constructor.build());

    public MockRestServiceServer servidor() {
        return servidor;
    }

    public CatalogoMaestro cliente() {
        return cliente;
    }
}
