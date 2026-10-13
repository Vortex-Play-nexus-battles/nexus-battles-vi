package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.moderacion.AccionDeModeracion;
import com.nexusbattles.plataforma.comentarios.moderacion.AsientoDeModeracion;
import com.nexusbattles.plataforma.comentarios.moderacion.AsientoRepository;
import com.nexusbattles.plataforma.comentarios.moderacion.AvisoAlAutor;
import com.nexusbattles.plataforma.comentarios.moderacion.RegistroDeAuditoria;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * La decision en lote contra PostgreSQL de verdad — HU-COM-005 (#519), contrato 1.10.0.
 *
 * <p>Lo que solo se comprueba aqui, y no con los dobles de
 * {@code FlujoDeModeracionTest}: que el lote persiste estados y asientos en el
 * esquema {@code comentarios}, que un lote con un comentario invalido deja la
 * base intacta y, sobre todo, que cuando la escritura falla A MEDIAS la
 * transaccion se revierte de verdad: lo que ya se habia enviado a la base
 * desaparece, y no se avisa ni se audita.
 *
 * <h2>Por que {@code saveAndFlush} y no {@code save}</h2>
 *
 * Con el id asignado a mano, {@code save} hace un {@code merge}: el INSERT del
 * asiento y el UPDATE del comentario se difieren al flush, que llega al
 * commit. Si la escritura fallase antes del commit, no se habria enviado nada
 * y la prueba de reversion no probaria nada. {@link #saveDifiereYSaveAndFlushEnvia}
 * deja esa premisa comprobada con las estadisticas de Hibernate, y
 * {@link #rollbackRealSiFallaLaEscrituraAMedias} comprueba ademas que, cuando el
 * asiento falla, lo previo ya habia salido a la base.
 *
 * <p>Requiere Docker (Testcontainers).
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.generate_statistics=true",
                "comentarios.imagenes.limpieza-activa=false"
        })
@DisplayName("HU-COM-005: la decision en lote contra PostgreSQL")
class DecisionEnLoteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    static VecinosDePrueba vecinos;

    @BeforeAll
    static void levantarVecinos() throws Exception {
        vecinos = VecinosDePrueba.arrancar();
    }

    @AfterAll
    static void apagarVecinos() {
        if (vecinos != null) {
            vecinos.close();
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
        vecinos.registrar(registro);
    }

    private static final UUID UID_MODERADORA = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String RUTA_LOTE = "/api/v1/comentarios/moderacion/decisiones-en-lote";

    @LocalServerPort
    private int puerto;

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ComentarioRepository comentarios;

    @Autowired
    private TransactionTemplate transaccion;

    /** Espia del repositorio real: solo IT-c le pone comportamiento (se limpia tras cada test). */
    @MockitoSpyBean
    private AsientoRepository asientosEspiados;

    /** Las dos salidas del flujo son interfaces que hoy se publican como lambdas: se sustituyen, no se espian. */
    @MockitoBean
    private AvisoAlAutor aviso;

    @MockitoBean
    private RegistroDeAuditoria auditoria;

    private final HttpClient http = HttpClient.newHttpClient();
    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();
    private Statistics estadisticas;

    @BeforeEach
    void prepararEstadisticas() {
        estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
    }

    // ---------------------------------------------------------------- utilidades

    private String moderadora() {
        return "Bearer " + emisor.tokenDeUsuario("AdaLaJusta", UID_MODERADORA, "MODERADOR");
    }

    /** Inserta por SQL: la siembra no pasa por el repositorio ni por el contexto de persistencia. */
    private String sembrar(String estado) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into comentarios.comentarios
                    (id, producto_id, autor_id, apodo_autor, texto, fecha_publicacion, estado)
                values (?, ?, ?, ?, ?, now(), ?)
                """, id, "producto-lote-it", "autor-" + id.substring(0, 8), "ApodoIt", "texto de prueba", estado);
        return id;
    }

    private String estadoDe(String comentarioId) {
        return jdbc.queryForObject("select estado from comentarios.comentarios where id = ?",
                String.class, comentarioId);
    }

    private int asientosDe(String comentarioId) {
        Integer filas = jdbc.queryForObject(
                "select count(*) from comentarios.comentario_moderacion where comentario_id = ?",
                Integer.class, comentarioId);
        return filas == null ? -1 : filas;
    }

    private static String cuerpo(List<String> ids, String accion, String motivo) {
        StringBuilder json = new StringBuilder("{\"comentarioIds\":[");
        for (int i = 0; i < ids.size(); i++) {
            json.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append('"');
        }
        return json.append("],\"accion\":\"").append(accion)
                .append("\",\"motivo\":\"").append(motivo).append("\"}").toString();
    }

    private HttpResponse<String> decidirEnLote(String json) throws IOException, InterruptedException {
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + RUTA_LOTE))
                .header("Content-Type", "application/json")
                .header("Authorization", moderadora())
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private static <T> T leer(HttpResponse<String> respuesta, String ruta) {
        return JsonPath.read(respuesta.body(), ruta);
    }

    private static AsientoDeModeracion asiento(String comentarioId) {
        return new AsientoDeModeracion(UUID.randomUUID().toString(), comentarioId, UID_MODERADORA.toString(),
                "AdaLaJusta", AccionDeModeracion.OCULTAR, "prueba de premisa",
                Comentario.Estado.PUBLICADO, Comentario.Estado.OCULTO, Instant.now());
    }

    // ------------------------------------------------------------------ IT-a, b, c

    @Test
    @DisplayName("IT-a: un lote exitoso persiste los estados y un asiento por comentario, en el orden recibido")
    void loteExitosoPersiste() throws Exception {
        String c1 = sembrar("PUBLICADO");
        String c2 = sembrar("PUBLICADO");
        String c3 = sembrar("PUBLICADO");
        when(aviso.notificar(any(), any())).thenReturn(true);

        HttpResponse<String> r = decidirEnLote(cuerpo(List.of(c3, c1, c2), "OCULTAR", "spam coordinado"));

        assertEquals(200, r.statusCode(), r.body());
        assertEquals("OCULTAR", leer(r, "$.accion"));
        assertEquals(3, (Integer) leer(r, "$.total"));
        // El orden es el de la lista recibida, no el que devuelva findAllById.
        assertEquals(c3, leer(r, "$.resultados[0].comentarioId"));
        assertEquals(c1, leer(r, "$.resultados[1].comentarioId"));
        assertEquals(c2, leer(r, "$.resultados[2].comentarioId"));
        for (String id : List.of(c1, c2, c3)) {
            assertEquals("OCULTO", estadoDe(id), id);
            assertEquals(1, asientosDe(id), "un asiento por comentario: " + id);
        }
        assertEquals(3, jdbc.queryForObject("""
                select count(*) from comentarios.comentario_moderacion
                where comentario_id in (?, ?, ?) and moderador_id = ? and accion = 'OCULTAR'
                  and estado_anterior = 'PUBLICADO' and estado_nuevo = 'OCULTO' and motivo = 'spam coordinado'
                """, Integer.class, c1, c2, c3, UID_MODERADORA.toString()),
                "los tres asientos llevan al moderador del token y las cinco cosas que pide RF-COM-008");
        verify(aviso, times(3)).notificar(any(), any());
        verify(auditoria, times(3)).registrar(any());
    }

    @Test
    @DisplayName("IT-b: un lote con un comentario invalido es 409 con sus ids y deja la base intacta")
    void loteConUnInvalidoDejaLaBaseIntacta() throws Exception {
        String c1 = sembrar("PUBLICADO");
        String eliminado = sembrar("ELIMINADO");
        String c3 = sembrar("PUBLICADO");

        HttpResponse<String> r = decidirEnLote(cuerpo(List.of(c1, eliminado, c3), "OCULTAR", "spam coordinado"));

        assertEquals(409, r.statusCode(), r.body());
        assertEquals("TRANSICION_INVALIDA", leer(r, "$.motivo"));
        assertEquals(List.of(eliminado), leer(r, "$.comentarioIds"));
        assertEquals("PUBLICADO", estadoDe(c1));
        assertEquals("ELIMINADO", estadoDe(eliminado));
        assertEquals("PUBLICADO", estadoDe(c3));
        for (String id : List.of(c1, eliminado, c3)) {
            assertEquals(0, asientosDe(id), "ningun asiento: " + id);
        }
        verifyNoInteractions(aviso, auditoria);
    }

    @Test
    @DisplayName("IT-c: si el asiento del segundo comentario falla, se revierte TODO: estados, asientos, avisos y auditoria")
    void rollbackRealSiFallaLaEscrituraAMedias() throws Exception {
        String c1 = sembrar("PUBLICADO");
        String c2 = sembrar("PUBLICADO");
        String c3 = sembrar("PUBLICADO");

        // El espia envuelve el proxy del repositorio y callRealMethod() no sirve
        // (ver la IT de la carrera de reportes); Spring tampoco deja la instancia
        // espiada accesible (getSpiedInstance() es null: delega por respuesta).
        // El primer guardado va a un repositorio real, creado sobre el
        // EntityManager compartido, que usa la transaccion en curso del servicio.
        AsientoRepository real = new JpaRepositoryFactory(entityManager).getRepository(AsientoRepository.class);
        AtomicInteger guardados = new AtomicInteger();
        AtomicLong actualizacionesYaEnviadas = new AtomicLong(-1);
        AtomicLong insercionesYaEnviadas = new AtomicLong(-1);
        doAnswer(llamada -> {
            if (guardados.incrementAndGet() == 2) {
                // Lo que ya se envio a la base ANTES de fallar: es lo que la
                // reversion tiene que deshacer. Con save() seria 0 en los dos.
                actualizacionesYaEnviadas.set(estadisticas.getEntityUpdateCount());
                insercionesYaEnviadas.set(estadisticas.getEntityInsertCount());
                throw new DataIntegrityViolationException("simulado: el asiento del segundo comentario falla");
            }
            return real.saveAndFlush(llamada.<AsientoDeModeracion>getArgument(0));
        }).when(asientosEspiados).saveAndFlush(any(AsientoDeModeracion.class));

        HttpResponse<String> r = decidirEnLote(cuerpo(List.of(c1, c2, c3), "OCULTAR", "spam coordinado"));

        assertEquals(409, r.statusCode(), "la violacion de integridad cae en el 409 generico: " + r.body());
        assertEquals(2, guardados.get(), "el lote no sigue despues del fallo");
        // La premisa del diseno: con saveAndFlush lo anterior YA estaba en la base cuando fallo.
        assertTrue(actualizacionesYaEnviadas.get() >= 1,
                "el UPDATE del primer comentario ya habia salido a la base: " + actualizacionesYaEnviadas);
        assertTrue(insercionesYaEnviadas.get() >= 1,
                "el INSERT del primer asiento ya habia salido a la base: " + insercionesYaEnviadas);
        // Y aun asi, tras la reversion no queda nada: se lee por JDBC, no por el repositorio.
        for (String id : List.of(c1, c2, c3)) {
            assertEquals("PUBLICADO", estadoDe(id), "sin cambiar: " + id);
            assertEquals(0, asientosDe(id), "sin asiento: " + id);
        }
        verifyNoInteractions(aviso, auditoria);
    }

    // ---------------------------------------------------------------- la premisa

    @Test
    @DisplayName("premisa: con id asignado a mano save() difiere el UPDATE y el INSERT; saveAndFlush los envia ya")
    void saveDifiereYSaveAndFlushEnvia() {
        String idA = sembrar("PUBLICADO");
        String idB = sembrar("PUBLICADO");

        transaccion.executeWithoutResult(estado -> {
            Comentario a = comentarios.findById(idA).orElseThrow().aDominio();
            estadisticas.clear();
            comentarios.save(RegistroDeComentario.desde(a.con(Comentario.Estado.OCULTO)));
            asientosEspiados.save(asiento(idA));
            assertEquals(0, estadisticas.getEntityUpdateCount(), "save() no envia el UPDATE hasta el flush");
            assertEquals(0, estadisticas.getEntityInsertCount(), "save() no envia el INSERT hasta el flush");
            estado.setRollbackOnly();
        });

        transaccion.executeWithoutResult(estado -> {
            Comentario b = comentarios.findById(idB).orElseThrow().aDominio();
            estadisticas.clear();
            comentarios.saveAndFlush(RegistroDeComentario.desde(b.con(Comentario.Estado.OCULTO)));
            asientosEspiados.saveAndFlush(asiento(idB));
            assertEquals(1, estadisticas.getEntityUpdateCount(), "saveAndFlush() envia el UPDATE ya");
            assertEquals(1, estadisticas.getEntityInsertCount(), "saveAndFlush() envia el INSERT ya");
            estado.setRollbackOnly();
        });

        assertEquals("PUBLICADO", estadoDe(idA));
        assertEquals("PUBLICADO", estadoDe(idB));
        assertFalse(asientosDe(idA) + asientosDe(idB) > 0, "las dos transacciones se revirtieron");
    }
}
