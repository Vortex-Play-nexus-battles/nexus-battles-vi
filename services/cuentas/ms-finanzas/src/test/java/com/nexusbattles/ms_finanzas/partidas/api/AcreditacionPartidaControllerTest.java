package com.nexusbattles.ms_finanzas.partidas.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nexusbattles.ms_finanzas.partidas.AcreditacionPartidaService;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaRequest;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaRequest.ParticipantePartidaRequest;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaResponse;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaResponse.AcreditacionAplicada;
import com.nexusbattles.ms_finanzas.partidas.TipoPartida;

@ExtendWith(MockitoExtension.class)
class AcreditacionPartidaControllerTest {

    @Mock
    private AcreditacionPartidaService servicio;

    @InjectMocks
    private AcreditacionPartidaController controller;

    @Test
    void resultado_delegaAlServicioYDevuelveOk() {
        ResultadoPartidaRequest req = new ResultadoPartidaRequest(
                "partida-1", TipoPartida.UNO_A_UNO, "uid-a",
                List.of(new ParticipantePartidaRequest("uid-a", false)));
        ResultadoPartidaResponse esperada = new ResultadoPartidaResponse(
                "partida-1",
                List.of(new AcreditacionAplicada("uid-a", 2, true, null)),
                List.of());
        when(servicio.procesarResultadoPartida(req)).thenReturn(esperada);

        ResponseEntity<ResultadoPartidaResponse> resp = controller.resultado(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(esperada);
    }
}
