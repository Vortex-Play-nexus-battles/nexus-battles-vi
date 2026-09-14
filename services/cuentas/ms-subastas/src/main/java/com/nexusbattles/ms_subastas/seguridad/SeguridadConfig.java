package com.nexusbattles.ms_subastas.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Crea el validador de tokens como bean.
 *
 * <p>{@link ValidadorDeToken} no lleva anotaciones de Spring a proposito: esta
 * escrito para poder mudarse a {@code shared/libs/} cuando los tres equipos
 * acuerden esa estructura, y una clase con {@code @Component} ya no seria
 * reutilizable fuera de Spring. Asi que el cableado vive aqui y la clase se
 * queda limpia.
 */
@Configuration
public class SeguridadConfig {

    /**
     * @param claveSecreta la misma con la que firma ms-identidad. Por variable
     *        de entorno (regla 10 de plataforma): ningun valor real se versiona.
     *        Si difiere de la de identidad, todo token legitimo se rechaza por
     *        firma invalida — es el primer sitio donde mirar si el login
     *        funciona pero las pujas devuelven 401.
     */
    @Bean
    public ValidadorDeToken validadorDeToken(
            @Value("${app.jwt.clave-secreta}") String claveSecreta,
            Clock clock) {
        return new ValidadorDeToken(claveSecreta, clock);
    }
}
