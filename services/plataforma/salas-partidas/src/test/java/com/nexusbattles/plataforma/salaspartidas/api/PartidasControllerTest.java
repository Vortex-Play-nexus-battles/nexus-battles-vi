package com.nexusbattles.plataforma.salaspartidas.api;

import com.nexusbattles.plataforma.salaspartidas.aplicacion.MisPartidas;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ObtenerPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaAjena;
import com.nexusbattles.plataforma.salaspartidas.dominio.PartidaNoEncontrada;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import com.nexusbattles.plataforma.salaspartidas.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API del combate — RF-JUE-017, salas-partidas.yaml 1.7.0.
 *
 * <p>Se comprueba la forma del contrato, los caminos que la interfaz distingue
 * (existe, no existe, no es tuya) y que quien pregunta sale SIEMPRE del token:
 * ni el estado de una partida ni el historial se piden por la identidad de otro.
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

    @MockitoBean
    private MisPartidas misPartidas;

    @MockitoBean
    private com.nexusbattles.plataforma.salaspartidas.aplicacion.EjecutarAccion ejecutarAccion;

    private static RequestPostProcessor jugador() {
        return conRol("ROLE_JUGADOR");
    }

    private static RequestPostProcessor conRol(String rol) {
        return jwt().jwt(token -> token
                        .subject("demo_grupo6")
                        .claim("uid", ANFITRION.toString()))
                .authorities(new SimpleGrantedAuthority(rol));
    }

    private static Partida partidaDeEjemplo() {
        Sala sala = Sala.crear(
                new ParametrosDeSala(2, Modalidad.CONTRA_IA, 320, true, false, null), ANFITRION);
        return Partida.iniciar(sala, Instant.parse("2026-09-17T20:00:00Z"));
    }

    @Test
    @DisplayName("la partida sale con la forma exacta del contrato")
    void devuelveLaPartida() throws Exception {
        when(obtenerPartida.ejecutar(any(), any(), anyBoolean())).thenReturn(partidaDeEjemplo());

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
                // D-B7-14: sin tiempo por turno, sin cuenta atras.
                .andExpect(jsonPath("$.turnoActual.segundosRestantes").isEmpty())
                .andExpect(jsonPath("$.resultado").isEmpty())
                .andExpect(jsonPath("$.ganadores").isEmpty())
                .andExpect(jsonPath("$.iniciadaEn").exists());
    }

    @Test
    @DisplayName("quien pregunta sale del token (uid), y un jugador no es de operacion")
    void quienPreguntaSaleDelToken() throws Exception {
        when(obtenerPartida.ejecutar(any(), any(), anyBoolean())).thenReturn(partidaDeEjemplo());

        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA).with(jugador()))
                .andExpect(status().isOk());

        verify(obtenerPartida).ejecutar(ID_PARTIDA, ANFITRION, false);
    }

    @Test
    @DisplayName("un moderador consulta como rol de operacion, para atender reportes")
    void unModeradorEsDeOperacion() throws Exception {
        when(obtenerPartida.ejecutar(any(), any(), anyBoolean())).thenReturn(partidaDeEjemplo());

        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA).with(conRol("ROLE_MODERADOR")))
                .andExpect(status().isOk());

        verify(obtenerPartida).ejecutar(ID_PARTIDA, ANFITRION, true);
    }

    @Test
    @DisplayName("1.7.0: quien no juega la partida recibe 403 partida-ajena")
    void unaPartidaAjenaEs403() throws Exception {
        when(obtenerPartida.ejecutar(any(), any(), anyBoolean())).thenThrow(new PartidaAjena());

        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA).with(jugador()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/partida-ajena"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("sin heroe conocido el campo viaja vacio, no inventado")
    void sinHeroeElCampoVaVacio() throws Exception {
        when(obtenerPartida.ejecutar(any(), any(), anyBoolean())).thenReturn(partidaDeEjemplo());

        mockMvc.perform(get("/api/v1/partidas/{id}", ID_PARTIDA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes[0].heroe").isEmpty());
    }

    @Test
    @DisplayName("una partida que no existe responde 404 con su tipo")
    void partidaInexistente() throws Exception {
        when(obtenerPartida.ejecutar(any(), any(), anyBoolean())).thenThrow(new PartidaNoEncontrada(ID_PARTIDA));

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

    @Test
    @DisplayName("1.7.0: /partidas/mias da el historial de quien firma el token, con la forma del contrato")
    void historialDelJugador() throws Exception {
        UUID idPartida = UUID.randomUUID();
        UUID idSala = UUID.randomUUID();
        when(misPartidas.ejecutar(eq(ANFITRION), anyInt(), anyInt())).thenReturn(new MisPartidas.Pagina(
                List.of(new MisPartidas.Resumen(idPartida, idSala, Modalidad.UNO_CONTRA_UNO,
                        EstadoPartida.FINALIZADA, "VICTORIA", "Sombra de Vael", 2,
                        Instant.parse("2026-09-25T10:00:00Z"), Instant.parse("2026-09-25T10:04:00Z"))),
                0, 16, 1, 1));

        mockMvc.perform(get("/api/v1/partidas/mias").with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].id").value(idPartida.toString()))
                .andExpect(jsonPath("$.contenido[0].idSala").value(idSala.toString()))
                .andExpect(jsonPath("$.contenido[0].modalidad").value("UNO_CONTRA_UNO"))
                .andExpect(jsonPath("$.contenido[0].estado").value("FINALIZADA"))
                .andExpect(jsonPath("$.contenido[0].resultado").value("VICTORIA"))
                .andExpect(jsonPath("$.contenido[0].heroe").value("Sombra de Vael"))
                .andExpect(jsonPath("$.contenido[0].participantes").value(2))
                .andExpect(jsonPath("$.contenido[0].iniciadaEn").exists())
                .andExpect(jsonPath("$.contenido[0].finalizadaEn").exists())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(16))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));

        // Tamano de diseno 16 (RNF-USA-001) cuando no se pide otro.
        verify(misPartidas).ejecutar(ANFITRION, 0, 16);
    }

    @Test
    @DisplayName("el historial respeta la pagina y el tamano pedidos, y exige token")
    void historialPaginado() throws Exception {
        when(misPartidas.ejecutar(any(), anyInt(), anyInt()))
                .thenReturn(new MisPartidas.Pagina(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get("/api/v1/partidas/mias").param("pagina", "2").param("tamano", "5").with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(2));
        verify(misPartidas).ejecutar(ANFITRION, 2, 5);

        mockMvc.perform(get("/api/v1/partidas/mias"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 1.10.0 · rendirse (revision del modo jugador, punto 19) ----

    @Test
    @DisplayName("1.10.0 · rendirse devuelve la partida como queda; quien se rinde sale del token")
    void rendirseDevuelveLaPartida() throws Exception {
        when(ejecutarAccion.rendirse(any(), any())).thenReturn(partidaDeEjemplo());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/partidas/{id}/rendicion", ID_PARTIDA).with(jugador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantes.length()").value(2));

        verify(ejecutarAccion).rendirse(ID_PARTIDA, ANFITRION);
    }

    @Test
    @DisplayName("1.10.0 · quien no combate en la partida no puede rendirse en ella: 403")
    void rendirseEnPartidaAjena() throws Exception {
        when(ejecutarAccion.rendirse(any(), any())).thenThrow(new PartidaAjena());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/partidas/{id}/rendicion", ID_PARTIDA).with(jugador()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("1.10.0 · un moderador no se rinde por nadie: 403 sin llegar al caso de uso")
    void unModeradorNoSeRinde() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/partidas/{id}/rendicion", ID_PARTIDA).with(conRol("ROLE_MODERADOR")))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(ejecutarAccion);
    }

    @Test
    @DisplayName("1.10.0 · rendirse sin sesion es 401")
    void rendirseSinSesion() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/partidas/{id}/rendicion", ID_PARTIDA))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(ejecutarAccion);
    }
}
