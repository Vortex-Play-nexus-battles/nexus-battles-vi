package com.nexusbattles.plataforma.salaspartidas;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SalasPartidasApplication · arranque")
class SalasPartidasApplicationTest {

    @Test
    @DisplayName("HU-DIS-003: la cache de DNS de la JVM queda acotada antes de arrancar (5 s positiva, 1 s negativa)")
    void acotaLaCacheDeDns() {
        // Lo destapo el E2E: con los 30 s / 10 s por defecto, un contenedor
        // recien encendido seguia siendo «desconocido» para este servicio y el
        // primer reintento del jugador abria el circuito justo cuando la
        // dependencia ya habia vuelto.
        SalasPartidasApplication.acotarCacheDeDns();

        assertAll(
                () -> assertEquals("5", Security.getProperty("networkaddress.cache.ttl")),
                () -> assertEquals("1", Security.getProperty("networkaddress.cache.negative.ttl")));
    }
}
