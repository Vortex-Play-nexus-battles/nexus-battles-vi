package com.nexusbattles.ms_subastas.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena de filtros de Spring Security de este servicio — transitoria.
 *
 * <p>{@code plataforma-seguridad} entro al classpath por su lado cliente
 * ({@code TokenDeServicio}, ADR-005: la credencial con la que este servicio
 * habla con ms-finanzas e inventario), y arrastra {@code spring-security}. Sin
 * una cadena propia, Boot pondria la suya por defecto (todo autenticado con
 * usuario y clave generados) y ninguna ruta de subastas respondería.
 *
 * <p>Las rutas de este servicio siguen protegidas donde siempre: cada
 * controlador exige el JWT a traves de {@code IdentidadDesdeToken} /
 * {@code ValidadorDeToken}. Por eso aqui todo pasa: la cadena solo apaga lo
 * que no aplica a una API sin sesion (CSRF, estado). Cuando este servicio
 * migre al servidor de recursos compartido ({@code CadenaDeSeguridad} +
 * {@code ConversorRolesJwt}, como hicieron ms-finanzas, ms-ecommerce y
 * ms-cumplimiento), esta clase se sustituye por las reglas de rutas reales.
 */
@Configuration
public class SeguridadWebConfig {

    @Bean
    public SecurityFilterChain cadenaTransitoria(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
