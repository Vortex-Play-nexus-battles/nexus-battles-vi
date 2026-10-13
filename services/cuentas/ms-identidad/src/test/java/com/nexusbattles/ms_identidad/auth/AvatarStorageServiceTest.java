package com.nexusbattles.ms_identidad.auth;

import com.nexusbattles.ms_identidad.auth.service.AvatarStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvatarStorageServiceTest {

    @TempDir
    Path carpetaTemporal;

    private AvatarStorageService avatarStorageService;

    @BeforeEach
    void setUp() {
        avatarStorageService = new AvatarStorageService();
        ReflectionTestUtils.setField(avatarStorageService, "rutaAlmacenamiento", carpetaTemporal.toString());
    }

    @Test
    void debeRetornarNullSiArchivoEsNulo() {
        assertNull(avatarStorageService.guardarAvatar(null));
    }

    @Test
    void debeRetornarNullSiArchivoEstaVacio() {
        MockMultipartFile archivoVacio = new MockMultipartFile("avatar", "foto.jpg", "image/jpeg", new byte[0]);
        assertNull(avatarStorageService.guardarAvatar(archivoVacio));
    }

    @Test
    void debeRechazarTipoDeArchivoNoPermitido() {
        MockMultipartFile archivoInvalido = new MockMultipartFile(
            "avatar", "video.mp4", "video/mp4", "contenido".getBytes());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> avatarStorageService.guardarAvatar(archivoInvalido)
        );

        assertTrue(exception.getMessage().contains("JPG, PNG o WEBP"));
    }

    @Test
    void debeRechazarArchivoQueSuperaTamanoMaximo() {
        // Se simula el tamaño con un mock, en vez de reservar 500+ MB reales
        // en memoria solo para probar el rechazo.
        MultipartFile archivoGrande = mock(MultipartFile.class);
        when(archivoGrande.isEmpty()).thenReturn(false);
        when(archivoGrande.getContentType()).thenReturn("image/jpeg");
        when(archivoGrande.getSize()).thenReturn(501L * 1024 * 1024);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> avatarStorageService.guardarAvatar(archivoGrande)
        );

        assertTrue(exception.getMessage().toLowerCase().contains("no debe superar"));
    }

    @Test
    void debeGuardarArchivoValidoYDevolverUrlConLaExtensionCorrecta() throws IOException {

        MockMultipartFile archivoValido = new MockMultipartFile(
            "avatar", "mi-foto.png", "image/png", "contenido de prueba".getBytes());

        String url = avatarStorageService.guardarAvatar(archivoValido);

        assertNotNull(url);
        assertTrue(url.startsWith("/avatares-subidos/"));
        assertTrue(url.endsWith(".png"));

        String nombreArchivo = url.substring("/avatares-subidos/".length());
        Path archivoGuardado = carpetaTemporal.resolve(nombreArchivo);
        assertTrue(Files.exists(archivoGuardado));
        assertEquals("contenido de prueba", Files.readString(archivoGuardado));
    }

    @Test
    void debeGenerarNombresUnicosParaCadaArchivo() {

        MockMultipartFile archivo1 = new MockMultipartFile(
            "avatar", "foto.jpg", "image/jpeg", "contenido A".getBytes());
        MockMultipartFile archivo2 = new MockMultipartFile(
            "avatar", "foto.jpg", "image/jpeg", "contenido B".getBytes());

        String url1 = avatarStorageService.guardarAvatar(archivo1);
        String url2 = avatarStorageService.guardarAvatar(archivo2);

        assertNotEquals(url1, url2);
    }

    // RFINAL-05 — derecho al olvido: el archivo del avatar sale del disco.

    @Test
    void borrarAvatar_eliminaElArchivoGuardado() throws IOException {
        MockMultipartFile foto = new MockMultipartFile("avatar", "yo.png", "image/png", "png".getBytes());
        String url = avatarStorageService.guardarAvatar(foto);
        Path archivo = carpetaTemporal.resolve(url.substring("/avatares-subidos/".length()));
        assertTrue(Files.exists(archivo));

        assertTrue(avatarStorageService.borrarAvatar(url));

        assertFalse(Files.exists(archivo));
        assertFalse(avatarStorageService.borrarAvatar(url), "borrar dos veces no falla: ya no esta");
    }

    @Test
    void borrarAvatar_nuncaSaleDeLaCarpeta() throws IOException {
        Path fuera = Files.writeString(carpetaTemporal.getParent().resolve("fuera-" + System.nanoTime() + ".txt"),
                "no tocar");
        try {
            assertFalse(avatarStorageService.borrarAvatar("/avatares-subidos/../" + fuera.getFileName()));
            assertFalse(avatarStorageService.borrarAvatar("/otra-carpeta/" + fuera.getFileName()));
            assertFalse(avatarStorageService.borrarAvatar("/avatares-subidos/sub/archivo.png"));
            assertFalse(avatarStorageService.borrarAvatar("/avatares-subidos/"));
            assertFalse(avatarStorageService.borrarAvatar(null));
            assertTrue(Files.exists(fuera));
        } finally {
            Files.deleteIfExists(fuera);
        }
    }
}
