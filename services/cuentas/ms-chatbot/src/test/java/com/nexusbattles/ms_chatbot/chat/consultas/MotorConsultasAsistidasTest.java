package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.AvisoDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ElementoInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MisionActivaDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoResumenDto;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotorConsultasAsistidasTest {

    private static final String TOKEN = "token-de-prueba";
    private static final String UID = "6f1c2a7e-3d6b-4b9a-8f0e-1c2d3e4f5a6b";

    @Mock
    private InventarioClient inventarioClient;
    @Mock
    private SubastasClient subastasClient;
    @Mock
    private NotificacionesClient notificacionesClient;
    @Mock
    private TorneosClient torneosClient;
    @Mock
    private FinanzasClient finanzasClient;
    @Mock
    private MisionesClient misionesClient;

    private MotorConsultasAsistidas motor;

    @BeforeEach
    void configurar() {
        motor = new MotorConsultasAsistidas(inventarioClient, subastasClient, notificacionesClient,
            torneosClient, finanzasClient, misionesClient);
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

    // 7.4.4 (1.3.0): las misiones en curso, con el token del propio jugador.
    @Test
    void consultaDeMisionesDevuelveLasQueEstanEnCurso() {
        MisionActivaDto templo = new MisionActivaDto("El Templo Olvidado", "EXPLORACION",
            new MisionActivaDto.Heroe("Guerrero Tanque", 3), Instant.parse("2026-09-28T20:30:00Z"), 0.4);
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of(templo));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("cual es mi progreso en misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto())
            .contains("1 mision(es) en curso")
            .contains("«El Templo Olvidado» con Guerrero Tanque (nivel 3)")
            .contains("40 % completada")
            .contains("termina el 28/09 a las 15:30");
    }

    @Test
    void consultaDeMisionesSinNingunaLlevaALaSeccion() {
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("my missions", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("No tienes misiones en curso").contains("seccion Misiones");
    }

    @Test
    void consultaDeMisionesConDatosIncompletosNoFalla() {
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of(
            new MisionActivaDto("Cacería", null, null, null, null)));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("estado de mis misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).isEqualTo("Tienes 1 mision(es) en curso: «Cacería».");
    }

    @Test
    void consultaDeMisionesConElServicioCaidoLoDice() {
        when(misionesClient.enCurso(TOKEN)).thenThrow(new ResourceAccessException("caido"));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("mis misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("No pude consultar tus misiones");
    }

    // B11 — 7.4.4 «informacion de torneos en curso»: datos publicos, el uid
    // del token solo sirve para encontrar el equipo del jugador.
    @Test
    void consultaDeTorneosEncuentraElEquipoDelJugadorYSuProximoEncuentro() {
        UUID torneo = UUID.randomUUID();
        UUID equipo = UUID.randomUUID();
        when(torneosClient.listar()).thenReturn(List.of(
            new TorneoResumenDto(torneo, "Copa Otono", "EN_CURSO", 10, 8, 8)));
        when(torneosClient.obtener(torneo)).thenReturn(new TorneoDetalleDto(torneo, "Copa Otono", "EN_CURSO", null,
            List.of(new TorneoDetalleDto.Equipo(equipo, "Los Valientes", false,
                List.of(UUID.fromString(UID), UUID.randomUUID()), true, 1, false)),
            List.of(new TorneoDetalleDto.Encuentro(1, "JUGADO", equipo, UUID.randomUUID()),
                new TorneoDetalleDto.Encuentro(5, "LISTO", equipo, UUID.randomUUID()))));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("en que torneo estoy", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("Copa Otono").contains("Los Valientes").contains("encuentro es el 5");
        verifyNoInteractions(finanzasClient);
    }

    @Test
    void consultaDeTorneosConInscripcionesAbiertasDiceSiElEquipoYaPago() {
        UUID torneo = UUID.randomUUID();
        when(torneosClient.listar()).thenReturn(List.of(
            new TorneoResumenDto(torneo, "Copa Invierno", "INSCRIPCIONES_ABIERTAS", 10, 1, 8)));
        when(torneosClient.obtener(torneo)).thenReturn(new TorneoDetalleDto(torneo, "Copa Invierno",
            "INSCRIPCIONES_ABIERTAS", null,
            List.of(new TorneoDetalleDto.Equipo(UUID.randomUUID(), "Los Nuevos", false,
                List.of(UUID.fromString(UID), UUID.randomUUID()), false, null, false)),
            List.of()));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("estado de mi torneo", TOKEN, UID);

        assertThat(resultado.get().texto()).contains("Los Nuevos").contains("todavia no se ha inscrito");
    }

    @Test
    void consultaDeTorneosSinEquipoInvitaAlTorneoAbierto() {
        UUID torneo = UUID.randomUUID();
        when(torneosClient.listar()).thenReturn(List.of(
            new TorneoResumenDto(torneo, "Copa Invierno", "INSCRIPCIONES_ABIERTAS", 25, 3, 8),
            new TorneoResumenDto(UUID.randomUUID(), "Copa Vieja", "FINALIZADO", 0, 8, 8)));
        when(torneosClient.obtener(torneo)).thenReturn(new TorneoDetalleDto(torneo, "Copa Invierno",
            "INSCRIPCIONES_ABIERTAS", null, List.of(), List.of()));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("hay torneos", TOKEN, UID);

        assertThat(resultado.get().texto()).contains("No estas en ningun torneo").contains("3 de 8 equipos")
            .contains("25 creditos").contains("seccion Torneo");
    }

    @Test
    void consultaDeTorneosSinTorneosActivosYConElServicioCaido() {
        when(torneosClient.listar()).thenReturn(List.of());
        assertThat(motor.generarRespuesta("mis torneos", TOKEN, UID).get().texto())
            .contains("no hay ningun torneo");

        when(torneosClient.listar()).thenThrow(new ResourceAccessException("caido"));
        assertThat(motor.generarRespuesta("mis torneos", TOKEN, UID).get().texto())
            .contains("no esta disponible");
    }

    @Test
    void consultaDeTorneosDeUnTorneoEnCursoConElEquipoEliminado() {
        UUID torneo = UUID.randomUUID();
        when(torneosClient.listar()).thenReturn(List.of(new TorneoResumenDto(torneo, "Copa", "EN_CURSO", 0, 8, 8)));
        when(torneosClient.obtener(torneo)).thenReturn(new TorneoDetalleDto(torneo, "Copa", "EN_CURSO", null,
            List.of(new TorneoDetalleDto.Equipo(UUID.randomUUID(), "Los Caidos", false,
                List.of(UUID.fromString(UID)), true, 2, true)), List.of()));

        assertThat(motor.generarRespuesta("mi torneo", TOKEN, UID).get().texto()).contains("quedo eliminado");
    }

    // B11 — 7.4.4 «historial de transacciones reciente», con el token del jugador.
    @Test
    void consultaDeMovimientosReenviaElTokenDelJugador() {
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenReturn(new PaginaMovimientosDto(List.of(
            new PaginaMovimientosDto.Movimiento(new BigDecimal("2.00"), "recompensa-victoria", "SUMA"),
            new PaginaMovimientosDto.Movimiento(new BigDecimal("10"), "inscripcion-torneo", "RESTA"),
            new PaginaMovimientosDto.Movimiento(new BigDecimal("60"), "apuesta-sala", "APARTA"),
            new PaginaMovimientosDto.Movimiento(new BigDecimal("5"), null, "NEUTRO")), 4));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("muestrame mis movimientos", TOKEN, UID);

        assertThat(resultado.get().texto()).contains("+2 (recompensa-victoria)").contains("-10 (inscripcion-torneo)")
            .contains("60 apartados").contains("5 sin mover saldo");
    }

    @Test
    void consultaDeMovimientosSinMovimientosYConElServicioCaido() {
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenReturn(new PaginaMovimientosDto(List.of(), 0));
        assertThat(motor.generarRespuesta("mis transacciones", TOKEN, UID).get().texto())
            .contains("Todavia no tienes movimientos");

        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenThrow(new ResourceAccessException("caido"));
        assertThat(motor.generarRespuesta("mis transacciones", TOKEN, UID).get().texto())
            .contains("no esta disponible");
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

    // B11: las secciones Misiones y Torneo ya existen en el menu principal.
    @Test
    void navegacionAMisionesLlevaALaSeccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("llevame a mis misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("seccion Misiones");
    }

    @Test
    void navegacionATorneosLlevaALaSeccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("ir a mis torneos", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("seccion Torneo");
        verifyNoInteractions(torneosClient);
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
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenReturn(new PaginaMovimientosDto(List.of(
            new PaginaMovimientosDto.Movimiento(new BigDecimal("4"), "recompensa-victoria", "SUMA")), 1));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("dame un informe de mi actividad", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto())
            .contains("Escudo de Hierro")
            .contains("1 subasta(s)")
            .contains("Nueva mision disponible")
            .contains("+4 (recompensa-victoria)")
            .contains("Misiones: No tienes misiones en curso");
    }

    @Test
    void informeDeActividadMuestraAvisoDeNoDisponibleSoloParaElServicioQueFalla() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenThrow(new ResourceAccessException("caido"));
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(new MiResumenDto("0.00", "100.00", 0));
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenReturn(new BandejaResponseDto(0, List.of()));
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenThrow(new ResourceAccessException("caido"));
        when(misionesClient.enCurso(TOKEN)).thenThrow(new ResourceAccessException("caido"));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("resumen de mi actividad", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto())
            .contains("no esta disponible")
            .contains("No tienes notificaciones sin leer")
            .contains("movimientos de creditos en este momento")
            .contains("No pude consultar tus misiones");
    }
}
