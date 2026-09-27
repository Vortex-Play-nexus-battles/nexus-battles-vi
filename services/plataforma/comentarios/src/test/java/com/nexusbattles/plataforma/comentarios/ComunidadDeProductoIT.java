package com.nexusbattles.plataforma.comentarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.imagenes.ExaminadorDeImagenes;
import com.nexusbattles.plataforma.comentarios.imagenes.RegistroDeImagen;
import com.nexusbattles.plataforma.comentarios.imagenes.RepositorioDeImagenes;
import com.nexusbattles.plataforma.comentarios.imagenes.ServicioDeImagenes;
import com.nexusbattles.plataforma.comentarios.imagenes.TipoDeImagen;
import com.nexusbattles.plataforma.comentarios.moderacion.AsientoDeModeracion;
import com.nexusbattles.plataforma.comentarios.moderacion.AsientoRepository;

import jakarta.persistence.EntityManagerFactory;

/**
 * La comunidad de producto de B3 de punta a punta: aplicacion entera, PostgreSQL
 * de Testcontainers, HTTP de verdad y los servicios vecinos (catalogo, sanciones,
 * lista negra) como servidores HTTP de prueba ({@link VecinosDePrueba}).
 *
 * <p>Lo que solo se puede comprobar aqui, y no en una prueba unitaria:
 * <ul>
 *   <li>que la restriccion unica de la base decide la calificacion unica
 *       aunque lleguen dos peticiones a la vez;</li>
 *   <li>que leer una pagina del hilo cuesta las mismas consultas con un
 *       comentario que con doce (sin N+1);</li>
 *   <li>que el multipart real corta a los 2 MB con el problem detail del
 *       servicio, y que las cabeceras de una imagen salen como deben;</li>
 *   <li>que ocultar o eliminar un comentario ya no se lleva la calificacion de
 *       su autor, el defecto que encontro la auditoria.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.properties.hibernate.generate_statistics=true",
                "comentarios.imagenes.limpieza-activa=false"
        })
@DisplayName("B3: comunidad de producto de punta a punta")
class ComunidadDeProductoIT {

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

    @LocalServerPort
    private int puerto;

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private RepositorioDeImagenes imagenes;

    @Autowired
    private ServicioDeImagenes servicioDeImagenes;

    @Autowired
    private AsientoRepository asientos;

    private final HttpClient http = HttpClient.newHttpClient();
    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    // ---------------------------------------------------------------- utilidades

    private static String productoNuevo() {
        String id = UUID.randomUUID().toString();
        vecinos.productos.add(id);
        return id;
    }

    private String jugador(UUID uid) {
        return "Bearer " + emisor.tokenDeJugador("Jugador-" + uid.toString().substring(0, 6), uid);
    }

    private String moderadora() {
        return "Bearer " + emisor.tokenDeUsuario("AdaLaJusta", UUID.randomUUID(), "MODERADOR");
    }

    private HttpResponse<String> pedir(String metodo, String ruta, String token, String json, String... cabeceras)
            throws IOException, InterruptedException {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            peticion.header("Content-Type", "application/json");
        }
        if (token != null) {
            peticion.header("Authorization", token);
        }
        for (int i = 0; i < cabeceras.length; i += 2) {
            peticion.header(cabeceras[i], cabeceras[i + 1]);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> comentar(String producto, String token, String texto, Integer estrellas,
            String... imagenesAdjuntas) throws Exception {
        StringBuilder json = new StringBuilder("{\"texto\":\"").append(texto).append('"');
        if (estrellas != null) {
            json.append(",\"estrellas\":").append(estrellas);
        }
        json.append(",\"imagenes\":[");
        for (int i = 0; i < imagenesAdjuntas.length; i++) {
            json.append(i == 0 ? "" : ",").append('"').append(imagenesAdjuntas[i]).append('"');
        }
        json.append("]}");
        return pedir("POST", "/api/v1/products/" + producto + "/comments", token, json.toString());
    }

    private HttpResponse<String> calificar(String producto, String token, int estrellas) throws Exception {
        return pedir("POST", "/api/v1/products/" + producto + "/rating", token, "{\"estrellas\":" + estrellas + "}");
    }

    private HttpResponse<String> subir(String token, byte[] bytes, String nombre, String tipoDeclarado)
            throws IOException, InterruptedException {
        String frontera = "----nexus" + UUID.randomUUID();
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream();
        cuerpo.writeBytes(("--" + frontera + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\""
                + nombre + "\"\r\nContent-Type: " + tipoDeclarado + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        cuerpo.writeBytes(bytes);
        cuerpo.writeBytes(("\r\n--" + frontera + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/v1/comentarios/imagenes"))
                .header("Content-Type", "multipart/form-data; boundary=" + frontera)
                .header("Authorization", token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()))
                .build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private static <T> T leer(HttpResponse<String> respuesta, String ruta) {
        return JsonPath.read(respuesta.body(), ruta);
    }

    // ------------------------------------------------------------------ calificar

    @Nested
    @DisplayName("la calificacion, separada del comentario")
    class Calificaciones {

        @Test
        @DisplayName("una sola vez: 201 y luego 409; el resumen y la propia salen de la tabla")
        void unaSolaVez() throws Exception {
            String producto = productoNuevo();
            UUID uid = UUID.randomUUID();

            HttpResponse<String> vacio = pedir("GET", "/api/v1/products/" + producto + "/rating", null, null);
            assertEquals(200, vacio.statusCode(), vacio.body());
            assertNull(leer(vacio, "$.promedio"));

            HttpResponse<String> primera = calificar(producto, jugador(uid), 4);
            assertEquals(201, primera.statusCode(), primera.body());
            assertEquals(4.0, ((Number) leer(primera, "$.resumen.promedio")).doubleValue());

            HttpResponse<String> segunda = calificar(producto, jugador(uid), 1);
            assertEquals(409, segunda.statusCode(), segunda.body());
            assertEquals("https://nexusbattles.local/errores/ya-calificado", leer(segunda, "$.type"));

            HttpResponse<String> mia = pedir("GET", "/api/v1/products/" + producto + "/rating/mia", jugador(uid), null);
            assertEquals(4, (Integer) leer(mia, "$.estrellas"), "no se sustituyo");

            calificar(producto, jugador(UUID.randomUUID()), 5);
            HttpResponse<String> resumen = pedir("GET", "/api/v1/products/" + producto + "/rating", null, null);
            assertEquals(4.5, ((Number) leer(resumen, "$.promedio")).doubleValue());
            assertEquals(2, (Integer) leer(resumen, "$.total"));
            assertEquals(1, (Integer) leer(resumen, "$.distribucion['5']"));
            assertEquals(0, (Integer) leer(resumen, "$.distribucion['1']"));
        }

        @Test
        @DisplayName("dos POST simultaneos del mismo jugador: la restriccion unica deja uno (201) y el otro es 409")
        void concurrencia() throws Exception {
            for (int ronda = 0; ronda < 5; ronda++) {
                String producto = productoNuevo();
                String token = jugador(UUID.randomUUID());
                List<Integer> estados = aLaVez(6, () -> calificar(producto, token, 3).statusCode());

                assertEquals(1, estados.stream().filter(e -> e == 201).count(), "ronda " + ronda + ": " + estados);
                assertEquals(5, estados.stream().filter(e -> e == 409).count(), "ronda " + ronda + ": " + estados);
                HttpResponse<String> resumen = pedir("GET", "/api/v1/products/" + producto + "/rating", null, null);
                assertEquals(1, (Integer) leer(resumen, "$.total"));
            }
        }

        @Test
        @DisplayName("dos comentarios simultaneos con estrellas: los dos entran y solo uno califica")
        void concurrenciaDesdeComentarios() throws Exception {
            String producto = productoNuevo();
            String token = jugador(UUID.randomUUID());

            List<Boolean> descartadas = aLaVez(4, () -> {
                HttpResponse<String> r = comentar(producto, token, "opinion simultanea", 5);
                assertEquals(201, r.statusCode(), r.body());
                return (Boolean) leer(r, "$.calificacionDescartada");
            });

            assertEquals(1, descartadas.stream().filter(d -> !d).count(), descartadas.toString());
            HttpResponse<String> hilo = pedir("GET", "/api/v1/products/" + producto + "/comments", null, null);
            assertEquals(4, (Integer) leer(hilo, "$.total"));
            assertEquals(1, (Integer) leer(hilo, "$.totalCalificaciones"));
        }

        @Test
        @DisplayName("no se califica un producto que no existe (404) ni a ciegas si el catalogo no responde (503)")
        void productoValidado() throws Exception {
            HttpResponse<String> inexistente = calificar("no-existe-" + UUID.randomUUID(), jugador(UUID.randomUUID()), 4);
            assertEquals(404, inexistente.statusCode());
            assertEquals("https://nexusbattles.local/errores/producto-inexistente", leer(inexistente, "$.type"));

            String producto = UUID.randomUUID().toString();
            vecinos.catalogoCaido.set(true);
            try {
                HttpResponse<String> aCiegas = calificar(producto, jugador(UUID.randomUUID()), 4);
                assertEquals(503, aCiegas.statusCode(), aCiegas.body());
                // Una lectura si se degrada: el resumen sale aunque el catalogo no conteste.
                HttpResponse<String> resumen = pedir("GET", "/api/v1/products/" + producto + "/rating", null, null);
                assertEquals(200, resumen.statusCode());
            } finally {
                vecinos.catalogoCaido.set(false);
            }
        }

        @Test
        @DisplayName("un jugador sancionado no califica: 403")
        void sancionado() throws Exception {
            UUID uid = UUID.randomUUID();
            vecinos.sancionados.add(uid.toString());
            HttpResponse<String> r = calificar(productoNuevo(), jugador(uid), 5);
            assertEquals(403, r.statusCode(), r.body());
            assertEquals("AUTOR_SILENCIADO", leer(r, "$.motivo"));
        }
    }

    // -------------------------------------------------------- comentarios y hilo

    @Nested
    @DisplayName("comentarios")
    class Comentarios {

        @Test
        @DisplayName("comentar con estrellas crea la calificacion; la segunda se descarta y el comentario las ensena")
        void compatibilidadDeEstrellas() throws Exception {
            String producto = productoNuevo();
            String token = jugador(UUID.randomUUID());

            HttpResponse<String> primero = comentar(producto, token, "Muy buena", 5);
            assertEquals(201, primero.statusCode(), primero.body());
            assertFalse((Boolean) leer(primero, "$.calificacionDescartada"));
            assertEquals(5, (Integer) leer(primero, "$.estrellas"));

            HttpResponse<String> segundo = comentar(producto, token, "Sigo pensando igual", 1);
            assertEquals(201, segundo.statusCode(), segundo.body());
            assertTrue((Boolean) leer(segundo, "$.calificacionDescartada"));
            assertEquals(5, (Integer) leer(segundo, "$.estrellas"), "las de su calificacion, no las que mando");

            HttpResponse<String> hilo = pedir("GET", "/api/v1/products/" + producto + "/comments", null, null);
            assertEquals(List.of(5, 5), leer(hilo, "$.comentarios[*].estrellas"));
            assertEquals(5.0, ((Number) leer(hilo, "$.calificacionPromedio")).doubleValue());
            assertEquals(1, (Integer) leer(hilo, "$.totalCalificaciones"));
        }

        @Test
        @DisplayName("ocultar o eliminar el comentario ya no bloquea ni borra la calificacion de su autor (auditoria)")
        void moderarNoSeLlevaLaCalificacion() throws Exception {
            String producto = productoNuevo();
            UUID autor = UUID.randomUUID();
            HttpResponse<String> comentario = comentar(producto, jugador(autor), "Me gusto", 4);
            String id = leer(comentario, "$.id");

            HttpResponse<String> oculto = pedir("POST", "/api/v1/comentarios/moderacion/" + id + "/decision",
                    moderadora(), "{\"accion\":\"OCULTAR\",\"motivo\":\"prueba de moderacion\"}");
            assertEquals(200, oculto.statusCode(), oculto.body());

            HttpResponse<String> resumen = pedir("GET", "/api/v1/products/" + producto + "/rating", null, null);
            assertEquals(1, (Integer) leer(resumen, "$.total"), "su calificacion sigue contando");
            assertEquals(4.0, ((Number) leer(resumen, "$.promedio")).doubleValue());

            // Puede seguir comentando; sus estrellas de antes siguen siendo las suyas.
            HttpResponse<String> otro = comentar(producto, jugador(autor), "Vuelvo a opinar", 2);
            assertEquals(201, otro.statusCode());
            assertTrue((Boolean) leer(otro, "$.calificacionDescartada"));
            assertEquals(4, (Integer) leer(otro, "$.estrellas"));

            // Y retirar su propio comentario tampoco toca la calificacion (7.1).
            String otroId = leer(otro, "$.id");
            assertEquals(204, pedir("DELETE", "/api/v1/products/" + producto + "/comments/" + otroId,
                    jugador(autor), null).statusCode());
            assertEquals(1, (Integer) leer(
                    pedir("GET", "/api/v1/products/" + producto + "/rating", null, null), "$.total"));
        }

        @Test
        @DisplayName("el hilo se pagina: mas reciente primero, orden estable y total de todas las paginas")
        void paginacion() throws Exception {
            String producto = productoNuevo();
            List<String> publicados = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                HttpResponse<String> r = comentar(producto, jugador(UUID.randomUUID()), "comentario " + i, null);
                publicados.add(leer(r, "$.id"));
                // Fechas distintas: el orden que se afirma es el de publicacion,
                // no el desempate por id de dos del mismo microsegundo.
                Thread.sleep(5);
            }

            HttpResponse<String> primera = pedir("GET",
                    "/api/v1/products/" + producto + "/comments?pagina=0&tamano=2", null, null);
            HttpResponse<String> segunda = pedir("GET",
                    "/api/v1/products/" + producto + "/comments?pagina=1&tamano=2", null, null);
            HttpResponse<String> tercera = pedir("GET",
                    "/api/v1/products/" + producto + "/comments?pagina=2&tamano=2", null, null);
            HttpResponse<String> despues = pedir("GET",
                    "/api/v1/products/" + producto + "/comments?pagina=9&tamano=2", null, null);

            List<String> vistos = new ArrayList<>();
            vistos.addAll(leer(primera, "$.comentarios[*].id"));
            vistos.addAll(leer(segunda, "$.comentarios[*].id"));
            vistos.addAll(leer(tercera, "$.comentarios[*].id"));
            List<String> esperados = new ArrayList<>(publicados);
            java.util.Collections.reverse(esperados);
            assertEquals(esperados, vistos, "del mas reciente al mas antiguo, sin repetir ni perder");
            assertEquals(5, (Integer) leer(primera, "$.total"));
            assertEquals(3, (Integer) leer(primera, "$.totalPaginas"));
            assertEquals(2, (Integer) leer(primera, "$.tamano"));
            assertEquals(List.of(), leer(despues, "$.comentarios"));

            HttpResponse<String> porOmision = pedir("GET", "/api/v1/products/" + producto + "/comments", null, null);
            assertEquals(16, (Integer) leer(porOmision, "$.tamano"));
            HttpResponse<String> recortada = pedir("GET",
                    "/api/v1/products/" + producto + "/comments?tamano=500", null, null);
            assertEquals(50, (Integer) leer(recortada, "$.tamano"));
            assertEquals(400, pedir("GET", "/api/v1/products/" + producto + "/comments?pagina=-1", null, null)
                    .statusCode());
        }

        @Test
        @DisplayName("sin N+1: una pagina de doce comentarios con imagenes cuesta las mismas consultas que una de uno")
        void sinNMasUno() throws Exception {
            String conUno = productoNuevo();
            String conDoce = productoNuevo();
            byte[] png = ExaminadorDeImagenesTestAcceso.png();
            comentarConImagen(conUno, png, 0);
            for (int i = 0; i < 12; i++) {
                comentarConImagen(conDoce, png, i);
            }

            long consultasConUno = consultasDe("/api/v1/products/" + conUno + "/comments");
            long consultasConDoce = consultasDe("/api/v1/products/" + conDoce + "/comments");

            assertEquals(consultasConUno, consultasConDoce, "el numero de consultas no crece con la pagina");
            assertTrue(consultasConDoce <= 5, "pagina, total, imagenes, estrellas y resumen: " + consultasConDoce);
        }

        private void comentarConImagen(String producto, byte[] png, int i) throws Exception {
            UUID autor = UUID.randomUUID();
            HttpResponse<String> subida = subir(jugador(autor), png, "foto.png", "image/png");
            assertEquals(201, subida.statusCode(), subida.body());
            HttpResponse<String> r = comentar(producto, jugador(autor), "con foto " + i, (i % 5) + 1,
                    (String) leer(subida, "$.id"));
            assertEquals(201, r.statusCode(), r.body());
        }

        private long consultasDe(String ruta) throws Exception {
            Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
            estadisticas.clear();
            HttpResponse<String> hilo = pedir("GET", ruta, null, null);
            assertEquals(200, hilo.statusCode());
            return estadisticas.getPrepareStatementCount();
        }

        @Test
        @DisplayName("no se comenta un producto que no existe (404) ni a ciegas (503); el hilo se lee igual")
        void productoValidado() throws Exception {
            String inexistente = "no-existe-" + UUID.randomUUID();
            HttpResponse<String> r = comentar(inexistente, jugador(UUID.randomUUID()), "hola", null);
            assertEquals(404, r.statusCode());
            assertEquals("https://nexusbattles.local/errores/producto-inexistente", leer(r, "$.type"));

            String producto = UUID.randomUUID().toString();
            vecinos.catalogoCaido.set(true);
            try {
                assertEquals(503, comentar(producto, jugador(UUID.randomUUID()), "hola", null).statusCode());
                HttpResponse<String> hilo = pedir("GET", "/api/v1/products/" + producto + "/comments", null, null);
                assertEquals(200, hilo.statusCode(), "leer el hilo no depende del catalogo");
            } finally {
                vecinos.catalogoCaido.set(false);
            }
        }

        @Test
        @DisplayName("la lista negra se consulta con contexto COMENTARIO y su REVISION retiene (202)")
        void listaNegraConContexto() throws Exception {
            String producto = productoNuevo();
            vecinos.verificaciones.clear();

            HttpResponse<String> r = comentar(producto, jugador(UUID.randomUUID()), "esto esta prohibido", null);

            assertEquals(202, r.statusCode(), r.body());
            assertEquals("EN_REVISION", leer(r, "$.estado"));
            assertTrue(vecinos.verificaciones.stream().anyMatch(v -> v.contains("\"contexto\":\"COMENTARIO\"")),
                    vecinos.verificaciones.toString());
        }
    }

    // ------------------------------------------------------------------ imagenes

    @Nested
    @DisplayName("imagenes")
    class Imagenes {

        @Test
        @DisplayName("el ciclo completo: pendiente y privada, adjunta y publica, oculta y privada otra vez")
        void cicloCompleto() throws Exception {
            String producto = productoNuevo();
            UUID autor = UUID.randomUUID();
            byte[] png = ExaminadorDeImagenesTestAcceso.png();

            HttpResponse<String> subida = subir(jugador(autor), png, "../../foto <b>.png", "text/html");
            assertEquals(201, subida.statusCode(), subida.body());
            String id = leer(subida, "$.id");
            assertEquals("image/png", leer(subida, "$.tipo"), "el tipo lo dice la firma, no lo que declara el cliente");
            assertEquals("/api/v1/comentarios/imagenes/" + id, leer(subida, "$.url"));
            assertEquals(png.length, (Integer) leer(subida, "$.tamano"));

            String ruta = "/api/v1/comentarios/imagenes/" + id;
            assertEquals(404, pedir("GET", ruta, null, null).statusCode(), "pendiente: nadie mas la ve");
            assertEquals(404, pedir("GET", ruta, jugador(UUID.randomUUID()), null).statusCode());
            HttpResponse<byte[]> delAutor = bytes(ruta, jugador(autor));
            assertEquals(200, delAutor.statusCode());
            assertEquals("private, no-store", delAutor.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(Arrays.equals(png, delAutor.body()));

            // Otro jugador no puede adjuntar la imagen de otro.
            HttpResponse<String> ajena = comentar(producto, jugador(UUID.randomUUID()), "me la quedo", null, id);
            assertEquals(400, ajena.statusCode(), ajena.body());
            assertEquals("https://nexusbattles.local/errores/imagenes-no-validas", leer(ajena, "$.type"));

            HttpResponse<String> comentario = comentar(producto, jugador(autor), "mira mi foto", null, id);
            assertEquals(201, comentario.statusCode(), comentario.body());
            assertEquals(List.of(id), leer(comentario, "$.imagenes"));

            HttpResponse<byte[]> publica = bytes(ruta, null);
            assertEquals(200, publica.statusCode());
            assertEquals("image/png", publica.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("nosniff", publica.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertEquals("default-src 'none'", publica.headers().firstValue("Content-Security-Policy").orElseThrow());
            assertEquals("public, max-age=31536000, immutable",
                    publica.headers().firstValue("Cache-Control").orElseThrow());

            // Ya usada: ni su propio autor la puede adjuntar a otro comentario.
            assertEquals(400, comentar(producto, jugador(autor), "otra vez", null, id).statusCode());

            // Oculto el comentario, la imagen deja de ser publica; moderacion la sigue viendo.
            String comentarioId = leer(comentario, "$.id");
            pedir("POST", "/api/v1/comentarios/moderacion/" + comentarioId + "/decision", moderadora(),
                    "{\"accion\":\"OCULTAR\",\"motivo\":\"revisar la foto\"}");
            assertEquals(404, pedir("GET", ruta, null, null).statusCode());
            assertEquals(200, pedir("GET", ruta, moderadora(), null).statusCode());
        }

        @Test
        @DisplayName("rechazos por contenido: HTML con extension .png es 415; un id inventado o un nombre de archivo, 400")
        void rechazos() throws Exception {
            String token = jugador(UUID.randomUUID());
            HttpResponse<String> html = subir(token,
                    "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8), "foto.png", "image/png");
            assertEquals(415, html.statusCode(), html.body());
            assertEquals("FORMATO_DE_IMAGEN_NO_ADMITIDO", leer(html, "$.motivo"));

            String producto = productoNuevo();
            assertEquals(400, comentar(producto, token, "hola", null, UUID.randomUUID().toString()).statusCode());
            assertEquals(400, comentar(producto, token, "hola", null, "captura.jpg").statusCode());
        }

        @Test
        @DisplayName("mas de 2 MB lo corta el multipart real con el problem detail del servicio (413)")
        void demasiadoPesada() throws Exception {
            byte[] png = ExaminadorDeImagenesTestAcceso.png();
            byte[] grande = Arrays.copyOf(png, ExaminadorDeImagenes.TAMANO_MAXIMO + 64 * 1024);
            HttpResponse<String> r = subir(jugador(UUID.randomUUID()), grande, "grande.png", "image/png");
            assertEquals(413, r.statusCode(), r.body());
            assertEquals("https://nexusbattles.local/errores/imagen-demasiado-grande", leer(r, "$.type"));
        }

        @Test
        @DisplayName("la limpieza borra las pendientes de mas de 24 h y deja las usadas y las recientes")
        void limpieza() throws Exception {
            UUID autor = UUID.randomUUID();
            byte[] png = ExaminadorDeImagenesTestAcceso.png();
            String vieja = UUID.randomUUID().toString();
            imagenes.save(new RegistroDeImagen(vieja, autor.toString(), TipoDeImagen.PNG, png, "h",
                    Instant.now().minus(Duration.ofHours(25))));
            String reciente = leer(subir(jugador(autor), png, "a.png", "image/png"), "$.id");
            String usada = leer(subir(jugador(autor), png, "b.png", "image/png"), "$.id");
            comentar(productoNuevo(), jugador(autor), "con foto", null, usada);

            int borradas = servicioDeImagenes.limpiarPendientes();

            assertTrue(borradas >= 1);
            assertFalse(imagenes.existsById(vieja));
            assertTrue(imagenes.existsById(reciente));
            assertTrue(imagenes.existsById(usada));
        }

        private HttpResponse<byte[]> bytes(String ruta, String token) throws Exception {
            HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
            if (token != null) {
                peticion.header("Authorization", token);
            }
            return http.send(peticion.build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    // ---------------------------------------------------------------- moderacion

    @Nested
    @DisplayName("moderacion ampliada (7.3.3)")
    class Moderacion {

        @Test
        @DisplayName("EDITAR con la IP de X-Forwarded-For: el hilo muestra el texto nuevo y el asiento guarda los dos")
        void editar() throws Exception {
            String producto = productoNuevo();
            String id = leer(comentar(producto, jugador(UUID.randomUUID()), "texto con insulto", null), "$.id");

            HttpResponse<String> r = pedir("POST", "/api/v1/comentarios/moderacion/" + id + "/decision", moderadora(),
                    "{\"accion\":\"EDITAR\",\"motivo\":\"quitar el insulto\",\"textoNuevo\":\"texto moderado\"}",
                    "X-Forwarded-For", "198.51.100.7, 10.0.0.1");
            assertEquals(200, r.statusCode(), r.body());
            assertEquals("texto con insulto", leer(r, "$.asiento.textoAnterior"));

            HttpResponse<String> hilo = pedir("GET", "/api/v1/products/" + producto + "/comments", null, null);
            assertEquals("texto moderado", leer(hilo, "$.comentarios[0].texto"));
            assertTrue((Boolean) leer(hilo, "$.comentarios[0].editado"));

            AsientoDeModeracion asiento = asientos.findByComentarioIdOrderByFechaAsc(id).get(0);
            assertEquals("198.51.100.7", asiento.ipOrigen());
            assertEquals("texto con insulto", asiento.textoAnterior());
            assertEquals("texto moderado", asiento.textoNuevo());

            assertEquals(400, pedir("POST", "/api/v1/comentarios/moderacion/" + id + "/decision", moderadora(),
                    "{\"accion\":\"EDITAR\",\"motivo\":\"sin texto\"}").statusCode());
        }

        @Test
        @DisplayName("MARCAR lo pone en la lista de seguimiento aunque este publicado; DESMARCAR lo saca")
        void marcar() throws Exception {
            String producto = productoNuevo();
            String id = leer(comentar(producto, jugador(UUID.randomUUID()), "a vigilar", null), "$.id");
            String decision = "/api/v1/comentarios/moderacion/" + id + "/decision";

            assertEquals(200, pedir("POST", decision, moderadora(),
                    "{\"accion\":\"MARCAR\",\"motivo\":\"seguimiento especial\"}").statusCode());
            HttpResponse<String> seguimiento = pedir("GET",
                    "/api/v1/comentarios/moderacion?marcado=true&productoId=" + producto, moderadora(), null);
            assertEquals(List.of(id), leer(seguimiento, "$.entradas[*].comentario.id"));
            assertEquals(List.of(true), leer(seguimiento, "$.entradas[*].comentario.marcado"));

            HttpResponse<String> hilo = pedir("GET", "/api/v1/products/" + producto + "/comments", null, null);
            assertEquals(List.of(), leer(hilo, "$.comentarios[*].marcado"), "la marca no se ensena en el hilo publico");

            assertEquals(409, pedir("POST", decision, moderadora(),
                    "{\"accion\":\"MARCAR\",\"motivo\":\"otra vez\"}").statusCode());
            assertEquals(200, pedir("POST", decision, moderadora(),
                    "{\"accion\":\"DESMARCAR\",\"motivo\":\"ya no hace falta\"}").statusCode());
            assertEquals(0, (Integer) leer(pedir("GET",
                    "/api/v1/comentarios/moderacion?marcado=true&productoId=" + producto, moderadora(), null), "$.total"));
        }
    }

    // ------------------------------------------------------------------- apoyo

    private static <T> List<T> aLaVez(int cuantas, Callable<T> accion) throws Exception {
        ExecutorService hilos = Executors.newFixedThreadPool(cuantas);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < cuantas; i++) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    return accion.call();
                }));
            }
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get());
            }
            return resultados;
        } finally {
            hilos.shutdownNow();
        }
    }

    /** Los archivos de prueba de las imagenes, desde otro paquete. */
    static final class ExaminadorDeImagenesTestAcceso {

        static byte[] png() throws IOException {
            try (var entrada = ComunidadDeProductoIT.class.getResourceAsStream("/imagenes/real.png")) {
                return entrada.readAllBytes();
            }
        }
    }
}
