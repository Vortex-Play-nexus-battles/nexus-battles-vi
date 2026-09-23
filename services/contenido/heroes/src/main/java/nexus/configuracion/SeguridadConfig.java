package nexus.configuracion;

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
 * Seguridad del servicio de heroes.
 *
 * <h2>Por que aparece ahora y no antes</h2>
 *
 * <p>Hasta el bloque R8 este modulo aplicaba solo {@code nexus.spring-conventions},
 * de modo que <b>no tenia Spring Security en el classpath</b>: los cuatro
 * controladores respondian a cualquiera, y el borde publica
 * {@code /api/v1/(heroes|equipos|estrategias|progresion)} a Internet
 * ({@code infrastructure/red-balanceo/borde-dev.conf}). La auditoria de rumbo
 * del 2026-09-22 lo clasifico como CRITICO.
 *
 * <h2>Clasificacion de cada ruta</h2>
 *
 * <p>No todo se cierra igual: cerrar de mas rompe consumidores legitimos y
 * cerrar de menos deja el agujero. Cada ruta se clasifico por lo que hace,
 * no por su verbo.
 *
 * <ul>
 *   <li><b>Catalogo, publico</b> — {@code GET /api/v1/heroes/**},
 *       {@code GET /api/v1/progresion/niveles} y
 *       {@code GET /api/v1/progresion/experiencia-por-enemigo/*}.
 *       Son las fichas de los prototipos y las tablas de referencia del juego:
 *       datos del <i>producto</i>, identicos para todo el mundo, sin un solo
 *       dato de jugador. Los consumen hoy, sin credencial, tres servicios
 *       ({@code motor-combate} via {@code ClienteHeroesHttp},
 *       {@code inventario} via {@code ResolutorDeEstadisticasHeroeHttp} y
 *       {@code salas-partidas} via {@code ClienteInventarioHeroes}). Cerrarlos
 *       en este PR habria roto el combate sin ganar nada que proteger.</li>
 *
 *   <li><b>Calculo del jugador</b> — {@code POST /api/v1/equipos/validacion} y
 *       {@code POST /api/v1/estrategias/validacion}. No mutan nada, pero son
 *       computo del servidor y contestan a una pregunta que solo tiene sentido
 *       dentro de una partida. Exigen un usuario autenticado (o un servicio
 *       actuando por el).</li>
 *
 *   <li><b>Logica de servidor</b> — {@code POST /api/v1/estrategias/decision} y
 *       {@code POST /api/v1/progresion/experiencia}. La primera <b>decide la
 *       jugada de la maquina</b>; la segunda resuelve la subida de nivel. Las
 *       dos son decisiones del servidor: si el cliente pudiera invocarlas a
 *       voluntad, podria adelantarse a la IA o calcularse su propia
 *       progresion. Solo {@code ROLE_SERVICIO}.</li>
 *
 *   <li><b>Actuator</b> — abierto para la sonda de salud (regla 3).</li>
 * </ul>
 *
 * <p><b>Comprobado antes de cerrar:</b> ningun servicio del monorepo llama hoy
 * a las cuatro rutas POST — solo la coleccion de Postman de este servicio. Por
 * eso pasar {@code decision} y {@code experiencia} a {@code ROLE_SERVICIO} no
 * rompe a nadie, y deja la puerta lista para cuando la IA se sirva de aqui.
 *
 * <h2>Identidad</h2>
 *
 * <p>Se usa {@link ConversorRolesJwt} compartido, que ya implementa ADR-002:
 * el principal es {@code uid} cuando existe, nunca el {@code sub} (que es el
 * apodo y es mutable). Este servicio no guarda nada por usuario, asi que no
 * lee la identidad; la necesita solo para el rol.
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    /** Quien puede pedir un calculo: cualquier usuario real, o un servicio por el. */
    static final String[] ROLES_DEL_JUEGO =
            {"JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR", "SERVICIO"};

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor)
            throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);

        // La cadena base deja el 401 vacio de Spring. La regla 4 de plataforma
        // pide el mismo problem detail en los 20 modulos, asi que se escribe
        // aqui igual que en inventario y productos.
        http.exceptionHandling(excepciones -> excepciones
                .authenticationEntryPoint((solicitud, respuesta, error) -> problema(
                        solicitud, respuesta, HttpStatus.UNAUTHORIZED,
                        "No autenticado",
                        "Se requiere un token Bearer valido",
                        "urn:nexus:problema:no-autenticado"))
                .accessDeniedHandler((solicitud, respuesta, error) -> problema(
                        solicitud, respuesta, HttpStatus.FORBIDDEN,
                        "Acceso denegado",
                        "Tu token no tiene permiso para esta operacion",
                        "urn:nexus:problema:acceso-denegado")));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()

                // La pagina de demo del catalogo (src/main/resources/static).
                // No lleva un solo dato de jugador y es el unico recurso
                // estatico del servicio; lo destapo AceptacionDeHeroesTest, que
                // comprueba que la raiz sirve algo y salia 401 al cerrar
                // `anyRequest`.
                .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico").permitAll()

                // --- Catalogo del juego: publico ---
                .requestMatchers(HttpMethod.GET, "/api/v1/heroes", "/api/v1/heroes/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/progresion/niveles").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/progresion/experiencia-por-enemigo/*")
                .permitAll()

                // --- Logica de servidor: solo servicios ---
                .requestMatchers(HttpMethod.POST, "/api/v1/estrategias/decision")
                .hasRole("SERVICIO")
                .requestMatchers(HttpMethod.POST, "/api/v1/progresion/experiencia")
                .hasRole("SERVICIO")

                // --- Calculo a peticion del jugador ---
                .requestMatchers(HttpMethod.POST, "/api/v1/equipos/validacion")
                .hasAnyRole(ROLES_DEL_JUEGO)
                .requestMatchers(HttpMethod.POST, "/api/v1/estrategias/validacion")
                .hasAnyRole(ROLES_DEL_JUEGO)

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
