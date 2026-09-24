package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.AvisoDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ElementoInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotorConsultasAsistidasTest {

    private static final String TOKEN = "token-de-prueba";
    private static final String UID = "uid-123";

    @Mock
    private InventarioClient inventarioClient;
    @Mock
    private SubastasClient subastasClient;
    @Mock
    private NotificacionesClient notificacionesClient;

    private MotorConsultasAsistidas motor;

    @BeforeEach
    void configurar() {
        motor = new MotorConsultasAsistidas(inventarioClient, subastasClient, notificacionesClient);
    }

    @Test
    void detectaConsultaDeInventarioYDevuelveDatosReales() {
        PaginaInventarioDto pagina = new PaginaInventarioDto(
            List.of(new ElementoInventarioDto("Espada del Alba", "ARMA", true)), 1);
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(pagina);

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("como esta mi inventario", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("1 elemento(s)").contains("Espada del Alba");
    }

    @Test
    void detectaConsultaDeSubastasYDevuelveDatosReales() {
        MiResumenDto resumen = new MiResumenDto("150.00", "300.00", 2);
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(resumen);

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("como van mis subastas", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("2 subasta(s)").contains("300.00");
    }

    @Test
    void detectaConsultaDeNotificacionesYDevuelveDatosReales() {
        BandejaResponseDto bandeja = new BandejaResponseDto(2,
            List.of(new AvisoDto("Tu subasta fue superada", false)));
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenReturn(bandeja);

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("tengo notificaciones nuevas", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("2 notificacion(es)").contains("Tu subasta fue superada");
    }

    @Test
    void consultaDeMisionesRespondeQueEstaEnConstruccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("cual es mi progreso en misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("en construccion");
    }

    @Test
    void consultaDeTorneosRespondeQueEstaEnConstruccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("en que torneo estoy", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("en construccion");
    }

    @Test
    void mensajeSinIntencionPersonalDevuelveOptionalVacio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("como me registro en el juego", TOKEN, UID);

        assertThat(resultado).isEmpty();
    }

    @Test
    void siInventarioNoRespondeDevuelveMensajeDeServicioNoDisponible() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenThrow(new ResourceAccessException("caido"));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("muestrame mi inventario", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("no esta disponible");
    }

    @Test
    void detectaNavegacionAInventarioSinConsultarElServicio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("llevame a mi inventario", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("seccion de Inventario");
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void detectaNavegacionASubastasSinConsultarElServicio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("ir a mis subastas", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("seccion de Subastas");
        verifyNoInteractions(subastasClient);
    }

    @Test
    void detectaNavegacionANotificacionesSinConsultarElServicio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("abrir mis notificaciones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("seccion de Notificaciones");
        verifyNoInteractions(notificacionesClient);
    }

    @Test
    void detectaNavegacionAPerfil() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("llevame a mi perfil", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("Mi Perfil");
        verifyNoInteractions(inventarioClient, subastasClient, notificacionesClient);
    }

    @Test
    void navegacionAMisionesRespondeQueEstaEnConstruccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("llevame a mis misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("en construccion");
    }

    @Test
    void navegacionATorneosRespondeQueEstaEnConstruccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("ir a mis torneos", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("en construccion");
    }

    @Test
    void informeDeActividadCombinaLasTresConsultasEnUnSoloMensaje() {
        PaginaInventarioDto pagina = new PaginaInventarioDto(
            List.of(new ElementoInventarioDto("Escudo de Hierro", "ARMADURA", true)), 1);
        MiResumenDto resumen = new MiResumenDto("50.00", "500.00", 1);
        BandejaResponseDto bandeja = new BandejaResponseDto(1,
            List.of(new AvisoDto("Nueva mision disponible", false)));

        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(pagina);
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(resumen);
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenReturn(bandeja);

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("dame un informe de mi actividad", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto())
            .contains("Escudo de Hierro")
            .contains("1 subasta(s)")
            .contains("Nueva mision disponible");
    }

    @Test
    void informeDeActividadMuestraAvisoDeNoDisponibleSoloParaElServicioQueFalla() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenThrow(new ResourceAccessException("caido"));
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(new MiResumenDto("0.00", "100.00", 0));
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenReturn(new BandejaResponseDto(0, List.of()));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("resumen de mi actividad", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto())
            .contains("no esta disponible")
            .contains("No tienes notificaciones sin leer");
    }
}
