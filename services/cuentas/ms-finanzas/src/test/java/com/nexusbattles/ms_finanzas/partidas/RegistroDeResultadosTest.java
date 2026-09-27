package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaRequest.ParticipantePartidaRequest;

/**
 * El resultado de una partida se repite si otra escritura se cruzó (B7): el
 * contador de cofres lleva {@code @Version} y todo lo demás es idempotente.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistroDeResultados · repetir cuando otra escritura se cruza")
class RegistroDeResultadosTest {

    private static final ResultadoPartidaRequest PARTIDA = new ResultadoPartidaRequest(
            "partida-1", TipoPartida.UNO_A_UNO, "uid-a", List.of(new ParticipantePartidaRequest("uid-a", false)));
    private static final ResultadoPartidaResponse HECHO = new ResultadoPartidaResponse("partida-1", List.of(), List.of());

    @Mock
    private AcreditacionPartidaService acreditacion;

    @InjectMocks
    private RegistroDeResultados registro;

    @Test
    @DisplayName("un conflicto de version se repite y el segundo intento entra")
    void repite() {
        when(acreditacion.procesarResultadoPartida(PARTIDA))
                .thenThrow(new ObjectOptimisticLockingFailureException(ContadorDeCofres.class, "uid-a"))
                .thenReturn(HECHO);

        assertThat(registro.registrar(PARTIDA)).isEqualTo(HECHO);
        verify(acreditacion, times(2)).procesarResultadoPartida(PARTIDA);
    }

    @Test
    @DisplayName("dos altas a la vez del mismo contador (clave duplicada) tambien se repiten")
    void claveDuplicada() {
        when(acreditacion.procesarResultadoPartida(PARTIDA))
                .thenThrow(new DataIntegrityViolationException("pk contador_de_cofres"))
                .thenReturn(HECHO);

        assertThat(registro.registrar(PARTIDA)).isEqualTo(HECHO);
    }

    @Test
    @DisplayName("tras tres conflictos seguidos se rinde y deja el error al llamador")
    void seRinde() {
        when(acreditacion.procesarResultadoPartida(PARTIDA))
                .thenThrow(new ObjectOptimisticLockingFailureException(ContadorDeCofres.class, "uid-a"));

        assertThatThrownBy(() -> registro.registrar(PARTIDA))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(acreditacion, times(RegistroDeResultados.INTENTOS)).procesarResultadoPartida(PARTIDA);
    }

    @Test
    @DisplayName("una partida ya procesada no se repite: es la respuesta idempotente (409)")
    void yaProcesada() {
        when(acreditacion.procesarResultadoPartida(PARTIDA)).thenThrow(new PartidaYaProcesadaException("partida-1"));

        assertThatThrownBy(() -> registro.registrar(PARTIDA)).isInstanceOf(PartidaYaProcesadaException.class);
        verify(acreditacion, times(1)).procesarResultadoPartida(PARTIDA);
    }
}
