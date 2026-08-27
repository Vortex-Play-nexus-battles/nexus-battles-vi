package nexus.configuracion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SeguridadConfig {

        @Bean
        public SecurityFilterChain cadenaDeSeguridad(
                        HttpSecurity http,
                        JwtAuthenticationConverter convertidorRoles) throws Exception {

                http
                        .csrf(AbstractHttpConfigurer::disable)
                        .sessionManagement(session -> session
                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .authorizeHttpRequests(autorizacion -> autorizacion
                                .requestMatchers(
                                        "/actuator/health/**",
                                        "/actuator/info",
                                        "/actuator/prometheus")
                                .permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/v1/productos")
                                .hasAnyRole("ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                                .anyRequest()
                                .authenticated())
                        .oauth2ResourceServer(oauth2 -> oauth2
                                .jwt(jwt -> jwt
                                        .jwtAuthenticationConverter(convertidorRoles)));

                return http.build();
        }

        @Bean
        public JwtAuthenticationConverter convertidorRolesKeycloak() {
                JwtGrantedAuthoritiesConverter convertidorPermisos =
                        new JwtGrantedAuthoritiesConverter();

                JwtAuthenticationConverter convertidor =
                        new JwtAuthenticationConverter();

                convertidor.setJwtGrantedAuthoritiesConverter(jwt -> {
                        Collection<GrantedAuthority> autoridades = new ArrayList<>();

                        Collection<GrantedAuthority> permisos =
                                convertidorPermisos.convert(jwt);

                        if (permisos != null) {
                                autoridades.addAll(permisos);
                        }

                        Map<String, Object> accesoAlReino =
                                jwt.getClaimAsMap("realm_access");

                        if (accesoAlReino != null
                                        && accesoAlReino.get("roles") instanceof Collection<?> roles) {
                                roles.stream()
                                        .map(Object::toString)
                                        .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                                        .forEach(autoridades::add);
                        }

                        return autoridades;
                });

                return convertidor;
        }
}