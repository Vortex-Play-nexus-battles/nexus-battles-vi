package com.nexusbattles.ms_chatbot.chat.conocimiento;

import com.nexusbattles.ms_chatbot.chat.motor.model.CasoEvaluacion;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.repository.CasoEvaluacionRepository;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CasoEvaluacionServiceTest {

    @Mock
    private CasoEvaluacionRepository casoRepository;
    @Mock
    private TemaConocimientoRepository temaRepository;

    private CasoEvaluacionService servicio;

    @BeforeEach
    void configurar() {
        servicio = new CasoEvaluacionService(casoRepository, temaRepository);
    }

    @Test
    void crear_conClaveExistente_guardaElCasoActivo() {
        when(temaRepository.findByClaveIn(List.of("clave-pujas"))).thenReturn(List.of(mock(TemaConocimiento.class)));
        when(casoRepository.save(any(CasoEvaluacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CasoEvaluacion caso = servicio.crear(new CasoEvaluacionRequest("  como pujo  ", " clave-pujas ", null));

        assertThat(caso.getPregunta()).isEqualTo("como pujo");
        assertThat(caso.getTemaClaveEsperada()).isEqualTo("clave-pujas");
        assertThat(caso.isActivo()).isTrue();
    }

    // Sin clave, el caso espera que el motor escale; no hay tema que buscar.
    @Test
    void crear_sinClave_esperaEscalamiento() {
        when(casoRepository.save(any(CasoEvaluacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CasoEvaluacion caso = servicio.crear(new CasoEvaluacionRequest("como hackeo el juego", "  ", null));

        assertThat(caso.esperaEscalamiento()).isTrue();
        verifyNoInteractions(temaRepository);
    }

    @Test
    void crear_inactivo_loGuardaInactivo() {
        when(casoRepository.save(any(CasoEvaluacion.class))).thenAnswer(inv -> inv.getArgument(0));

        CasoEvaluacion caso = servicio.crear(new CasoEvaluacionRequest("como hackeo el juego", null, false));

        assertThat(caso.isActivo()).isFalse();
    }

    @Test
    void crear_conClaveInexistente_lanza400YNoGuarda() {
        when(temaRepository.findByClaveIn(List.of("no-existe"))).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.crear(new CasoEvaluacionRequest("como pujo", "no-existe", null)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(casoRepository, never()).save(any());
    }

    @Test
    void editar_actualizaPreguntaClaveYEstado() {
        UUID casoId = UUID.randomUUID();
        CasoEvaluacion existente = CasoEvaluacion.nuevo("como pujo", null);
        when(casoRepository.findById(casoId)).thenReturn(Optional.of(existente));
        when(temaRepository.findByClaveIn(List.of("clave-pujas"))).thenReturn(List.of(mock(TemaConocimiento.class)));

        CasoEvaluacion editado = servicio.editar(casoId, new CasoEvaluacionRequest("como hago una puja", "clave-pujas", false));

        assertThat(editado.getPregunta()).isEqualTo("como hago una puja");
        assertThat(editado.getTemaClaveEsperada()).isEqualTo("clave-pujas");
        assertThat(editado.isActivo()).isFalse();
    }

    @Test
    void editar_casoInexistente_lanza404() {
        UUID casoId = UUID.randomUUID();
        when(casoRepository.findById(casoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.editar(casoId, new CasoEvaluacionRequest("como pujo", null, null)))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void eliminar_borraElCaso() {
        UUID casoId = UUID.randomUUID();
        CasoEvaluacion existente = CasoEvaluacion.nuevo("como pujo", null);
        when(casoRepository.findById(casoId)).thenReturn(Optional.of(existente));

        servicio.eliminar(casoId);

        verify(casoRepository).delete(existente);
    }

    @Test
    void eliminar_casoInexistente_lanza404() {
        UUID casoId = UUID.randomUUID();
        when(casoRepository.findById(casoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.eliminar(casoId))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(casoRepository, never()).delete(any());
    }
}
