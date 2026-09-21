package com.nexusbattles.plataforma.salaspartidas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class SalasPartidasApplication {

    /**
     * Cache de DNS de la JVM acotada — HU-DIS-003.
     *
     * <p>Por defecto la JVM recuerda 30 s una direccion resuelta y 10 s un
     * nombre que no resolvio. En una red de contenedores eso se traduce en
     * dos cosas que destapo el E2E de inyeccion de fallos: (1) un servicio
     * recien apagado se sigue llamando a su direccion vieja hasta 30 s, y
     * (2) un servicio recien encendido sigue siendo «desconocido» hasta 10 s
     * despues de que ya contesta, asi que el primer reintento del jugador
     * fallaba y abria el circuito justo cuando la dependencia habia vuelto.
     * Con 5 s y 1 s la recuperacion se ve cuando ocurre. Se fija aqui, antes
     * de que nada resuelva un nombre, porque la JVM lee estos valores una
     * sola vez.
     */
    static void acotarCacheDeDns() {
        Security.setProperty("networkaddress.cache.ttl", "5");
        Security.setProperty("networkaddress.cache.negative.ttl", "1");
    }

    public static void main(String[] args) {
        acotarCacheDeDns();
        SpringApplication.run(SalasPartidasApplication.class, args);
    }
}
