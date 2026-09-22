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
    @DisplayName("la segunda calificación entra sin estrellas y se dice (RF-COM-002, D-07); los comentarios no tienen tope")
    void laSegundaCalificacionEntraSinEstrellas() {
        hilo.publicar(solicitud("com-1", "jugador-1", List.of(), 4), HABILITADO, LIMPIO);
        assertTrue(hilo.yaCalifico("jugador-1"));
        assertFalse(hilo.ultimaCalificacionDescartada());

        Comentario segundo = hilo.publicar(solicitud("com-2", "jugador-1", List.of(), 5), HABILITADO, LIMPIO);

        assertTrue(segundo.calificacion().isEmpty(), "entra sin estrellas");
        assertTrue(hilo.ultimaCalificacionDescartada(), "y se dice");
        assertEquals(Comentario.Estado.PUBLICADO, segundo.estado());

        for (int i = 3; i <= 7; i++) {
            hilo.publicar(solicitud("com-" + i, "jugador-1", List.of(), null), HABILITADO, LIMPIO);
        }
        assertFalse(hilo.ultimaCalificacionDescartada(), "sin estrellas no hay nada que descartar");
        assertEquals(7, hilo.visibles().size());
        assertEquals(4.0, hilo.promedio().orElseThrow(), "solo cuenta la primera");
    }

    @Test
    @DisplayName("retirar el propio comentario lo saca del hilo, del promedio y libera la calificación (HU-COM-004, D-19)")
    void retirarElPropio() {
        hilo.publicar(solicitud("com-1", "jugador-1", List.of(), 4), HABILITADO, LIMPIO);
        hilo.publicar(solicitud("com-2", "jugador-2", List.of(), 2), HABILITADO, LIMPIO);
        assertEquals(3.0, hilo.promedio().orElseThrow());

        Comentario retirado = hilo.eliminar("com-1", "jugador-1");

        assertEquals(Comentario.Estado.ELIMINADO, retirado.estado());
        assertTrue(retirado.calificacion().isEmpty());
        assertEquals(List.of("com-2"), hilo.visibles().stream().map(Comentario::id).toList());
        assertEquals(2.0, hilo.promedio().orElseThrow(), "la calificación retirada no cuenta");
        assertFalse(hilo.yaCalifico("jugador-1"), "puede volver a calificar");

        // Idempotente: retirar de nuevo devuelve el mismo, sin error.
        assertEquals(retirado, hilo.eliminar("com-1", "jugador-1"));
    }

    @Test
    @DisplayName("solo el autor retira: ajeno es ComentarioAjeno, inexistente es ComentarioNoEncontrado")
    void soloElAutorRetira() {
        hilo.publicar(solicitud("com-1", "jugador-1", List.of(), 4), HABILITADO, LIMPIO);

        assertThrows(HiloDeComentarios.ComentarioAjeno.class, () -> hilo.eliminar("com-1", "jugador-2"));
        assertThrows(HiloDeComentarios.ComentarioNoEncontrado.class, () -> hilo.eliminar("no-existe", "jugador-1"));
        assertEquals(1, hilo.visibles().size(), "nada cambio");
    }

    @Test
    @DisplayName("un comentario retirado cargado de la base no reserva la calificación de su autor")
    void elRetiradoNoReservaCalificacion() {
        Comentario retirado = new Comentario("com-0", "espada-del-alba", "jugador-1", "Lyra", "viejo",
                List.of(), null, Instant.parse("2026-08-30T00:00:00Z"), Comentario.Estado.ELIMINADO);
        HiloDeComentarios cargado = HiloDeComentarios.reconstituir("espada-del-alba", Set.of("jpg"),
                List.of(retirado));

        assertFalse(cargado.yaCalifico("jugador-1"));
        assertTrue(cargado.visibles().isEmpty());
        assertTrue(cargado.promedio().isEmpty());
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
}
