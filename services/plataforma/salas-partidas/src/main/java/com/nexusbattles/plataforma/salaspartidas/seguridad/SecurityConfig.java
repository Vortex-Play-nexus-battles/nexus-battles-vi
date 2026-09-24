package com.nexusbattles.plataforma.salaspartidas.seguridad;

import com.nexusbattles.comun.seguridad.CadenaDeSeguridad;
import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servicio de salas y partidas.
 *
 * <p>El andamiaje —sin CSRF, sin estado y token de Keycloak traducido— viene de
 * {@link CadenaDeSeguridad}, compartido con el resto de la plataforma. Aqui solo
 * las reglas de rutas de este dominio.
 *
 * <p>Crear una sala exige rol JUGADOR: RF-JUE-001 la describe como accion del
 * jugador, y la seccion 3.1.1 del SRS dice que el visitante «no podra participar
 * en partidas». Actuator queda abierto porque lo consulta la sonda de salud, que
 * no tiene token (regla 3).
 *
 * <p>Leer, en cambio, no es participar. Los roles de operacion pueden consultar
 * salas y partidas porque la consola administrativa tiene una seccion
 * «Partidas» y porque quien atiende el reporte de una partida necesita poder
 * mirarla. La separacion es por METODO: GET para operacion y jugador, el resto
 * solo para el jugador.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public ConversorRolesJwt conversorRolesJwt() {
        return new ConversorRolesJwt();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ConversorRolesJwt conversor) throws Exception {
        CadenaDeSeguridad.aplicarBase(http, conversor);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                // Handshake del chat (HU-JUE-015): el navegador no manda cabeceras
                // aqui; el JWT se exige en el CONNECT de STOMP (AutenticacionStomp).
                .requestMatchers("/ws/**").permitAll()
                // LEER salas y partidas lo pueden hacer, ademas del jugador,
                // los roles de operacion. La consola administrativa tiene una
                // seccion «Partidas» y sin esto devolvia 403 a un super
                // administrador: quien responde un reporte de una partida no
                // podia ni ver la partida. Es una ampliacion de LECTURA y
                // nada mas.
                .requestMatchers(HttpMethod.GET, "/api/v1/salas/**", "/api/v1/partidas/**")
                .hasAnyRole("JUGADOR", "MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")
                // Todo lo que ESCRIBE sigue siendo del jugador: crear una sala,
                // entrar, verificar heroe, jugar. RF-JUE-001 la describe como
                // accion del jugador y un administrador no juega por nadie.
                .requestMatchers("/api/v1/salas/**").hasRole("JUGADOR")
                .anyRequest().authenticated());

        return http.build();
    }
}
