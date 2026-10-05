package com.nexusbattles.ms_identidad.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class AvatarStorageService {

    // Tipos de imagen permitidos. Cualquier otro tipo (video, PDF, etc.) se rechaza.
    private static final List<String> TIPOS_PERMITIDOS = List.of(
        "image/jpeg", "image/png", "image/webp"
    );

    private static final long TAMANO_MAXIMO_BYTES = 500L * 1024 * 1024; // 500 MB

    @Value("${app.avatares.ruta-almacenamiento:./avatares-subidos}")
    private String rutaAlmacenamiento;

    /**
     * Valida y guarda la imagen en disco. Devuelve la URL relativa con la
     * que luego se puede servir (ver WebConfig).
     */
    public String guardarAvatar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            return null; // El avatar sigue siendo opcional.
        }

        validarArchivo(archivo);

        String extension = obtenerExtension(archivo.getOriginalFilename());
        String nombreUnico = UUID.randomUUID() + extension;

        try {
            Path carpeta = Path.of(rutaAlmacenamiento);
            Files.createDirectories(carpeta);

            Path destino = carpeta.resolve(nombreUnico);
            archivo.transferTo(destino);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen del avatar.", e);
        }

        return "/avatares-subidos/" + nombreUnico;
    }

    /**
     * RFINAL-05 (derecho al olvido) — borra del disco el archivo de un avatar
     * guardado por {@link #guardarAvatar}. Solo acepta la forma que esa
     * funcion devuelve ({@code /avatares-subidos/<nombre>}, sin carpetas): una
     * URL ajena o con {@code ..} no borra nada fuera de la carpeta. Sin
     * archivo, no hace nada.
     *
     * @return {@code true} si habia archivo y se borro
     */
    public boolean borrarAvatar(String url) {
        if (url == null || !url.startsWith(PREFIJO_URL)) {
            return false;
        }
        String nombre = url.substring(PREFIJO_URL.length());
        if (nombre.isBlank() || nombre.contains("/") || nombre.contains("\\") || nombre.contains("..")) {
            return false;
        }
        Path carpeta = Path.of(rutaAlmacenamiento).toAbsolutePath().normalize();
        Path archivo = carpeta.resolve(nombre).normalize();
        if (!archivo.startsWith(carpeta)) {
            return false;
        }
        try {
            return Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo borrar la imagen del avatar.", e);
        }
    }

    private static final String PREFIJO_URL = "/avatares-subidos/";

    private void validarArchivo(MultipartFile archivo) {
        if (!TIPOS_PERMITIDOS.contains(archivo.getContentType())) {
            throw new IllegalArgumentException(
                "El avatar debe ser una imagen JPG, PNG o WEBP.");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new IllegalArgumentException(
                "La imagen no debe superar los 500 MB.");
        }
    }

    private String obtenerExtension(String nombreOriginal) {
        String nombreLimpio = StringUtils.cleanPath(
            nombreOriginal != null ? nombreOriginal : "");
        int puntoIndex = nombreLimpio.lastIndexOf('.');
        return puntoIndex >= 0 ? nombreLimpio.substring(puntoIndex) : "";
    }
}
