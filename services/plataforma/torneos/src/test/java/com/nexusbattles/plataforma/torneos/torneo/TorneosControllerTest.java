package com.nexusbattles.plataforma.torneos.torneo;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.torneos.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La cadena y el controlador con tokens reales: quien actua sale del token; problem details con motivo. */
@WebMvcTest(controllers = TorneosController.class)
@Import({SecurityConfig.class, DecodificadorDePrueba.class, ManejadorErroresTorneos.class})
@DisplayName("Torneos · API con tokens de ms-identidad")
class TorneosControllerTest {

    private static final UUID ADMIN = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID JUGADORA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TorneosService servicio;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private static TorneosService.TorneoCompleto torneo() {
        Torneo t = new Torneo(UUID.randomUUID(), "Copa Otono", ADMIN, AHORA, AHORA.plusDays(7), 10);
        return new TorneosService.TorneoCompleto(t, List.of(), List.of());
    }

    @Test
    @DisplayName("el listado y el detalle son publicos (espectador, HU-TOR-008)")
    void publico() throws Exception {
        TorneosService.TorneoCompleto t = torneo();
        when(servicio.listar()).thenReturn(List.of(t));
        when(servicio.obtener(t.torneo().id())).thenReturn(t);
        mvc.perform(get("/api/v1/torneos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Copa Otono"))
                .andExpect(jsonPath("$[0].cupos").value(8))
                .andExpect(jsonPath("$[0].estado").value("INSCRIPCIONES_ABIERTAS"));
        mvc.perform(get("/api/v1/torneos/" + t.torneo().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costoInscripcion").value(10))
                .andExpect(jsonPath("$.equipos").isArray())
                .andExpect(jsonPath("$.encuentros").isArray());
    }

    @Test
    @DisplayName("crear: el actor es el uid y el rol del token; 201")
    void crear() throws Exception {
        when(servicio.crear(any(), any())).thenReturn(torneo());
        mvc.perform(post("/api/v1/torneos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("admin", ADMIN, "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Copa Otono\",\"inscripcionesCierranEn\":\"2026-10-08T10:00:00Z\",\"costoInscripcion\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Copa Otono"));
        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<TorneosService.SolicitudDeTorneo> solicitud = ArgumentCaptor.forClass(TorneosService.SolicitudDeTorneo.class);
        verify(servicio).crear(actor.capture(), solicitud.capture());
        assertThat(actor.getValue().id()).isEqualTo(ADMIN);
        assertThat(actor.getValue().rol()).isEqualTo("ADMINISTRADOR");
        assertThat(solicitud.getValue().costoInscripcion()).isEqualTo(10);
    }

    @Test
    @DisplayName("sin token 401; el 409 de la ventana de 91 dias trae la proxima fecha posible")
    void ventana() throws Exception {
        mvc.perform(post("/api/v1/torneos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
        when(servicio.crear(any(), any())).thenThrow(new TorneoRechazado(TorneoRechazado.Motivo.VENTANA_DE_91_DIAS,
                "ya hay uno", AHORA.plusDays(91)));
        mvc.perform(post("/api/v1/torneos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("admin", ADMIN, "ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Copa\",\"inscripcionesCierranEn\":\"2026-10-08T10:00:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/ventana-de-91-dias"))
                .andExpect(jsonPath("$.motivo").value("VENTANA_DE_91_DIAS"))
                .andExpect(jsonPath("$.proximaFechaPosible").exists());
    }

    @Test
    @DisplayName("el resultado lo registra un servicio (actor SERVICIO con su client id) o un administrador; un jugador 403 en la cadena")
    void resultado() throws Exception {
        UUID torneoId = UUID.randomUUID();
        UUID ganador = UUID.randomUUID();
        when(servicio.registrarResultado(any(), eq(torneoId), anyInt(), any())).thenReturn(torneo());
        mvc.perform(post("/api/v1/torneos/" + torneoId + "/encuentros/3/resultado")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("salas-partidas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ganadorEquipoId\":\"" + ganador + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        verify(servicio).registrarResultado(actor.capture(), eq(torneoId), eq(3), any());
        assertThat(actor.getValue().esServicio()).isTrue();
        assertThat(actor.getValue().nombre()).isEqualTo("salas-partidas");

        // 1.1.0: ganadorUid + partidaId, como lo manda salas-partidas
        UUID uid = UUID.randomUUID();
        UUID partida = UUID.randomUUID();
        mvc.perform(post("/api/v1/torneos/" + torneoId + "/encuentros/4/resultado")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("salas-partidas"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ganadorUid\":\"" + uid + "\",\"partidaId\":\"" + partida + "\"}"))
                .andExpect(status().isOk());
        ArgumentCaptor<TorneosService.SolicitudDeResultado> solicitud =
                ArgumentCaptor.forClass(TorneosService.SolicitudDeResultado.class);
        verify(servicio).registrarResultado(any(), eq(torneoId), eq(4), solicitud.capture());
        assertThat(solicitud.getValue().ganadorEquipoId()).isNull();
        assertThat(solicitud.getValue().ganadorUid()).isEqualTo(uid);
        assertThat(solicitud.getValue().partidaId()).isEqualTo(partida);

        mvc.perform(post("/api/v1/torneos/" + torneoId + "/encuentros/3/resultado")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADORA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ganadorEquipoId\":\"" + ganador + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un jugador registra su equipo y lo inscribe; los rechazos de negocio salen como problem details")
    void equipos() throws Exception {
        UUID torneoId = UUID.randomUUID();
        Equipo equipo = Equipo.deJugadores(UUID.randomUUID(), torneoId, "Los Valientes", "avatar-1", JUGADORA,
                UUID.randomUUID(), AHORA);
        when(servicio.crearEquipo(any(), eq(torneoId), any())).thenReturn(equipo);
        mvc.perform(post("/api/v1/torneos/" + torneoId + "/equipos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADORA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Los Valientes\",\"avatar\":\"avatar-1\",\"companeroUid\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.integrantes.length()").value(2))
                .andExpect(jsonPath("$.inscrito").value(false));

        when(servicio.inscribir(any(), eq(torneoId), eq(equipo.id())))
                .thenThrow(new TorneoRechazado(TorneoRechazado.Motivo.CREDITOS_INSUFICIENTES, "no alcanza"));
        mvc.perform(post("/api/v1/torneos/" + torneoId + "/equipos/" + equipo.id() + "/inscripcion")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeJugador("lyra", JUGADORA)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.motivo").value("CREDITOS_INSUFICIENTES"))
                .andExpect(jsonPath("$.title").value("Creditos insuficientes"));

        when(servicio.iniciar(any(), eq(torneoId)))
                .thenThrow(new TorneoRechazado(TorneoRechazado.Motivo.LIBRO_NO_DISPONIBLE, "caido"));
        mvc.perform(post("/api/v1/torneos/" + torneoId + "/inicio")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeUsuario("admin", ADMIN, "ADMINISTRADOR")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.motivo").value("LIBRO_NO_DISPONIBLE"));
    }
}
