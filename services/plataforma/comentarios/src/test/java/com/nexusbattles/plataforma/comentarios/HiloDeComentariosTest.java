package com.nexusbattles.plataforma.comentarios;

import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.HABILITADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.SILENCIADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.LIMPIO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.SENALADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de HU-COM-001 - Publicacion de comentarios con texto e imagenes.
 *
 * Fuente: Proyecto Integrador II, seccion 7.1, p. 34 y seccion 7.7.9, p. 55.
 * Regla RN-CMT-001: el comentario lleva texto e imagenes, mas el apodo del
 * jugador, la calificacion en estrellas y la fecha de publicacion.
 *
 * <p>Desde B3 las reglas no necesitan el hilo entero: la calificacion unica la
 * guarda su tabla (ver {@code ServicioDeCalificacionesTest} y la IT), y aqui
 * queda lo que se decide sobre una sola solicitud.
 */
class HiloDeComentariosTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T15:00:00Z");
    private static final String IMAGEN = "3f1c2b4a-1111-4222-8333-944455566677";

    private static SolicitudDePublicacion solicitud(List<String> imagenes, Integer estrellas) {
        return new SolicitudDePublicacion(
                "com-1", "jugador-1", "LyraRoja", "La use toda la temporada y aguanta bien.",
                imagenes, estrellas, AHORA);
    }

    @Nested
    @DisplayName("publicar")
    class Publicar {

        @Test
        @DisplayName("el comentario queda con apodo, texto, imagenes y fecha, publicado y sin editar ni marcar")
        void publicaConTextoEImagen() {
            Comentario comentario = HiloDeComentarios.publicar(
                    "espada-del-alba", solicitud(List.of(IMAGEN), 4), HABILITADO, () -> LIMPIO);

            assertEquals("espada-del-alba", comentario.productoId());
            assertEquals("LyraRoja", comentario.apodoAutor());
            assertEquals(AHORA, comentario.fechaPublicacion());
            assertEquals(List.of(IMAGEN), comentario.imagenes());
            assertTrue(comentario.estaPublicado());
            assertFalse(comentario.editado());
            assertFalse(comentario.marcado());
        }

        @Test
        @DisplayName("el silencio rechaza y el filtro ni se consulta: no se gasta una llamada en quien no va a publicar")
        void elSilencioRechazaSinConsultarElFiltro() {
            AtomicInteger consultas = new AtomicInteger();

            HiloDeComentarios.PublicacionRechazada rechazo = assertThrows(
                    HiloDeComentarios.PublicacionRechazada.class,
                    () -> HiloDeComentarios.publicar("espada-del-alba", solicitud(List.of(), null), SILENCIADO,
                            () -> {
                                consultas.incrementAndGet();
                                return LIMPIO;
                            }));

            assertEquals(HiloDeComentarios.MotivoDeRechazo.AUTOR_SILENCIADO, rechazo.motivo());
            assertEquals(0, consultas.get());
        }

        @Test
        @DisplayName("lo que el filtro senala se guarda en revision, no se rechaza")
        void elFiltroSoloRetiene() {
            Comentario retenido = HiloDeComentarios.publicar(
                    "espada-del-alba", solicitud(List.of(), 3), HABILITADO, () -> SENALADO);

            assertEquals(Comentario.Estado.EN_REVISION, retenido.estado());
            assertTrue(retenido.estaEnRevision());
            assertFalse(retenido.estaPublicado());
        }

        @Test
        @DisplayName("un filtro sin veredicto es un fallo de programacion, no un comentario limpio")
        void filtroSinVeredicto() {
            assertThrows(NullPointerException.class, () -> HiloDeComentarios.publicar(
                    "espada-del-alba", solicitud(List.of(), null), HABILITADO, () -> null));
        }
    }

    @Nested
    @DisplayName("la solicitud se valida antes de llamar a nadie")
    class Solicitud {

        @Test
        @DisplayName("sin texto no hay comentario (RN-CMT-001)")
        void sinTexto() {
            assertThrows(IllegalArgumentException.class, () -> new SolicitudDePublicacion(
                    "c", "a", "Apodo", "   ", List.of(), null, AHORA));
            assertThrows(IllegalArgumentException.class, () -> new SolicitudDePublicacion(
                    "c", "a", "Apodo", null, List.of(), null, AHORA));
        }

        @Test
        @DisplayName("las estrellas, si vienen, van de 1 a 5")
        void estrellasFueraDeRango() {
            assertThrows(IllegalArgumentException.class, () -> solicitud(List.of(), 0));
            assertThrows(IllegalArgumentException.class, () -> solicitud(List.of(), 6));
            assertEquals(5, solicitud(List.of(), 5).estrellas());
            assertEquals(null, solicitud(List.of(), null).estrellas());
        }

        @Test
        @DisplayName("un nombre de archivo ya no es una imagen: 400, el servidor no la tiene (1.4.0)")
        void nombreDeArchivo() {
            HiloDeComentarios.ImagenesNoValidas error = assertThrows(HiloDeComentarios.ImagenesNoValidas.class,
                    () -> solicitud(List.of("captura.jpg"), null));
            assertTrue(error.getMessage().contains("no un nombre de archivo"));
        }

        @Test
        @DisplayName("como mucho tres imagenes, sin repetir, y en la forma exacta en que las emite el servicio")
        void limitesDeImagenes() {
            List<String> cuatro = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(), UUID.randomUUID().toString());
            assertThrows(HiloDeComentarios.ImagenesNoValidas.class, () -> solicitud(cuatro, null));
            assertThrows(HiloDeComentarios.ImagenesNoValidas.class, () -> solicitud(List.of(IMAGEN, IMAGEN), null));
            assertThrows(HiloDeComentarios.ImagenesNoValidas.class,
                    () -> solicitud(List.of(IMAGEN.toUpperCase()), null));
            assertThrows(HiloDeComentarios.ImagenesNoValidas.class, () -> solicitud(List.of("1-1-1-1-1"), null));
            assertEquals(3, solicitud(cuatro.subList(0, 3), null).imagenes().size());
        }

        @Test
        @DisplayName("imagenes nulas es lo mismo que ninguna")
        void imagenesNulas() {
            assertTrue(solicitud(null, null).imagenes().isEmpty());
            assertFalse(SolicitudDePublicacion.esIdentificador(null));
        }
    }

    @Nested
    @DisplayName("retirar (HU-COM-004)")
    class Retirar {

        private final Comentario mio = new Comentario("com-1", "espada-del-alba", "jugador-1", "Lyra",
                "texto", List.of(), AHORA, Comentario.Estado.PUBLICADO);

        @Test
        @DisplayName("el autor lo retira y queda ELIMINADO")
        void elPropio() {
            Comentario retirado = HiloDeComentarios.retirar(mio, "jugador-1");
            assertEquals(Comentario.Estado.ELIMINADO, retirado.estado());
            assertTrue(retirado.estaEliminado());
        }

        @Test
        @DisplayName("idempotente: retirar uno ya retirado devuelve el mismo, sin error (CA-03)")
        void idempotente() {
            Comentario retirado = HiloDeComentarios.retirar(mio, "jugador-1");
            assertSame(retirado, HiloDeComentarios.retirar(retirado, "jugador-1"));
        }

        @Test
        @DisplayName("el de otro jugador es ComentarioAjeno (CA-02)")
        void ajeno() {
            assertThrows(HiloDeComentarios.ComentarioAjeno.class, () -> HiloDeComentarios.retirar(mio, "jugador-2"));
        }
    }

    @Nested
    @DisplayName("el comentario del dominio")
    class ElComentario {

        private final Comentario base = new Comentario("com-1", "espada-del-alba", "jugador-1", "Lyra",
                "texto original", List.of(), AHORA, Comentario.Estado.EN_REVISION);

        @Test
        @DisplayName("EDITAR cambia el texto, lo deja editado y no toca el estado ni la marca")
        void editar() {
            Comentario editado = base.conMarca(true).editadoCon("texto moderado");
            assertEquals("texto moderado", editado.texto());
            assertTrue(editado.editado());
            assertTrue(editado.marcado());
            assertEquals(Comentario.Estado.EN_REVISION, editado.estado());
        }

        @Test
        @DisplayName("cambiar de estado conserva la edicion y la marca")
        void cambiarDeEstado() {
            Comentario aprobado = base.editadoCon("otro").conMarca(true).con(Comentario.Estado.PUBLICADO);
            assertTrue(aprobado.editado());
            assertTrue(aprobado.marcado());
            assertTrue(aprobado.estaPublicado());
        }

        @Test
        @DisplayName("no hay comentario sin texto, autor, producto, fecha o estado")
        void obligatorios() {
            assertThrows(IllegalArgumentException.class, () -> new Comentario(" ", "p", "a", "ap", "t",
                    List.of(), AHORA, Comentario.Estado.PUBLICADO));
            assertThrows(IllegalArgumentException.class, () -> new Comentario("c", "p", "a", "ap", "",
                    List.of(), AHORA, Comentario.Estado.PUBLICADO));
            assertThrows(NullPointerException.class, () -> new Comentario("c", "p", "a", "ap", "t",
                    List.of(), null, Comentario.Estado.PUBLICADO));
            assertThrows(NullPointerException.class, () -> new Comentario("c", "p", "a", "ap", "t",
                    List.of(), AHORA, null));
            assertTrue(new Comentario("c", "p", "a", "ap", "t", null, AHORA, Comentario.Estado.PUBLICADO)
                    .imagenes().isEmpty());
        }
    }
}
