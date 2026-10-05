package nexus.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nexus.dominio.Banner;
import nexus.persistencia.BannerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que sirve {@code GET /api/v1/banners/vigentes} es lo que promete el
 * esquema {@code Banner} de contracts/openapi/productos.yaml (1.6.0).
 *
 * <h2>El defecto que esta clase fija</h2>
 *
 * El esquema se habia escrito como {@code allOf: [SolicitudBanner, {id,
 * retirado, creadoEn, modificadoEn}]}, y {@code SolicitudBanner} cierra sus
 * propiedades con {@code additionalProperties: false}. En JSON Schema esa
 * restriccion solo conoce las propiedades de SU subesquema: un objeto con
 * {@code id} incumple la primera rama del {@code allOf}. El contrato
 * declaraba una forma que <b>ninguna respuesta real puede cumplir</b>, y un
 * doble generado desde el (la regla de backend para Pact) habria rechazado
 * todas las respuestas del servicio. Nada contrastaba el contrato con lo que
 * el servicio serializa de verdad.
 *
 * <p>No se usa el validador de OpenAPI de heroes a proposito: este servicio
 * ya trae springdoc (swagger-core en su variante jakarta) y ese validador
 * arrastra la variante javax de las mismas clases. Aqui se comprueba lo que
 * el contrato promete de este esquema concreto: no se compone sobre un
 * esquema cerrado, cada campo serializado esta declarado, cada campo
 * obligatorio llega y las fechas son instantes ISO-8601.
 */
@SpringBootTest(properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
@AutoConfigureMockMvc
@DisplayName("Banners frente a contracts/openapi/productos.yaml")
class ContratoDeBannersTest {

        /** El contrato publicado, el mismo que leen los consumidores (como los pactos). */
        private static final String CONTRATO = "../../../contracts/openapi/productos.yaml";

        private static final Set<String> FECHAS =
                Set.of("publicarDesde", "vigenteHasta", "creadoEn", "modificadoEn");

        private static final Pattern PROPIEDAD = Pattern.compile("^properties\\.([^.\\[]+)\\..*$");
        private static final Pattern OBLIGATORIO = Pattern.compile("^required\\[\\d+]$");

        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private JwtDecoder jwtDecoder;

        @MockitoBean
        private BannerRepository repositorio;

        @Test
        @DisplayName("el esquema Banner se declara completo, sin heredar el cierre de SolicitudBanner")
        void elEsquemaNoSeCierraSobreSiMismo() throws IOException {
                Map<String, String> banner = esquema("Banner");

                assertThat(banner.keySet())
                        .as("Banner no puede componerse con allOf sobre un esquema con"
                                + " additionalProperties: false: ninguna respuesta lo cumpliria")
                        .noneMatch(clave -> clave.startsWith("allOf"));
                assertThat(banner.get("type")).isEqualTo("object");
        }

        @Test
        @DisplayName("lo que serializa la consulta publica es exactamente lo que declara Banner")
        void vigentesCumplenElEsquema() throws Exception {
                Instant ahora = Instant.now();
                when(repositorio
                        .findByRetiradoFalseAndPublicarDesdeLessThanEqualAndVigenteHastaGreaterThanOrderByPublicarDesdeDesc(
                                any(Instant.class),
                                any(Instant.class)))
                        .thenReturn(List.of(new Banner(
                                "banner-1",
                                "Temporada de héroes",
                                ahora.minusSeconds(60),
                                ahora.plusSeconds(3600),
                                false,
                                ahora.minusSeconds(120),
                                ahora.minusSeconds(120))));

                String cuerpo = mvc.perform(get("/api/v1/banners/vigentes"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(StandardCharsets.UTF_8);
                JsonNode primero = JsonMapper.builder().build().readTree(cuerpo).get(0);
                Set<String> serializados = new TreeSet<>(primero.propertyNames());

                Map<String, String> esquema = esquema("Banner");
                Set<String> declarados = new TreeSet<>();
                Set<String> obligatorios = new TreeSet<>();
                esquema.forEach((clave, valor) -> {
                        Matcher propiedad = PROPIEDAD.matcher(clave);
                        if (propiedad.matches()) {
                                declarados.add(propiedad.group(1));
                        }
                        if (OBLIGATORIO.matcher(clave).matches()) {
                                obligatorios.add(valor);
                        }
                });

                assertThat(serializados)
                        .as("cada campo que sale del servicio esta declarado en el contrato")
                        .isEqualTo(declarados);
                assertThat(obligatorios)
                        .as("el contrato exige todos los campos que el servicio siempre envia")
                        .isEqualTo(new TreeSet<>(Arrays.stream(RespuestaBanner.class.getRecordComponents())
                                .map(componente -> componente.getName())
                                .toList()));
                for (String campo : FECHAS) {
                        assertThat(esquema.get("properties." + campo + ".format"))
                                .as("%s se declara como date-time", campo)
                                .isEqualTo("date-time");
                        assertThat(Instant.parse(primero.get(campo).asString()))
                                .as("%s viaja como instante ISO-8601", campo)
                                .isNotNull();
                }
        }

        /**
         * Un esquema de components.schemas aplanado como lo lee Spring
         * ({@code properties.id.type}, {@code required[0]}, {@code allOf[0].$ref}).
         */
        private static Map<String, String> esquema(String nombre) throws IOException {
                List<PropertySource<?>> fuentes = new YamlPropertySourceLoader()
                        .load("productos.yaml", new FileSystemResource(CONTRATO));
                EnumerablePropertySource<?> contrato = (EnumerablePropertySource<?>) fuentes.get(0);
                String prefijo = "components.schemas." + nombre + ".";
                Map<String, String> esquema = new TreeMap<>();
                for (String clave : contrato.getPropertyNames()) {
                        if (clave.startsWith(prefijo)) {
                                esquema.put(clave.substring(prefijo.length()),
                                        String.valueOf(contrato.getProperty(clave)));
                        }
                }
                assertThat(esquema).as("el contrato declara el esquema %s", nombre).isNotEmpty();
                return esquema;
        }
}
