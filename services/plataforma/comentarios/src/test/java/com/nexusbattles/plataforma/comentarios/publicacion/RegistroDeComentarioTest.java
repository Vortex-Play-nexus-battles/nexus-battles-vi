package com.nexusbattles.plataforma.comentarios.publicacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * porque de esa conversion depende que la moderacion actualice un comentario
 * sin perder su texto, sus imagenes, la edicion o la marca.
 */
class RegistroDeComentarioTest {

    private static final Instant AYER = Instant.parse("2026-08-30T15:00:00Z");

    private static Comentario comentario(Comentario.Estado estado) {
        return new Comentario("com-1", "espada-del-alba", "jugador-1", "LyraRoja",
                "La use toda la temporada y aguanta bien.",
                List.of("3f1c2b4a-1111-4222-8333-944455566677"), AYER, estado);
    }

    @Test
    @DisplayName("el viaje de ida y vuelta conserva todos los datos del comentario")
    void laConversionNoPierdeNada() {
        Comentario original = comentario(Comentario.Estado.PUBLICADO);

        Comentario recuperado = RegistroDeComentario.desde(original).aDominio();

        assertEquals(original, recuperado);
        assertFalse(recuperado.editado());
        assertFalse(recuperado.marcado());
    }

    @Test
    @DisplayName("la edicion y la marca de moderacion vuelven tal cual")
    void edicionYMarca() {
        Comentario moderado = comentario(Comentario.Estado.OCULTO).editadoCon("texto moderado").conMarca(true);

        Comentario recuperado = RegistroDeComentario.desde(moderado).aDominio();

        assertEquals("texto moderado", recuperado.texto());
        assertTrue(recuperado.editado());
        assertTrue(recuperado.marcado());
        assertEquals(Comentario.Estado.OCULTO, recuperado.estado());
    }

    @Test
    @DisplayName("un comentario retenido vuelve retenido, no publicado")
    void elComentarioRetenidoVuelveRetenido() {
        Comentario recuperado =
                RegistroDeComentario.desde(comentario(Comentario.Estado.EN_REVISION)).aDominio();

        assertEquals(Comentario.Estado.EN_REVISION, recuperado.estado());
        assertFalse(recuperado.estaPublicado());
    }

    @Test
    @DisplayName("la entidad expone el producto para poder comprobar a que hilo pertenece")
    void laEntidadExponeSuProducto() {
        RegistroDeComentario registro = RegistroDeComentario.desde(comentario(Comentario.Estado.PUBLICADO));

        assertEquals("espada-del-alba", registro.getProductoId());
        assertEquals("com-1", registro.getId());
    }
}
