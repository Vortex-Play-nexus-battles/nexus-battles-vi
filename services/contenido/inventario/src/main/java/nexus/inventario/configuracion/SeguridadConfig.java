package nexus.inventario.configuracion;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/** Protege las operaciones internas sin alterar las rutas temporales del jugador. */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ConversorRolesJwt conversor,
            @Value("${integraciones.subastas.client-id}") String subastasClientId) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/busqueda").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/*")
                .access((authentication, context) -> {
                    if (authentication.get() instanceof JwtAuthenticationToken jwt) {
                        return new AuthorizationDecision(
                                subastasClientId.equals(jwt.getToken().getClaimAsString("azp")));
                    }
                    return new AuthorizationDecision(false);
                })
                .anyRequest().permitAll());
        return http.build();
    }
}
