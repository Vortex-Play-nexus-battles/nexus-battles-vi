package com.nexusbattles.ms_ecommerce.catalogo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESCUDO;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.ESPADA;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.POCION;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.json;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.pagina;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

/**
 * El cliente del catalogo maestro contra un servidor simulado.
 *
 * <p>El cliente se construye con {@code ConfiguracionDelCatalogo.constructorDelCliente}
 * — el mismo constructor que el bean desplegado, con sus convertidores — y
 * solo se cambia la fabrica de peticiones por la del servidor simulado.
 */
@DisplayName("Catalogo maestro: el cliente que proyecta el catalogo del servicio productos")
class CatalogoMaestroTest {

    private static final String BASE = "http://catalogo.test";
    private static final String LISTADO = BASE + "/api/v1/productos?page=%d&size=50";

    private MockRestServiceServer servidor;
    private CatalogoMaestro catalogo;

    @BeforeEach
    void preparar() {
        RestClient.Builder constructor = ConfiguracionDelCatalogo.constructorDelCliente(
                new PropiedadesDelCatalogo(BASE, Duration.ofSeconds(2), Duration.ofSeconds(3)));
        servidor = MockRestServiceServer.bindTo(constructor).build();
        catalogo = new CatalogoMaestro(constructor.build());
    }

    private void esperarPagina(int numero, String cuerpo) {
        servidor.expect(requestTo(LISTADO.formatted(numero)))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
    }

    @Nested
    @DisplayName("productosEnVenta: el listado completo")
    class Listado {

        @Test
        @DisplayName("recorre todas las paginas de 50 y conserva el orden del catalogo")
        void recorreTodasLasPaginas() {
            esperarPagina(0, pagina(0, 2, json(ESPADA, "Espada"), json(ESCUDO, "Escudo")));
            esperarPagina(1, pagina(1, 2, json(POCION, "Pocion")));

            List<ProductoDelCatalogo> productos = catalogo.productosEnVenta();

            assertThat(productos).extracting(ProductoDelCatalogo::id).containsExactly(ESPADA, ESCUDO, POCION);
            servidor.verify();
        }

        @Test
        @DisplayName("con una sola pagina hace una sola peticion")
        void unaSolaPagina() {
            esperarPagina(0, pagina(0, 1, json(ESPADA, "Espada")));

            assertThat(catalogo.productosEnVenta()).hasSize(1);
            servidor.verify();
        }

        @Test
        @DisplayName("un catalogo vacio es una lista vacia, no un error")
        void catalogoVacio() {
            esperarPagina(0, pagina(0, 0));

            assertThat(catalogo.productosEnVenta()).isEmpty();
            servidor.verify();
        }

        @Test
        @DisplayName("una pagina vacia termina la lectura aunque el total diga que hay mas")
        void paginaVaciaTermina() {
            esperarPagina(0, pagina(0, 5, json(ESPADA, "Espada")));
            esperarPagina(1, pagina(1, 5));

            assertThat(catalogo.productosEnVenta()).extracting(ProductoDelCatalogo::id).containsExactly(ESPADA);
            servidor.verify();
        }

        @Test
        @DisplayName("sin totalPages sigue mientras la pagina venga llena y para en la primera que no")
        void sinTotalDePaginas() {
            String[] llena = IntStream.range(0, CatalogoMaestro.TAMANO_DE_PAGINA)
                    .mapToObj(i -> json("p-%02d".formatted(i), "Producto " + i))
                    .toArray(String[]::new);
            esperarPagina(0, "{\"content\":[" + String.join(",", llena) + "]}");
            esperarPagina(1, "{\"content\":[" + json(ESPADA, "Espada") + "]}");

            assertThat(catalogo.productosEnVenta()).hasSize(CatalogoMaestro.TAMANO_DE_PAGINA + 1);
            servidor.verify();
        }

        @Test
        @DisplayName("sin content la pagina cuenta como vacia")
        void sinContenido() {
            esperarPagina(0, "{\"totalPages\":3}");

            assertThat(catalogo.productosEnVenta()).isEmpty();
            servidor.verify();
        }

        @Test
        @DisplayName("un servidor que declara paginas sin fin no deja la lectura colgada")
        void topeDePaginas() {
            servidor.expect(ExpectedCount.times(CatalogoMaestro.MAXIMO_DE_PAGINAS),
                            requestTo(startsWith(BASE + "/api/v1/productos?page=")))
                    .andRespond(withSuccess(pagina(0, 100_000, json(ESPADA, "Espada")), MediaType.APPLICATION_JSON));

            assertThat(catalogo.productosEnVenta()).hasSize(CatalogoMaestro.MAXIMO_DE_PAGINAS);
            servidor.verify();
        }

        @Test
        @DisplayName("un 5xx es catalogo no disponible")
        void errorDelServidor() {
            servidor.expect(requestTo(LISTADO.formatted(0))).andRespond(withServerError());

            assertThatThrownBy(catalogo::productosEnVenta).isInstanceOf(CatalogoNoDisponibleException.class);
        }

        @Test
        @DisplayName("si falla una pagina intermedia no se devuelve un catalogo a medias")
        void falloEnLaSegundaPagina() {
            esperarPagina(0, pagina(0, 2, json(ESPADA, "Espada")));
            servidor.expect(requestTo(LISTADO.formatted(1))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

            assertThatThrownBy(catalogo::productosEnVenta)
                    .isInstanceOf(CatalogoNoDisponibleException.class)
                    .hasMessageContaining("pagina 1");
        }

        @Test
        @DisplayName("un tiempo de espera agotado es catalogo no disponible")
        void tiempoAgotado() {
            servidor.expect(requestTo(LISTADO.formatted(0)))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThatThrownBy(catalogo::productosEnVenta)
                    .isInstanceOf(CatalogoNoDisponibleException.class)
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
        }

        @Test
        @DisplayName("una respuesta que no es JSON es catalogo no disponible")
        void respuestaIlegible() {
            servidor.expect(requestTo(LISTADO.formatted(0)))
                    .andRespond(withSuccess("<html>mantenimiento</html>", MediaType.TEXT_HTML));

            assertThatThrownBy(catalogo::productosEnVenta).isInstanceOf(CatalogoNoDisponibleException.class);
        }

        @Test
        @DisplayName("un JSON roto es catalogo no disponible")
        void jsonRoto() {
            servidor.expect(requestTo(LISTADO.formatted(0)))
                    .andRespond(withSuccess("{\"content\":[{\"id\":", MediaType.APPLICATION_JSON));

            assertThatThrownBy(catalogo::productosEnVenta).isInstanceOf(CatalogoNoDisponibleException.class);
        }

        @Test
        @DisplayName("una respuesta sin cuerpo es catalogo no disponible")
        void sinCuerpo() {
            servidor.expect(requestTo(LISTADO.formatted(0))).andRespond(withSuccess());

            assertThatThrownBy(catalogo::productosEnVenta)
                    .isInstanceOf(CatalogoNoDisponibleException.class)
                    .hasMessageContaining("sin cuerpo");
        }
    }

    @Nested
    @DisplayName("lectura tolerante del JSON del catalogo")
    class LecturaTolerante {

        @Test
        @DisplayName("ignora los campos que no conoce y tolera los que faltan")
        void camposDesconocidosYAusentes() {
            String conExtras = """
                    {"id":"%s","nombre":"Espada","imagen":"img/espada.png","descripcion":"Filo","tipo":"ARMA",\
                    "tiraje":7,"precioCreditos":120,"precioMonedaReal":6000.50,"premium":true,"estado":"UNICO",\
                    "poderDeAtaque":40,"tasaDeCaida":12.5,"efectos":{"fuego":{"danio":3}},"version":2,\
                    "creadoEn":"2026-09-01T10:00:00Z","campoQueAunNoExiste":[1,2,3]}""".formatted(ESPADA);
            String sinOpcionales = """
                    {"id":"%s","nombre":"Escudo","tipo":"ARMADURA","estado":"ACTIVO"}""".formatted(ESCUDO);
            esperarPagina(0, pagina(0, 1, conExtras, sinOpcionales));

            List<ProductoDelCatalogo> productos = catalogo.productosEnVenta();

            ProductoDelCatalogo espada = productos.get(0);
            assertThat(espada.nombre()).isEqualTo("Espada");
            assertThat(espada.imagen()).isEqualTo("img/espada.png");
            assertThat(espada.descripcion()).isEqualTo("Filo");
            assertThat(espada.tipo()).isEqualTo("ARMA");
            assertThat(espada.tiraje()).isEqualTo(7);
            assertThat(espada.precioCreditos()).isEqualTo(120);
            assertThat(espada.precioMonedaReal()).isEqualByComparingTo(new BigDecimal("6000.50"));
            assertThat(espada.premium()).isTrue();
            assertThat(espada.estado()).isEqualTo("UNICO");
            assertThat(espada.habilidades()).isNull();

            ProductoDelCatalogo escudo = productos.get(1);
            assertThat(escudo.precioMonedaReal()).isNull();
            assertThat(escudo.tiraje()).isNull();
            assertThat(escudo.premium()).isNull();
            assertThat(escudo.imagen()).isNull();
        }

        @Test
        @DisplayName("habilidades llega como texto, como lista o no llega")
        void habilidadesEnSusTresFormas() {
            String comoTexto = """
                    {"id":"%s","estado":"ACTIVO","habilidades":"Golpe certero"}""".formatted(ESPADA);
            String comoLista = """
                    {"id":"%s","estado":"ACTIVO","habilidades":["Bloqueo","Contraataque"]}""".formatted(ESCUDO);
            String sinEllas = """
                    {"id":"%s","estado":"ACTIVO"}""".formatted(POCION);
            esperarPagina(0, pagina(0, 1, comoTexto, comoLista, sinEllas));

            List<ProductoDelCatalogo> productos = catalogo.productosEnVenta();

            assertThat(productos.get(0).habilidades()).isEqualTo("Golpe certero");
            assertThat(productos.get(1).habilidades()).isEqualTo(List.of("Bloqueo", "Contraataque"));
            assertThat(productos.get(2).habilidades()).isNull();
        }

        @Test
        @DisplayName("un elemento nulo en content se descarta")
        void elementoNulo() {
            esperarPagina(0, pagina(0, 1, "null", json(ESPADA, "Espada")));

            assertThat(catalogo.productosEnVenta()).extracting(ProductoDelCatalogo::id).containsExactly(ESPADA);
        }
    }

    @Nested
    @DisplayName("producto(id): un producto del catalogo")
    class UnProducto {

        private void esperarProducto(String id, ResponseCreator respuesta) {
            servidor.expect(requestTo(BASE + "/api/v1/productos/" + id))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(respuesta);
        }

        @Test
        @DisplayName("existente: lo devuelve, sea cual sea su estado")
        void existente() {
            esperarProducto(ESPADA, withSuccess(json(ESPADA, "Espada", "ARMA", "SUSPENDIDO", 3, "6000"),
                    MediaType.APPLICATION_JSON));

            assertThat(catalogo.producto(ESPADA)).hasValueSatisfying(producto -> {
                assertThat(producto.id()).isEqualTo(ESPADA);
                assertThat(producto.nombre()).isEqualTo("Espada");
                assertThat(producto.estado()).isEqualTo("SUSPENDIDO");
                assertThat(producto.tiraje()).isEqualTo(3);
            });
            servidor.verify();
        }

        @Test
        @DisplayName("404: no existe")
        void noExiste() {
            esperarProducto(ESPADA, withResourceNotFound());

            assertThat(catalogo.producto(ESPADA)).isEmpty();
            servidor.verify();
        }

        @Test
        @DisplayName("un id numerico lo decide el catalogo: con su 404, no existe")
        void idNumerico() {
            esperarProducto("1", withResourceNotFound());

            assertThat(catalogo.producto("1")).isEmpty();
            servidor.verify();
        }

        @Test
        @DisplayName("un id legible de siembra tambien se consulta: la tienda no exige UUID")
        void idLegible() {
            esperarProducto("p-heroe-e2e", withSuccess(json("p-heroe-e2e", "Heroe"), MediaType.APPLICATION_JSON));

            assertThat(catalogo.producto("p-heroe-e2e")).isPresent();
            servidor.verify();
        }

        @Test
        @DisplayName("400: el catalogo rechaza el identificador, asi que no existe")
        void rechazado() {
            esperarProducto("otro-id", withBadRequest());

            assertThat(catalogo.producto("otro-id")).isEmpty();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"../estadisticas", "a b", ".", "..", "-empieza-con-guion", "con/barra", "con?consulta",
                "12345678901234567890123456789012345678901234567890123456789012345"})
        @DisplayName("un id que no puede ser del catalogo no llega al catalogo")
        void idImposible(String id) {
            // Sin expectativas: cualquier peticion haria fallar la prueba.
            assertThat(catalogo.producto(id)).isEmpty();
            servidor.verify();
        }

        @Test
        @DisplayName("un 5xx es catalogo no disponible")
        void errorDelServidor() {
            esperarProducto(ESPADA, withServerError());

            assertThatThrownBy(() -> catalogo.producto(ESPADA))
                    .isInstanceOf(CatalogoNoDisponibleException.class)
                    .hasMessageContaining(ESPADA);
        }

        @Test
        @DisplayName("un 401 (el catalogo exige un token que no tenemos) es catalogo no disponible")
        void sinPermiso() {
            esperarProducto(ESPADA, withUnauthorizedRequest());

            assertThatThrownBy(() -> catalogo.producto(ESPADA)).isInstanceOf(CatalogoNoDisponibleException.class);
        }

        @Test
        @DisplayName("un tiempo de espera agotado es catalogo no disponible")
        void tiempoAgotado() {
            esperarProducto(ESPADA, withException(new SocketTimeoutException("connect timed out")));

            assertThatThrownBy(() -> catalogo.producto(ESPADA))
                    .isInstanceOf(CatalogoNoDisponibleException.class)
                    .hasRootCauseInstanceOf(SocketTimeoutException.class);
        }

        @Test
        @DisplayName("una respuesta sin cuerpo es catalogo no disponible")
        void sinCuerpo() {
            esperarProducto(ESPADA, withSuccess());

            assertThatThrownBy(() -> catalogo.producto(ESPADA))
                    .isInstanceOf(CatalogoNoDisponibleException.class)
                    .hasMessageContaining("sin cuerpo");
        }
    }

    @Nested
    @DisplayName("mal configurado")
    class MalConfigurado {

        @Test
        @DisplayName("con PRODUCTOS_URL vacia responde catalogo no disponible, no un 500")
        void urlVacia() {
            CatalogoMaestro sinUrl = new CatalogoMaestro(ConfiguracionDelCatalogo.constructorDelCliente(
                    new PropiedadesDelCatalogo("", Duration.ofMillis(200), Duration.ofMillis(200))).build());

            assertThatThrownBy(sinUrl::productosEnVenta).isInstanceOf(CatalogoNoDisponibleException.class);
            assertThatThrownBy(() -> sinUrl.producto(ESPADA)).isInstanceOf(CatalogoNoDisponibleException.class);
        }
    }
}
