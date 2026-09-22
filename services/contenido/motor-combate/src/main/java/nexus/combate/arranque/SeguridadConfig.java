package nexus.combate.arranque;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del motor de combate.
 *
 * <h2>Por que este servicio es el caso mas claro de todos</h2>
 *
 * <p>{@code POST /api/v1/combate/ataques} <b>resuelve un ataque</b>: tira los
 * dados, aplica la tabla de efectos y devuelve el dano. Es la pieza que decide
 * quien gana una partida. Hasta R8 no tenia Spring Security en el classpath, de
 * modo que cualquiera que alcanzara el puerto podia resolver ataques — y con
 * ello sondear la tabla de efectos y el generador, que es exactamente lo que un
 * jugador no debe poder hacer.
 *
 * <p>Lo unico que lo contenia era la red: el borde no enruta
 * {@code /api/v1/combate}, asi que la exposicion quedaba en el puerto 8104 del
 * host de contenido, abierto a {@code 0.0.0.0/0} en su Security Group. Eso es
 * una mitigacion de infraestructura, no una de aplicacion, y se cierra en R9.4.
 *
 * <h2>Clasificacion</h2>
 *
 * <ul>
 *   <li>{@code POST /api/v1/combate/ataques} — <b>solo {@code ROLE_SERVICIO}</b>.
 *       Su unico consumidor legitimo es {@code salas-partidas}
 *       ({@code ClienteMotorCombate}), que es quien conoce el estado de la
 *       partida. Un jugador nunca debe invocarlo directamente: el motor no
 *       guarda estado y creeria cualquier peticion que le llegue bien formada.</li>
 *   <li>{@code GET /api/v1/combate/distribuciones} — autenticado. Es la tabla
 *       de probabilidades por prototipo: no es secreta (esta en el manual del
 *       juego) pero tampoco tiene por que servirse a un anonimo.</li>
 *   <li>{@code /actuator/**} — abierto para la sonda de salud (regla 3).</li>
 * </ul>
 *
 * <p><b>Consecuencia que este PR resuelve a la vez:</b> {@code ClienteMotorCombate}
 * era el <b>unico</b> cliente HTTP de {@code salas-partidas} que se construia
 * sin el interceptor de credencial de servicio. Cerrar esta ruta sin arreglarlo
 * habria dejado el combate en 401. Va corregido en el mismo cambio.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor)
            throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);

        http.exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint((solicitud, respuesta, error) -> problema(
                        solicitud, respuesta, HttpStatus.UNAUTHORIZED,
                        "No autenticado",
                        "Se requiere un token Bearer valido",
                        "urn:nexus:problema:no-autenticado"))
                .accessDeniedHandler((solicitud, respuesta, error) -> problema(
                        solicitud, respuesta, HttpStatus.FORBIDDEN,
                        "Acceso denegado",
                        "Resolver un ataque es una operacion de servicio",
                        "urn:nexus:problema:acceso-denegado")));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/combate/ataques").hasRole("SERVICIO")
                .requestMatchers(HttpMethod.GET, "/api/v1/combate/distribuciones").authenticated()
                .anyRequest().authenticated());

        return http.build();
    }

    static void problema(
            HttpServletRequest solicitud,
            HttpServletResponse respuesta,
            HttpStatus estado,
            String titulo,
            String detalle,
            String tipo) throws IOException {

        respuesta.setStatus(estado.value());
        if (estado == HttpStatus.UNAUTHORIZED) {
            respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.getWriter().write(
                """
                {"type":"%s","title":"%s","status":%d,"detail":"%s","instance":"%s"}"""
                        .formatted(tipo, titulo, estado.value(), detalle, solicitud.getRequestURI()));
    }
}
