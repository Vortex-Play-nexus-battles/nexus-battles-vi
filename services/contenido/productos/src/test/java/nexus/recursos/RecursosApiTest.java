package nexus.recursos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = "KEYCLOAK_JWK_SET_URI=http://localhost/prueba/jwks")
@AutoConfigureMockMvc
@DisplayName("API de recursos gráficos (productos.yaml 1.10.0)")
class RecursosApiTest {

    private static final String RUTA = "/api/v1/productos/recursos";
    private static final String ADMIN = "0f3c1a5e-2b7d-4e8f-9a01-23456789abcd";
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private GestionarRecursosServicio servicio;

    private static RequestPostProcessor como(String rol) {
        return jwt().jwt(j -> j.subject(ADMIN)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static RecursoGrafico recurso(String id) {
        return new RecursoGrafico(id, ClaseDeRecurso.IMAGEN, FormatoDeRecurso.PNG, 3, 4, 4, "abc123",
                new byte[] {(byte) 0x89, 'P', 'N'}, null, "espada.png", ADMIN, AHORA, null, null, null);
    }

    private static MockMultipartFile archivo() {
        return new MockMultipartFile("archivo", "espada.png", "image/png", ArchivosDePrueba.png(8, 8));
    }

    @Test
    @DisplayName("subir exige administración: sin token 401, jugador y moderador 403")
    void subirExigeAdministracion() throws Exception {
        mvc.perform(multipart(RUTA).file(archivo()).param("clase", "IMAGEN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(RUTA).file(archivo()).param("clase", "IMAGEN").with(como("JUGADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(RUTA).file(archivo()).param("clase", "IMAGEN").with(como("MODERADOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("un administrador sube: 201 con la ubicación; el mismo archivo otra vez, 200")
    void subir() throws Exception {
        RecursoGrafico guardado = recurso("r-1");
        RecursoGraficoVista vista = RecursoGraficoVista.de(guardado, List.of());
        when(servicio.subir(any(), eq(ClaseDeRecurso.IMAGEN), eq("p-1"), eq("espada.png"), eq(ADMIN)))
                .thenReturn(new GestionarRecursosServicio.Subida(guardado, true));
        when(servicio.vista(guardado)).thenReturn(vista);

        mvc.perform(multipart(RUTA).file(archivo()).param("clase", "imagen").param("productoId", "p-1")
                        .with(como("ADMINISTRADOR")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/productos/recursos/r-1"))
                .andExpect(jsonPath("$.id").value("r-1"))
                .andExpect(jsonPath("$.clase").value("IMAGEN"))
                .andExpect(jsonPath("$.formato").value("PNG"))
                .andExpect(jsonPath("$.tipoContenido").value("image/png"))
                .andExpect(jsonPath("$.url").value("/api/v1/productos/recursos/r-1"))
                .andExpect(jsonPath("$.contenido").doesNotExist())
                .andExpect(jsonPath("$.huella").doesNotExist());

        when(servicio.subir(any(), eq(ClaseDeRecurso.IMAGEN), isNull(), eq("espada.png"), eq(ADMIN)))
                .thenReturn(new GestionarRecursosServicio.Subida(guardado, false));
        mvc.perform(multipart(RUTA).file(archivo()).param("clase", "IMAGEN").with(como("SUPER_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("r-1"));
    }

    @Test
    @DisplayName("un archivo que no cumple dice el requisito y su motivo, con el código de su motivo")
    void rechazos() throws Exception {
        when(servicio.subir(any(), any(), any(), any(), any()))
                .thenThrow(new RecursoNoAdmitidoException(RecursoNoAdmitidoException.Motivo.FORMATO_NO_ADMITIDO,
                        "Icono admite PNG o WebP; este archivo es JPEG."));
        mvc.perform(multipart(RUTA).file(archivo()).param("clase", "ICONO").with(como("ADMINISTRADOR")))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Icono admite PNG o WebP; este archivo es JPEG."))
                .andExpect(jsonPath("$.motivo").value("FORMATO_NO_ADMITIDO"));

        mvc.perform(multipart(RUTA).file(archivo()).param("clase", "VIDEO").with(como("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.motivo").value("CLASE_INVALIDA"))
                .andExpect(jsonPath("$.detail").value("La clase tiene que ser IMAGEN, ICONO o ANIMACION."));
    }

    @Test
    @DisplayName("la biblioteca es de administración y pagina de 16 en 16")
    void biblioteca() throws Exception {
        when(servicio.listar(eq(ClaseDeRecurso.ANIMACION), isNull(), eq(false), eq(0), eq(16)))
                .thenReturn(new PaginaDeRecursos(
                        List.of(RecursoGraficoVista.de(recurso("r-1"),
                                List.of(new RecursoGraficoVista.UsoDeRecurso("p-1", "Espada larga")))),
                        0, 16, 1, 1));

        mvc.perform(get(RUTA).param("clase", "ANIMACION")).andExpect(status().isUnauthorized());
        mvc.perform(get(RUTA).param("clase", "ANIMACION").with(como("JUGADOR"))).andExpect(status().isForbidden());
        mvc.perform(get(RUTA).param("clase", "ANIMACION").with(como("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("r-1"))
                .andExpect(jsonPath("$.content[0].usadoEn[0].nombre").value("Espada larga"))
                .andExpect(jsonPath("$.size").value(16))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get(RUTA).param("size", "51").with(como("ADMINISTRADOR"))).andExpect(status().isBadRequest());
        mvc.perform(get(RUTA).param("clase", "VIDEO").with(como("ADMINISTRADOR"))).andExpect(status().isBadRequest());
        verify(servicio, never()).listar(any(), any(), anyBoolean(), anyInt(), eq(51));
    }

    @Test
    @DisplayName("los bytes son públicos, con su tipo, nosniff, sin ejecutar nada y en caché; con la misma ETag, 304")
    void contenido() throws Exception {
        when(servicio.obtener("r-1")).thenReturn(recurso("r-1"));

        mvc.perform(get(RUTA + "/r-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {(byte) 0x89, 'P', 'N'}))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andExpect(header().string("ETag", "\"abc123\""));

        mvc.perform(get(RUTA + "/r-1").header("If-None-Match", "\"abc123\""))
                .andExpect(status().isNotModified());

        when(servicio.obtener("nada")).thenThrow(new RecursoNoEncontradoException());
        mvc.perform(get(RUTA + "/nada"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe ese recurso gráfico."));
    }

    @Test
    @DisplayName("retirar es de administración, lleva el motivo y no deja sin imagen a un producto")
    void retirar() throws Exception {
        RecursoGrafico retirado = recurso("r-2").retirar(ADMIN, AHORA, "Duplicada");
        when(servicio.retirar("r-2", ADMIN, "Duplicada")).thenReturn(retirado);
        when(servicio.vista(retirado)).thenReturn(RecursoGraficoVista.de(retirado, List.of()));
        when(servicio.retirar("r-1", ADMIN, "Ya no sirve"))
                .thenThrow(new RecursoEnUsoException("Espada larga"));

        mvc.perform(delete(RUTA + "/r-2").param("motivo", "Duplicada")).andExpect(status().isUnauthorized());
        mvc.perform(delete(RUTA + "/r-2").param("motivo", "Duplicada").with(como("JUGADOR")))
                .andExpect(status().isForbidden());
        mvc.perform(delete(RUTA + "/r-2").param("motivo", "Duplicada").with(como("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retiradoPor").value(ADMIN))
                .andExpect(jsonPath("$.motivoRetiro").value("Duplicada"));
        mvc.perform(delete(RUTA + "/r-1").param("motivo", "Ya no sirve").with(como("ADMINISTRADOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Es la imagen actual de «Espada larga». Ponle otra imagen antes de retirarla."));

        when(servicio.retirar("r-3", ADMIN, "x")).thenThrow(new MotivoInvalidoException("El motivo admite como máximo 500 caracteres."));
        mvc.perform(delete(RUTA + "/r-3").param("motivo", "x").with(como("ADMINISTRADOR")))
                .andExpect(status().isBadRequest());
    }
}
