package com.nexusbattles.ms_identidad.onboarding.cliente;

import com.nexusbattles.ms_identidad.auth.servicio.CredencialPropia;
import com.nexusbattles.ms_identidad.auth.servicio.EmisorDeTokensDeServicio;
import com.nexusbattles.ms_identidad.onboarding.ServidorFalso;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import com.nexusbattles.ms_identidad.onboarding.traza.Traza;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Los cuatro clientes del alta contra un servidor HTTP real: que envian lo
 * que dice el contrato de cada dueno, con credencial y traza, y como
 * traducen cada fallo a la causa que entiende el alta.
 */
@DisplayName("Clientes del alta del jugador (R17)")
class ClientesDelAltaTest {

    private static final UUID UID = UUID.fromString("0b7c1f00-1111-4222-8333-944455556666");
    private static final String TRAZA = "4bf92f3577b34da6a3ce929d0e0e4736";

    private ServidorFalso servidor;
    private CredencialPropia credencial;
    private final InterceptorDeTraza traza = new InterceptorDeTraza();

    @BeforeEach
    void preparar() throws Exception {
        servidor = new ServidorFalso();
        // Credencial real con un emisor simulado: un mock de la credencial no
        // sirve, porque RestClient compone los interceptores con sus metodos
        // por defecto (apply/andThen) y un mock los devuelve nulos.
        EmisorDeTokensDeServicio emisor = mock(EmisorDeTokensDeServicio.class);
        when(emisor.emitir(CredencialPropia.CLIENT_ID))
                .thenReturn(new EmisorDeTokensDeServicio.TokenEmitido("token-de-servicio", 900));
        credencial = new CredencialPropia(emisor);
        Traza.abrir(TRAZA);
    }

    @AfterEach
    void cerrar() {
        Traza.cerrar();
        servidor.close();
    }

    /** Un puerto donde no escucha nadie: conexion rechazada al instante. */
    private static String urlSinServidor() throws Exception {
        try (ServerSocket libre = new ServerSocket(0)) {
            return "http://127.0.0.1:" + libre.getLocalPort();
        }
    }

    @Nested
    @DisplayName("ms-finanzas")
    class Creditos {

        private ClienteCreditos cliente() {
            return new ClienteCreditos(servidor.url() + "/api/v1/", credencial, traza);
        }

        @Test
        @DisplayName("acredita con el contrato AcreditarCreditos, la credencial de servicio y la traza del alta")
        void acredita() {
            servidor.responder("POST", "/api/v1/creditos/acreditar", 200, """
                    {"transaccionId":"TX-ACR-0A1B2C3D","refId":"bono-registro-v1-x","estado":"APLICADO",
                     "montoAcreditado":500.00,"nuevoSaldoDisponible":500.00,"otroCampo":true}""");

            ClienteCreditos.Acreditacion respuesta = cliente().acreditar(UID, 500, "bono-registro-v1-x");

            assertThat(respuesta.transaccionId()).isEqualTo("TX-ACR-0A1B2C3D");
            assertThat(respuesta.montoAcreditado()).isEqualByComparingTo(new BigDecimal("500"));
            ServidorFalso.Peticion enviada = servidor.recibidas("POST", "/api/v1/creditos/acreditar").get(0);
            assertThat(enviada.cuerpo())
                    .contains("\"uid\":\"" + UID + "\"")
                    .contains("\"monto\":500")
                    .contains("\"refId\":\"bono-registro-v1-x\"")
                    .contains("\"concepto\":\"bono-registro\"");
            assertThat(enviada.cabecera("Authorization")).isEqualTo("Bearer token-de-servicio");
            assertThat(enviada.cabecera("traceparent")).startsWith("00-" + TRAZA + "-").endsWith("-01");
        }

        @Test
        @DisplayName("5xx y 429 son «no disponible»; 4xx es un rechazo con el cuerpo recortado")
        void traduceEstados() {
            servidor.responder("POST", "/api/v1/creditos/acreditar", 503, "{}");
            assertThatThrownBy(() -> cliente().acreditar(UID, 1, "r"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));

            servidor.responder("POST", "/api/v1/creditos/acreditar", 429, "{}");
            assertThatThrownBy(() -> cliente().acreditar(UID, 1, "r"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));

            servidor.responder("POST", "/api/v1/creditos/acreditar", 422, "{\"detail\":\"monto   no\n valido\"}");
            assertThatThrownBy(() -> cliente().acreditar(UID, 1, "r"))
                    .isInstanceOfSatisfying(PasoFallido.class, f -> {
                        assertThat(f.causa()).isEqualTo(Causa.RECHAZADO);
                        assertThat(f.getMessage()).contains("422").contains("monto no valido");
                    });

            // Credencial de servicio rechazada: es un rechazo (alguien tiene que
            // mirar el JWKS o el cliente), no un «ya se arreglara solo».
            servidor.responder("POST", "/api/v1/creditos/acreditar", 401, "{}");
            assertThatThrownBy(() -> cliente().acreditar(UID, 1, "r"))
                    .isInstanceOfSatisfying(PasoFallido.class, f -> {
                        assertThat(f.causa()).isEqualTo(Causa.RECHAZADO);
                        assertThat(f.getMessage()).contains("401");
                    });
        }

        @Test
        @DisplayName("una respuesta sin transaccion no se da por buena")
        void sinTransaccion() {
            servidor.responder("POST", "/api/v1/creditos/acreditar", 200, "{\"estado\":\"APLICADO\"}");
            assertThatThrownBy(() -> cliente().acreditar(UID, 1, "r"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.RECHAZADO));
        }

        @Test
        @DisplayName("servicio apagado: conexion rechazada es «no disponible», no una excepcion suelta")
        void apagado() throws Exception {
            ClienteCreditos apagado = new ClienteCreditos(urlSinServidor(), credencial, traza);
            assertThatThrownBy(() -> apagado.acreditar(UID, 1, "r"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));
        }

        @Test
        @DisplayName("sin URL configurada falta configuracion, no se inventa un destino")
        void sinUrl() {
            ClienteCreditos sinUrl = new ClienteCreditos("  ", credencial, traza);
            assertThatThrownBy(() -> sinUrl.acreditar(UID, 1, "r"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.CONFIGURACION_INCOMPLETA));
        }
    }

    @Nested
    @DisplayName("inventario")
    class Inventario {

        private ClienteInventario cliente() {
            return new ClienteInventario(servidor.url(), credencial, traza);
        }

        @Test
        @DisplayName("lista todas las paginas del jugador con X-User-Name = uid")
        void recorrePaginas() {
            servidor.responder("GET", "/api/v1/inventario/elementos", peticion -> {
                if ("pagina=0".equals(peticion.consulta())) {
                    return new ServidorFalso.Respuesta(200, """
                            {"elementos":[{"id":"e1","productoId":"p1","tipo":"HEROE","nombrePropio":"H",
                            "parteArmadura":null,"disponible":true,"subastaId":null}],
                            "numero":0,"tamanio":16,"totalElementos":2,"totalPaginas":2,"ultima":false}""");
                }
                return new ServidorFalso.Respuesta(200, """
                        {"elementos":[{"id":"e2","productoId":"p2","tipo":"ARMA","nombrePropio":"A",
                        "disponible":true}],"numero":1,"tamanio":16,"totalElementos":2,"totalPaginas":2,"ultima":true}""");
            });

            List<ClienteInventario.Elemento> elementos = cliente().elementosDe(UID);

            assertThat(elementos).extracting(ClienteInventario.Elemento::id).containsExactly("e1", "e2");
            assertThat(servidor.recibidas()).allSatisfy(p -> {
                assertThat(p.cabecera("X-User-Name")).isEqualTo(UID.toString());
                assertThat(p.cabecera("Authorization")).isEqualTo("Bearer token-de-servicio");
            });
        }

        @Test
        @DisplayName("un inventario vacio es una lista vacia, no un error")
        void vacio() {
            servidor.responder("GET", "/api/v1/inventario/elementos", 200,
                    "{\"elementos\":[],\"numero\":0,\"tamanio\":16,\"totalElementos\":0,\"totalPaginas\":0,\"ultima\":true}");
            assertThat(cliente().elementosDe(UID)).isEmpty();
        }

        @Test
        @DisplayName("crea con el cuerpo de CrearElementoRequest y devuelve el elemento creado")
        void crea() {
            servidor.responder("POST", "/api/v1/inventario/elementos", 201,
                    "{\"id\":\"nuevo\",\"productoId\":\"p9\",\"tipo\":\"ARMADURA\",\"nombrePropio\":\"Casco\","
                            + "\"parteArmadura\":\"CASCO\",\"disponible\":true}");

            ClienteInventario.Elemento creado = cliente().crear(UID, "p9", "ARMADURA", "Casco", "CASCO");

            assertThat(creado.id()).isEqualTo("nuevo");
            assertThat(servidor.recibidas("POST", "/api/v1/inventario/elementos").get(0).cuerpo())
                    .contains("\"productoId\":\"p9\"").contains("\"tipo\":\"ARMADURA\"")
                    .contains("\"nombrePropio\":\"Casco\"").contains("\"parteArmadura\":\"CASCO\"");
        }

        @Test
        @DisplayName("un 422 al crear (producto inexistente para el inventario) es un rechazo")
        void creaRechazado() {
            servidor.responder("POST", "/api/v1/inventario/elementos", 422, "{\"detail\":\"producto no existe\"}");
            assertThatThrownBy(() -> cliente().crear(UID, "p", "HEROE", "H", null))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.RECHAZADO));
        }

        @Test
        @DisplayName("equipar: 200 es hecho; 409 con el elemento ya puesto tambien; 409 sin el es un rechazo")
        void equipa() {
            servidor.responder("PUT", "/api/v1/inventario/heroes/h1/equipamiento/.*", 200,
                    "{\"heroeId\":\"h1\",\"armas\":[\"a1\"],\"armaduras\":{},\"items\":[]}");
            cliente().equipar(UID, "h1", "a1");

            servidor.responder("PUT", "/api/v1/inventario/heroes/h1/equipamiento/.*", 409, "{\"detail\":\"ya\"}");
            servidor.responder("GET", "/api/v1/inventario/heroes/h1/equipamiento", 200,
                    "{\"heroeId\":\"h1\",\"armas\":[],\"armaduras\":{\"CASCO\":\"c1\"},\"items\":[\"i1\"]}");
            cliente().equipar(UID, "h1", "c1");
            cliente().equipar(UID, "h1", "i1");

            assertThatThrownBy(() -> cliente().equipar(UID, "h1", "otro"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.RECHAZADO));
        }

        @Test
        @DisplayName("el equipamiento vacio se lee como vacio")
        void equipamientoVacio() {
            servidor.responder("GET", "/api/v1/inventario/heroes/h1/equipamiento", 200, "");
            ClienteInventario.Equipamiento vacio = cliente().equipamientoDe(UID, "h1");
            assertThat(vacio.contiene("x")).isFalse();
            assertThat(new ClienteInventario.Equipamiento("h", null, null, null).contiene("x")).isFalse();
        }

        @Test
        @DisplayName("inventario caido es «no disponible» en las cuatro operaciones")
        void caido() throws Exception {
            ClienteInventario caido = new ClienteInventario(urlSinServidor(), credencial, traza);
            assertThatThrownBy(() -> caido.elementosDe(UID)).isInstanceOf(PasoFallido.class);
            assertThatThrownBy(() -> caido.crear(UID, "p", "HEROE", "H", null)).isInstanceOf(PasoFallido.class);
            assertThatThrownBy(() -> caido.equipamientoDe(UID, "h")).isInstanceOf(PasoFallido.class);
            assertThatThrownBy(() -> caido.equipar(UID, "h", "e"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));
        }

        @Test
        @DisplayName("sin URL: configuracion incompleta")
        void sinUrl() {
            ClienteInventario sinUrl = new ClienteInventario(null, credencial, traza);
            assertThatThrownBy(() -> sinUrl.elementosDe(UID))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.CONFIGURACION_INCOMPLETA));
        }
    }

    @Nested
    @DisplayName("productos")
    class Productos {

        private ClienteProductos cliente() {
            return new ClienteProductos(servidor.url(), traza);
        }

        @Test
        @DisplayName("consulta el producto sin credencial (ruta publica) e ignora campos que no usa")
        void consulta() {
            servidor.responder("GET", "/api/v1/productos/aec4", 200,
                    "{\"id\":\"aec4\",\"nombre\":\"Guerrero Tanque\",\"tipo\":\"HEROE\",\"parte\":null,"
                            + "\"estado\":\"PUBLICADO\",\"precioCreditos\":300,\"version\":2}");

            ClienteProductos.Producto producto = cliente().consultar("aec4");

            assertThat(producto.tipo()).isEqualTo("HEROE");
            assertThat(producto.nombre()).isEqualTo("Guerrero Tanque");
            assertThat(servidor.recibidas().get(0).cabecera("Authorization")).isNull();
            assertThat(servidor.recibidas().get(0).cabecera("traceparent")).contains(TRAZA);
        }

        @Test
        @DisplayName("404: el kit apunta a un producto que no existe, es configuracion; 500 es «no disponible»")
        void errores() {
            servidor.responder("GET", "/api/v1/productos/.*", 404, "{}");
            assertThatThrownBy(() -> cliente().consultar("nada"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.CONFIGURACION_INCOMPLETA));

            servidor.responder("GET", "/api/v1/productos/.*", 500, "{}");
            assertThatThrownBy(() -> cliente().consultar("x"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));

            servidor.responder("GET", "/api/v1/productos/.*", 200, "{\"id\":\"x\"}");
            assertThatThrownBy(() -> cliente().consultar("x"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.RECHAZADO));
        }

        @Test
        @DisplayName("catalogo caido o sin URL")
        void caido() throws Exception {
            assertThatThrownBy(() -> new ClienteProductos(urlSinServidor(), traza).consultar("x"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));
            assertThatThrownBy(() -> new ClienteProductos("", traza).consultar("x"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.CONFIGURACION_INCOMPLETA));
        }
    }

    @Nested
    @DisplayName("admin-parametros")
    class Parametros {

        private ClienteParametros cliente() {
            return new ClienteParametros(servidor.url() + "/api/v1", traza);
        }

        @Test
        @DisplayName("el valor llega como texto o como numero; nulo o vacio es «sin decidir»")
        void valores() {
            servidor.responder("GET", "/api/v1/parametros/jugador.creditos-iniciales/valor", 200,
                    "{\"clave\":\"jugador.creditos-iniciales\",\"valor\":750,\"tipo\":\"ENTERO\",\"version\":2}");
            assertThat(cliente().valorDe("jugador.creditos-iniciales")).contains("750");

            servidor.responder("GET", "/api/v1/parametros/jugador.kit-inicial/valor", 200,
                    "{\"clave\":\"jugador.kit-inicial\",\"valor\":\" a,b \",\"tipo\":\"TEXTO\",\"version\":1}");
            assertThat(cliente().valorDe("jugador.kit-inicial")).contains("a,b");

            servidor.responder("GET", "/api/v1/parametros/sin.decidir/valor", 200,
                    "{\"clave\":\"sin.decidir\",\"valor\":null,\"tipo\":\"ENTERO\",\"version\":1}");
            assertThat(cliente().valorDe("sin.decidir")).isEmpty();

            servidor.responder("GET", "/api/v1/parametros/vacio/valor", 200, "{\"valor\":\"  \"}");
            assertThat(cliente().valorDe("vacio")).isEmpty();
        }

        @Test
        @DisplayName("una clave que el catalogo no tiene (404) es «sin valor»: admin-parametros sin la migracion nueva")
        void claveDesconocida() {
            servidor.responder("GET", "/api/v1/parametros/.*", 404, "{}");
            assertThat(cliente().valorDe("jugador.creditos-iniciales")).isEqualTo(Optional.empty());
        }

        @Test
        @DisplayName("servicio caido NO cae al respaldo: se reintenta, porque el valor puede ser otro")
        void caido() throws Exception {
            servidor.responder("GET", "/api/v1/parametros/.*", 500, "{}");
            assertThatThrownBy(() -> cliente().valorDe("x"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));
            assertThatThrownBy(() -> new ClienteParametros(urlSinServidor(), traza).valorDe("x"))
                    .isInstanceOfSatisfying(PasoFallido.class,
                            f -> assertThat(f.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE));
        }

        @Test
        @DisplayName("sin URL configurada no se consulta nada")
        void sinUrl() {
            assertThat(new ClienteParametros(null, traza).valorDe("x")).isEmpty();
        }
    }

    @Test
    @DisplayName("utilidades: la barra final sobra, un fallo desconocido es «no disponible», el cuerpo se recorta")
    void utilidades() {
        assertThat(ClientesHttp.sinBarraFinal(" http://a/b// ")).isEqualTo("http://a/b");
        assertThat(ClientesHttp.sinBarraFinal(null)).isEmpty();
        PasoFallido desconocido = ClientesHttp.traducir("x", "y", new IllegalStateException("raro"));
        assertThat(desconocido.causa()).isEqualTo(Causa.SERVICIO_NO_DISPONIBLE);
        assertThat(desconocido.getMessage()).contains("IllegalStateException");
        PasoFallido ya = new PasoFallido(Causa.RECHAZADO, "ya");
        assertThat(ClientesHttp.traducir("x", "y", ya)).isSameAs(ya);
        assertThat(ClientesHttp.recortar("a".repeat(500))).hasSize(203).endsWith("...");
        assertThat(ClientesHttp.recortar(null)).isEmpty();
    }
}
