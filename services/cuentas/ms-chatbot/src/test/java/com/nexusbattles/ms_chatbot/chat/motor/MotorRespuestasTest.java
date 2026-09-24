package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.EstadoVersion;
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

        when(temaConocimientoRepository.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION))
            .thenReturn(List.of(temaRegistro(0), temaSubasta()));
    }

    @Test
    void generarRespuesta_conCoincidenciaExactaEnEspanol_devuelveContenidoEnEspanol() {
        ResultadoMotor resultado = motorRespuestas.generarRespuesta("¿Cómo me registro en el juego?");

        assertThat(resultado.requiereEscalamiento()).isFalse();
        assertThat(resultado.categoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
        assertThat(resultado.texto()).contains("Para crear tu cuenta");
        assertThat(resultado.temaClave()).isEqualTo("clave-registro");
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
        when(temaConocimientoRepository.findByVersionEstadoAndActivoTrue(EstadoVersion.PRODUCCION))
            .thenReturn(List.of());

        ResultadoMotor resultado = motorRespuestas.generarRespuesta("cualquier cosa");

        assertThat(resultado.requiereEscalamiento()).isTrue();
        assertThat(resultado.temasSugeridos()).isEmpty();
    }

    // HU-CHA-012: responderCon evalua una lista de temas cualquiera (la version
    // candidata) sin tocar el repositorio, es decir, sin activarla.
    @Test
    void responderCon_usaSoloLosTemasRecibidos() {
        ResultadoMotor resultado = motorRespuestas.responderCon("como publicar en subasta", List.of(temaSubasta()));

        assertThat(resultado.requiereEscalamiento()).isFalse();
        assertThat(resultado.temaClave()).isEqualTo("clave-subasta");
    }

    // HU-CHA-012: un tema inactivo no responde aunque este en la lista.
    @Test
    void responderCon_ignoraLosTemasInactivos() {
        TemaConocimiento subastaInactiva = new TemaConocimiento(null, "clave-subasta",
            Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.PASO_A_PASO, "Cómo publicar en subasta",
            "subasta, subastas, como subastar", null, "Elige el ítem y confirma.", null, 0, false);

        ResultadoMotor resultado = motorRespuestas.responderCon("como subastar", List.of(subastaInactiva));

        assertThat(resultado.requiereEscalamiento()).isTrue();
    }

    // HU-CHA-012: a igual puntaje gana el tema de mayor prioridad, sin
    // importar en que orden lleguen.
    @Test
    void responderCon_aIgualPuntajeGanaLaMayorPrioridad() {
        TemaConocimiento registroPrioritario = new TemaConocimiento(null, "clave-registro-nuevo",
            Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.PASO_A_PASO, "Registro (version nueva)",
            "registrarme, crear cuenta, registro, como me registro", null,
            "Version nueva de la respuesta de registro.", null, 5, true);

        ResultadoMotor resultado = motorRespuestas.responderCon("como me registro",
            List.of(temaRegistro(0), registroPrioritario));

        assertThat(resultado.temaClave()).isEqualTo("clave-registro-nuevo");
    }

    private static TemaConocimiento temaRegistro(int prioridad) {
        return new TemaConocimiento(null, "clave-registro",
            Categoria.CUENTA_Y_REGISTRO,
            TipoRespuesta.PASO_A_PASO,
            "Cómo crear una cuenta",
            "registrarme, crear cuenta, registro, como me registro",
            "register, sign up, create account",
            "Para crear tu cuenta necesitas nombres, correo, contraseña, apodo y avatar.",
            "To create your account you need your name, email, password, nickname and avatar.",
            prioridad, true);
    }

    private static TemaConocimiento temaSubasta() {
        return new TemaConocimiento(null, "clave-subasta",
            Categoria.SUBASTA_Y_COMERCIO,
            TipoRespuesta.PASO_A_PASO,
            "Cómo publicar en subasta",
            "subasta, subastas, como subastar, vender un producto",
            "auction, auctions, how to auction, sell an item",
            "Elige el ítem, configura duración y precio, y confirma la publicación.",
            "Choose the item, set the duration and price, and confirm the listing.",
            0, true);
    }
}
