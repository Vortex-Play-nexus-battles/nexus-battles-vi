package nexus.recursos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * HU-PRD-009 de punta a punta contra MongoDB real, con tokens firmados de
 * verdad: subir, encontrar en la biblioteca sin los bytes, servir los bytes,
 * no poder retirar la imagen actual de un producto y seguir sirviendo el
 * recurso retirado (una versión anterior del diseño puede apuntar a él).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("Recursos gráficos contra MongoDB real (HU-PRD-009)")
class RecursosContraMongoIT {

    @Container
    @ServiceConnection
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final UUID UID_ADMIN = UUID.fromString("0f3c1a5e-2b7d-4e8f-9a01-23456789abcd");

    @DynamicPropertySource
    static void emisorReal(DynamicPropertyRegistry registro) {
        EmisorDeTokensDePrueba.registrarJwks(registro);
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ProductoRepository productos;

    private static String admin() {
        return "Bearer " + EMISOR.tokenDeUsuario("raiz", UID_ADMIN, "ADMINISTRADOR");
    }

    private static String jugador() {
        return "Bearer " + EMISOR.tokenDeJugador("lyra", UUID.randomUUID());
    }

    @Test
    @DisplayName("subir, listar, servir, no retirar la imagen en uso y seguir sirviendo la retirada")
    void cicloCompleto() throws Exception {
        byte[] png = ArchivosDePrueba.png(64, 48);
        String productoId = UUID.randomUUID().toString();
        productos.insert(new Producto(productoId, "Espada de prueba", "data:image/png;base64,AA==",
                "Una espada", TipoProducto.ARMA, -1, 10, null, false, null, null, null, null, null, null,
                null, null, null, null, null, 5, null, EstadoProducto.ACTIVO, 0, Instant.now(), Instant.now()));

        String cuerpo = mvc.perform(multipart("/api/v1/productos/recursos")
                        .file(new MockMultipartFile("archivo", "espada.png", "text/html", png))
                        .param("clase", "IMAGEN")
                        .param("productoId", productoId)
                        .header("Authorization", admin()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formato").value("PNG"))
                .andExpect(jsonPath("$.ancho").value(64))
                .andExpect(jsonPath("$.alto").value(48))
                .andExpect(jsonPath("$.subidoPor").value(UID_ADMIN.toString()))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(cuerpo, "$.id");
        String url = JsonPath.read(cuerpo, "$.url");

        // El mismo archivo otra vez: el mismo recurso, 200.
        mvc.perform(multipart("/api/v1/productos/recursos")
                        .file(new MockMultipartFile("archivo", "otra.png", "image/png", png))
                        .param("clase", "IMAGEN")
                        .param("productoId", productoId)
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        // Un HTML con nombre de imagen no entra.
        mvc.perform(multipart("/api/v1/productos/recursos")
                        .file(new MockMultipartFile("archivo", "x.png", "image/png", "<html></html>".getBytes()))
                        .param("clase", "IMAGEN")
                        .header("Authorization", admin()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.motivo").value("FORMATO_NO_ADMITIDO"));

        String biblioteca = mvc.perform(get("/api/v1/productos/recursos")
                        .param("productoId", productoId)
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andReturn().getResponse().getContentAsString();
        assertThat(biblioteca).doesNotContain("contenido").doesNotContain("huella");
        mvc.perform(get("/api/v1/productos/recursos").header("Authorization", jugador()))
                .andExpect(status().isForbidden());

        // Los bytes, públicos y con el tipo detectado (no el declarado).
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        // Es la imagen actual del producto: no se retira.
        Producto conImagen = productos.findById(productoId).orElseThrow();
        productos.save(new Producto(conImagen.id(), conImagen.nombre(), url, conImagen.descripcion(),
                conImagen.tipo(), conImagen.tiraje(), conImagen.precioCreditos(), null, false, null, null, null,
                null, null, null, null, null, null, null, null, conImagen.poderDeAtaque(), null,
                EstadoProducto.ACTIVO, conImagen.version(), conImagen.creadoEn(), Instant.now()));
        mvc.perform(get("/api/v1/productos/recursos").param("productoId", productoId).header("Authorization", admin()))
                .andExpect(jsonPath("$.content[0].usadoEn[0].nombre").value("Espada de prueba"));
        mvc.perform(delete("/api/v1/productos/recursos/" + id).param("motivo", "Ya no sirve")
                        .header("Authorization", admin()))
                .andExpect(status().isConflict());

        // Con otra imagen, sí; y el retirado se sigue sirviendo.
        Producto sinEsa = productos.findById(productoId).orElseThrow();
        productos.save(new Producto(sinEsa.id(), sinEsa.nombre(), "data:image/png;base64,AA==",
                sinEsa.descripcion(), sinEsa.tipo(), sinEsa.tiraje(), sinEsa.precioCreditos(), null, false, null,
                null, null, null, null, null, null, null, null, null, null, sinEsa.poderDeAtaque(), null,
                EstadoProducto.ACTIVO, sinEsa.version(), sinEsa.creadoEn(), Instant.now()));
        mvc.perform(delete("/api/v1/productos/recursos/" + id).param("motivo", "Ya no sirve")
                        .header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoRetiro").value("Ya no sirve"));
        mvc.perform(get("/api/v1/productos/recursos").param("productoId", productoId).header("Authorization", admin()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/productos/recursos").param("productoId", productoId)
                        .param("incluirRetirados", "true").header("Authorization", admin()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(url)).andExpect(status().isOk());
    }
}
