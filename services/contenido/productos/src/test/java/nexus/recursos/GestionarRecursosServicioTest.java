package nexus.recursos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.ProductoNoEncontradoException;
import nexus.dominio.TipoProducto;
import nexus.persistencia.ProductoRepository;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@DisplayName("Biblioteca de recursos gráficos (HU-PRD-009)")
class GestionarRecursosServicioTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private static final String ADMIN = "0f3c1a5e-2b7d-4e8f-9a01-23456789abcd";
    private static final String PRODUCTO = "550e8400-e29b-41d4-a716-446655440000";

    private final RecursoGraficoRepository recursos = mock(RecursoGraficoRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final GestionarRecursosServicio servicio = new GestionarRecursosServicio(
            recursos, mongo, productos, new ExaminadorDeRecursos(LimitesDeRecursos.porOmision()),
            Clock.fixed(AHORA, ZoneOffset.UTC));

    private static RecursoGrafico recurso(String id, String productoId) {
        return new RecursoGrafico(id, ClaseDeRecurso.IMAGEN, FormatoDeRecurso.PNG, 10, 4, 4, "huella",
                new byte[] {1, 2, 3}, productoId, "espada.png", ADMIN, AHORA, null, null, null);
    }

    private static Producto producto(String id, String nombre, String imagen) {
        return new Producto(id, nombre, imagen, "Descripción", TipoProducto.ARMA, -1, 10, null, false,
                null, null, null, null, null, null, null, null, null, null, null, 5, null,
                EstadoProducto.ACTIVO, 1, AHORA, AHORA);
    }

    @Nested
    @DisplayName("subir")
    class Subir {

        @Test
        @DisplayName("guarda el archivo examinado con quién lo subió, cuándo y su huella")
        void guarda() {
            byte[] png = ArchivosDePrueba.png(48, 32);
            when(productos.existsById(PRODUCTO)).thenReturn(true);
            when(recursos.findFirstByHuellaAndClaseAndProductoIdAndRetiradoEnIsNull(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(recursos.insert(any(RecursoGrafico.class))).thenAnswer(i -> i.getArgument(0));

            GestionarRecursosServicio.Subida subida = servicio.subir(
                    png, ClaseDeRecurso.IMAGEN, " " + PRODUCTO + " ", "C:\\fotos\\Espada\u0007 larga.png", ADMIN);

            assertThat(subida.nuevo()).isTrue();
            RecursoGrafico guardado = subida.recurso();
            assertThat(guardado.formato()).isEqualTo(FormatoDeRecurso.PNG);
            assertThat(guardado.ancho()).isEqualTo(48);
            assertThat(guardado.alto()).isEqualTo(32);
            assertThat(guardado.bytes()).isEqualTo(png.length);
            assertThat(guardado.contenido()).isEqualTo(png);
            assertThat(guardado.productoId()).isEqualTo(PRODUCTO);
            assertThat(guardado.nombre()).isEqualTo("Espada larga.png");
            assertThat(guardado.subidoPor()).isEqualTo(ADMIN);
            assertThat(guardado.subidoEn()).isEqualTo(AHORA);
            assertThat(guardado.huella()).hasSize(64).isEqualTo(GestionarRecursosServicio.huellaDe(png));
            assertThat(guardado.url()).isEqualTo("/api/v1/productos/recursos/" + guardado.id());
            assertThat(guardado.retirado()).isFalse();
        }

        @Test
        @DisplayName("el mismo archivo para el mismo producto no se guarda dos veces")
        void sinDuplicados() {
            byte[] png = ArchivosDePrueba.png(8, 8);
            RecursoGrafico existente = recurso("r-1", null);
            when(recursos.findFirstByHuellaAndClaseAndProductoIdAndRetiradoEnIsNull(
                    GestionarRecursosServicio.huellaDe(png), ClaseDeRecurso.IMAGEN, null))
                    .thenReturn(Optional.of(existente));

            GestionarRecursosServicio.Subida subida = servicio.subir(png, ClaseDeRecurso.IMAGEN, null, "a.png", ADMIN);

            assertThat(subida.nuevo()).isFalse();
            assertThat(subida.recurso()).isSameAs(existente);
            verify(recursos, never()).insert(any(RecursoGrafico.class));
        }

        @Test
        @DisplayName("no se asocia a un producto que no existe, y un archivo inválido no se guarda")
        void rechazos() {
            when(productos.existsById("no-existe")).thenReturn(false);
            assertThatThrownBy(() -> servicio.subir(ArchivosDePrueba.png(8, 8), ClaseDeRecurso.IMAGEN,
                    "no-existe", "a.png", ADMIN)).isInstanceOf(ProductoNoEncontradoException.class);

            assertThatThrownBy(() -> servicio.subir(new byte[] {1, 2, 3}, ClaseDeRecurso.IMAGEN, null, "a.png", ADMIN))
                    .isInstanceOf(RecursoNoAdmitidoException.class);
            verify(recursos, never()).insert(any(RecursoGrafico.class));
        }
    }

    @Nested
    @DisplayName("listar")
    class Listar {

        @Test
        @DisplayName("pide la página sin los bytes, de lo más reciente a lo más antiguo, y dice qué productos usan cada recurso")
        void pagina() {
            RecursoGrafico usado = recurso("r-1", PRODUCTO);
            RecursoGrafico libre = recurso("r-2", null);
            when(mongo.count(any(Query.class), eq(RecursoGrafico.class))).thenReturn(18L);
            when(mongo.find(any(Query.class), eq(RecursoGrafico.class))).thenReturn(List.of(usado, libre));
            when(productos.findByImagenIn(anyCollection()))
                    .thenReturn(List.of(producto(PRODUCTO, "Espada larga", usado.url())));

            PaginaDeRecursos pagina = servicio.listar(ClaseDeRecurso.IMAGEN, PRODUCTO, false, 1, 16);

            assertThat(pagina.totalElements()).isEqualTo(18);
            assertThat(pagina.totalPages()).isEqualTo(2);
            assertThat(pagina.page()).isEqualTo(1);
            assertThat(pagina.content()).extracting(RecursoGraficoVista::id).containsExactly("r-1", "r-2");
            assertThat(pagina.content().get(0).usadoEn())
                    .containsExactly(new RecursoGraficoVista.UsoDeRecurso(PRODUCTO, "Espada larga"));
            assertThat(pagina.content().get(1).usadoEn()).isEmpty();
            assertThat(pagina.content().get(0).tipoContenido()).isEqualTo("image/png");

            ArgumentCaptor<Query> consulta = ArgumentCaptor.forClass(Query.class);
            verify(mongo).find(consulta.capture(), eq(RecursoGrafico.class));
            Query hecha = consulta.getValue();
            assertThat(hecha.getFieldsObject()).isEqualTo(new Document("contenido", 0));
            assertThat(hecha.getSkip()).isEqualTo(16);
            assertThat(hecha.getLimit()).isEqualTo(16);
            assertThat(hecha.getSortObject()).isEqualTo(new Document("subidoEn", -1).append("_id", -1));
            assertThat(hecha.getQueryObject().toString())
                    .contains("retiradoEn").contains("IMAGEN").contains(PRODUCTO);
        }

        @Test
        @DisplayName("sin filtros ni resultados no consulta productos")
        void vacia() {
            when(mongo.count(any(Query.class), eq(RecursoGrafico.class))).thenReturn(0L);
            when(mongo.find(any(Query.class), eq(RecursoGrafico.class))).thenReturn(List.of());

            PaginaDeRecursos pagina = servicio.listar(null, null, true, 0, 16);

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.totalPages()).isZero();
            verify(productos, never()).findByImagenIn(anyCollection());
        }
    }

    @Nested
    @DisplayName("retirar")
    class Retirar {

        @Test
        @DisplayName("lo saca de la biblioteca con quién, cuándo y por qué, sin borrar sus bytes")
        void retira() {
            RecursoGrafico libre = recurso("r-2", null);
            when(recursos.findById("r-2")).thenReturn(Optional.of(libre));
            when(productos.findFirstByImagen(libre.url())).thenReturn(Optional.empty());
            when(recursos.save(any(RecursoGrafico.class))).thenAnswer(i -> i.getArgument(0));

            RecursoGrafico retirado = servicio.retirar("r-2", ADMIN, "  Imagen duplicada  ");

            assertThat(retirado.retiradoEn()).isEqualTo(AHORA);
            assertThat(retirado.retiradoPor()).isEqualTo(ADMIN);
            assertThat(retirado.motivoRetiro()).isEqualTo("Imagen duplicada");
            assertThat(retirado.contenido()).containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("no retira la imagen actual de un producto")
        void enUso() {
            RecursoGrafico usado = recurso("r-1", PRODUCTO);
            when(recursos.findById("r-1")).thenReturn(Optional.of(usado));
            when(productos.findFirstByImagen(usado.url()))
                    .thenReturn(Optional.of(producto(PRODUCTO, "Espada larga", usado.url())));

            assertThatThrownBy(() -> servicio.retirar("r-1", ADMIN, "ya no sirve"))
                    .isInstanceOf(RecursoEnUsoException.class)
                    .hasMessage("Es la imagen actual de «Espada larga». Ponle otra imagen antes de retirarla.");
            verify(recursos, never()).save(any(RecursoGrafico.class));
        }

        @Test
        @DisplayName("retirar uno ya retirado no cambia nada; un motivo de más de 500 caracteres se rechaza")
        void bordes() {
            RecursoGrafico yaRetirado = recurso("r-3", null).retirar("otro", AHORA.minusSeconds(60), "antes");
            when(recursos.findById("r-3")).thenReturn(Optional.of(yaRetirado));
            assertThat(servicio.retirar("r-3", ADMIN, "otra vez")).isSameAs(yaRetirado);

            when(recursos.findById("r-2")).thenReturn(Optional.of(recurso("r-2", null)));
            assertThatThrownBy(() -> servicio.retirar("r-2", ADMIN, "x".repeat(501)))
                    .isInstanceOf(MotivoInvalidoException.class)
                    .hasMessageContaining("500");

            when(recursos.findById("nada")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.retirar("nada", ADMIN, null))
                    .isInstanceOf(RecursoNoEncontradoException.class);
            verify(recursos, never()).save(any(RecursoGrafico.class));
        }
    }

    @Test
    @DisplayName("el nombre del archivo se guarda sin ruta, sin caracteres de control y recortado")
    void nombres() {
        assertThat(GestionarRecursosServicio.nombreSaneado("/tmp/../escudo.webp", FormatoDeRecurso.WEBP))
                .isEqualTo("escudo.webp");
        assertThat(GestionarRecursosServicio.nombreSaneado("  <script>\"x\".png  ", FormatoDeRecurso.PNG))
                .isEqualTo("scriptx.png");
        assertThat(GestionarRecursosServicio.nombreSaneado(null, FormatoDeRecurso.GIF)).isEqualTo("recurso.gif");
        assertThat(GestionarRecursosServicio.nombreSaneado("   ", FormatoDeRecurso.PNG)).isEqualTo("recurso.png");
        assertThat(GestionarRecursosServicio.nombreSaneado("a".repeat(100), FormatoDeRecurso.PNG)).hasSize(80);
        assertThat(recurso("r", null).toString()).doesNotContain("[1, 2, 3]").contains("bytes=10");
        assertThat(recurso("r", null)).isEqualTo(recurso("r", "otro")).hasSameHashCodeAs(recurso("r", "otro"));
    }
}
