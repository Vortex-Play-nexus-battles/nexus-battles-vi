package com.nexusbattles.plataforma.notificaciones.bandeja;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas del alta de sesion que llega por STOMP.
 *
 * El controlador no decide nada, solo traslada al servicio el identificador
 * estable que envio el cliente. Lo que se verifica es justamente eso: que pase
 * el que viene en el mensaje y no invente otro, porque si tomara el de STOMP la
 * sesion que vuelve de una caida recibiria repetido lo que ya habia visto.
 */
@ExtendWith(MockitoExtension.class)
class CanalDeSesionesControllerTest {

    @Mock
    private ServicioDeNotificaciones servicio;

    @InjectMocks
    private CanalDeSesionesController controlador;

    @Test
    @DisplayName("el alta traslada al servicio el jugador y su sesion estable")
    void trasladaElAltaAlServicio() {
        controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion("jugador-1", "movil"));

        verify(servicio).registrarSesion("jugador-1", "movil");
    }

    @Test
    @DisplayName("dos sesiones del mismo jugador se dan de alta por separado")
    void cadaSesionSeDaDeAltaPorSeparado() {
        controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion("jugador-1", "movil"));
        controlador.registrarSesion(
                new CanalDeSesionesController.RegistrarSesion("jugador-1", "escritorio"));

        verify(servicio).registrarSesion("jugador-1", "movil");
        verify(servicio).registrarSesion("jugador-1", "escritorio");
    }
}
