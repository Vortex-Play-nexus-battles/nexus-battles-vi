package com.nexusbattles.plataforma.metricasplataforma.seguridad;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena abierta para las rebanadas {@code @WebMvcTest} que prueban el
 * COMPORTAMIENTO de un endpoint, no su seguridad.
 *
 * <p>Desde que el modulo es servidor de recursos (HU-MET-001, #527) una
 * rebanada sin cadena ni siquiera levanta: la autoconfiguracion de OAuth2 pide
 * un {@code HttpSecurity} que solo aparece con {@code @EnableWebSecurity}. En
 * vez de repetir el token administrativo en cada peticion de seis ficheros de
 * prueba —lo que las volveria pruebas de seguridad disfrazadas— se importa
 * esta cadena, que deja pasar todo.
 *
 * <p>Que la API real exija rol administrativo lo afirma
 * {@link SeguridadDeObservabilidadTest} contra {@link SecurityConfig}, la
 * cadena de produccion, con tokens firmados de verdad.
 */
@TestConfiguration
@EnableWebSecurity
public class SeguridadAbiertaDePrueba {

    @Bean
    SecurityFilterChain cadenaAbierta(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
