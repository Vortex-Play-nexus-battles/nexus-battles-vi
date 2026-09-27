package com.nexusbattles.plataforma.comentarios.imagenes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexusbattles.comun.seguridad.pruebas.DecodificadorDePrueba;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import com.nexusbattles.plataforma.comentarios.publicacion.ManejadorErroresComentarios;
import com.nexusbattles.plataforma.comentarios.seguridad.SecurityConfig;

/**
 * {@code /comentarios/imagenes} por HTTP — contrato 1.4.0, B3: que solo los
 * bytes del campo {@code archivo} llegan al servicio (ni el nombre ni el tipo
 * que declara el navegador), que cada rechazo tiene su codigo, y que la
 * descarga sale con las cabeceras que la hacen inofensiva.
 */
@WebMvcTest(ImagenesController.class)
@Import({ManejadorErroresComentarios.class, SecurityConfig.class, DecodificadorDePrueba.class})
class ImagenesControllerTest {

    private static final String RUTA = "/api/v1/comentarios/imagenes";
    private static final UUID UID_LYRA = UUID.fromString("7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55");
    private static final String IMAGEN = "3f1c2b4a-1111-4222-8333-944455566677";

    private final EmisorDeTokensDePrueba emisor = EmisorDeTokensDePrueba.emisor();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioDeImagenes servicio;

    private String comoLyra() {
        return "Bearer " + emisor.tokenDeJugador("LyraRoja", UID_LYRA);
    }

    private static MockMultipartFile archivo(String nombreOriginal, String tipoDeclarado, byte[] bytes) {
        return new MockMultipartFile("archivo", nombreOriginal, tipoDeclarado, bytes);
    }

    @Test
    @DisplayName("subir: 201 con id, tipo, tamano y url; al servicio solo le llegan los bytes y el uid del token")
    void subir() throws Exception {
        byte[] png = ExaminadorDeImagenesTest.archivo("real.png");
        when(servicio.subir(eq(UID_LYRA.toString()), any()))
                .thenReturn(new ServicioDeImagenes.ImagenGuardada(IMAGEN, TipoDeImagen.PNG, png.length));

        mvc.perform(multipart(RUTA)
                        .file(archivo("../../mi foto <script>.jpg", "image/jpeg", png))
                        .header(HttpHeaders.AUTHORIZATION, comoLyra()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, RUTA + "/" + IMAGEN))
                .andExpect(jsonPath("$.id").value(IMAGEN))
                .andExpect(jsonPath("$.tipo").value("image/png"))
                .andExpect(jsonPath("$.tamano").value(png.length))
                .andExpect(jsonPath("$.url").value(RUTA + "/" + IMAGEN));

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(servicio).subir(eq(UID_LYRA.toString()), bytes.capture());
        assertEquals(png.length, bytes.getValue().length);
    }

    @Test
    @DisplayName("sin el campo archivo, el servicio recibe nada y responde 400 imagen-ausente")
    void sinArchivo() throws Exception {
        when(servicio.subir(UID_LYRA.toString(), null)).thenThrow(new ArchivoAusente());

        mvc.perform(multipart(RUTA)
                        .file(new MockMultipartFile("otro", "x.png", "image/png", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, comoLyra()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/imagen-ausente"));
    }

    @Test
    @DisplayName("413 si es demasiado grande y 415 con motivo si no es una imagen valida")
    void rechazos() throws Exception {
        when(servicio.subir(eq(UID_LYRA.toString()), any()))
                .thenThrow(new ImagenDemasiadoGrande("la imagen mide 5000x5000"))
                .thenThrow(new ImagenNoAdmitida("no es JPEG, PNG ni WebP"));

        mvc.perform(multipart(RUTA).file(archivo("a.png", "image/png", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, comoLyra()))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/imagen-demasiado-grande"));

        mvc.perform(multipart(RUTA).file(archivo("a.png", "image/png", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, comoLyra()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/imagen-no-admitida"))
                .andExpect(jsonPath("$.motivo").value("FORMATO_DE_IMAGEN_NO_ADMITIDO"));
    }

    @Test
    @DisplayName("subir exige sesion de persona: 401 sin token, 403 con uno de servicio; un JSON no es un multipart")
    void subirSeguridad() throws Exception {
        mvc.perform(multipart(RUTA).file(archivo("a.png", "image/png", new byte[] {1})))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(RUTA).file(archivo("a.png", "image/png", new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + emisor.tokenDeServicio("ms-subastas")))
                .andExpect(status().isForbidden());
        mvc.perform(post(RUTA).header(HttpHeaders.AUTHORIZATION, comoLyra())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"archivo\":\"foto.png\"}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("una publica se sirve sin token, con su tipo, nosniff, CSP que no ejecuta nada y cache larga e inmutable")
    void descargaPublica() throws Exception {
        byte[] png = ExaminadorDeImagenesTest.archivo("real.png");
        when(servicio.obtener(eq(IMAGEN), any()))
                .thenReturn(new ServicioDeImagenes.ImagenServida(TipoDeImagen.PNG, png, true));

        mvc.perform(get(RUTA + "/" + IMAGEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                .andExpect(content().bytes(png));

        ArgumentCaptor<Solicitante> quien = ArgumentCaptor.forClass(Solicitante.class);
        verify(servicio).obtener(eq(IMAGEN), quien.capture());
        assertEquals(Solicitante.ANONIMO, quien.getValue());
    }

    @Test
    @DisplayName("una privada se sirve al autor con cache privada y sin guardar; al resto, 404")
    void descargaPrivada() throws Exception {
        byte[] webp = ExaminadorDeImagenesTest.archivo("real-sin-perdida.webp");
        when(servicio.obtener(eq(IMAGEN), eq(new Solicitante(UID_LYRA.toString(), false))))
                .thenReturn(new ServicioDeImagenes.ImagenServida(TipoDeImagen.WEBP, webp, false));
        when(servicio.obtener(eq(IMAGEN), eq(Solicitante.ANONIMO))).thenThrow(new ImagenNoEncontrada(IMAGEN));

        mvc.perform(get(RUTA + "/" + IMAGEN).header(HttpHeaders.AUTHORIZATION, comoLyra()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/webp"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));

        mvc.perform(get(RUTA + "/" + IMAGEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://nexusbattles.local/errores/imagen-no-encontrada"));
    }

    @Test
    @DisplayName("quien pide: anonimo, autor, moderacion; un token de servicio cuenta como anonimo")
    void solicitante() {
        assertEquals(Solicitante.ANONIMO, Solicitante.de(null));
        assertEquals(Solicitante.ANONIMO, Solicitante.de(new UsernamePasswordAuthenticationToken("x", "y")));

        Jwt deJugadora = Jwt.withTokenValue("t").header("alg", "none").claim("uid", UID_LYRA.toString())
                .subject("LyraRoja").build();
        Solicitante jugadora = Solicitante.de(new JwtAuthenticationToken(deJugadora,
                List.of(new SimpleGrantedAuthority("ROLE_JUGADOR"))));
        assertEquals(UID_LYRA.toString(), jugadora.uid());
        assertFalse(jugadora.modera());
        assertTrue(jugadora.puedeVerLaPrivadaDe(UID_LYRA.toString()));
        assertFalse(jugadora.puedeVerLaPrivadaDe("otro"));

        Solicitante moderadora = Solicitante.de(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none").claim("uid", UUID.randomUUID().toString())
                        .subject("Ada").build(),
                List.of(new SimpleGrantedAuthority("ROLE_MODERADOR"))));
        assertTrue(moderadora.modera());
        assertTrue(moderadora.puedeVerLaPrivadaDe("cualquiera"));

        Solicitante servicioAjeno = Solicitante.de(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none").subject("ms-subastas").build(),
                List.of(new SimpleGrantedAuthority("ROLE_SERVICIO"))));
        assertNull(servicioAjeno.uid());
        assertFalse(servicioAjeno.puedeVerLaPrivadaDe("ms-subastas"));
    }
}
