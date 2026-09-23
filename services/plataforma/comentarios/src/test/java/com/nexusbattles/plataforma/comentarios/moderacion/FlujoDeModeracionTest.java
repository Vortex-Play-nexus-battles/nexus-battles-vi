package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El flujo de moderacion completo — R10.1 (RF-COM-005, RF-COM-006, RF-COM-008).
 *
 * <h2>Que se prueba aqui, y por que asi</h2>
 *
 * El defecto que R10.1 cierra no es una excepcion mal lanzada: es que un
 * comentario podia entrar en EN_REVISION y no tener salida. Una prueba que
 * mirase solo "reportar devuelve 201" no lo habria visto nunca. Por eso el
 * caso central recorre el camino entero —reportar, aparecer en la cola,
 * resolverse, quedar el asiento— y comprueba que el comentario SALE del
 * estado en el que entro.
 *
 * <p>Los repositorios son simulacros RESPALDADOS POR COLECCIONES, no
 * {@code when(...).thenReturn(...)} por llamada: el flujo guarda y vuelve a
 * leer varias veces, y un simulacro por llamada acabaria probando el guion de
 * la prueba en vez del comportamiento.
 */
@DisplayName("R10.1: el comentario en revision tiene salida")
class FlujoDeModeracionTest {

    private static final Instant AHORA = Instant.parse("2026-09-23T10:00:00Z");
    private static final String PRODUCTO = "prod-1";

    private Map<String, RegistroDeComentario> filasDeComentarios;
    private List<RegistroDeReporte> filasDeReportes;
    private List<AsientoDeModeracion> filasDeAsientos;
    private ComentarioRepository comentarios;
    private ReporteRepository reportes;
    private AsientoRepository asientos;
    private List<String> avisos;
    private List<String> auditados;
    private ServicioDeModeracion servicio;

    @BeforeEach
    void montar() {
        filasDeComentarios = new LinkedHashMap<>();
        filasDeReportes = new ArrayList<>();
        filasDeAsientos = new ArrayList<>();
        comentarios = comentariosEnMemoria(filasDeComentarios);
        reportes = reportesEnMemoria(filasDeReportes);
        asientos = asientosEnMemoria(filasDeAsientos);
        avisos = new ArrayList<>();
        auditados = new ArrayList<>();

        servicio = new ServicioDeModeracion(
                comentarios, reportes, asientos,
                (comentario, asiento) -> {
                    avisos.add(comentario.autorId() + ":" + asiento.accion());
                    return true;
                },
                asiento -> auditados.add(asiento.comentarioId() + ":" + asiento.accion()),
                Clock.fixed(AHORA, ZoneOffset.UTC),
                3);
    }

    private Comentario publicar(String id, String autor) {
        Comentario c = new Comentario(id, PRODUCTO, autor, "apodo-" + autor,
                "texto", List.of(), null, AHORA.minusSeconds(3600),
                Comentario.Estado.PUBLICADO);
        comentarios.save(RegistroDeComentario.desde(c));
        return c;
    }

    // ------------------------------------------------------------------------

    @Test
    @DisplayName("el camino entero: se reporta, entra en la cola, se resuelve y sale")
    void elCaminoEntero() {
        publicar("c-1", "autor-1");

        // 1. un jugador lo reporta
        ServicioDeModeracion.Reportado reportado = servicio.reportar(
                PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.ACOSO, "me insulta");

        assertEquals(Comentario.Estado.EN_REVISION, reportado.comentario().estado(),
                "el primer reporte tiene que encolarlo");
        assertEquals(1, reportado.totales());

        // 2. aparece en la cola del moderador
        ServicioDeModeracion.Cola cola = servicio.cola(null, 0, 20);
        assertEquals(1, cola.total());
        assertEquals("c-1", cola.entradas().get(0).comentario().id());
        assertEquals(1, cola.entradas().get(0).reportes());
        assertEquals(1L, cola.entradas().get(0).porCategoria().get(CategoriaDeReporte.ACOSO));

        // 3. el moderador decide
        ServicioDeModeracion.Resuelto resuelto = servicio.resolver(
                "c-1", "mod-1", "moderadora", AccionDeModeracion.OCULTAR, "acoso confirmado");

        // 4. SALE del estado en el que entro. Esto es el defecto que R10.1 cierra.
        assertEquals(Comentario.Estado.OCULTO, resuelto.comentario().estado());
        assertEquals(0, servicio.cola(null, 0, 20).total(),
                "resuelto es resuelto: deja de estar en la cola");

        // 5. queda el asiento, con las cinco cosas que pide la ficha
        AsientoDeModeracion asiento = resuelto.asiento();
        assertEquals("mod-1", asiento.moderadorId());
        assertEquals("moderadora", asiento.apodoModerador());
        assertEquals("acoso confirmado", asiento.motivo());
        assertEquals(Comentario.Estado.EN_REVISION, asiento.estadoAnterior());
        assertEquals(Comentario.Estado.OCULTO, asiento.estadoNuevo());
        assertEquals(AHORA, asiento.fecha());

        // 6. y el autor se entera
        assertTrue(resuelto.autorNotificado());
        assertEquals(List.of("autor-1:OCULTAR"), avisos);
        assertEquals(List.of("c-1:OCULTAR"), auditados);
    }

    @Nested
    @DisplayName("Reportar (RF-COM-006)")
    class Reportar {

        @Test
        @DisplayName("el mismo usuario no puede reportar dos veces el mismo comentario")
        void duplicado() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);

            // Reportar dos veces no sube la prioridad: eso seria premiar la
            // insistencia de uno sobre el acuerdo de varios.
            assertThrows(ServicioDeModeracion.ReporteDuplicado.class, () ->
                    servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.ACOSO, null));
        }

        @Test
        @DisplayName("varias personas distintas suman, y el comentario no se reencola")
        void variosReportantes() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);
            ServicioDeModeracion.Reportado segundo = servicio.reportar(
                    PRODUCTO, "c-1", "jugador-b", CategoriaDeReporte.SPAM, null);

            assertEquals(2, segundo.totales());
            assertEquals(Comentario.Estado.EN_REVISION, segundo.comentario().estado());
        }

        @Test
        @DisplayName("el limite diario corta, y es configurable porque la ficha no fija el valor")
        void limiteDiario() {
            for (int i = 1; i <= 3; i++) {
                publicar("c-" + i, "autor-" + i);
                servicio.reportar(PRODUCTO, "c-" + i, "jugador-a", CategoriaDeReporte.SPAM, null);
            }
            publicar("c-4", "autor-4");

            assertThrows(ServicioDeModeracion.LimiteDeReportesAgotado.class, () ->
                    servicio.reportar(PRODUCTO, "c-4", "jugador-a", CategoriaDeReporte.SPAM, null));
        }

        @Test
        @DisplayName("un comentario de otro producto no es un comentario de este")
        void productoAjeno() {
            publicar("c-1", "autor-1");

            assertThrows(ServicioDeModeracion.ComentarioNoEncontrado.class, () ->
                    servicio.reportar("otro-producto", "c-1", "jugador-a",
                            CategoriaDeReporte.SPAM, null));
        }
    }

    @Nested
    @DisplayName("La cola (RF-COM-005)")
    class Cola {

        @Test
        @DisplayName("vacia es una respuesta correcta, no un 404 (CA-03)")
        void vacia() {
            ServicioDeModeracion.Cola cola = servicio.cola(null, 0, 20);
            assertEquals(0, cola.total());
            assertTrue(cola.entradas().isEmpty());
        }

        @Test
        @DisplayName("priorizada: el mas reportado primero")
        void prioridad() {
            publicar("poco", "autor-1");
            publicar("mucho", "autor-2");

            servicio.reportar(PRODUCTO, "poco", "jugador-a", CategoriaDeReporte.SPAM, null);
            servicio.reportar(PRODUCTO, "mucho", "jugador-a", CategoriaDeReporte.ACOSO, null);
            servicio.reportar(PRODUCTO, "mucho", "jugador-b", CategoriaDeReporte.ACOSO, null);
            servicio.reportar(PRODUCTO, "mucho", "jugador-c", CategoriaDeReporte.SPAM, null);

            List<ServicioDeModeracion.Entrada> entradas = servicio.cola(null, 0, 20).entradas();
            assertEquals("mucho", entradas.get(0).comentario().id());
            assertEquals(3, entradas.get(0).reportes());
            assertEquals(2L, entradas.get(0).porCategoria().get(CategoriaDeReporte.ACOSO));
            assertEquals("poco", entradas.get(1).comentario().id());
        }
    }

    @Nested
    @DisplayName("Decidir (RF-COM-008)")
    class Decidir {

        @Test
        @DisplayName("ocultar conserva el registro y se puede deshacer; eliminar no")
        void ocultarNoEsEliminar() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);
            servicio.resolver("c-1", "mod-1", "moderadora", AccionDeModeracion.OCULTAR, "spam");

            // El registro sigue ahi: es lo que la ficha exige distinguir.
            assertTrue(filasDeComentarios.containsKey("c-1"),
                    "el registro sigue en la base: es lo que la ficha exige distinguir");

            ServicioDeModeracion.Resuelto vuelta = servicio.resolver(
                    "c-1", "mod-1", "moderadora", AccionDeModeracion.RESTAURAR, "era una cita");
            assertEquals(Comentario.Estado.PUBLICADO, vuelta.comentario().estado());

            servicio.resolver("c-1", "mod-1", "moderadora", AccionDeModeracion.ELIMINAR, "reincide");
            // De ELIMINADO no se vuelve: ese es el punto de ELIMINAR.
            assertThrows(ServicioDeModeracion.TransicionInvalida.class, () ->
                    servicio.resolver("c-1", "mod-1", "moderadora",
                            AccionDeModeracion.RESTAURAR, "me arrepenti"));
        }

        @Test
        @DisplayName("si otro moderador ya lo resolvio, el segundo recibe 409 y nada cambia (CA-03)")
        void carreraEntreModeradores() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);

            servicio.resolver("c-1", "mod-1", "primera", AccionDeModeracion.APROBAR, "es legitimo");

            // La segunda llega tarde: APROBAR solo vale desde EN_REVISION.
            assertThrows(ServicioDeModeracion.TransicionInvalida.class, () ->
                    servicio.resolver("c-1", "mod-2", "segunda",
                            AccionDeModeracion.APROBAR, "yo tambien lo veo bien"));

            assertEquals(1, filasDeAsientos.size(),
                    "el intento fallido no deja asiento: nada cambio");
        }

        @Test
        @DisplayName("el motivo es obligatorio, incluso para aprobar")
        void motivoObligatorio() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);

            assertThrows(ServicioDeModeracion.MotivoRequerido.class, () ->
                    servicio.resolver("c-1", "mod-1", "moderadora",
                            AccionDeModeracion.APROBAR, "   "));
        }

        @Test
        @DisplayName("un aviso que no sale no deshace la decision (HU-DIS-003)")
        void avisoFailOpen() {
            ServicioDeModeracion conAvisoCaido = new ServicioDeModeracion(
                    comentarios, reportes, asientos,
                    (c, a) -> false,
                    asiento -> { },
                    Clock.fixed(AHORA, ZoneOffset.UTC), 10);

            publicar("c-1", "autor-1");
            conAvisoCaido.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.ACOSO, null);

            ServicioDeModeracion.Resuelto resuelto = conAvisoCaido.resolver(
                    "c-1", "mod-1", "moderadora", AccionDeModeracion.OCULTAR, "acoso");

            // Un servicio de avisos caido no puede impedir que se retire un
            // comentario ofensivo. Pero tampoco se traga en silencio.
            assertEquals(Comentario.Estado.OCULTO, resuelto.comentario().estado());
            assertFalse(resuelto.autorNotificado());
        }

        @Test
        @DisplayName("el detalle trae el comentario, sus reportes y su historial")
        void detalle() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, "publicidad");
            servicio.resolver("c-1", "mod-1", "moderadora", AccionDeModeracion.OCULTAR, "spam");

            ServicioDeModeracion.Detalle detalle = servicio.detalle("c-1");
            assertEquals(Comentario.Estado.OCULTO, detalle.comentario().estado());
            assertEquals(1, detalle.reportes().size());
            assertEquals("publicidad", detalle.reportes().get(0).descripcion());
            assertEquals(1, detalle.historial().size());
            assertEquals(AccionDeModeracion.OCULTAR, detalle.historial().get(0).accion());
        }
    }

    @Nested
    @DisplayName("Las transiciones, en una tabla")
    class Transiciones {

        @Test
        @DisplayName("desde cada estado solo vale lo que tiene sentido")
        void tabla() {
            assertEquals(
                    java.util.Set.of(AccionDeModeracion.APROBAR, AccionDeModeracion.OCULTAR,
                            AccionDeModeracion.ELIMINAR),
                    AccionDeModeracion.desde(Comentario.Estado.EN_REVISION));

            assertEquals(
                    java.util.Set.of(AccionDeModeracion.OCULTAR, AccionDeModeracion.ELIMINAR),
                    AccionDeModeracion.desde(Comentario.Estado.PUBLICADO));

            assertEquals(
                    java.util.Set.of(AccionDeModeracion.RESTAURAR, AccionDeModeracion.ELIMINAR),
                    AccionDeModeracion.desde(Comentario.Estado.OCULTO));

            assertTrue(AccionDeModeracion.desde(Comentario.Estado.ELIMINADO).isEmpty(),
                    "ELIMINADO es terminal: no hay accion que valga desde ahi");
        }
    }

    // ----------------------------------------------------------- dobles simples
    //
    // Mockito respaldado por colecciones, no `when(...).thenReturn(...)` por
    // llamada. El flujo guarda y vuelve a leer varias veces, y un simulacro por
    // llamada acabaria probando el guion de la prueba en vez del comportamiento.
    // Implementar JpaRepository a mano tampoco: son treinta metodos que no
    // intervienen aqui y que cambian con cada version de Spring Data.

    private static ComentarioRepository comentariosEnMemoria(Map<String, RegistroDeComentario> datos) {
        ComentarioRepository repo = mock(ComentarioRepository.class);

        when(repo.save(any(RegistroDeComentario.class))).thenAnswer(inv -> {
            RegistroDeComentario r = inv.getArgument(0);
            datos.put(r.aDominio().id(), r);
            return r;
        });
        when(repo.findById(anyString())).thenAnswer(inv ->
                Optional.ofNullable(datos.get(inv.<String>getArgument(0))));
        when(repo.findByEstadoOrderByFechaPublicacionAsc(any())).thenAnswer(inv -> {
            Comentario.Estado estado = inv.getArgument(0);
            return datos.values().stream()
                    .filter(r -> r.aDominio().estado() == estado)
                    .sorted(Comparator.comparing(
                            (RegistroDeComentario r) -> r.aDominio().fechaPublicacion()))
                    .toList();
        });
        when(repo.findByEstadoAndProductoIdOrderByFechaPublicacionAsc(any(), anyString()))
                .thenAnswer(inv -> {
                    Comentario.Estado estado = inv.getArgument(0);
                    String producto = inv.getArgument(1);
                    return datos.values().stream()
                            .filter(r -> r.aDominio().estado() == estado)
                            .filter(r -> r.aDominio().productoId().equals(producto))
                            .sorted(Comparator.comparing(
                            (RegistroDeComentario r) -> r.aDominio().fechaPublicacion()))
                            .toList();
                });
        return repo;
    }

    private static ReporteRepository reportesEnMemoria(List<RegistroDeReporte> datos) {
        ReporteRepository repo = mock(ReporteRepository.class);

        when(repo.save(any(RegistroDeReporte.class))).thenAnswer(inv -> {
            RegistroDeReporte r = inv.getArgument(0);
            datos.add(r);
            return r;
        });
        when(repo.existsByComentarioIdAndReportanteId(anyString(), anyString())).thenAnswer(inv ->
                datos.stream().anyMatch(x -> x.comentarioId().equals(inv.getArgument(0))
                        && x.reportanteId().equals(inv.getArgument(1))));
        when(repo.findByComentarioIdOrderByFechaAsc(anyString())).thenAnswer(inv ->
                datos.stream().filter(x -> x.comentarioId().equals(inv.getArgument(0)))
                        .sorted(Comparator.comparing(RegistroDeReporte::fecha))
                        .toList());
        when(repo.countByComentarioId(anyString())).thenAnswer(inv ->
                datos.stream().filter(x -> x.comentarioId().equals(inv.getArgument(0))).count());
        when(repo.countByReportanteIdAndFechaAfter(anyString(), any())).thenAnswer(inv -> {
            String reportante = inv.getArgument(0);
            Instant desde = inv.getArgument(1);
            return datos.stream().filter(x -> x.reportanteId().equals(reportante)
                    && x.fecha().isAfter(desde)).count();
        });
        return repo;
    }

    private static AsientoRepository asientosEnMemoria(List<AsientoDeModeracion> datos) {
        AsientoRepository repo = mock(AsientoRepository.class);

        when(repo.save(any(AsientoDeModeracion.class))).thenAnswer(inv -> {
            AsientoDeModeracion a = inv.getArgument(0);
            datos.add(a);
            return a;
        });
        when(repo.findByComentarioIdOrderByFechaAsc(anyString())).thenAnswer(inv ->
                datos.stream().filter(x -> x.comentarioId().equals(inv.getArgument(0))).toList());
        return repo;
    }
}
