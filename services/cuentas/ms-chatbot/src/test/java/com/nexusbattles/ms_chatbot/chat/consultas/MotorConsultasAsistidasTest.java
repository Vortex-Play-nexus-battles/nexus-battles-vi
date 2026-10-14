package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.auditoria.AuditoriaDeAccesos;
import com.nexusbattles.ms_chatbot.chat.auditoria.DatoConsultado;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.AvisoDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ElementoInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiPublicacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MisionActivaDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ParticipacionDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoResumenDto;
import com.nexusbattles.ms_chatbot.chat.enriquecido.EnlaceInterno;
import com.nexusbattles.ms_chatbot.chat.enriquecido.TarjetaInformativa;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
        assertThat(resultado.get().texto()).contains("2 subasta(s)").contains("con 150 créditos retenidos")
            .contains("Tu saldo disponible es de 300 créditos.");
    }

    // 1.16.0 (7.4.4 «estado de productos en la subasta»): cada subasta abierta
    // en la que puja y cada una que publico, las que cierran antes primero.
    @Test
    void susSubastasTraenCadaUnaAbiertaEnTarjetasLasQueCierranAntesPrimero() {
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(new MiResumenDto("120.00", "380.00", 1));
        when(subastasClient.misParticipaciones(TOKEN)).thenReturn(List.of(
            new ParticipacionDto("Orbe de manos ardientes", "ACTIVA", "SUPERADA", "60.00", "75.00", 4,
                Instant.parse("2026-10-16T02:00:00Z")),
            new ParticipacionDto("Hacha del Norte", "ACTIVA", "GANANDO", "120.00", "120.00", 2,
                Instant.parse("2026-10-15T17:30:00Z")),
            new ParticipacionDto("Arco viejo", "ADJUDICADA", "GANADA", "40.00", "40.00", 1,
                Instant.parse("2026-10-01T10:00:00Z")),
            new ParticipacionDto("Daga", "ACTIVA", "AUTOMATICA", null, "15.00", 0, null)));
        when(subastasClient.misPublicacionesActivas(TOKEN)).thenReturn(List.of(
            new MiPublicacionDto("Escudo de roble", "ACTIVA", "80.00", 3, Instant.parse("2026-10-15T20:00:00Z"))));

        ResultadoMotor resultado = motor.generarRespuesta("como van mis subastas", TOKEN, UID).orElseThrow();

        assertThat(resultado.texto())
            .startsWith("Vas ganando 1 subasta(s) en este momento, con 120 créditos retenidos en pujas activas.")
            .contains("Pujas en 3 subasta(s) abierta(s) y tienes 1 publicada(s); las que cierran antes, abajo.");
        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
        // La cerrada (adjudicada) no sale; la que no tiene fecha, al final.
        assertThat(resultado.enriquecido().tarjetas()).extracting(TarjetaInformativa::titulo)
            .containsExactly("Hacha del Norte", "Escudo de roble", "Orbe de manos ardientes", "Daga");
        assertThat(resultado.enriquecido().tarjetas()).extracting(TarjetaInformativa::texto).containsExactly(
            "Vas ganando con 120 créditos · 2 puja(s) · cierra el 15/10 a las 12:30.",
            "Tu subasta: oferta vigente 80 créditos · 3 puja(s) · cierra el 15/10 a las 15:00.",
            "Te superaron: la oferta vigente es 75 y tu mejor puja, 60 · 4 puja(s) · cierra el 15/10 a las 21:00.",
            "Tu puja automática aún no ha pujado; la oferta vigente es 15 · 0 puja(s) · sin fecha de cierre.");
        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::destino).containsExactly("subastas");
    }

    @Test
    void susSubastasEnInglesYSinNingunaAbierta() {
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(new MiResumenDto("0", null, 0));
        when(subastasClient.misParticipaciones(TOKEN)).thenReturn(List.of(new ParticipacionDto("Arco", "CANCELADA",
            "CERRADA", "10", "10", 1, Instant.parse("2026-10-01T10:00:00Z"))));
        when(subastasClient.misPublicacionesActivas(TOKEN)).thenReturn(List.of());

        ResultadoMotor resultado = motor.generarRespuesta("how are my auctions", TOKEN, UID).orElseThrow();

        assertThat(resultado.texto()).isEqualTo("You're winning 0 auction(s) right now, with 0 credits held in "
            + "active bids. You have no bids or listings in auctions that are still open.");
        assertThat(resultado.enriquecido().tarjetas()).isEmpty();
    }

    @Test
    void sinElDetalleDeSusSubastasDaElResumenYDondeVerlas() {
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(new MiResumenDto("0.00", "10.00", 0));
        when(subastasClient.misParticipaciones(TOKEN)).thenThrow(new ResourceAccessException("caido"));
        when(subastasClient.misPublicacionesActivas(TOKEN)).thenReturn(List.of());

        ResultadoMotor resultado = motor.generarRespuesta("como van mis subastas", TOKEN, UID).orElseThrow();

        assertThat(resultado.texto()).endsWith("No pude traer el detalle de cada una: lo ves en «Subasta», en «Mis "
            + "subastas».");
        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
        assertThat(resultado.enriquecido().tarjetas()).isEmpty();
    }

    // 1.16.0: «¿cuántas subastas hay?» pregunta por lo que hay (ConsultaDeSubastas), no por lo suyo.
    @Test
    void cuantasSubastasHayNoEsUnaConsultaDeLoSuyo() {
        assertThat(motor.generarRespuesta("cuantas subastas hay", TOKEN, UID)).isEmpty();
        assertThat(motor.generarRespuesta("muestrame las subastas activas", TOKEN, UID)).isEmpty();
        assertThat(motor.pideDatosDeSuCuenta("¿Cuántas subastas hay?")).isFalse();
        assertThat(motor.pideDatosDeSuCuenta("¿Cuántas subastas tengo?")).isTrue();
        verifyNoInteractions(subastasClient);
    }

    @Test
    void detectaConsultaDeNotificacionesYDevuelveDatosReales() {
        BandejaResponseDto bandeja = new BandejaResponseDto(2,
            List.of(new AvisoDto("Tu subasta fue superada", false)));
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenReturn(bandeja);

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("tengo notificaciones nuevas", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("2 notificación(es)").contains("Tu subasta fue superada");
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
            .contains("1 misión(es) en curso")
            .contains("«El Templo Olvidado» con Guerrero Tanque (nivel 3)")
            .contains("40 % completada")
            .contains("termina el 28/09 a las 15:30");
    }

    // 1.3.4: cada mision en curso sale ademas como tarjeta, con enlace a Misiones.
    @Test
    void consultaDeMisionesTraeUnaTarjetaPorMision() {
        MisionActivaDto templo = new MisionActivaDto("El Templo Olvidado", "EXPLORACION",
            new MisionActivaDto.Heroe("Guerrero Tanque", 3), null, 0.4);
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of(templo));

        ResultadoMotor resultado = motor.generarRespuesta("mis misiones", TOKEN, UID).orElseThrow();

        assertThat(resultado.enriquecido().tarjetas()).singleElement()
            .satisfies(t -> assertThat(t.titulo()).isEqualTo("El Templo Olvidado"));
        assertThat(resultado.enriquecido().enlaces()).extracting(e -> e.destino()).containsExactly("misiones");
    }

    // 1.3.4: la navegacion asistida lleva un enlace de verdad a la seccion.
    @Test
    void navegacionYConsultaTraenElEnlaceASuSeccion() {
        assertThat(motor.generarRespuesta("llevame a mi inventario", TOKEN, UID).orElseThrow()
            .enriquecido().enlaces()).extracting(e -> e.destino()).containsExactly("inventario");
        assertThat(motor.generarRespuesta("ir a mis torneos", TOKEN, UID).orElseThrow()
            .enriquecido().enlaces()).extracting(e -> e.destino()).containsExactly("torneos");
    }

    @Test
    void consultaDeMisionesSinNingunaLlevaALaSeccion() {
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("mis misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("No tienes misiones en curso").contains("«Misiones»");
    }

    @Test
    void consultaDeMisionesConDatosIncompletosNoFalla() {
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of(
            new MisionActivaDto("Cacería", null, null, null, null)));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("estado de mis misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).isEqualTo("Tienes 1 misión(es) en curso: «Cacería».");
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

        assertThat(resultado.get().texto()).contains("Los Nuevos").contains("todavía no se ha inscrito");
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

        assertThat(resultado.get().texto()).contains("No estás en ningún torneo").contains("3 de 8 equipos")
            .contains("25 créditos").contains("«Torneo»");
    }

    @Test
    void consultaDeTorneosSinTorneosActivosYConElServicioCaido() {
        when(torneosClient.listar()).thenReturn(List.of());
        assertThat(motor.generarRespuesta("mis torneos", TOKEN, UID).get().texto())
            .contains("no hay ningún torneo");

        when(torneosClient.listar()).thenThrow(new ResourceAccessException("caido"));
        assertThat(motor.generarRespuesta("mis torneos", TOKEN, UID).get().texto())
            .contains("No pude consultar los torneos");
    }

    @Test
    void consultaDeTorneosDeUnTorneoEnCursoConElEquipoEliminado() {
        UUID torneo = UUID.randomUUID();
        when(torneosClient.listar()).thenReturn(List.of(new TorneoResumenDto(torneo, "Copa", "EN_CURSO", 0, 8, 8)));
        when(torneosClient.obtener(torneo)).thenReturn(new TorneoDetalleDto(torneo, "Copa", "EN_CURSO", null,
            List.of(new TorneoDetalleDto.Equipo(UUID.randomUUID(), "Los Caidos", false,
                List.of(UUID.fromString(UID)), true, 2, true)), List.of()));

        assertThat(motor.generarRespuesta("mi torneo", TOKEN, UID).get().texto()).contains("quedó eliminado");
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
            .contains("Todavía no tienes movimientos");

        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenThrow(new ResourceAccessException("caido"));
        assertThat(motor.generarRespuesta("mis transacciones", TOKEN, UID).get().texto())
            .contains("No pude consultar tus movimientos de créditos");
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
        assertThat(resultado.get().texto()).contains("No pude consultar tu inventario");
    }

    // Revision de plataforma (7.4.4): se clasifica la intencion antes de
    // consultar. Sus datos -> inventario real; un consejo -> base de conocimiento.
    @Test
    void preguntarQueTieneEnSuInventarioConsultaElServicio() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(new PaginaInventarioDto(
            List.of(new ElementoInventarioDto("Espada del Alba", "ARMA", true)), 1));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("¿Qué tengo en mi inventario?", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("Espada del Alba");
    }

    @Test
    void pedirQueLeMuestreSusHeroesConsultaElServicio() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(new PaginaInventarioDto(
            List.of(new ElementoInventarioDto("Guerrero de Hierro", "HEROE", true)), 1));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("Muéstrame mis héroes", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("Guerrero de Hierro");
    }

    @Test
    void pedirConsejosParaOrganizarElInventarioNoLlamaAlServicio() {
        Optional<ResultadoMotor> resultado =
            motor.generarRespuesta("Dame consejos para organizar mi inventario", TOKEN, UID);

        assertThat(resultado).isEmpty();
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void pedirQueLeRecomiendeObjetosNoLlamaAlServicio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("¿Qué objetos me recomiendas usar?", TOKEN, UID);

        assertThat(resultado).isEmpty();
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void preguntasDeReglasConPalabrasDeInventarioNoLlamanAlServicio() {
        for (String pregunta : List.of("tienes consejos de inventario", "como equipo a mi heroe",
            "pierdo mis objetos", "¿Qué es el inventario?", "how do I equip my heroes")) {
            assertThat(motor.generarRespuesta(pregunta, TOKEN, UID)).as(pregunta).isEmpty();
        }
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void detectaNavegacionAInventarioSinConsultarElServicio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("llevame a mi inventario", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("«Mi inventario»");
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void detectaNavegacionASubastasSinConsultarElServicio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("ir a mis subastas", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("«Subasta»").contains("«Mis subastas»");
        verifyNoInteractions(subastasClient);
    }

    @Test
    void detectaNavegacionANotificacionesSinConsultarElServicio() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("abrir mis notificaciones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("campana");
        verifyNoInteractions(notificacionesClient);
    }

    @Test
    void detectaNavegacionAPerfil() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("llevame a mi perfil", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("«Mi Cuenta», pestaña «Perfil»");
        verifyNoInteractions(inventarioClient, subastasClient, notificacionesClient);
    }

    // B11: las secciones Misiones y Torneo ya existen en el menu principal.
    @Test
    void navegacionAMisionesLlevaALaSeccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("llevame a mis misiones", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("«Misiones»");
    }

    @Test
    void navegacionATorneosLlevaALaSeccion() {
        Optional<ResultadoMotor> resultado = motor.generarRespuesta("ir a mis torneos", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().texto()).contains("«Torneo»");
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
            .contains("Inventario: No pude consultar tu inventario")
            .contains("No tienes notificaciones sin leer")
            .contains("movimientos de créditos en este momento")
            .contains("No pude consultar tus misiones");
    }

    // ---- 1.5.0: idioma ------------------------------------------------------

    @Test
    void unaPreguntaEnInglesSeRespondeEnInglesConLaEtiquetaDeLaInterfaz() {
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        ResultadoMotor resultado = motor.generarRespuesta("my missions", TOKEN, UID).orElseThrow();

        assertThat(resultado.ingles()).isTrue();
        assertThat(resultado.texto()).startsWith("You have no missions in progress.")
            .contains("«Misiones» (Missions)");
        assertThat(resultado.enriquecido().enlaces()).containsExactly(new EnlaceInterno("Go to Misiones", "misiones"));
    }

    @Test
    void laNavegacionEnInglesTambienVaEnIngles() {
        ResultadoMotor resultado = motor.generarRespuesta("take me to my inventory", TOKEN, UID).orElseThrow();

        assertThat(resultado.texto()).isEqualTo("Your inventory is in «Mi inventario» (My inventory), in the main menu.");
        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::texto)
            .containsExactly("Go to Mi inventario");
        verifyNoInteractions(inventarioClient);
    }

    // La preferencia del jugador manda sobre el idioma de la pregunta.
    @Test
    void laPreferenciaDeIdiomaMandaSobreElDeLaPregunta() {
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        assertThat(motor.generarRespuesta("my missions", TOKEN, UID, IdiomaPreferido.ES).orElseThrow().texto())
            .startsWith("No tienes misiones en curso.");
        assertThat(motor.generarRespuesta("mis misiones", TOKEN, UID, IdiomaPreferido.EN).orElseThrow().texto())
            .startsWith("You have no missions in progress.");
        assertThat(motor.generarRespuesta("mis misiones", TOKEN, UID, null).orElseThrow().ingles()).isFalse();
    }

    @Test
    void elServicioCaidoSeDiceEnElIdiomaDeLaPregunta() {
        when(subastasClient.consultarMiResumen(TOKEN)).thenThrow(new ResourceAccessException("caido"));

        assertThat(motor.generarRespuesta("my auctions", TOKEN, UID).orElseThrow().texto())
            .isEqualTo("I couldn't check your auctions right now: the service didn't answer in time. "
                + "Please try again in a few minutes.");
    }

    // 7.4.2 «errores ortograficos»: un error de tecleo por palabra larga.
    @Test
    void toleraUnErrorDeTecleoEnLasFrases() {
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(new MiResumenDto("0.00", "10.00", 0));

        assertThat(motor.generarRespuesta("como van mis subatsas", TOKEN, UID)).isPresent();
        assertThat(motor.generarRespuesta("llevame a mi inventraio", TOKEN, UID).orElseThrow().enriquecido().enlaces())
            .extracting(EnlaceInterno::destino).containsExactly("inventario");
    }

    // ---- 1.5.0: busqueda en el inventario (7.4.4) ---------------------------

    @Test
    void buscaEnElInventarioConLaBusquedaReal() {
        when(inventarioClient.buscar(TOKEN, "espada")).thenReturn(new PaginaInventarioDto(
            List.of(new ElementoInventarioDto("Espada del Alba", "ARMA", true),
                new ElementoInventarioDto("Espada de una mano", "ARMA", false)), 2));

        ResultadoMotor resultado = motor.generarRespuesta("busca la espada en mi inventario", TOKEN, UID).orElseThrow();

        assertThat(resultado.texto()).isEqualTo("En tu inventario hay 2 resultado(s) para «espada»: Espada del Alba, "
            + "Espada de una mano. En «Mi inventario» puedes ver su ficha.");
        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::destino).containsExactly("inventario");
        verify(inventarioClient, never()).consultarInventario(anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void buscaEnElInventarioEnInglesYConUnErrorDeTecleo() {
        when(inventarioClient.buscar(TOKEN, "sword")).thenReturn(new PaginaInventarioDto(
            List.of(new ElementoInventarioDto("Espada del Alba", "ARMA", true)), 1));
        when(inventarioClient.buscar(TOKEN, "escudo")).thenReturn(new PaginaInventarioDto(List.of(), 0));

        assertThat(motor.generarRespuesta("search my inventory for a sword", TOKEN, UID).orElseThrow().texto())
            .startsWith("Your inventory has 1 result(s) for «sword»: Espada del Alba.");
        assertThat(motor.generarRespuesta("¿Tengo algún escudo en mi inventraio?", TOKEN, UID).orElseThrow().texto())
            .startsWith("No encontré «escudo» en tu inventario.");
    }

    @Test
    void unaBusquedaMuyCortaNoConsultaNada() {
        ResultadoMotor resultado = motor.generarRespuesta("busca as en mi inventario", TOKEN, UID).orElseThrow();

        assertThat(resultado.texto()).contains("al menos 4 letras");
        verifyNoInteractions(inventarioClient);
    }

    @Test
    void unaBusquedaConElInventarioCaidoLoDice() {
        when(inventarioClient.buscar(TOKEN, "pocion")).thenThrow(new ResourceAccessException("caido"));
        when(inventarioClient.buscar(TOKEN, "armadura")).thenReturn(null);

        assertThat(motor.generarRespuesta("encuentra pocion en mi inventario", TOKEN, UID).orElseThrow().texto())
            .startsWith("No pude consultar tu inventario");
        assertThat(motor.generarRespuesta("buscar en mi inventario armadura", TOKEN, UID).orElseThrow().texto())
            .startsWith("No encontré «armadura»");
    }

    @Test
    void elCriterioSaleDeLasFormasDeBuscar() {
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("busca la espada en mi inventario")).contains("espada");
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("buscame mago de fuego en mi inventario"))
            .contains("mago de fuego");
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("buscar en mi inventario la armadura"))
            .contains("armadura");
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("tengo alguna pocion en mi inventario")).contains("pocion");
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("look for the shield in my inventory")).contains("shield");
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("find a staff in my inventory")).contains("staff");
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("do i have any potion in my inventory"))
            .contains("potion");
        // Preguntar por todo el inventario no es buscar.
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("que tengo en mi inventario")).isEmpty();
        assertThat(MotorConsultasAsistidas.criterioDeBusqueda("como busco cosas en la tienda")).isEmpty();
    }

    @Test
    void corrigeSoloLasPalabrasAUnErrorDeLaReferencia() {
        List<String> referencias = List.of("inventario", "inventory");

        assertThat(MotorConsultasAsistidas.corregirPalabras("busca espada en mi inventraio", referencias))
            .isEqualTo("busca espada en mi inventario");
        assertThat(MotorConsultasAsistidas.corregirPalabras("search my inventroy for x", referencias))
            .isEqualTo("search my inventory for x");
        assertThat(MotorConsultasAsistidas.corregirPalabras("invento algo", referencias)).isEqualTo("invento algo");
        assertThat(MotorConsultasAsistidas.corregirPalabras("", referencias)).isEmpty();
    }

    // ---- 1.5.0: informe de actividad en paralelo (7.4.11, < 2 s) ------------

    private static <T> org.mockito.stubbing.Answer<T> tardaYDevuelve(long milisegundos, T valor) {
        return invocacion -> {
            Thread.sleep(milisegundos);
            return valor;
        };
    }

    @Test
    void elInformePideSusCincoPartesALaVez() {
        when(inventarioClient.consultarInventario(TOKEN, 0))
            .thenAnswer(tardaYDevuelve(400, new PaginaInventarioDto(List.of(), 0)));
        when(subastasClient.consultarMiResumen(TOKEN)).thenAnswer(tardaYDevuelve(400, new MiResumenDto("0", "1", 0)));
        when(notificacionesClient.consultarBandeja(TOKEN, UID))
            .thenAnswer(tardaYDevuelve(400, new BandejaResponseDto(0, List.of())));
        when(finanzasClient.movimientos(TOKEN, UID, 5))
            .thenAnswer(tardaYDevuelve(400, new PaginaMovimientosDto(List.of(), 0)));
        when(misionesClient.enCurso(TOKEN)).thenAnswer(tardaYDevuelve(400, List.of()));

        long inicio = System.nanoTime();
        String texto = motor.generarRespuesta("informe de mi actividad", TOKEN, UID).orElseThrow().texto();
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        // Una tras otra serian 2 s; a la vez, poco mas de 0,4 s.
        assertThat(milisegundos).isLessThan(1500);
        assertThat(texto).contains("Tu inventario está vacío").contains("Vas ganando 0 subasta(s)")
            .contains("No tienes notificaciones sin leer").contains("Todavía no tienes movimientos")
            .contains("No tienes misiones en curso");
    }

    @Test
    void elInformeNoEsperaAUnServicioColgadoMasDelPlazo() {
        when(inventarioClient.consultarInventario(TOKEN, 0))
            .thenAnswer(tardaYDevuelve(10_000, new PaginaInventarioDto(List.of(), 0)));
        when(subastasClient.consultarMiResumen(TOKEN)).thenReturn(new MiResumenDto("0", "1", 0));
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenReturn(new BandejaResponseDto(0, List.of()));
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenReturn(new PaginaMovimientosDto(List.of(), 0));
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        long inicio = System.nanoTime();
        ResultadoMotor resultado = motor.generarRespuesta("show my activity report", TOKEN, UID).orElseThrow();
        long milisegundos = (System.nanoTime() - inicio) / 1_000_000;

        assertThat(milisegundos).isGreaterThanOrEqualTo(MotorConsultasAsistidas.PLAZO_DEL_INFORME.toMillis() - 50)
            .isLessThan(2000);
        assertThat(resultado.ingles()).isTrue();
        assertThat(resultado.texto()).startsWith("Here's a summary of your recent activity:")
            .contains("Inventory: I couldn't check your inventory right now")
            .contains("Auctions: You're winning 0 auction(s)")
            .contains("Missions: You have no missions in progress.");
    }

    // 1.5.0: las demas consultas en vivo tambien responden en ingles.
    @Test
    void lasConsultasEnVivoRespondenEnIngles() {
        UUID torneo = UUID.randomUUID();
        UUID equipo = UUID.randomUUID();
        when(torneosClient.listar()).thenReturn(List.of(new TorneoResumenDto(torneo, "Autumn Cup", "EN_CURSO", 10, 8, 8)));
        when(torneosClient.obtener(torneo)).thenReturn(new TorneoDetalleDto(torneo, "Autumn Cup", "EN_CURSO", null,
            List.of(new TorneoDetalleDto.Equipo(equipo, "The Brave", false, List.of(UUID.fromString(UID)), true, 1,
                false)),
            List.of(new TorneoDetalleDto.Encuentro(3, "PENDIENTE", equipo, UUID.randomUUID()))));
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenReturn(new BandejaResponseDto(1,
            List.of(new AvisoDto("Your auction was outbid", false))));
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenReturn(new PaginaMovimientosDto(List.of(
            new PaginaMovimientosDto.Movimiento(new BigDecimal("60"), "apuesta-sala", "APARTA"),
            new PaginaMovimientosDto.Movimiento(new BigDecimal("5"), null, "NEUTRO")), 2));
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of(new MisionActivaDto("The Temple", "EXPLORACION",
            new MisionActivaDto.Heroe("Ice Mage", 2), Instant.parse("2026-09-28T20:30:00Z"), 0.5)));
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(new PaginaInventarioDto(List.of(), 0));

        assertThat(motor.generarRespuesta("my tournament", TOKEN, UID).orElseThrow().texto())
            .isEqualTo("You're in the «Autumn Cup» tournament with your team «The Brave». Your team is still in the "
                + "running; your next match waits for the previous ones to be played.");
        assertThat(motor.generarRespuesta("my notifications", TOKEN, UID).orElseThrow().texto())
            .isEqualTo("You have 1 unread notification(s). The latest: Your auction was outbid.");
        assertThat(motor.generarRespuesta("my transactions", TOKEN, UID).orElseThrow().texto())
            .isEqualTo("Your latest credit movements: 60 held (apuesta-sala); 5 without changing the balance. "
                + "The details are in «Mi Cuenta» (My Account), «Historial» tab.");
        assertThat(motor.generarRespuesta("my missions", TOKEN, UID).orElseThrow().texto())
            .isEqualTo("You have 1 mission(s) in progress: «The Temple» with Ice Mage (level 2), 50% done, ends on "
                + "09/28 at 15:30 (Colombia time).");
        assertThat(motor.generarRespuesta("show me my inventory", TOKEN, UID).orElseThrow().texto())
            .isEqualTo("Your inventory is empty for now.");
    }

    @Test
    void losTorneosEnInglesConInscripcionYSinTorneos() {
        UUID torneo = UUID.randomUUID();
        when(torneosClient.listar())
            .thenReturn(List.of(new TorneoResumenDto(torneo, "Winter Cup", "INSCRIPCIONES_ABIERTAS", 25, 3, 8)))
            .thenReturn(List.of(new TorneoResumenDto(torneo, "Winter Cup", "INSCRIPCIONES_ABIERTAS", 25, 3, 8)))
            .thenReturn(List.of());
        when(torneosClient.obtener(torneo))
            .thenReturn(new TorneoDetalleDto(torneo, "Winter Cup", "INSCRIPCIONES_ABIERTAS", null,
                List.of(new TorneoDetalleDto.Equipo(UUID.randomUUID(), "The New", false, List.of(UUID.fromString(UID)),
                    true, 4, false)), List.of()))
            .thenReturn(new TorneoDetalleDto(torneo, "Winter Cup", "INSCRIPCIONES_ABIERTAS", null, List.of(),
                List.of()));

        assertThat(motor.generarRespuesta("my tournament", TOKEN, UID).orElseThrow().texto())
            .endsWith("Your team is already registered, in position 4.");
        assertThat(motor.generarRespuesta("my tournament", TOKEN, UID).orElseThrow().texto())
            .startsWith("You're not in any active tournament. The «Winter Cup» tournament is open for registration "
                + "(3 of 8 teams; registration costs 25 credits).");
        assertThat(motor.generarRespuesta("my tournament", TOKEN, UID).orElseThrow().texto())
            .isEqualTo("Right now there is no tournament open for registration or in progress.");
    }

    // ---- 1.7.0: cada respuesta dice como termino (tasa de resolucion) -------

    private ResultadoDeRespuesta resultadoDe(String pregunta) {
        return motor.generarRespuesta(pregunta, TOKEN, UID).orElseThrow().resultado();
    }

    // Datos reales, aunque digan «no tienes ninguno», resuelven.
    @Test
    void unaConsultaQueTrajoDatosEsConsultaEnVivo() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(new PaginaInventarioDto(List.of(), 0));
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());
        when(inventarioClient.buscar(TOKEN, "escudo")).thenReturn(null);

        assertThat(resultadoDe("que tengo en mi inventario")).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
        assertThat(resultadoDe("mis misiones")).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
        assertThat(resultadoDe("busca escudo en mi inventario")).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
        assertThat(ResultadoDeRespuesta.CONSULTA_EN_VIVO.resuelve()).isTrue();
    }

    // La que dijo «no pude consultar» antes contaba como resuelta.
    @Test
    void unaConsultaQueNoRespondioEsNoDisponible() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenThrow(new ResourceAccessException("caido"));
        when(misionesClient.enCurso(TOKEN)).thenThrow(new ResourceAccessException("caido"));
        when(subastasClient.consultarMiResumen(TOKEN)).thenThrow(new ResourceAccessException("caido"));

        assertThat(resultadoDe("muestrame mi inventario")).isEqualTo(ResultadoDeRespuesta.NO_DISPONIBLE);
        assertThat(resultadoDe("mis misiones")).isEqualTo(ResultadoDeRespuesta.NO_DISPONIBLE);
        assertThat(resultadoDe("mis subastas")).isEqualTo(ResultadoDeRespuesta.NO_DISPONIBLE);
        assertThat(ResultadoDeRespuesta.NO_DISPONIBLE.resuelve()).isFalse();
    }

    @Test
    void laNavegacionEsNavegacionYUnaBusquedaCortaPideReformular() {
        assertThat(resultadoDe("llevame a mi inventario")).isEqualTo(ResultadoDeRespuesta.NAVEGACION);
        assertThat(resultadoDe("busca as en mi inventario")).isEqualTo(ResultadoDeRespuesta.PIDE_REFORMULAR);
        assertThat(ResultadoDeRespuesta.NAVEGACION.resuelve()).isTrue();
        assertThat(ResultadoDeRespuesta.PIDE_REFORMULAR.resuelve()).isFalse();
    }

    // El informe trae datos si llega al menos una parte; si no llega ninguna,
    // el jugador no recibio nada de su cuenta.
    @Test
    void elInformeEsConsultaEnVivoConUnaParteYNoDisponibleSinNinguna() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenThrow(new ResourceAccessException("caido"));
        when(subastasClient.consultarMiResumen(TOKEN)).thenThrow(new ResourceAccessException("caido"));
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenThrow(new ResourceAccessException("caido"));
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenThrow(new ResourceAccessException("caido"));
        when(misionesClient.enCurso(TOKEN))
            .thenThrow(new ResourceAccessException("caido"))
            .thenReturn(List.of());

        assertThat(resultadoDe("informe de mi actividad")).isEqualTo(ResultadoDeRespuesta.NO_DISPONIBLE);
        assertThat(resultadoDe("informe de mi actividad")).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
    }

    // 1.11.0: otras formas de pedir lo mismo llegan a la consulta en vivo.
    @Test
    void unaMisionActivaSePreguntaDeOtrasFormas() {
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        assertThat(motor.generarRespuesta("¿Tengo alguna misión activa?", TOKEN, UID)).isPresent();
        assertThat(motor.generarRespuesta("¿Qué misiones tengo en curso?", TOKEN, UID)).isPresent();

        verify(misionesClient, times(2)).enCurso(TOKEN);
    }

    @Test
    void lasUltimasTransaccionesSonLosMovimientosDeCreditos() {
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenReturn(new PaginaMovimientosDto(List.of(), 0));

        Optional<ResultadoMotor> resultado = motor.generarRespuesta("¿Cuáles son mis últimas transacciones?", TOKEN, UID);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().resultado()).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
    }

    // Y una pregunta de reglas, aunque traiga «mi puja», la responde la base.
    @Test
    void unaPreguntaDeReglasSobreLoSuyoNoConsultaNada() {
        assertThat(motor.generarRespuesta("¿Puedo cancelar mi puja?", TOKEN, UID)).isEmpty();
        assertThat(motor.generarRespuesta("¿Qué pasa si abandono mi misión?", TOKEN, UID)).isEmpty();

        verifyNoInteractions(subastasClient, misionesClient, inventarioClient, notificacionesClient, finanzasClient);
    }

    // 1.11.0 (7.4.8): reconocer que un visitante pide algo de su cuenta, sin consultar nada.
    @ParameterizedTest
    @ValueSource(strings = {"¿Qué tengo en mi inventario?", "muéstrame mis últimas transacciones",
        "dame un informe de mi actividad", "busca espada en mi inventario", "llévame a mi inventario",
        "¿Tengo notificaciones?", "¿Tengo alguna misión activa?", "show me my notifications", "mis pujas"})
    void reconoceQuePideDatosDeSuCuenta(String mensaje) {
        assertThat(motor.pideDatosDeSuCuenta(mensaje)).as(mensaje).isTrue();
        verifyNoInteractions(subastasClient, misionesClient, inventarioClient, notificacionesClient, finanzasClient,
            torneosClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"¿Cómo me registro?", "¿Cómo funciona el combate?", "llévame a la tienda",
        "¿Hay torneos en curso?", "¿Puedo cancelar mi puja?", "llévame a la subasta", "   "})
    void loQueNoEsDeSuCuentaNoLoEs(String mensaje) {
        assertThat(motor.pideDatosDeSuCuenta(mensaje)).as(mensaje).isFalse();
    }

    @Test
    void sinMensajeNoPideNada() {
        assertThat(motor.pideDatosDeSuCuenta(null)).isFalse();
    }

    // ---- 1.18.0: cada consulta a sus datos queda auditada (RF-CHA-008) ------

    private static final String ORIGEN = "203.0.113.7";

    private MotorConsultasAsistidas motorAuditado(AuditoriaDeAccesos auditoria) {
        return new MotorConsultasAsistidas(inventarioClient, subastasClient, notificacionesClient, torneosClient,
            finanzasClient, misionesClient, auditoria);
    }

    @Test
    void cadaConsultaASusDatosQuedaAuditadaConSuUidYSuOrigen() {
        AuditoriaDeAccesos auditoria = mock(AuditoriaDeAccesos.class);
        MotorConsultasAsistidas auditado = motorAuditado(auditoria);
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(new PaginaInventarioDto(List.of(), 0));
        when(inventarioClient.buscar(TOKEN, "espada")).thenReturn(new PaginaInventarioDto(List.of(), 0));
        when(subastasClient.consultarMiResumen(TOKEN)).thenThrow(new ResourceAccessException("caido"));
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        auditado.generarRespuesta("que tengo en mi inventario", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("busca la espada en mi inventario", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("como van mis subastas", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("mis misiones activas", TOKEN, UID, IdiomaPreferido.AUTOMATICO, null);

        verify(auditoria).registrar(UID, DatoConsultado.INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO, ORIGEN);
        verify(auditoria).registrar(UID, DatoConsultado.BUSQUEDA_EN_INVENTARIO, ResultadoDeRespuesta.CONSULTA_EN_VIVO,
            ORIGEN);
        // Tambien lo que se intento y no llego: el acceso se pidio con su token.
        verify(auditoria).registrar(UID, DatoConsultado.SUBASTAS, ResultadoDeRespuesta.NO_DISPONIBLE, ORIGEN);
        verify(auditoria).registrar(UID, DatoConsultado.MISIONES, ResultadoDeRespuesta.CONSULTA_EN_VIVO, null);
        verifyNoMoreInteractions(auditoria);
    }

    @Test
    void avisosMovimientosEInformeTambienQuedanAuditados() {
        AuditoriaDeAccesos auditoria = mock(AuditoriaDeAccesos.class);
        MotorConsultasAsistidas auditado = motorAuditado(auditoria);
        when(notificacionesClient.consultarBandeja(TOKEN, UID)).thenThrow(new ResourceAccessException("caido"));
        when(finanzasClient.movimientos(TOKEN, UID, 5)).thenThrow(new ResourceAccessException("caido"));
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenThrow(new ResourceAccessException("caido"));
        when(subastasClient.consultarMiResumen(TOKEN)).thenThrow(new ResourceAccessException("caido"));
        when(misionesClient.enCurso(TOKEN)).thenReturn(List.of());

        auditado.generarRespuesta("mis notificaciones", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("mis ultimas transacciones", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("dame un informe de mi actividad", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);

        verify(auditoria).registrar(UID, DatoConsultado.NOTIFICACIONES, ResultadoDeRespuesta.NO_DISPONIBLE, ORIGEN);
        verify(auditoria).registrar(UID, DatoConsultado.MOVIMIENTOS, ResultadoDeRespuesta.NO_DISPONIBLE, ORIGEN);
        verify(auditoria).registrar(UID, DatoConsultado.INFORME_DE_ACTIVIDAD, ResultadoDeRespuesta.CONSULTA_EN_VIVO,
            ORIGEN);
        verifyNoMoreInteractions(auditoria);
    }

    // Ir a una seccion, una busqueda demasiado corta y los torneos (listado
    // publico, sin su token) no leen nada suyo: no hay asiento.
    @Test
    void loQueNoLeeSusDatosNoSeAudita() {
        AuditoriaDeAccesos auditoria = mock(AuditoriaDeAccesos.class);
        MotorConsultasAsistidas auditado = motorAuditado(auditoria);
        when(torneosClient.listar()).thenThrow(new ResourceAccessException("caido"));

        auditado.generarRespuesta("llevame a mi inventario", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("busca as en mi inventario", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("hay torneos en curso", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);
        auditado.generarRespuesta("hola", TOKEN, UID, IdiomaPreferido.AUTOMATICO, ORIGEN);

        verify(auditoria, never()).registrar(any(), any(), any(), any());
    }

    // Sin bitacora central (el constructor de las pruebas) no se rompe nada.
    @Test
    void sinBitacoraCentralLaConsultaSigueIgual() {
        when(inventarioClient.consultarInventario(TOKEN, 0)).thenReturn(new PaginaInventarioDto(List.of(), 0));

        assertThat(motor.generarRespuesta("que tengo en mi inventario", TOKEN, UID, IdiomaPreferido.AUTOMATICO,
            ORIGEN).orElseThrow().resultado()).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
    }
}
