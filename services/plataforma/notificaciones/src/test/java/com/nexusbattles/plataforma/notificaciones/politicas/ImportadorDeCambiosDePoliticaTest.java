package com.nexusbattles.plataforma.notificaciones.politicas;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.nexusbattles.plataforma.notificaciones.Notificacion;
import com.nexusbattles.plataforma.notificaciones.bandeja.AvisoDuplicado;
import com.nexusbattles.plataforma.notificaciones.bandeja.ServicioDeNotificaciones;

/**
 * SA-11 (§7.3.7) — el importador que lleva los cambios en las politicas de
 * uso a la bandeja antes de entregar lo pendiente a una sesion: la ventana
 * del primer ingreso, el intervalo minimo, el solape del cursor, la
 * idempotencia y que ningun fallo salga de aqui.
 */
@DisplayName("Notificaciones · importador de cambios en las politicas de uso")
class ImportadorDeCambiosDePoliticaTest {

    private static final String JUGADOR = "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55";
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final Duration INTERVALO = Duration.ofSeconds(60);
    private static final Duration VENTANA = Duration.ofDays(30);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static final Instant CURSOR = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-14T14:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-14T14:30:00Z");

    private CambiosDePolitica politicas;
    private CursorPoliticasRepository cursores;
    private ServicioDeNotificaciones servicio;
    private ImportadorDeCambiosDePolitica importador;

    @BeforeEach
    void preparar() {
        politicas = mock(CambiosDePolitica.class);
        cursores = mock(CursorPoliticasRepository.class);
        servicio = mock(ServicioDeNotificaciones.class);
        importador = new ImportadorDeCambiosDePolitica(politicas, cursores, servicio,
                Clock.fixed(AHORA, ZoneOffset.UTC), INTERVALO, VENTANA, BOGOTA);
    }

    private static CambioDePoliticaDeUso cambio(String id, String politica, Instant publicadoEn) {
        return new CambioDePoliticaDeUso(id, politica, "Política de privacidad", "Datos del chat",
                "Los mensajes del chat se guardan noventa días y luego se borran",
                Instant.parse("2026-10-20T05:00:00Z"), publicadoEn);
    }

    private void conCursor(Instant hasta, Instant consultadoEn) {
        when(cursores.findById(JUGADOR)).thenReturn(Optional.of(new RegistroDeCursorPoliticas(JUGADOR, hasta,
                consultadoEn)));
    }

    @Test
    @DisplayName("sin cursor lee la ventana de 30 dias: el aviso dice que cambio, en que politica y desde cuando")
    void primerIngreso() {
        when(cursores.findById(JUGADOR)).thenReturn(Optional.empty());
        when(politicas.consultar(AHORA.minus(VENTANA))).thenReturn(new LoteDeCambiosDePolitica(T2, true, List.of(
                cambio("c-1", "POLITICA_DE_PRIVACIDAD", T1),
                cambio("c-2", "TERMINOS_Y_CONDICIONES", T2))));

        importador.incorporar(JUGADOR);

        ArgumentCaptor<Notificacion> avisos = ArgumentCaptor.forClass(Notificacion.class);
        InOrder orden = inOrder(servicio, cursores);
        orden.verify(servicio, times(2)).emitir(eq(JUGADOR), avisos.capture());
        orden.verify(cursores).guardar(JUGADOR, T2, AHORA);

        Notificacion primero = avisos.getAllValues().get(0);
        assertEquals("politica:c-1", primero.id());
        assertEquals("CAMBIO_POLITICA", primero.tipo());
        assertEquals("Actualizamos la política de privacidad", primero.titulo());
        assertEquals("Datos del chat. Los mensajes del chat se guardan noventa días y luego se borran."
                + " Rige desde el 20 de octubre de 2026, 00:00.", primero.cuerpo());
        assertEquals(T1, primero.creadaEn());
        assertEquals("Actualizamos los términos y condiciones del servicio", avisos.getAllValues().get(1).titulo());
    }

    @Test
    @DisplayName("con cursor lee desde un minuto antes de el; lo que ya estaba se ignora y el cursor avanza")
    void conSolape() {
        conCursor(CURSOR, AHORA.minusSeconds(120));
        when(politicas.consultar(CURSOR.minus(Duration.ofMinutes(1)))).thenReturn(new LoteDeCambiosDePolitica(T1,
                true, List.of(cambio("c-0", "POLITICA_DE_PRIVACIDAD", CURSOR), cambio("c-1",
                        "POLITICA_DE_PRIVACIDAD", T1))));
        when(servicio.emitir(eq(JUGADOR), any())).thenThrow(new AvisoDuplicado("ya estaba"))
                .thenReturn(java.util.Set.of());

        importador.incorporar(JUGADOR);

        verify(servicio, times(2)).emitir(eq(JUGADOR), any());
        verify(cursores).guardar(JUGADOR, T1, AHORA);
    }

    @Test
    @DisplayName("si consulto hace menos del intervalo minimo no pregunta a admin-parametros")
    void intervaloMinimo() {
        conCursor(CURSOR, AHORA.minusSeconds(30));

        importador.incorporar(JUGADOR);

        verifyNoInteractions(politicas, servicio);
        verify(cursores, never()).guardar(anyString(), any(), any());
    }

    @Test
    @DisplayName("un lote cortado guarda el cursor sin renovar la hora: la siguiente sesion trae el resto")
    void loteCortado() {
        Instant consultado = AHORA.minusSeconds(90);
        conCursor(CURSOR, consultado);
        when(politicas.consultar(CURSOR.minus(Duration.ofMinutes(1)))).thenReturn(
                new LoteDeCambiosDePolitica(T1, false, List.of(cambio("c-1", "POLITICA_DE_PRIVACIDAD", T1))));

        importador.incorporar(JUGADOR);

        verify(cursores).guardar(JUGADOR, T1, consultado);
    }

    @Test
    @DisplayName("si el solape solo llena un lote sin avanzar, se lee otra vez sin solape")
    void solapeSinAvance() {
        conCursor(CURSOR, AHORA.minusSeconds(90));
        when(politicas.consultar(CURSOR.minus(Duration.ofMinutes(1)))).thenReturn(
                new LoteDeCambiosDePolitica(CURSOR, false, List.of(cambio("c-0", "POLITICA_DE_PRIVACIDAD", CURSOR))));
        when(politicas.consultar(CURSOR)).thenReturn(
                new LoteDeCambiosDePolitica(T2, true, List.of(cambio("c-2", "TERMINOS_Y_CONDICIONES", T2))));

        importador.incorporar(JUGADOR);

        ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
        verify(servicio).emitir(eq(JUGADOR), aviso.capture());
        assertEquals("politica:c-2", aviso.getValue().id());
        verify(cursores).guardar(JUGADOR, T2, AHORA);
    }

    @Test
    @DisplayName("admin-parametros caido: sin cursor no se guarda nada; con cursor solo se anota la consulta")
    void noDisponible() {
        when(cursores.findById(JUGADOR)).thenReturn(Optional.empty());
        when(politicas.consultar(any())).thenThrow(new PoliticasNoDisponibles("admin-parametros respondio 503"));

        assertDoesNotThrow(() -> importador.incorporar(JUGADOR));
        verify(cursores, never()).guardar(anyString(), any(), any());
        verify(cursores, never()).marcarConsulta(anyString(), any());

        conCursor(CURSOR, AHORA.minusSeconds(120));
        assertDoesNotThrow(() -> importador.incorporar(JUGADOR));
        verify(cursores).marcarConsulta(JUGADOR, AHORA);
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("si la bandeja falla a mitad, se para y el cursor no avanza")
    void bandejaCaida() {
        when(cursores.findById(JUGADOR)).thenReturn(Optional.empty());
        when(politicas.consultar(any())).thenReturn(new LoteDeCambiosDePolitica(T2, true, List.of(
                cambio("c-1", "POLITICA_DE_PRIVACIDAD", T1), cambio("c-2", "POLITICA_DE_PRIVACIDAD", T2))));
        when(servicio.emitir(eq(JUGADOR), any())).thenThrow(new IllegalStateException("base caida"));

        assertDoesNotThrow(() -> importador.incorporar(JUGADOR));

        verify(servicio, times(1)).emitir(eq(JUGADOR), any());
        verify(cursores, never()).guardar(anyString(), any(), any());
    }

    @Test
    @DisplayName("una carrera de clave repetida o un aviso que el dominio rechaza no frenan a los demas")
    void carreras() {
        when(cursores.findById(JUGADOR)).thenReturn(Optional.empty());
        when(politicas.consultar(any())).thenReturn(new LoteDeCambiosDePolitica(T2, true, List.of(
                cambio("c-1", "POLITICA_DE_PRIVACIDAD", T1),
                cambio("c-2", "POLITICA_DE_PRIVACIDAD", T1),
                cambio("c-3", "POLITICA_DE_PRIVACIDAD", T2))));
        when(servicio.emitir(eq(JUGADOR), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key",
                        new SQLException("duplicate key value violates unique constraint", "23505")))
                .thenThrow(new DataIntegrityViolationException("otra", new SQLException("otra", "23514")))
                .thenThrow(new IllegalArgumentException("ya estaba"));

        importador.incorporar(JUGADOR);

        verify(servicio, times(3)).emitir(eq(JUGADOR), any());
        verify(cursores).guardar(JUGADOR, T2, AHORA);
    }

    @Test
    @DisplayName("un cambio sin id, sin fecha o sin titulo se salta; los demas llegan")
    void cambiosInvalidos() {
        when(cursores.findById(JUGADOR)).thenReturn(Optional.empty());
        CambioDePoliticaDeUso sinTitulo = new CambioDePoliticaDeUso("c-4", "POLITICA_DE_PRIVACIDAD", null, null,
                "Algo cambia en la politica de privacidad", null, T1);
        when(politicas.consultar(any())).thenReturn(new LoteDeCambiosDePolitica(T2, true, List.of(
                cambio(null, "POLITICA_DE_PRIVACIDAD", T1),
                cambio(" ", "POLITICA_DE_PRIVACIDAD", T1),
                cambio("c-3", "POLITICA_DE_PRIVACIDAD", null),
                sinTitulo,
                cambio("c-5", "POLITICA_DE_PRIVACIDAD", T2))));

        importador.incorporar(JUGADOR);

        ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
        verify(servicio).emitir(eq(JUGADOR), aviso.capture());
        assertEquals("politica:c-5", aviso.getValue().id());
        verify(cursores).guardar(JUGADOR, T2, AHORA);
    }

    @Test
    @DisplayName("sin vigencia, rige desde la publicacion")
    void sinVigencia() {
        Notificacion aviso = importador.avisoDe(new CambioDePoliticaDeUso("c-1", "TERMINOS_Y_CONDICIONES",
                null, "Nuevas reglas del chat!", "Los enlaces externos se ocultan.", null, T1));

        assertEquals("Nuevas reglas del chat! Los enlaces externos se ocultan. Rige desde el 14 de octubre de 2026,"
                + " 09:00.", aviso.cuerpo());
    }

    @Test
    @DisplayName("si hasta leer el cursor falla, no sale nada de aqui")
    void nuncaLanza() {
        doThrow(new IllegalStateException("base caida")).when(cursores).findById(JUGADOR);

        assertDoesNotThrow(() -> importador.incorporar(JUGADOR));
        verifyNoInteractions(politicas, servicio);
    }

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @CsvSource(delimiter = '|', value = {
            "TERMINOS_Y_CONDICIONES|Términos y condiciones del servicio|Actualizamos los términos y condiciones del servicio",
            "POLITICA_DE_PRIVACIDAD|Política de privacidad|Actualizamos la política de privacidad",
            "COOKIES|Política de cookies|Cambio en Política de cookies",
            "COOKIES| |Cambio en las políticas de uso",
            "|               |Cambio en las políticas de uso"})
    @DisplayName("el titulo del aviso segun la politica; una nueva no frena la importacion")
    void titulos(String politica, String nombre, String esperado) {
        assertEquals(esperado, ImportadorDeCambiosDePolitica.tituloDe(politica, nombre));
    }
}
