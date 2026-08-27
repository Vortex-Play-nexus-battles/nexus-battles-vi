package nexus.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import nexus.dominio.Producto;
import nexus.persistencia.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest
@AutoConfigureMockMvc
class ProductosApiTest {

        private static final String PRODUCTO_VALIDO = """
                {
                  "nombre": "Arma de prueba",
                  "imagen": "productos/arma-prueba.webp",
                  "descripcion": "Arma creada para verificar el contrato",
                  "tipo": "ARMA",
                  "tiraje": 100,
                  "precioCreditos": 500,
                  "premium": false,
                  "poderDeAtaque": 25,
                  "tasaDeCaida": 10
                }
                """;

        private static final String PRODUCTO_HEROE_VALIDO = """
                {
                  "nombre": "Heroe de prueba",
                  "imagen": "productos/heroe-prueba.webp",
                  "descripcion": "Heroe creado para verificar el contrato",
                  "tipo": "HEROE",
                  "tiraje": -1,
                  "precioCreditos": 1000,
                  "premium": false,
                  "prototipo": "Guerrero Tanque"
                }
                """;

        private static final String PRODUCTO_HABILIDAD_VALIDO = """
                {
                  "nombre": "Habilidad de prueba",
                  "imagen": "productos/habilidad-prueba.webp",
                  "descripcion": "Habilidad creada para verificar el contrato",
                  "tipo": "HABILIDAD",
                  "tiraje": 50,
                  "precioCreditos": 800,
                  "premium": false,
                  "heroe": "550e8400-e29b-41d4-a716-446655440000",
                  "costoPoder": 3,
                  "multiplicadorNivel": 1.5,
                  "turnosCarga": 2
                }
                """;

        private static final String PRODUCTO_ARMADURA_VALIDO = """
                {
                  "nombre": "Armadura de prueba",
                  "imagen": "productos/armadura-prueba.webp",
                  "descripcion": "Armadura creada para verificar el contrato",
                  "tipo": "ARMADURA",
                  "tiraje": 75,
                  "precioCreditos": 1200,
                  "premium": false,
                  "defensa": 30,
                  "parte": "PECHO",
                  "tasaDeCaida": 12.5
                }
                """;
        private static final String PRODUCTO_ITEM_VALIDO = """
                {
                  "nombre": "Pocion de prueba",
                  "imagen": "productos/pocion-prueba.webp",
                  "descripcion": "Item creado para verificar el contrato",
                  "tipo": "ITEM",
                  "tiraje": 200,
                  "precioCreditos": 250,
                  "premium": false,
                  "efecto": "Recupera 20 puntos de vida",
                  "tasaDeCaida": 35.0
                }
                """;
        private static final String PRODUCTO_EPICA_VALIDO = """
                {
                  "nombre": "Epica de prueba",
                  "imagen": "productos/epica-prueba.webp",
                  "descripcion": "Habilidad epica creada para verificar el contrato",
                  "tipo": "EPICA",
                  "tiraje": 25,
                  "precioCreditos": 5000,
                  "premium": false,
                  "heroe": "550e8400-e29b-41d4-a716-446655440000",
                  "turnosRecarga": 4,
                  "efectoGeneral": "Aumenta el poder de todo el equipo",
                  "efectoPotenciado": "Duplica el poder durante dos turnos"
                }
                """;

        @Autowired
        private MockMvc mvc;
        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private ProductoRepository productoRepository;

        @BeforeEach
        void simularPersistencia() {
                when(productoRepository.save(any(Producto.class)))
                        .thenAnswer(invocacion ->
                                invocacion.getArgument(0, Producto.class));
        }


        @Test
        @DisplayName("rechaza la creacion cuando no se envia un token")
        void requiereAutenticacion() throws Exception {
                mvc.perform(post("/api/v1/productos")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(PRODUCTO_VALIDO))
                        .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("rechaza un jugador autenticado sin permiso administrativo")
        void rechazaUsuarioSinPermiso() throws Exception {
                publicarComo("ROLE_JUGADOR", PRODUCTO_VALIDO)
                        .andExpect(status().isForbidden());
        }

        @ParameterizedTest(name = "permite crear productos con {0}")
        @ValueSource(strings = {
                "ROLE_ADMINISTRADOR",
                "ROLE_SUPER_ADMINISTRADOR"
        })
        void permiteRolesAdministrativos(String autoridad) throws Exception {
                publicarComo(autoridad, PRODUCTO_VALIDO)
                        .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "rechaza la ausencia del campo obligatorio {0}")
        @ValueSource(strings = {
                "nombre",
                "imagen",
                "descripcion",
                "tipo",
                "tiraje",
                "premium"
        })
        void rechazaCamposObligatoriosAusentes(String campo) throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", sinCampo(PRODUCTO_VALIDO, campo))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rechaza un tipo de producto no permitido")
        void rechazaTipoNoPermitido() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"tipo\": \"ARMA\"",
                        "\"tipo\": \"POCION\"");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rechaza un precio en creditos negativo")
        void rechazaPrecioNegativo() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"precioCreditos\": 500",
                        "\"precioCreditos\": -1");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rechaza tiraje cero")
        void rechazaTirajeCero() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"tiraje\": 100",
                        "\"tiraje\": 0");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

                @Test
        @DisplayName("un producto premium exige precio en moneda real")
        void premiumExigePrecioMonedaReal() throws Exception {
                String solicitud = PRODUCTO_VALIDO.replace(
                        "\"premium\": false",
                        "\"premium\": true");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("un producto no premium exige precio en creditos")
        void noPremiumExigePrecioCreditos() throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_VALIDO, "precioCreditos"))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("un arma exige poder de ataque")
        void armaExigePoderDeAtaque() throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_VALIDO, "poderDeAtaque"))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("un arma exige tasa de caida")
        void armaExigeTasaDeCaida() throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_VALIDO, "tasaDeCaida"))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("permite crear un heroe con prototipo valido")
        void permiteCrearHeroeConPrototipoValido() throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", PRODUCTO_HEROE_VALIDO)
                        .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("un heroe exige un prototipo")
        void heroeExigePrototipo() throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_HEROE_VALIDO, "prototipo"))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("rechaza un prototipo de heroe no permitido")
        void rechazaPrototipoNoPermitido() throws Exception {
                String solicitud = PRODUCTO_HEROE_VALIDO.replace(
                        "\"prototipo\": \"Guerrero Tanque\"",
                        "\"prototipo\": \"Nigromante\"");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("permite crear una habilidad con atributos validos")
        void permiteCrearHabilidadValida() throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", PRODUCTO_HABILIDAD_VALIDO)
                        .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "una habilidad exige el campo {0}")
        @ValueSource(strings = {
                "heroe",
                "costoPoder",
                "multiplicadorNivel",
                "turnosCarga"
        })
        void habilidadExigeSusAtributos(String campo) throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_HABILIDAD_VALIDO, campo))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una habilidad exige un identificador de heroe valido")
        void habilidadRechazaHeroeSinUuid() throws Exception {
                String solicitud = PRODUCTO_HABILIDAD_VALIDO.replace(
                        "\"heroe\": \"550e8400-e29b-41d4-a716-446655440000\"",
                        "\"heroe\": \"identificador-invalido\"");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una habilidad exige costo de poder mayor que cero")
        void habilidadRechazaCostoPoderCero() throws Exception {
                String solicitud = PRODUCTO_HABILIDAD_VALIDO.replace(
                        "\"costoPoder\": 3",
                        "\"costoPoder\": 0");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una habilidad exige multiplicador mayor que cero")
        void habilidadRechazaMultiplicadorCero() throws Exception {
                String solicitud = PRODUCTO_HABILIDAD_VALIDO.replace(
                        "\"multiplicadorNivel\": 1.5",
                        "\"multiplicadorNivel\": 0");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una habilidad no permite turnos de carga negativos")
        void habilidadRechazaTurnosCargaNegativos() throws Exception {
                String solicitud = PRODUCTO_HABILIDAD_VALIDO.replace(
                        "\"turnosCarga\": 2",
                        "\"turnosCarga\": -1");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

                @Test
        @DisplayName("permite crear una armadura con atributos validos")
        void permiteCrearArmaduraValida() throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", PRODUCTO_ARMADURA_VALIDO)
                        .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "una armadura exige el campo {0}")
        @ValueSource(strings = {
                "defensa",
                "parte",
                "tasaDeCaida"
        })
        void armaduraExigeSusAtributos(String campo) throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_ARMADURA_VALIDO, campo))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una armadura exige defensa mayor que cero")
        void armaduraRechazaDefensaCero() throws Exception {
                String solicitud = PRODUCTO_ARMADURA_VALIDO.replace(
                        "\"defensa\": 30",
                        "\"defensa\": 0");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una armadura rechaza una parte no permitida")
        void armaduraRechazaParteNoPermitida() throws Exception {
                String solicitud = PRODUCTO_ARMADURA_VALIDO.replace(
                        "\"parte\": \"PECHO\"",
                        "\"parte\": \"CAPA\"");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "una armadura rechaza la tasa de caida {0}")
        @ValueSource(strings = {"-0.1", "100.1"})
        void armaduraRechazaTasaFueraDeRango(String tasa) throws Exception {
                String solicitud = PRODUCTO_ARMADURA_VALIDO.replace(
                        "\"tasaDeCaida\": 12.5",
                        "\"tasaDeCaida\": " + tasa);

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

                @Test
        @DisplayName("permite crear un item con atributos validos")
        void permiteCrearItemValido() throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", PRODUCTO_ITEM_VALIDO)
                        .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "un item exige el campo {0}")
        @ValueSource(strings = {
                "efecto",
                "tasaDeCaida"
        })
        void itemExigeSusAtributos(String campo) throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_ITEM_VALIDO, campo))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("un item rechaza un efecto vacio")
        void itemRechazaEfectoVacio() throws Exception {
                String solicitud = PRODUCTO_ITEM_VALIDO.replace(
                        "\"efecto\": \"Recupera 20 puntos de vida\"",
                        "\"efecto\": \"   \"");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "un item rechaza la tasa de caida {0}")
        @ValueSource(strings = {"-0.1", "100.1"})
        void itemRechazaTasaFueraDeRango(String tasa) throws Exception {
                String solicitud = PRODUCTO_ITEM_VALIDO.replace(
                        "\"tasaDeCaida\": 35.0",
                        "\"tasaDeCaida\": " + tasa);

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("permite crear una epica con atributos validos")
        void permiteCrearEpicaValida() throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", PRODUCTO_EPICA_VALIDO)
                        .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "una epica exige el campo {0}")
        @ValueSource(strings = {
                "heroe",
                "turnosRecarga",
                "efectoGeneral",
                "efectoPotenciado"
        })
        void epicaExigeSusAtributos(String campo) throws Exception {
                publicarComo(
                        "ROLE_ADMINISTRADOR",
                        sinCampo(PRODUCTO_EPICA_VALIDO, campo))
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una epica exige un identificador de heroe valido")
        void epicaRechazaHeroeSinUuid() throws Exception {
                String solicitud = PRODUCTO_EPICA_VALIDO.replace(
                        "\"heroe\": \"550e8400-e29b-41d4-a716-446655440000\"",
                        "\"heroe\": \"identificador-invalido\"");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("una epica no permite turnos de recarga negativos")
        void epicaRechazaTurnosRecargaNegativos() throws Exception {
                String solicitud = PRODUCTO_EPICA_VALIDO.replace(
                        "\"turnosRecarga\": 4",
                        "\"turnosRecarga\": -1");

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "una epica rechaza el campo vacio {0}")
        @ValueSource(strings = {
                "efectoGeneral",
                "efectoPotenciado"
        })
        void epicaRechazaEfectosVacios(String campo) throws Exception {
                String solicitud;

                if (campo.equals("efectoGeneral")) {
                        solicitud = PRODUCTO_EPICA_VALIDO.replace(
                                "\"efectoGeneral\": \"Aumenta el poder de todo el equipo\"",
                                "\"efectoGeneral\": \"   \"");
                } else {
                        solicitud = PRODUCTO_EPICA_VALIDO.replace(
                                "\"efectoPotenciado\": \"Duplica el poder durante dos turnos\"",
                                "\"efectoPotenciado\": \"   \"");
                }

                publicarComo("ROLE_ADMINISTRADOR", solicitud)
                        .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("responde el producto creado y su ubicacion")
        void respondeProductoCreado() throws Exception {
                publicarComo("ROLE_ADMINISTRADOR", PRODUCTO_VALIDO)
                        .andExpect(status().isCreated())
                        .andExpect(header().string(
                                "Location",
                                matchesPattern(
                                        "^/api/v1/productos/"
                                                + "[0-9a-fA-F]{8}-"
                                                + "[0-9a-fA-F]{4}-"
                                                + "[0-9a-fA-F]{4}-"
                                                + "[0-9a-fA-F]{4}-"
                                                + "[0-9a-fA-F]{12}$")))
                        .andExpect(jsonPath("$.id").isString())
                        .andExpect(jsonPath("$.nombre").value("Arma de prueba"))
                        .andExpect(jsonPath("$.tipo").value("ARMA"))
                        .andExpect(jsonPath("$.prototipo").doesNotExist())
                        .andExpect(jsonPath("$.heroe").doesNotExist())
                        .andExpect(jsonPath("$.estado").value("ACTIVO"))
                        .andExpect(jsonPath("$.version").value(1))
                        .andExpect(jsonPath("$.creadoEn").exists())
                        .andExpect(jsonPath("$.modificadoEn").exists());
        }


        private ResultActions publicarComo(String autoridad, String cuerpo) throws Exception {
                return mvc.perform(post("/api/v1/productos")
                        .with(jwt().authorities(new SimpleGrantedAuthority(autoridad)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo));
        }

        private static String sinCampo(String json, String campo) {
        String resultado = json.replaceFirst(
                "(?m)^\\s*\"" + campo + "\"\\s*:\\s*.*\\R",
                "");

        return resultado.replaceFirst(",\\s*}", "\n}");

    }

}