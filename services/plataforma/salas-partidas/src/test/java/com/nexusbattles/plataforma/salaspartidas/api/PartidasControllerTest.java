package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API del combate — RF-JUE-017.
 *
 * <p>Se comprueba la forma del contrato y los dos caminos que la interfaz
 * distingue: la partida existe, o no existe y sale en problem details.
 */
@WebMvcTest(controllers = PartidasController.class)
@Import({SecurityConfig.class, ManejadorDeErrores.class})
class PartidasControllerTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_PARTIDA = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ObtenerPartida obtenerPartida;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor jugador() {
        return jwt().jwt(token -> token
                        .subject("demo_grupo6")
                        .claim("uid", ANFITRION.toString()))
                .authorities(new org.springframework.security.core.authority
                        .SimpleGrantedAuthority("ROLE_JUGADOR"));
    }

    private static Partida partidaDeEjemplo() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 320, true, false, null), ANFITRION);
        return Partida.iniciar(sala, Instant.parse("2026-09-17T20:00:00Z"));
    }

    @Test
    @DisplayName("la partida sale con la forma exacta del contrato")
    void devuelveLaPartida() throws Exception {
        when(obtenerPartida.ejecutar(any())).thenReturn(partidaDeEjemplo());

        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_CURSO"))
                .andExpect(jsonPath("$.recompensaEnJuego").value(320))
                .andExpect(jsonPath("$.participantes.length()").value(2))
                .andExpect(jsonPath("$.participantes[0].jugador").value(ANFITRION.toString()))
                .andExpect(jsonPath("$.participantes[0].esIA").value(false))
                .andExpect(jsonPath("$.participantes[1].esIA").value(true))
                .andExpect(jsonPath("$.turnoActual.idJugador").value(ANFITRION.toString()))
                .andExpect(jsonPath("$.turnoActual.numeroTurno").value(1))
                .andExpect(jsonPath("$.iniciadaEn").exists());
    }

    @Test
    @DisplayName("sin heroe conocido el campo viaja vacio, no inventado")
    void sinHeroeElCampoVaVacio() throws Exception {
        when(obtenerPartida.ejecutar(any())).thenReturn(partidaDeEjemplo());

        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes[0].heroe").isEmpty());
    }

    @Test
    @DisplayName("una partida que no existe responde 404 con su tipo")
    void partidaInexistente() throws Exception {
        when(obtenerPartida.ejecutar(any())).thenThrow(new PartidaNoEncontrada(ID_PARTIDA));

        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA).with(jugador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/partida-no-encontrada"));
    }

    @Test
    @DisplayName("sin token no se consulta ninguna partida")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA))
                .andExpect(status().isUnauthorized());
    }
}
