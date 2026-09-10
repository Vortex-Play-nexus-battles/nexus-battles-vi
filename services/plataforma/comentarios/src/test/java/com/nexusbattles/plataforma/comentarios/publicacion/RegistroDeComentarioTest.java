package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nexusbattles.plataforma.comentarios.Comentario;

/**
 * Pruebas de la conversion entre el comentario del dominio y su forma guardada.
 *
 * El dominio es inmutable y valida sus reglas; la entidad solo sabe guardarse y
 * volver. Lo que se verifica es que el viaje de ida y vuelta no pierda nada,
 * porque de esa conversion depende que el hilo se reconstruya igual al que se
 * guardo y que la regla de la calificacion unica siga funcionando.
 */
class RegistroDeComentarioTest {

    private static final Instant AYER = Instant.parse("2026-08-30T15:00:00Z");

    private static Comentario comentario(Integer estrellas, Comentario.Estado estado) {
        return new Comentario("com-1", "espada-del-alba", "jugador-1", "LyraRoja",
                "La use toda la temporada y aguanta bien.",
                List.of("captura.jpg", "detalle.png"), estrellas, AYER, estado);
    }

    @Test
    @DisplayName("el viaje de ida y vuelta conserva todos los datos del comentario")
    void laConversionNoPierdeNada() {
        Comentario original = comentario(4, Comentario.Estado.PUBLICADO);

        Comentario recuperado = RegistroDeComentario.desde(original).aDominio();

        assertEquals(original.id(), recuperado.id());
        assertEquals(original.productoId(), recuperado.productoId());
        assertEquals(original.autorId(), recuperado.autorId());
        assertEquals(original.apodoAutor(), recuperado.apodoAutor());
        assertEquals(original.texto(), recuperado.texto());
        assertEquals(original.imagenes(), recuperado.imagenes());
        assertEquals(4, recuperado.calificacion().orElseThrow());
        assertEquals(original.fechaPublicacion(), recuperado.fechaPublicacion());
        assertEquals(original.estado(), recuperado.estado());
    }

    @Test
    @DisplayName("un comentario sin calificacion vuelve sin calificacion")
    void elComentarioSinEstrellasVuelveIgual() {
        Comentario recuperado =
                RegistroDeComentario.desde(comentario(null, Comentario.Estado.PUBLICADO)).aDominio();

        assertTrue(recuperado.calificacion().isEmpty());
        assertTrue(recuperado.estaPublicado());
    }

    @Test
    @DisplayName("un comentario retenido vuelve retenido, no publicado")
    void elComentarioRetenidoVuelveRetenido() {
        Comentario recuperado =
                RegistroDeComentario.desde(comentario(3, Comentario.Estado.EN_REVISION)).aDominio();

        assertEquals(Comentario.Estado.EN_REVISION, recuperado.estado());
        assertTrue(!recuperado.estaPublicado());
    }

    @Test
    @DisplayName("la entidad expone el producto para poder agrupar el hilo")
    void laEntidadExponeSuProducto() {
        RegistroDeComentario registro =
                RegistroDeComentario.desde(comentario(5, Comentario.Estado.PUBLICADO));

        assertEquals("espada-del-alba", registro.getProductoId());
        assertEquals("com-1", registro.getId());
    }
}
