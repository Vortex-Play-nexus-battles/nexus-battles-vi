package nexus.inventario.configuracion;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

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
        AuthorizationManager<RequestAuthorizationContext> soloSubastas = (authentication, context) -> {
            if (authentication.get() instanceof JwtAuthenticationToken jwt) {
                return new AuthorizationDecision(
                        subastasClientId.equals(jwt.getToken().getClaimAsString("azp")));
            }
            return new AuthorizationDecision(false);
        };
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/busqueda").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/inventario/elementos/*")
                .access(soloSubastas)
                .requestMatchers(HttpMethod.PUT, "/api/v1/inventario/elementos/*/bloqueo-subasta")
                .access(soloSubastas)
                .requestMatchers(HttpMethod.DELETE, "/api/v1/inventario/elementos/*/bloqueo-subasta/*")
                .access(soloSubastas)
                .anyRequest().permitAll());
        return http.build();
    }
}
