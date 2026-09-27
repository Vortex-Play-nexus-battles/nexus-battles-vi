package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.moderacionsanciones.seguridad.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La lista negra por HTTP con la cadena de seguridad real y tokens firmados
 * de verdad (EmisorDeTokensDePrueba): jerarquia de roles, detalle solo para
 * servicio o moderacion, y el contrato 2.0.x de las respuestas.
 */
@WebMvcTest(controllers = {ListaNegraAdminController.class, ListaNegraVerificacionController.class})
@Import({SecurityConfig.class, DecodificadorDePrueba.class, ManejadorErroresListaNegra.class,
        SeguridadListaNegraTest.CacheDePruebaConfig.class})
@DisplayName("Lista negra · API y seguridad (moderacion-lista-negra.yaml 2.0.x)")
class SeguridadListaNegraTest {

    @TestConfiguration
    static class CacheDePruebaConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    private static final UUID UID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ListaNegraAdminService admin;

    @MockitoBean
    private VerificacionListaNegraService verificacion;

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    private MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static TerminoProhibido spiderman() {
        return new TerminoProhibido("spiderman", "spiderman", CategoriaDeTermino.MARCA, ModoDeCoincidencia.SUBCADENA,
                true, "semilla", OffsetDateTime.of(2026, 9, 25, 10, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("sin token, el panel es 401 con problem details, no el cuerpo vacio de Spring")
    void sinToken() throws Exception {
        mvc.perform(get("/api/v1/lista-negra/terminos"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/no-autenticado"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/v1/lista-negra/terminos"));
        verifyNoInteractions(admin);
    }

    @Test
    @DisplayName("un jugador o un servicio no administran la lista: 403 con problem details")
    void jugadorYServicio() throws Exception {
        mvc.perform(con(get("/api/v1/lista-negra/terminos"), emisor.tokenDeJugador("lyra", UID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/permiso-insuficiente"))
                .andExpect(jsonPath("$.motivo").value("PERMISO_INSUFICIENTE"));
        mvc.perform(con(get("/api/v1/lista-negra/terminos"), emisor.tokenDeServicio("comentarios")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }

    @ParameterizedTest(name = "{0} administra la lista")
    @ValueSource(strings = {"MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
    @DisplayName("jerarquia de roles: el Super Administrador ya no recibe 403")
    void jerarquia(String rol) throws Exception {
        when(admin.listar(any())).thenReturn(new PageImpl<>(List.of(spiderman()), PageRequest.of(0, 50), 1));

        mvc.perform(con(get("/api/v1/lista-negra/terminos"), emisor.tokenDeUsuario("staff", UID, rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].termino").value("spiderman"))
                .andExpect(jsonPath("$.contenido[0].normalizado").value("spiderman"))
                .andExpect(jsonPath("$.contenido[0].categoria").value("MARCA"))
                .andExpect(jsonPath("$.contenido[0].modo").value("SUBCADENA"))
                .andExpect(jsonPath("$.contenido[0].activo").value(true))
                .andExpect(jsonPath("$.contenido[0].creadoPor").value("semilla"))
                .andExpect(jsonPath("$.contenido[0].creadoEn").exists())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(50))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @DisplayName("los filtros llegan al servicio tal cual; una categoria desconocida es 400")
    void filtros() throws Exception {
        when(admin.listar(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 16), 0));
        String token = emisor.tokenDeUsuario("mod", UID, "MODERADOR");

        mvc.perform(con(get("/api/v1/lista-negra/terminos?categoria=MARCA&activo=false&buscar=spi&pagina=1&tamano=16"),
                        token))
                .andExpect(status().isOk());
        verify(admin).listar(new ListaNegraAdminService.Filtro(CategoriaDeTermino.MARCA, false, "spi", 1, 16));

        mvc.perform(con(get("/api/v1/lista-negra/terminos?categoria=DEPORTISTA"), token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("alta: 201 con el termino; el autor es el apodo del token, no el cuerpo")
    void alta() throws Exception {
        when(admin.agregar(any(), eq("mod_ana"))).thenReturn(spiderman());

        mvc.perform(con(post("/api/v1/lista-negra/terminos"), emisor.tokenDeUsuario("mod_ana", UID, "MODERADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termino\":\"spiderman\",\"categoria\":\"MARCA\",\"creadoPor\":\"otro\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.normalizado").value("spiderman"));
        verify(admin).agregar(new ListaNegraAdminService.DatosDeTermino("spiderman", CategoriaDeTermino.MARCA,
                null, null), "mod_ana");
    }

    @Test
    @DisplayName("409, 404 y 400 del servicio salen como problem details con su tipo")
    void errores() throws Exception {
        String token = emisor.tokenDeUsuario("admin", UID, "ADMINISTRADOR");
        when(admin.agregar(any(), any())).thenThrow(new TerminoDuplicadoException("Spider-Man", "spiderman"));
        when(admin.editar(eq("fantasma"), any(), any())).thenThrow(new TerminoNoEncontradoException("fantasma"));

        mvc.perform(con(post("/api/v1/lista-negra/terminos"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"termino\":\"Spider-Man\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/termino-duplicado"))
                .andExpect(jsonPath("$.motivo").value("TERMINO_DUPLICADO"));
        mvc.perform(con(put("/api/v1/lista-negra/terminos/fantasma"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"termino\":\"nuevo\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/termino-no-encontrado"));
        mvc.perform(con(post("/api/v1/lista-negra/terminos"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"termino\":\"x\",\"categoria\":\"RARA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("edicion y baja con el termino en el camino; la baja es 204")
    void edicionYBaja() throws Exception {
        String token = emisor.tokenDeUsuario("super", UID, "SUPER_ADMINISTRADOR");
        when(admin.editar(eq("spiderman"), any(), eq("super"))).thenReturn(spiderman());

        mvc.perform(con(put("/api/v1/lista-negra/terminos/spiderman"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termino\":\"spiderman\",\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termino").value("spiderman"));
        mvc.perform(con(delete("/api/v1/lista-negra/terminos/spiderman"), token))
                .andExpect(status().isNoContent());
        verify(admin).eliminar("spiderman", "super");
    }

    @Test
    @DisplayName("verificar es publica: sin token responde, pero se pide SIN detalle")
    void verificarSinToken() throws Exception {
        when(verificacion.verificar("Spider-Man", ContextoDeTexto.APODO, false))
                .thenReturn(new VerificacionListaNegraService.ResultadoVerificacion(false, AccionDeModeracion.RECHAZAR,
                        "El apodo no está permitido", null, null));

        mvc.perform(post("/api/v1/lista-negra/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Spider-Man\",\"contexto\":\"APODO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobado").value(false))
                .andExpect(jsonPath("$.accion").value("RECHAZAR"))
                .andExpect(jsonPath("$.motivo").value("El apodo no está permitido"))
                .andExpect(jsonPath("$.categoria").doesNotExist())
                .andExpect(jsonPath("$.coincidencias").doesNotExist());
    }

    @Test
    @DisplayName("aprobado: solo aprobado y PERMITIR, sin campos nulos")
    void verificarAprobado() throws Exception {
        when(verificacion.verificar("hola", null, false))
                .thenReturn(new VerificacionListaNegraService.ResultadoVerificacion(true, AccionDeModeracion.PERMITIR,
                        null, null, null));

        mvc.perform(post("/api/v1/lista-negra/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"hola\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprobado").value(true))
                .andExpect(jsonPath("$.accion").value("PERMITIR"))
                .andExpect(jsonPath("$.motivo").doesNotExist());
    }

    @Test
    @DisplayName("con token de servicio o de moderacion (con jerarquia) se pide CON detalle; con uno de jugador no")
    void detallePorRol() throws Exception {
        when(verificacion.verificar(eq("spiderman"), eq(ContextoDeTexto.CHAT_SALA), anyBoolean()))
                .thenReturn(new VerificacionListaNegraService.ResultadoVerificacion(false, AccionDeModeracion.BLOQUEAR,
                        "El mensaje contiene un término no permitido y no se envió.", CategoriaDeTermino.MARCA,
                        List.of("spiderman")));
        String cuerpo = "{\"texto\":\"spiderman\",\"contexto\":\"CHAT_SALA\"}";

        mvc.perform(con(post("/api/v1/lista-negra/verificar"), emisor.tokenDeServicio("salas-partidas"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria").value("MARCA"))
                .andExpect(jsonPath("$.coincidencias[0]").value("spiderman"));
        verify(verificacion).verificar("spiderman", ContextoDeTexto.CHAT_SALA, true);

        mvc.perform(con(post("/api/v1/lista-negra/verificar"), emisor.tokenDeUsuario("super", UID, "SUPER_ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
        mvc.perform(con(post("/api/v1/lista-negra/verificar"), emisor.tokenDeJugador("lyra", UID))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());
        verify(verificacion, org.mockito.Mockito.times(2)).verificar("spiderman", ContextoDeTexto.CHAT_SALA, true);
        verify(verificacion).verificar("spiderman", ContextoDeTexto.CHAT_SALA, false);
    }

    @Test
    @DisplayName("un token presente pero invalido en verificar es 401: no se le trata como anonimo")
    void tokenInvalido() throws Exception {
        mvc.perform(con(post("/api/v1/lista-negra/verificar"), emisor.tokenCaducado("lyra", UID))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/no-autenticado"));
        verifyNoInteractions(verificacion);
    }

    @Test
    @DisplayName("un contexto que no es del contrato es 400")
    void contextoDesconocido() throws Exception {
        mvc.perform(post("/api/v1/lista-negra/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"hola\",\"contexto\":\"FORO\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verificacion);
    }

    @Test
    @DisplayName("el 400 del servicio (texto vacio o largo) sale como solicitud-invalida")
    void textoInvalido() throws Exception {
        when(verificacion.verificar(eq(""), any(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("El texto a verificar no puede estar vacío"));

        mvc.perform(post("/api/v1/lista-negra/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/solicitud-invalida"))
                .andExpect(jsonPath("$.motivo").value("SOLICITUD_INVALIDA"));
    }
}
