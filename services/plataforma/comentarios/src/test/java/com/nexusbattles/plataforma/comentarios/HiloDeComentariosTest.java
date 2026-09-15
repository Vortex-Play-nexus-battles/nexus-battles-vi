package com.nexusbattles.plataforma.comentarios;

import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.HABILITADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.SILENCIADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.LIMPIO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.SENALADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de HU-COM-001 - Publicacion de comentarios con texto e imagenes.
 *
 * Fuente: Proyecto Integrador II, seccion 7.1, p. 34 y seccion 7.7.9, p. 55.
 * Regla RN-CMT-001: el comentario lleva texto e imagenes, mas el apodo del
 * jugador,
 * la calificacion en estrellas y la fecha de publicacion. Un jugador comenta
 * cuantas
 * veces quiera pero califica una sola vez.
 */
class HiloDeComentariosTest {

    private static final Set<String> FORMATOS = Set.of("jpg", "png", "webp");
    private static final Instant AHORA = Instant.parse("2026-08-26T15:00:00Z");

    private HiloDeComentarios hilo;

    @BeforeEach
    void abrirHilo() {
        hilo = HiloDeComentarios.de("espada-del-alba", FORMATOS);
    }

    private static SolicitudDePublicacion solicitud(
            String id, String autor, List<String> imagenes, Integer estrellas) {
        return new SolicitudDePublicacion(
                id, autor, "LyraRoja", "La use toda la temporada y aguanta bien.",
                imagenes, estrellas, AHORA);
    }

    @Test
    @DisplayName("el comentario queda con apodo, estrellas y fecha, y entra al promedio")
    void publicaConTextoImagenYCalificacion() {
        Comentario comentario = hilo.publicar(
                solicitud("com-1", "jugador-1", List.of("captura.jpg"), 4), HABILITADO, LIMPIO);

        assertEquals("LyraRoja", comentario.apodoAutor());
        assertEquals(4, comentario.calificacion().orElseThrow());
        assertEquals(AHORA, comentario.fechaPublicacion());
        assertEquals(List.of("captura.jpg"), comentario.imagenes());
        assertTrue(comentario.estaPublicado());

        assertEquals(4.0, hilo.promedio().orElseThrow());
        assertEquals(1, hilo.visibles().size());
    }

    @Test
    @DisplayName("rechaza la segunda calificación pero admite múltiples comentarios sin estrellas")
    void rechazaSegundaCalificacionPeroAceptaMasComentarios() {
        // CP-03: Primera calificación válida entra al sistema
        hilo.publicar(solicitud("com-1", "jugador-1", List.of(), 4), HABILITADO, LIMPIO);
        assertTrue(hilo.yaCalifico("jugador-1"));

        // CP-01: Segunda calificación con estrellas se rechaza con excepción
        HiloDeComentarios.PublicacionRechazada excepcion = assertThrows(
                HiloDeComentarios.PublicacionRechazada.class,
                () -> hilo.publicar(solicitud("com-2", "jugador-1", List.of(), 5), HABILITADO, LIMPIO));
        assertEquals(HiloDeComentarios.MotivoDeRechazo.CALIFICACION_DUPLICADA, excepcion.motivo());

        // CP-02: Comentarios adicionales sin estrellas sí se admiten
        for (int i = 3; i <= 7; i++) {
            hilo.publicar(solicitud("com-" + i, "jugador-1", List.of(), null), HABILITADO, LIMPIO);
        }

        // 1 comentario inicial + 5 comentarios sin estrellas del ciclo = 6 visibles
        assertEquals(6, hilo.visibles().size());
        assertEquals(4.0, hilo.promedio().orElseThrow());
    }

    @Test
    @DisplayName("el silencio y la imagen invalida rechazan, el filtro solo retiene")
    void distingueElRechazoDeLaRetencion() {
        HiloDeComentarios.PublicacionRechazada porSancion = assertThrows(
                HiloDeComentarios.PublicacionRechazada.class,
                () -> hilo.publicar(solicitud("com-1", "jugador-2", List.of(), 3), SILENCIADO, LIMPIO));
        assertEquals(HiloDeComentarios.MotivoDeRechazo.AUTOR_SILENCIADO, porSancion.motivo());

        HiloDeComentarios.PublicacionRechazada porImagen = assertThrows(
                HiloDeComentarios.PublicacionRechazada.class,
                () -> hilo.publicar(
                        solicitud("com-2", "jugador-2", List.of("virus.exe"), 3), HABILITADO, LIMPIO));
        assertEquals(
                HiloDeComentarios.MotivoDeRechazo.FORMATO_DE_IMAGEN_NO_ADMITIDO, porImagen.motivo());

        assertTrue(hilo.comentarios().isEmpty());

        Comentario retenido = hilo.publicar(
                solicitud("com-3", "jugador-2", List.of(), 3), HABILITADO, SENALADO);

        assertEquals(Comentario.Estado.EN_REVISION, retenido.estado());
        assertFalse(retenido.estaPublicado());
        assertTrue(hilo.visibles().isEmpty());
        assertTrue(hilo.promedio().isEmpty());
        assertTrue(hilo.yaCalifico("jugador-2"));
    }

    @Test
    @DisplayName("el hilo rechaza productos, formatos y calificaciones invalidos")
    void rechazaEntradasInvalidas() {
        assertThrows(IllegalArgumentException.class,
                () -> HiloDeComentarios.de(" ", FORMATOS));
        assertThrows(IllegalArgumentException.class,
                () -> HiloDeComentarios.de("producto", Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> hilo.publicar(solicitud("com-1", "jugador-3", List.of(), 9), HABILITADO, LIMPIO));
        assertThrows(HiloDeComentarios.PublicacionRechazada.class,
                () -> hilo.publicar(solicitud("com-2", "jugador-3", List.of("sinextension"), 3),
                        HABILITADO, LIMPIO));
        assertThrows(UnsupportedOperationException.class,
                () -> hilo.comentarios().add(null));
        assertTrue(hilo.promedio().isEmpty());
    }

    @Test
    @DisplayName("el promedio y la lista de visibles excluyen comentarios eliminados (HU-COM-003)")
    void promedioExcluyeComentariosEliminados() {
        Comentario publicado1 = new Comentario("com-1", "espada-del-alba", "jugador-1", "Lyra", "texto", List.of(), 5,
                AHORA, Comentario.Estado.PUBLICADO);
        Comentario eliminado = new Comentario("com-2", "espada-del-alba", "jugador-2", "Orion", "texto", List.of(), 1,
                AHORA, Comentario.Estado.ELIMINADO);
        Comentario publicado2 = new Comentario("com-3", "espada-del-alba", "jugador-3", "Draco", "texto", List.of(), 4,
                AHORA, Comentario.Estado.PUBLICADO);

        HiloDeComentarios hiloCargado = HiloDeComentarios.reconstituir(
                "espada-del-alba", FORMATOS, List.of(publicado1, eliminado, publicado2));

        // El promedio debe ser (5 + 4) / 2 = 4.5, ignorando la calificación 1 del
        // comentario eliminado
        assertEquals(4.5, hiloCargado.promedio().orElseThrow());

        // Solo 2 comentarios deben ser visibles en el hilo público
        assertEquals(2, hiloCargado.visibles().size());
    }
}
