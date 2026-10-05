package nexus.combate.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import nexus.combate.FabricaProcesadorPerdidaEquipo;
import nexus.combate.IntegracionBotinException;
import nexus.combate.PerdidaEquipoAsignada;
import nexus.combate.ProcesadorPerdidaEquipo;
import nexus.combate.ResultadoPerdidaEquipo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BotinPartidaControllerTest {

    private final FabricaProcesadorPerdidaEquipo fabrica = mock(FabricaProcesadorPerdidaEquipo.class);
    private final ProcesadorPerdidaEquipo procesador = mock(ProcesadorPerdidaEquipo.class);
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(new BotinPartidaController(fabrica)).build();
    }

    @Test
    @DisplayName("el cierre de la partida selecciona y transfiere el equipo perdido")
    void cierraBotinDePartida() throws Exception {
        when(fabrica.crear(eq("botin-partida:123e4567-e89b-12d3-a456-426614174000"), anyList()))
                .thenReturn(procesador);
        when(procesador.resultado()).thenReturn(new ResultadoPerdidaEquipo(List.of(
                new PerdidaEquipoAsignada(
                        "combatiente-2", "elemento-9", "producto-4", "jugador-1", BigDecimal.valueOf(75)))));

        mvc.perform(post("/api/v1/combate/botin/cierres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PETICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidaId").value("123e4567-e89b-12d3-a456-426614174000"))
                .andExpect(jsonPath("$.asignaciones.length()").value(1))
                .andExpect(jsonPath("$.asignaciones[0].elementoId").value("elemento-9"))
                .andExpect(jsonPath("$.asignaciones[0].propietarioGanadorId").value("jugador-1"))
                .andExpect(jsonPath("$.asignaciones[0].tasaDeCaida").value(75));

        ArgumentCaptor<List<nexus.combate.ParticipantePerdidaEquipo>> participantes = ArgumentCaptor.forClass(List.class);
        verify(fabrica).crear(eq("botin-partida:123e4567-e89b-12d3-a456-426614174000"), participantes.capture());
        verify(procesador).procesar(any());
        org.junit.jupiter.api.Assertions.assertEquals("equipo-b", participantes.getValue().get(1).equipoId());
    }

    @Test
    @DisplayName("una petición incompleta responde 400")
    void rechazaPeticionIncompleta() throws Exception {
        mvc.perform(post("/api/v1/combate/botin/cierres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/peticion-invalida"));
    }

    @Test
    @DisplayName("si inventario o productos no responden el cierre devuelve 503")
    void integracionNoDisponible() throws Exception {
        when(fabrica.crear(any(), anyList())).thenReturn(procesador);
        doThrow(new IntegracionBotinException("inventario no responde"))
                .when(procesador).procesar(any());

        mvc.perform(post("/api/v1/combate/botin/cierres")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PETICION))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type")
                        .value("https://nexusbattles.local/errores/botin-no-disponible"));
    }

    private static final String PETICION = """
            {
              "partidaId": "123e4567-e89b-12d3-a456-426614174000",
              "equipoGanadorId": "equipo-a",
              "participantes": [
                {
                  "combatienteId": "combatiente-1",
                  "equipoId": "equipo-a",
                  "propietarioId": "jugador-1",
                  "heroeInventarioId": "heroe-1"
                },
                {
                  "combatienteId": "combatiente-2",
                  "equipoId": "equipo-b",
                  "propietarioId": "jugador-2",
                  "heroeInventarioId": "heroe-2"
                }
              ]
            }
            """;
}
