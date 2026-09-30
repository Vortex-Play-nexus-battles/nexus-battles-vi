package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * El flujo de moderacion completo — R10.1 (RF-COM-005, RF-COM-006, RF-COM-008)
 * y las acciones de B3 (EDITAR, MARCAR, DESMARCAR, 7.3.3).
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
    private static final String IP = "203.0.113.7";

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

        // El umbral nace en 0 = sin umbral: el PO aun no fija ningun valor.
        servicio = servicioConUmbral(0);
    }

    private ServicioDeModeracion servicioConUmbral(int umbral) {
        return new ServicioDeModeracion(
                comentarios, reportes, asientos,
                (comentario, asiento) -> {
                    avisos.add(comentario.autorId() + ":" + asiento.accion());
                    return true;
                },
                asiento -> auditados.add(asiento.comentarioId() + ":" + asiento.accion()),
                Clock.fixed(AHORA, ZoneOffset.UTC),
                3, umbral);
    }

    private Comentario publicar(String id, String autor) {
        Comentario c = new Comentario(id, PRODUCTO, autor, "apodo-" + autor,
                "texto", List.of(), AHORA.minusSeconds(3600),
                Comentario.Estado.PUBLICADO);
        comentarios.save(RegistroDeComentario.desde(c));
        return c;
    }

    private Comentario leido(String id) {
        return filasDeComentarios.get(id).aDominio();
    }

    private ServicioDeModeracion.Resuelto resolver(String id, AccionDeModeracion accion, String motivo) {
        return servicio.resolver(id, "mod-1", "moderadora", accion, motivo, null, IP);
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
        ServicioDeModeracion.Cola cola = servicio.cola(null, null, 0, 20);
        assertEquals(1, cola.total());
        assertEquals("c-1", cola.entradas().get(0).comentario().id());
        assertEquals(1, cola.entradas().get(0).reportes());
        assertEquals(1L, cola.entradas().get(0).porCategoria().get(CategoriaDeReporte.ACOSO));

        // 3. el moderador decide
        ServicioDeModeracion.Resuelto resuelto = resolver("c-1", AccionDeModeracion.OCULTAR, "acoso confirmado");

        // 4. SALE del estado en el que entro. Esto es el defecto que R10.1 cierra.
        assertEquals(Comentario.Estado.OCULTO, resuelto.comentario().estado());
        assertEquals(0, servicio.cola(null, null, 0, 20).total(),
                "resuelto es resuelto: deja de estar en la cola");

        // 5. queda el asiento, con las cinco cosas que pide la ficha y la IP (B3)
        AsientoDeModeracion asiento = resuelto.asiento();
        assertEquals("mod-1", asiento.moderadorId());
        assertEquals("moderadora", asiento.apodoModerador());
        assertEquals("acoso confirmado", asiento.motivo());
        assertEquals(Comentario.Estado.EN_REVISION, asiento.estadoAnterior());
        assertEquals(Comentario.Estado.OCULTO, asiento.estadoNuevo());
        assertEquals(AHORA, asiento.fecha());
        assertEquals(IP, asiento.ipOrigen());
        assertNull(asiento.textoAnterior(), "solo EDITAR registra textos");

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
        @DisplayName("sin categoria se rechaza como reporte invalido y no cambia nada")
        void sinCategoria() {
            publicar("c-1", "autor-1");

            assertThrows(ServicioDeModeracion.ReporteInvalido.class, () ->
                    servicio.reportar(PRODUCTO, "c-1", "jugador-a", null, "texto"));

            assertTrue(filasDeReportes.isEmpty(), "un reporte invalido no se guarda");
            assertEquals(Comentario.Estado.PUBLICADO, leido("c-1").estado(),
                    "y tampoco encola el comentario");
        }

        @Test
        @DisplayName("una descripcion de mas de 500 caracteres es invalida; de 500 exactos, valida")
        void descripcionLarga() {
            publicar("c-1", "autor-1");

            assertThrows(ServicioDeModeracion.ReporteInvalido.class, () ->
                    servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM,
                            "x".repeat(501)));
            assertTrue(filasDeReportes.isEmpty());
            assertEquals(Comentario.Estado.PUBLICADO, leido("c-1").estado());

            // El limite es inclusivo: la columna es de 500.
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, "x".repeat(500));
            assertEquals(1, filasDeReportes.size());
        }

        @Test
        @DisplayName("la descripcion es opcional: nula o en blanco queda null, y se recorta")
        void descripcionOpcional() {
            publicar("c-1", "autor-1");
            publicar("c-2", "autor-2");
            publicar("c-3", "autor-3");

            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);
            servicio.reportar(PRODUCTO, "c-2", "jugador-a", CategoriaDeReporte.SPAM, "   ");
            servicio.reportar(PRODUCTO, "c-3", "jugador-a", CategoriaDeReporte.SPAM, "  me insulta  ");

            assertNull(filasDeReportes.get(0).descripcion());
            assertNull(filasDeReportes.get(1).descripcion());
            assertEquals("me insulta", filasDeReportes.get(2).descripcion());
        }

        @Test
        @DisplayName("si el indice unico gana la carrera, es el mismo 409 y el comentario no se encola")
        void carreraDeDuplicados() {
            publicar("c-1", "autor-1");
            // doThrow(...).when(...): la forma when(...).thenThrow(...) ejecuta
            // primero el stub anterior con un argumento nulo y mete un null en la lista.
            doThrow(new DataIntegrityViolationException("uq_reporte_comentario_reportante"))
                    .when(reportes).saveAndFlush(any(RegistroDeReporte.class));

            assertThrows(ServicioDeModeracion.ReporteDuplicado.class, () ->
                    servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null));

            assertTrue(filasDeReportes.isEmpty());
            assertEquals(Comentario.Estado.PUBLICADO, leido("c-1").estado(),
                    "perder la carrera no deja el comentario a medias");
        }

        @Test
        @DisplayName("sin umbral configurado (0) ningun numero de reportes eleva la prioridad")
        void sinUmbralNuncaEleva() {
            publicar("c-1", "autor-1");

            ServicioDeModeracion.Reportado ultimo = null;
            for (String jugador : List.of("a", "b", "c", "d", "e")) {
                ultimo = servicio.reportar(PRODUCTO, "c-1", "jugador-" + jugador,
                        CategoriaDeReporte.SPAM, null);
                assertFalse(ultimo.prioridadElevada());
            }
            assertEquals(5, ultimo.totales());
        }

        @Test
        @DisplayName("al alcanzar el umbral el reporte eleva la prioridad, y sigue elevada con los siguientes")
        void alcanzarElUmbralEleva() {
            servicio = servicioConUmbral(2);
            publicar("c-1", "autor-1");

            ServicioDeModeracion.Reportado primero = servicio.reportar(
                    PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);
            ServicioDeModeracion.Reportado segundo = servicio.reportar(
                    PRODUCTO, "c-1", "jugador-b", CategoriaDeReporte.SPAM, null);
            ServicioDeModeracion.Reportado tercero = servicio.reportar(
                    PRODUCTO, "c-1", "jugador-c", CategoriaDeReporte.SPAM, null);

            assertFalse(primero.prioridadElevada(), "1 reporte < umbral 2");
            assertTrue(segundo.prioridadElevada(), "el umbral es inclusivo");
            assertTrue(tercero.prioridadElevada());
        }

        @Test
        @DisplayName("un reporte rechazado no cambia nada, tampoco la prioridad")
        void rechazadoNoCambiaLaPrioridad() {
            servicio = servicioConUmbral(2);
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);

            assertThrows(ServicioDeModeracion.ReporteDuplicado.class, () ->
                    servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.ACOSO, null));

            assertEquals(1, filasDeReportes.size());
            assertTrue(servicio.cola(null, null, 0, 20).entradas().stream()
                    .noneMatch(ServicioDeModeracion.Entrada::prioridadElevada));
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
            ServicioDeModeracion.Cola cola = servicio.cola(null, null, 0, 20);
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

            List<ServicioDeModeracion.Entrada> entradas = servicio.cola(null, null, 0, 20).entradas();
            assertEquals("mucho", entradas.get(0).comentario().id());
            assertEquals(3, entradas.get(0).reportes());
            assertEquals(2L, entradas.get(0).porCategoria().get(CategoriaDeReporte.ACOSO));
            assertEquals("poco", entradas.get(1).comentario().id());
        }

        @Test
        @DisplayName("la entrada lleva prioridadElevada al llegar al umbral, y las elevadas van antes")
        void prioridadElevadaEnLaCola() {
            servicio = servicioConUmbral(2);
            publicar("poco", "autor-1");
            publicar("mucho", "autor-2");

            servicio.reportar(PRODUCTO, "poco", "jugador-a", CategoriaDeReporte.SPAM, null);
            servicio.reportar(PRODUCTO, "mucho", "jugador-a", CategoriaDeReporte.ACOSO, null);
            servicio.reportar(PRODUCTO, "mucho", "jugador-b", CategoriaDeReporte.ACOSO, null);

            List<ServicioDeModeracion.Entrada> entradas = servicio.cola(null, null, 0, 20).entradas();
            assertEquals("mucho", entradas.get(0).comentario().id());
            assertTrue(entradas.get(0).prioridadElevada());
            assertEquals("poco", entradas.get(1).comentario().id());
            assertFalse(entradas.get(1).prioridadElevada());
        }

        @Test
        @DisplayName("sin umbral configurado ninguna entrada de la cola es de prioridad elevada")
        void colaSinUmbral() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);
            servicio.reportar(PRODUCTO, "c-1", "jugador-b", CategoriaDeReporte.SPAM, null);

            assertFalse(servicio.cola(null, null, 0, 20).entradas().get(0).prioridadElevada());
        }

        @Test
        @DisplayName("se puede acotar a un producto y paginar")
        void productoYPagina() {
            publicar("c-1", "autor-1");
            publicar("c-2", "autor-2");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);
            servicio.reportar(PRODUCTO, "c-2", "jugador-b", CategoriaDeReporte.SPAM, null);

            assertEquals(2, servicio.cola(PRODUCTO, null, 0, 20).total());
            assertEquals(0, servicio.cola("otro", null, 0, 20).total());
            ServicioDeModeracion.Cola segunda = servicio.cola(PRODUCTO, null, 1, 1);
            assertEquals(1, segunda.entradas().size());
            assertEquals(2, segunda.total());
        }

        @Test
        @DisplayName("marcado=true es la lista de seguimiento: marcados en cualquier estado salvo ELIMINADO (7.3.3)")
        void seguimiento() {
            publicar("publicado-marcado", "autor-1");
            publicar("en-revision", "autor-2");
            publicar("eliminado-marcado", "autor-3");
            resolver("publicado-marcado", AccionDeModeracion.MARCAR, "vigilar a este autor");
            servicio.reportar(PRODUCTO, "en-revision", "jugador-a", CategoriaDeReporte.SPAM, null);
            resolver("eliminado-marcado", AccionDeModeracion.MARCAR, "vigilar");
            resolver("eliminado-marcado", AccionDeModeracion.ELIMINAR, "reincide");

            assertEquals(List.of("publicado-marcado"), ids(servicio.cola(null, true, 0, 20)));
            assertEquals(List.of("publicado-marcado"), ids(servicio.cola(PRODUCTO, true, 0, 20)));
            assertEquals(List.of("en-revision"), ids(servicio.cola(null, false, 0, 20)),
                    "marcado=false son los EN_REVISION sin marcar");
            assertEquals(List.of("en-revision"), ids(servicio.cola(PRODUCTO, false, 0, 20)));
            assertEquals(List.of("en-revision"), ids(servicio.cola(null, null, 0, 20)),
                    "sin filtro, la cola de siempre");
        }

        private List<String> ids(ServicioDeModeracion.Cola cola) {
            return cola.entradas().stream().map(e -> e.comentario().id()).toList();
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
            resolver("c-1", AccionDeModeracion.OCULTAR, "spam");

            // El registro sigue ahi: es lo que la ficha exige distinguir.
            assertTrue(filasDeComentarios.containsKey("c-1"),
                    "el registro sigue en la base: es lo que la ficha exige distinguir");

            ServicioDeModeracion.Resuelto vuelta = resolver("c-1", AccionDeModeracion.RESTAURAR, "era una cita");
            assertEquals(Comentario.Estado.PUBLICADO, vuelta.comentario().estado());

            resolver("c-1", AccionDeModeracion.ELIMINAR, "reincide");
            // De ELIMINADO no se vuelve: ese es el punto de ELIMINAR.
            assertThrows(ServicioDeModeracion.TransicionInvalida.class, () ->
                    resolver("c-1", AccionDeModeracion.RESTAURAR, "me arrepenti"));
        }

        @Test
        @DisplayName("si otro moderador ya lo resolvio, el segundo recibe 409 y nada cambia (CA-03)")
        void carreraEntreModeradores() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);

            servicio.resolver("c-1", "mod-1", "primera", AccionDeModeracion.APROBAR, "es legitimo", null, IP);

            // La segunda llega tarde: APROBAR solo vale desde EN_REVISION.
            assertThrows(ServicioDeModeracion.TransicionInvalida.class, () ->
                    servicio.resolver("c-1", "mod-2", "segunda",
                            AccionDeModeracion.APROBAR, "yo tambien lo veo bien", null, IP));

            assertEquals(1, filasDeAsientos.size(),
                    "el intento fallido no deja asiento: nada cambio");
        }

        @Test
        @DisplayName("el motivo es obligatorio, incluso para aprobar, y va de 3 a 500 caracteres")
        void motivoObligatorio() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);

            assertThrows(ServicioDeModeracion.MotivoRequerido.class, () ->
                    resolver("c-1", AccionDeModeracion.APROBAR, "   "));
            assertThrows(ServicioDeModeracion.MotivoRequerido.class, () ->
                    resolver("c-1", AccionDeModeracion.APROBAR, null));
            assertThrows(ServicioDeModeracion.MotivoRequerido.class, () ->
                    resolver("c-1", AccionDeModeracion.APROBAR, "ok"));
            assertThrows(ServicioDeModeracion.MotivoRequerido.class, () ->
                    resolver("c-1", AccionDeModeracion.APROBAR, "x".repeat(501)));
            assertThrows(ServicioDeModeracion.DecisionIncompleta.class, () ->
                    resolver("c-1", null, "sin accion"));
            assertTrue(filasDeAsientos.isEmpty());
        }

        @Test
        @DisplayName("un aviso que no sale no deshace la decision (HU-DIS-003)")
        void avisoFailOpen() {
            ServicioDeModeracion conAvisoCaido = new ServicioDeModeracion(
                    comentarios, reportes, asientos,
                    (c, a) -> false,
                    asiento -> { },
                    Clock.fixed(AHORA, ZoneOffset.UTC), 10, 0);

            publicar("c-1", "autor-1");
            conAvisoCaido.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.ACOSO, null);

            ServicioDeModeracion.Resuelto resuelto = conAvisoCaido.resolver(
                    "c-1", "mod-1", "moderadora", AccionDeModeracion.OCULTAR, "acoso", null, null);

            // Un servicio de avisos caido no puede impedir que se retire un
            // comentario ofensivo. Pero tampoco se traga en silencio.
            assertEquals(Comentario.Estado.OCULTO, resuelto.comentario().estado());
            assertFalse(resuelto.autorNotificado());
            assertNull(resuelto.asiento().ipOrigen(), "sin IP conocida no se inventa una");
        }

        @Test
        @DisplayName("el detalle trae el comentario, sus reportes y su historial")
        void detalle() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, "publicidad");
            resolver("c-1", AccionDeModeracion.OCULTAR, "spam");

            ServicioDeModeracion.Detalle detalle = servicio.detalle("c-1");
            assertEquals(Comentario.Estado.OCULTO, detalle.comentario().estado());
            assertEquals(1, detalle.reportes().size());
            assertEquals("publicidad", detalle.reportes().get(0).descripcion());
            assertEquals(1, detalle.historial().size());
            assertEquals(AccionDeModeracion.OCULTAR, detalle.historial().get(0).accion());
        }
    }

    @Nested
    @DisplayName("Editar y marcar (B3, 7.3.3)")
    class EditarYMarcar {

        @Test
        @DisplayName("EDITAR cambia el texto, lo deja editado, no mueve el estado y registra antes y despues")
        void editar() {
            publicar("c-1", "autor-1");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.CONTENIDO_OFENSIVO, null);

            ServicioDeModeracion.Resuelto resuelto = servicio.resolver("c-1", "mod-1", "moderadora",
                    AccionDeModeracion.EDITAR, "quitar el insulto", "texto sin el insulto", IP);

            Comentario guardado = leido("c-1");
            assertEquals("texto sin el insulto", guardado.texto());
            assertTrue(guardado.editado());
            assertEquals(Comentario.Estado.EN_REVISION, guardado.estado(),
                    "editar no aprueba: el moderador decide aparte");

            AsientoDeModeracion asiento = resuelto.asiento();
            assertEquals(AccionDeModeracion.EDITAR, asiento.accion());
            assertEquals("texto", asiento.textoAnterior());
            assertEquals("texto sin el insulto", asiento.textoNuevo());
            assertEquals(Comentario.Estado.EN_REVISION, asiento.estadoAnterior());
            assertEquals(Comentario.Estado.EN_REVISION, asiento.estadoNuevo());
            assertEquals(IP, asiento.ipOrigen());
            assertEquals(List.of("autor-1:EDITAR"), avisos, "al autor se le dice que su texto cambio");
        }

        @Test
        @DisplayName("EDITAR sin texto nuevo, en blanco o de mas de 2000 caracteres es 400 y nada cambia")
        void editarSinTexto() {
            publicar("c-1", "autor-1");

            assertThrows(ServicioDeModeracion.DecisionIncompleta.class, () -> servicio.resolver("c-1",
                    "mod-1", "moderadora", AccionDeModeracion.EDITAR, "quitar insulto", null, IP));
            assertThrows(ServicioDeModeracion.DecisionIncompleta.class, () -> servicio.resolver("c-1",
                    "mod-1", "moderadora", AccionDeModeracion.EDITAR, "quitar insulto", "  ", IP));
            assertThrows(ServicioDeModeracion.DecisionIncompleta.class, () -> servicio.resolver("c-1",
                    "mod-1", "moderadora", AccionDeModeracion.EDITAR, "quitar insulto", "x".repeat(2001), IP));

            assertEquals("texto", leido("c-1").texto());
            assertTrue(filasDeAsientos.isEmpty());
        }

        @Test
        @DisplayName("en las demas acciones textoNuevo se ignora: ocultar no cambia el texto")
        void textoNuevoIgnorado() {
            publicar("c-1", "autor-1");
            servicio.resolver("c-1", "mod-1", "moderadora", AccionDeModeracion.OCULTAR, "spam", "otro", IP);

            assertEquals("texto", leido("c-1").texto());
            assertFalse(leido("c-1").editado());
        }

        @Test
        @DisplayName("un ELIMINADO no se edita ni se marca: es terminal")
        void eliminadoEsTerminal() {
            publicar("c-1", "autor-1");
            resolver("c-1", AccionDeModeracion.ELIMINAR, "reincide");

            assertThrows(ServicioDeModeracion.TransicionInvalida.class, () -> servicio.resolver("c-1",
                    "mod-1", "moderadora", AccionDeModeracion.EDITAR, "motivo", "nuevo", IP));
            assertThrows(ServicioDeModeracion.TransicionInvalida.class, () ->
                    resolver("c-1", AccionDeModeracion.MARCAR, "vigilar"));
        }

        @Test
        @DisplayName("MARCAR y DESMARCAR cambian la marca, no el estado, dejan asiento y no avisan al autor")
        void marcarYDesmarcar() {
            publicar("c-1", "autor-1");

            ServicioDeModeracion.Resuelto marcado = resolver("c-1", AccionDeModeracion.MARCAR, "seguimiento");
            assertTrue(leido("c-1").marcado());
            assertEquals(Comentario.Estado.PUBLICADO, leido("c-1").estado());
            assertFalse(marcado.autorNotificado(), "es una nota interna: el autor no se entera");

            // Marcar dos veces es lo mismo que aprobar dos veces: otro se adelanto.
            ServicioDeModeracion.TransicionInvalida doble = assertThrows(
                    ServicioDeModeracion.TransicionInvalida.class,
                    () -> resolver("c-1", AccionDeModeracion.MARCAR, "otra vez"));
            assertTrue(doble.getMessage().contains("ya esta marcado"));

            resolver("c-1", AccionDeModeracion.DESMARCAR, "ya no hace falta");
            assertFalse(leido("c-1").marcado());
            ServicioDeModeracion.TransicionInvalida sinMarca = assertThrows(
                    ServicioDeModeracion.TransicionInvalida.class,
                    () -> resolver("c-1", AccionDeModeracion.DESMARCAR, "otra vez"));
            assertTrue(sinMarca.getMessage().contains("no esta marcado"));

            assertTrue(avisos.isEmpty());
            assertEquals(List.of("c-1:MARCAR", "c-1:DESMARCAR"), auditados, "pero si quedan auditadas");
            assertEquals(2, filasDeAsientos.size());
        }

        @Test
        @DisplayName("la marca sobrevive a las decisiones: un marcado que se aprueba sigue marcado")
        void laMarcaSobrevive() {
            publicar("c-1", "autor-1");
            resolver("c-1", AccionDeModeracion.MARCAR, "seguimiento");
            servicio.reportar(PRODUCTO, "c-1", "jugador-a", CategoriaDeReporte.SPAM, null);
            resolver("c-1", AccionDeModeracion.APROBAR, "no es spam");

            assertTrue(leido("c-1").marcado());
            assertEquals(Comentario.Estado.PUBLICADO, leido("c-1").estado());
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
                            AccionDeModeracion.ELIMINAR, AccionDeModeracion.EDITAR,
                            AccionDeModeracion.MARCAR, AccionDeModeracion.DESMARCAR),
                    AccionDeModeracion.desde(Comentario.Estado.EN_REVISION));

            assertEquals(
                    java.util.Set.of(AccionDeModeracion.OCULTAR, AccionDeModeracion.ELIMINAR,
                            AccionDeModeracion.EDITAR, AccionDeModeracion.MARCAR, AccionDeModeracion.DESMARCAR),
                    AccionDeModeracion.desde(Comentario.Estado.PUBLICADO));

            assertEquals(
                    java.util.Set.of(AccionDeModeracion.RESTAURAR, AccionDeModeracion.ELIMINAR,
                            AccionDeModeracion.EDITAR, AccionDeModeracion.MARCAR, AccionDeModeracion.DESMARCAR),
                    AccionDeModeracion.desde(Comentario.Estado.OCULTO));

            assertTrue(AccionDeModeracion.desde(Comentario.Estado.ELIMINADO).isEmpty(),
                    "ELIMINADO es terminal: no hay accion que valga desde ahi");
        }

        @Test
        @DisplayName("solo las decisiones sobre el comentario se avisan; la marca es interna")
        void avisos() {
            for (AccionDeModeracion accion : AccionDeModeracion.values()) {
                boolean esMarca = accion == AccionDeModeracion.MARCAR || accion == AccionDeModeracion.DESMARCAR;
                assertEquals(!esMarca, accion.seAvisaAlAutor(), accion.name());
            }
        }

        @Test
        @DisplayName("las que no cambian el estado lo conservan; las demas van a su destino")
        void destinos() {
            assertEquals(Comentario.Estado.OCULTO,
                    AccionDeModeracion.EDITAR.destinoDesde(Comentario.Estado.OCULTO));
            assertEquals(Comentario.Estado.EN_REVISION,
                    AccionDeModeracion.MARCAR.destinoDesde(Comentario.Estado.EN_REVISION));
            assertEquals(Comentario.Estado.PUBLICADO,
                    AccionDeModeracion.RESTAURAR.destinoDesde(Comentario.Estado.OCULTO));
        }
    }

    // ----------------------------------------------------------- dobles simples
    //
    // Mockito respaldado por colecciones, no `when(...).thenReturn(...)` por
    // llamada. El flujo guarda y vuelve a leer varias veces, y un simulacro por
    // llamada acabaria probando el guion de la prueba en vez del comportamiento.
    // Implementar JpaRepository a mano tampoco: son treinta metodos que no
    // intervienen aqui y que cambian con cada version de Spring Data.

    private static List<RegistroDeComentario> filtrados(
            Map<String, RegistroDeComentario> datos, Predicate<Comentario> condicion) {
        return datos.values().stream()
                .filter(r -> condicion.test(r.aDominio()))
                .sorted(Comparator.comparing((RegistroDeComentario r) -> r.aDominio().fechaPublicacion()))
                .toList();
    }

    @SuppressWarnings("unchecked")
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
            return filtrados(datos, c -> c.estado() == estado);
        });
        when(repo.findByEstadoAndProductoIdOrderByFechaPublicacionAsc(any(), anyString())).thenAnswer(inv -> {
            Comentario.Estado estado = inv.getArgument(0);
            String producto = inv.getArgument(1);
            return filtrados(datos, c -> c.estado() == estado && c.productoId().equals(producto));
        });
        when(repo.findByEstadoAndMarcadoOrderByFechaPublicacionAsc(any(), anyBoolean())).thenAnswer(inv -> {
            Comentario.Estado estado = inv.getArgument(0);
            boolean marcado = inv.getArgument(1);
            return filtrados(datos, c -> c.estado() == estado && c.marcado() == marcado);
        });
        when(repo.findByEstadoAndMarcadoAndProductoIdOrderByFechaPublicacionAsc(any(), anyBoolean(), anyString()))
                .thenAnswer(inv -> {
                    Comentario.Estado estado = inv.getArgument(0);
                    boolean marcado = inv.getArgument(1);
                    String producto = inv.getArgument(2);
                    return filtrados(datos, c -> c.estado() == estado && c.marcado() == marcado
                            && c.productoId().equals(producto));
                });
        when(repo.findByMarcadoTrueAndEstadoInOrderByFechaPublicacionAsc(anyCollection())).thenAnswer(inv -> {
            Collection<Comentario.Estado> estados = inv.getArgument(0);
            return filtrados(datos, c -> c.marcado() && estados.contains(c.estado()));
        });
        when(repo.findByMarcadoTrueAndEstadoInAndProductoIdOrderByFechaPublicacionAsc(anyCollection(), anyString()))
                .thenAnswer(inv -> {
                    Collection<Comentario.Estado> estados = inv.getArgument(0);
                    String producto = inv.getArgument(1);
                    return filtrados(datos, c -> c.marcado() && estados.contains(c.estado())
                            && c.productoId().equals(producto));
                });
        return repo;
    }

    private static ReporteRepository reportesEnMemoria(List<RegistroDeReporte> datos) {
        ReporteRepository repo = mock(ReporteRepository.class);

        when(repo.saveAndFlush(any(RegistroDeReporte.class))).thenAnswer(inv -> {
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
        when(repo.findByComentarioIdInOrderByFechaAsc(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return datos.stream().filter(x -> ids.contains(x.comentarioId()))
                    .sorted(Comparator.comparing(RegistroDeReporte::fecha))
                    .toList();
        });
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
