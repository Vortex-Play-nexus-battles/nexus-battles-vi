package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.repository.TemaConocimientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MotorRespuestasTest {

    private TemaConocimientoRepository temaConocimientoRepository;
    private MotorRespuestas motorRespuestas;

    @BeforeEach
    void configurar() {
        temaConocimientoRepository = mock(TemaConocimientoRepository.class);
        motorRespuestas = new MotorRespuestas(temaConocimientoRepository);

        TemaConocimiento temaRegistro = new TemaConocimiento(
            Categoria.CUENTA_Y_REGISTRO,
            TipoRespuesta.PASO_A_PASO,
            "Cómo crear una cuenta",
            "registrarme, crear cuenta, registro, como me registro",
            "register, sign up, create account",
            "Para crear tu cuenta necesitas nombres, correo, contraseña, apodo y avatar.",
            "To create your account you need your name, email, password, nickname and avatar."
        );

        TemaConocimiento temaSubasta = new TemaConocimiento(
            Categoria.SUBASTA_Y_COMERCIO,
            TipoRespuesta.PASO_A_PASO,
            "Cómo publicar en subasta",
            "subasta, subastas, como subastar, vender un producto",
            "auction, auctions, how to auction, sell an item",
            "Elige el ítem, configura duración y precio, y confirma la publicación.",
            "Choose the item, set the duration and price, and confirm the listing."
        );

        when(temaConocimientoRepository.findByActivoTrue())
            .thenReturn(List.of(temaRegistro, temaSubasta));
    }

    @Test
    void generarRespuesta_conCoincidenciaExactaEnEspanol_devuelveContenidoEnEspanol() {
        ResultadoMotor resultado = motorRespuestas.generarRespuesta("¿Cómo me registro en el juego?");

        assertThat(resultado.requiereEscalamiento()).isFalse();
        assertThat(resultado.categoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
        assertThat(resultado.texto()).contains("Para crear tu cuenta");
    }

    @Test
    void generarRespuesta_conCoincidenciaExactaEnIngles_devuelveContenidoEnIngles() {
        ResultadoMotor resultado = motorRespuestas.generarRespuesta("how do i sign up for an account");

        assertThat(resultado.requiereEscalamiento()).isFalse();
        assertThat(resultado.categoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
        assertThat(resultado.texto()).contains("To create your account");
    }

    @Test
    void generarRespuesta_conErrorOrtografico_igualReconoceElTema() {
        ResultadoMotor resultado = motorRespuestas.generarRespuesta("como puedo crar una cuentaa");

        assertThat(resultado.requiereEscalamiento()).isFalse();
        assertThat(resultado.categoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
    }

    @Test
    void generarRespuesta_conMensajeSinRelacion_escalaYSugiereTemas() {
        ResultadoMotor resultado = motorRespuestas.generarRespuesta("xk fmk qzr blublu");

        assertThat(resultado.requiereEscalamiento()).isTrue();
        assertThat(resultado.categoria()).isNull();
        assertThat(resultado.texto()).contains("soporte humano");
    }

    @Test
    void generarRespuesta_sinTemasActivos_escalaSinSugerencias() {
        when(temaConocimientoRepository.findByActivoTrue()).thenReturn(List.of());

        ResultadoMotor resultado = motorRespuestas.generarRespuesta("cualquier cosa");

        assertThat(resultado.requiereEscalamiento()).isTrue();
        assertThat(resultado.temasSugeridos()).isEmpty();
    }
}
