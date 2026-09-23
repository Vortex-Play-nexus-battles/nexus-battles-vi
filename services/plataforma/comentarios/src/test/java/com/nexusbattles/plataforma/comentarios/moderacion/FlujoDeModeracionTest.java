package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
 * <p>Los repositorios son dobles en memoria y no simulacros con {@code when}:
 * el flujo guarda y vuelve a leer varias veces, y un simulacro por llamada
 * acabaria probando el guion de la prueba en vez del comportamiento.
 */
@DisplayName("R10.1: el comentario en revision tiene salida")
class FlujoDeModeracionTest {

    private static final Instant AHORA = Instant.parse("2026-09-23T10:00:00Z");
    private static final String PRODUCTO = "prod-1";

    private ComentariosEnMemoria comentarios;
    private ReportesEnMemoria reportes;
    private AsientosEnMemoria asientos;
    private List<String> avisos;
    private List<String> auditados;
    private ServicioDeModeracion servicio;

    @BeforeEach
    void montar() {
        comentarios = new ComentariosEnMemoria();
        reportes = new ReportesEnMemoria();
        asientos = new AsientosEnMemoria();
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
            assertTrue(comentarios.findById("c-1").isPresent());

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

            assertEquals(1, asientos.findByComentarioIdOrderByFechaAsc("c-1").size(),
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

    private static class ComentariosEnMemoria implements ComentarioRepository {
        private final Map<String, RegistroDeComentario> datos = new HashMap<>();

        @Override
        public Optional<RegistroDeComentario> findById(String id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public <S extends RegistroDeComentario> S save(S entidad) {
            datos.put(entidad.aDominio().id(), entidad);
            return entidad;
        }

        @Override
        public List<RegistroDeComentario> findByEstadoOrderByFechaPublicacionAsc(
                Comentario.Estado estado) {
            return datos.values().stream()
                    .filter(r -> r.aDominio().estado() == estado)
                    .sorted((a, b) -> a.aDominio().fechaPublicacion()
                            .compareTo(b.aDominio().fechaPublicacion()))
                    .toList();
        }

        @Override
        public List<RegistroDeComentario> findByEstadoAndProductoIdOrderByFechaPublicacionAsc(
                Comentario.Estado estado, String productoId) {
            return findByEstadoOrderByFechaPublicacionAsc(estado).stream()
                    .filter(r -> r.aDominio().productoId().equals(productoId))
                    .toList();
        }

        @Override
        public List<RegistroDeComentario> findByProductoIdOrderByFechaPublicacionAsc(String p) {
            throw new UnsupportedOperationException("no lo usa el flujo de moderacion");
        }

        // El resto de JpaRepository no interviene en este flujo.
        @Override public void flush() { }
        @Override public <S extends RegistroDeComentario> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends RegistroDeComentario> List<S> saveAllAndFlush(Iterable<S> e) {
            throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<RegistroDeComentario> e) { }
        @Override public void deleteAllByIdInBatch(Iterable<String> i) { }
        @Override public void deleteAllInBatch() { }
        @Override public RegistroDeComentario getOne(String id) { return datos.get(id); }
        @Override public RegistroDeComentario getById(String id) { return datos.get(id); }
        @Override public RegistroDeComentario getReferenceById(String id) { return datos.get(id); }
        @Override public <S extends RegistroDeComentario> List<S> findAll(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeComentario> List<S> findAll(
                org.springframework.data.domain.Example<S> e,
                org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeComentario> List<S> saveAll(Iterable<S> e) {
            throw new UnsupportedOperationException(); }
        @Override public List<RegistroDeComentario> findAll() { return List.copyOf(datos.values()); }
        @Override public List<RegistroDeComentario> findAllById(Iterable<String> i) {
            throw new UnsupportedOperationException(); }
        @Override public List<RegistroDeComentario> findAll(
                org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<RegistroDeComentario> findAll(
                org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(String id) { return datos.containsKey(id); }
        @Override public long count() { return datos.size(); }
        @Override public void deleteById(String id) { datos.remove(id); }
        @Override public void delete(RegistroDeComentario e) { }
        @Override public void deleteAllById(Iterable<? extends String> i) { }
        @Override public void deleteAll(Iterable<? extends RegistroDeComentario> e) { }
        @Override public void deleteAll() { datos.clear(); }
        @Override public <S extends RegistroDeComentario> Optional<S> findOne(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeComentario> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> e,
                org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeComentario> long count(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeComentario> boolean exists(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeComentario, R> R findBy(
                org.springframework.data.domain.Example<S> e,
                java.util.function.Function<org.springframework.data.repository.query.FluentQuery
                        .FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }

    private static class ReportesEnMemoria implements ReporteRepository {
        private final List<RegistroDeReporte> datos = new ArrayList<>();

        @Override
        public boolean existsByComentarioIdAndReportanteId(String c, String r) {
            return datos.stream().anyMatch(x ->
                    x.comentarioId().equals(c) && x.reportanteId().equals(r));
        }

        @Override
        public List<RegistroDeReporte> findByComentarioIdOrderByFechaAsc(String c) {
            return datos.stream().filter(x -> x.comentarioId().equals(c))
                    .sorted((a, b) -> a.fecha().compareTo(b.fecha())).toList();
        }

        @Override
        public long countByComentarioId(String c) {
            return datos.stream().filter(x -> x.comentarioId().equals(c)).count();
        }

        @Override
        public long countByReportanteIdAndFechaAfter(String r, Instant desde) {
            return datos.stream().filter(x -> x.reportanteId().equals(r)
                    && x.fecha().isAfter(desde)).count();
        }

        @Override
        public <S extends RegistroDeReporte> S save(S e) {
            datos.add(e);
            return e;
        }

        @Override public void flush() { }
        @Override public <S extends RegistroDeReporte> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends RegistroDeReporte> List<S> saveAllAndFlush(Iterable<S> e) {
            throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<RegistroDeReporte> e) { }
        @Override public void deleteAllByIdInBatch(Iterable<String> i) { }
        @Override public void deleteAllInBatch() { }
        @Override public RegistroDeReporte getOne(String id) { throw new UnsupportedOperationException(); }
        @Override public RegistroDeReporte getById(String id) { throw new UnsupportedOperationException(); }
        @Override public RegistroDeReporte getReferenceById(String id) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeReporte> List<S> findAll(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeReporte> List<S> findAll(
                org.springframework.data.domain.Example<S> e,
                org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeReporte> List<S> saveAll(Iterable<S> e) {
            throw new UnsupportedOperationException(); }
        @Override public List<RegistroDeReporte> findAll() { return List.copyOf(datos); }
        @Override public List<RegistroDeReporte> findAllById(Iterable<String> i) {
            throw new UnsupportedOperationException(); }
        @Override public List<RegistroDeReporte> findAll(
                org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<RegistroDeReporte> findAll(
                org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public Optional<RegistroDeReporte> findById(String id) {
            return datos.stream().filter(x -> x.id().equals(id)).findFirst(); }
        @Override public boolean existsById(String id) { return findById(id).isPresent(); }
        @Override public long count() { return datos.size(); }
        @Override public void deleteById(String id) { }
        @Override public void delete(RegistroDeReporte e) { }
        @Override public void deleteAllById(Iterable<? extends String> i) { }
        @Override public void deleteAll(Iterable<? extends RegistroDeReporte> e) { }
        @Override public void deleteAll() { datos.clear(); }
        @Override public <S extends RegistroDeReporte> Optional<S> findOne(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeReporte> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> e,
                org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeReporte> long count(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeReporte> boolean exists(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends RegistroDeReporte, R> R findBy(
                org.springframework.data.domain.Example<S> e,
                java.util.function.Function<org.springframework.data.repository.query.FluentQuery
                        .FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }

    private static class AsientosEnMemoria implements AsientoRepository {
        private final List<AsientoDeModeracion> datos = new ArrayList<>();

        @Override
        public List<AsientoDeModeracion> findByComentarioIdOrderByFechaAsc(String c) {
            return datos.stream().filter(x -> x.comentarioId().equals(c)).toList();
        }

        @Override
        public <S extends AsientoDeModeracion> S save(S e) {
            datos.add(e);
            return e;
        }

        @Override public void flush() { }
        @Override public <S extends AsientoDeModeracion> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends AsientoDeModeracion> List<S> saveAllAndFlush(Iterable<S> e) {
            throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<AsientoDeModeracion> e) { }
        @Override public void deleteAllByIdInBatch(Iterable<String> i) { }
        @Override public void deleteAllInBatch() { }
        @Override public AsientoDeModeracion getOne(String id) { throw new UnsupportedOperationException(); }
        @Override public AsientoDeModeracion getById(String id) { throw new UnsupportedOperationException(); }
        @Override public AsientoDeModeracion getReferenceById(String id) { throw new UnsupportedOperationException(); }
        @Override public <S extends AsientoDeModeracion> List<S> findAll(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends AsientoDeModeracion> List<S> findAll(
                org.springframework.data.domain.Example<S> e,
                org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends AsientoDeModeracion> List<S> saveAll(Iterable<S> e) {
            throw new UnsupportedOperationException(); }
        @Override public List<AsientoDeModeracion> findAll() { return List.copyOf(datos); }
        @Override public List<AsientoDeModeracion> findAllById(Iterable<String> i) {
            throw new UnsupportedOperationException(); }
        @Override public List<AsientoDeModeracion> findAll(
                org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<AsientoDeModeracion> findAll(
                org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public Optional<AsientoDeModeracion> findById(String id) {
            return datos.stream().filter(x -> x.id().equals(id)).findFirst(); }
        @Override public boolean existsById(String id) { return findById(id).isPresent(); }
        @Override public long count() { return datos.size(); }
        @Override public void deleteById(String id) { }
        @Override public void delete(AsientoDeModeracion e) { }
        @Override public void deleteAllById(Iterable<? extends String> i) { }
        @Override public void deleteAll(Iterable<? extends AsientoDeModeracion> e) { }
        @Override public void deleteAll() { datos.clear(); }
        @Override public <S extends AsientoDeModeracion> Optional<S> findOne(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends AsientoDeModeracion> org.springframework.data.domain.Page<S> findAll(
                org.springframework.data.domain.Example<S> e,
                org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends AsientoDeModeracion> long count(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends AsientoDeModeracion> boolean exists(
                org.springframework.data.domain.Example<S> e) { throw new UnsupportedOperationException(); }
        @Override public <S extends AsientoDeModeracion, R> R findBy(
                org.springframework.data.domain.Example<S> e,
                java.util.function.Function<org.springframework.data.repository.query.FluentQuery
                        .FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
    }

    /** Para que el UUID de los ids no moleste en las aserciones. */
    @SuppressWarnings("unused")
    private static String id() {
        return UUID.randomUUID().toString();
    }
}
