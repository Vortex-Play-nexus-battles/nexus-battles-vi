package com.nexusbattles.plataforma.notificaciones.catalogo;

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
import org.junit.jupiter.api.Nested;
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
 * HU-NOT-001 (#532) — el importador que lleva los cambios del catalogo a la
 * bandeja antes de entregar lo pendiente a una sesion.
 *
 * <p>Lo que se fija: la linea base del primer ingreso (sin volcar historial),
 * el intervalo minimo entre consultas, el orden y el cursor, que un aviso que
 * ya estaba (otra sesion, otra consulta) no se duplique ni frene nada, y que
 * ningun fallo de productos ni de la bandeja salga de aqui: la entrega de
 * pendientes sigue igual sin estos avisos (degradacion controlada).
 */
@DisplayName("Notificaciones · importador de avisos del catalogo")
class ImportadorDeAvisosDelCatalogoTest {

    private static final String JUGADOR = "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55";
    private static final Instant AHORA = Instant.parse("2026-10-05T15:00:00Z");
    private static final Duration INTERVALO = Duration.ofSeconds(60);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static final Instant CURSOR = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-05T14:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-05T14:30:00Z");

    private CambiosDelCatalogo catalogo;
    private CursorCatalogoRepository cursores;
    private ServicioDeNotificaciones servicio;
    private ImportadorDeAvisosDelCatalogo importador;

    @BeforeEach
    void preparar() {
        catalogo = mock(CambiosDelCatalogo.class);
        cursores = mock(CursorCatalogoRepository.class);
        servicio = mock(ServicioDeNotificaciones.class);
        importador = new ImportadorDeAvisosDelCatalogo(
                catalogo, cursores, servicio, Clock.fixed(AHORA, ZoneOffset.UTC), INTERVALO, BOGOTA);
    }

    private static CambioDelCatalogo cambio(String id, String tipo, Instant implementadaEn) {
        return new CambioDelCatalogo(id, "producto-1", "Espada solar", tipo,
                "El producto Espada solar fue modificado.", implementadaEn);
    }

    private void conCursor(Instant hasta, Instant consultadoEn) {
        when(cursores.findById(JUGADOR))
                .thenReturn(Optional.of(new RegistroDeCursorCatalogo(JUGADOR, hasta, consultadoEn)));
    }

    @Nested
    @DisplayName("primer ingreso y cursor")
    class Cursor {

        @Test
        @DisplayName("sin cursor pide la linea base, no emite nada y guarda el hasta que devuelve productos")
        void lineaBase() {
            when(cursores.findById(JUGADOR)).thenReturn(Optional.empty());
            when(catalogo.consultar(null)).thenReturn(new LoteDeCambios(AHORA, true, List.of()));

            importador.incorporar(JUGADOR);

            verify(catalogo).consultar(null);
            verifyNoInteractions(servicio);
            verify(cursores).guardar(JUGADOR, AHORA, AHORA);
        }

        @Test
        @DisplayName("con cursor pide desde el cursor, emite en orden y despues guarda el hasta del lote")
        void desdeElCursorEnOrden() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T2, true, List.of(
                    cambio("a1", "NUEVO_PRODUCTO", T1),
                    cambio("a2", "PRODUCTO_MODIFICADO", T2))));

            importador.incorporar(JUGADOR);

            InOrder orden = inOrder(servicio, cursores);
            ArgumentCaptor<Notificacion> avisos = ArgumentCaptor.forClass(Notificacion.class);
            orden.verify(servicio, times(2)).emitir(eq(JUGADOR), avisos.capture());
            orden.verify(cursores).guardar(JUGADOR, T2, AHORA);
            assertEquals(List.of("catalogo:a1", "catalogo:a2"),
                    avisos.getAllValues().stream().map(Notificacion::id).toList());
        }

        @Test
        @DisplayName("el aviso es CAMBIO_CATALOGO con id catalogo:{alerta}, la descripcion y la fecha legible en la zona configurada")
        void formaDelAviso() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            Instant implementada = Instant.parse("2026-10-05T15:30:00Z");
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(implementada, true,
                    List.of(cambio("alerta-9", "PRODUCTO_MODIFICADO", implementada))));

            importador.incorporar(JUGADOR);

            ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
            verify(servicio).emitir(eq(JUGADOR), aviso.capture());
            assertEquals("catalogo:alerta-9", aviso.getValue().id());
            assertEquals("CAMBIO_CATALOGO", aviso.getValue().tipo());
            assertEquals("Producto modificado", aviso.getValue().titulo());
            assertEquals("El producto Espada solar fue modificado. "
                    + "Fecha de implementación: 5 de octubre de 2026, 10:30.", aviso.getValue().cuerpo());
            assertEquals(implementada, aviso.getValue().creadaEn());
        }

        @Test
        @DisplayName("un lote incompleto no renueva la hora de consulta: la siguiente sesion trae el resto sin esperar")
        void loteIncompleto() {
            Instant consultadoAntes = AHORA.minusSeconds(3600);
            conCursor(CURSOR, consultadoAntes);
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T1, false,
                    List.of(cambio("a1", "CAMBIO_BALANCE", T1))));

            importador.incorporar(JUGADOR);

            verify(cursores).guardar(JUGADOR, T1, consultadoAntes);
        }
    }

    @Nested
    @DisplayName("intervalo minimo entre consultas")
    class Intervalo {

        @Test
        @DisplayName("si se consulto hace menos del intervalo no llama a productos ni toca nada")
        void dentroDelIntervaloNoLlama() {
            conCursor(CURSOR, AHORA.minusSeconds(30));

            importador.incorporar(JUGADOR);

            verifyNoInteractions(catalogo, servicio);
            verify(cursores, never()).guardar(anyString(), any(), any());
            verify(cursores, never()).marcarConsulta(anyString(), any());
        }

        @Test
        @DisplayName("cumplido el intervalo justo, vuelve a consultar")
        void alCumplirseElIntervaloLlama() {
            conCursor(CURSOR, AHORA.minus(INTERVALO));
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(CURSOR, true, List.of()));

            importador.incorporar(JUGADOR);

            verify(catalogo).consultar(CURSOR);
            verify(cursores).guardar(JUGADOR, CURSOR, AHORA);
        }
    }

    @Nested
    @DisplayName("lo que ya estaba no se duplica")
    class Duplicados {

        @Test
        @DisplayName("un aviso que ya estaba (otra sesion u otra consulta) se ignora y la importacion sigue")
        void avisoDuplicado() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T2, true, List.of(
                    cambio("a1", "NUEVO_PRODUCTO", T1),
                    cambio("a2", "PRODUCTO_MODIFICADO", T2))));
            when(servicio.emitir(eq(JUGADOR), any()))
                    .thenThrow(new AvisoDuplicado("ya estaba"))
                    .thenReturn(java.util.Set.of());

            importador.incorporar(JUGADOR);

            verify(servicio, times(2)).emitir(eq(JUGADOR), any());
            verify(cursores).guardar(JUGADOR, T2, AHORA);
        }

        @Test
        @DisplayName("la carrera de dos sesiones que choca con la clave unica es lo mismo que un duplicado")
        void carreraDeDosSesiones() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T1, true,
                    List.of(cambio("a1", "NUEVO_PRODUCTO", T1))));
            doThrow(new DataIntegrityViolationException("duplicado",
                    new SQLException("duplicate key value violates unique constraint", "23505")))
                    .when(servicio).emitir(eq(JUGADOR), any());

            importador.incorporar(JUGADOR);

            verify(cursores).guardar(JUGADOR, T1, AHORA);
        }

        @Test
        @DisplayName("si la otra sesion lo guardo justo antes, la bandeja lo rechaza como repetido y se sigue")
        void carreraQueVeLaBandejaYaCargada() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T2, true, List.of(
                    cambio("a1", "NUEVO_PRODUCTO", T1),
                    cambio("a2", "PRODUCTO_MODIFICADO", T2))));
            when(servicio.emitir(eq(JUGADOR), any()))
                    .thenThrow(new IllegalArgumentException("ya existe una notificacion con el identificador catalogo:a1"))
                    .thenReturn(java.util.Set.of());

            importador.incorporar(JUGADOR);

            verify(servicio, times(2)).emitir(eq(JUGADOR), any());
            verify(cursores).guardar(JUGADOR, T2, AHORA);
        }
    }

    @Nested
    @DisplayName("degradacion controlada: nada de esto rompe la entrega de pendientes")
    class Degradacion {

        @Test
        @DisplayName("si productos no responde no sale ninguna excepcion, no se emite nada y se anota la consulta")
        void productosNoResponde() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            when(catalogo.consultar(CURSOR)).thenThrow(new CatalogoNoDisponible("productos respondio 503"));

            assertDoesNotThrow(() -> importador.incorporar(JUGADOR));

            verifyNoInteractions(servicio);
            verify(cursores, never()).guardar(anyString(), any(), any());
            verify(cursores).marcarConsulta(JUGADOR, AHORA);
        }

        @Test
        @DisplayName("sin cursor y sin productos tampoco falla y no deja un cursor inventado")
        void productosNoRespondeEnElPrimerIngreso() {
            when(cursores.findById(JUGADOR)).thenReturn(Optional.empty());
            when(catalogo.consultar(null)).thenThrow(new CatalogoNoDisponible("sin respuesta"));

            assertDoesNotThrow(() -> importador.incorporar(JUGADOR));

            verify(cursores, never()).guardar(anyString(), any(), any());
            verify(cursores, never()).marcarConsulta(anyString(), any());
        }

        @Test
        @DisplayName("si la bandeja falla de forma inesperada el cursor no avanza: nada se pierde")
        void bandejaFalla() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T2, true, List.of(
                    cambio("a1", "NUEVO_PRODUCTO", T1),
                    cambio("a2", "PRODUCTO_MODIFICADO", T2))));
            when(servicio.emitir(eq(JUGADOR), any())).thenThrow(new IllegalStateException("base caida"));

            assertDoesNotThrow(() -> importador.incorporar(JUGADOR));

            verify(servicio, times(1)).emitir(eq(JUGADOR), any());
            verify(cursores, never()).guardar(anyString(), any(), any());
        }

        @Test
        @DisplayName("un cambio que no cabe en la bandeja se salta y los demas siguen")
        void cambioInvalidoSeSalta() {
            conCursor(CURSOR, AHORA.minusSeconds(3600));
            when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T2, true, List.of(
                    cambio("x".repeat(300), "NUEVO_PRODUCTO", T1),
                    cambio("a2", "PRODUCTO_MODIFICADO", T2))));

            importador.incorporar(JUGADOR);

            ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
            verify(servicio).emitir(eq(JUGADOR), aviso.capture());
            assertEquals("catalogo:a2", aviso.getValue().id());
            verify(cursores).guardar(JUGADOR, T2, AHORA);
        }

        @Test
        @DisplayName("si ni siquiera se puede leer el cursor, tampoco falla")
        void cursorIlegible() {
            when(cursores.findById(JUGADOR)).thenThrow(new IllegalStateException("base caida"));

            assertDoesNotThrow(() -> importador.incorporar(JUGADOR));

            verifyNoInteractions(catalogo, servicio);
        }
    }

    @ParameterizedTest(name = "{0} -> «{1}»")
    @CsvSource(delimiter = '|', value = {
            "NUEVO_PRODUCTO|Nuevo producto disponible",
            "PRODUCTO_MODIFICADO|Producto modificado",
            "PRODUCTO_SUSPENDIDO|Producto suspendido",
            "PRODUCTO_REACTIVADO|Producto disponible de nuevo",
            "CAMBIO_BALANCE|Cambio en el balance del juego",
            "UN_TIPO_FUTURO|Cambio en el catálogo"})
    @DisplayName("titulo por tipo de cambio")
    void tituloPorTipo(String tipo, String titulo) {
        conCursor(CURSOR, AHORA.minusSeconds(3600));
        when(catalogo.consultar(CURSOR)).thenReturn(new LoteDeCambios(T1, true, List.of(cambio("a1", tipo, T1))));

        importador.incorporar(JUGADOR);

        ArgumentCaptor<Notificacion> aviso = ArgumentCaptor.forClass(Notificacion.class);
        verify(servicio).emitir(eq(JUGADOR), aviso.capture());
        assertEquals(titulo, aviso.getValue().titulo());
    }
}
