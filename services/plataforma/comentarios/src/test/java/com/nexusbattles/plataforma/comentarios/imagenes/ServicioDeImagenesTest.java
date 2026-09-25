package com.nexusbattles.plataforma.comentarios.imagenes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;

/**
 * Subir, adjuntar, servir y limpiar imagenes — contrato 1.4.0, B3. Lo que se
 * guarda, quien puede ver cada una y que solo su autor la adjunta una vez.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeImagenesTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final String AUTOR = "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55";
    private static final String IMAGEN = "3f1c2b4a-1111-4222-8333-944455566677";
    private static final String COMENTARIO = "com-1";

    @Mock
    private RepositorioDeImagenes repositorio;

    @Mock
    private ComentarioRepository comentarios;

    private ServicioDeImagenes servicio;

    @BeforeEach
    void crear() {
        servicio = new ServicioDeImagenes(repositorio, comentarios, Clock.fixed(AHORA, ZoneOffset.UTC),
                List.of("jpg", "png", "webp"), Duration.ofHours(24));
    }

    private static RegistroDeImagen imagen(String comentarioId) {
        RegistroDeImagen registro = new RegistroDeImagen(IMAGEN, AUTOR, TipoDeImagen.PNG,
                ExaminadorDeImagenesTest.archivo("real.png"), "huella", AHORA);
        ReflectionTestUtils.setField(registro, "comentarioId", comentarioId);
        return registro;
    }

    @Nested
    @DisplayName("subir")
    class Subir {

        @Test
        @DisplayName("guarda los bytes con el tipo detectado, su tamano, su SHA-256 y un UUID: nada del nombre original")
        void guarda() {
            byte[] png = ExaminadorDeImagenesTest.archivo("real.png");

            ServicioDeImagenes.ImagenGuardada guardada = servicio.subir(AUTOR, png);

            ArgumentCaptor<RegistroDeImagen> captor = ArgumentCaptor.forClass(RegistroDeImagen.class);
            verify(repositorio).save(captor.capture());
            RegistroDeImagen registro = captor.getValue();
            assertEquals(guardada.id(), registro.id());
            assertEquals(36, registro.id().length());
            assertEquals(AUTOR, registro.autorId());
            assertEquals(TipoDeImagen.PNG, registro.tipo());
            assertEquals(png.length, registro.tamano());
            assertEquals(64, registro.sha256().length());
            assertArrayEquals(png, registro.datos());
            assertEquals(AHORA, registro.creadaEn());
            assertNull(registro.comentarioId(), "nace pendiente");
            assertEquals("/api/v1/comentarios/imagenes/" + guardada.id(), guardada.url());
        }

        @Test
        @DisplayName("lo que no es una imagen valida no llega a la base")
        void falsaNoSeGuarda() {
            assertThrows(ImagenNoAdmitida.class,
                    () -> servicio.subir(AUTOR, ExaminadorDeImagenesTest.archivo("html-con-extension.png")));
            assertThrows(ArchivoAusente.class, () -> servicio.subir(AUTOR, null));
            verifyNoInteractions(repositorio);
        }
    }

    @Nested
    @DisplayName("adjuntar a un comentario")
    class Adjuntar {

        @Test
        @DisplayName("solo si todas son del autor y siguen pendientes")
        void disponibles() {
            when(repositorio.contarDisponibles(List.of(IMAGEN), AUTOR)).thenReturn(1L);
            servicio.exigirDisponibles(List.of(IMAGEN), AUTOR);

            when(repositorio.contarDisponibles(List.of(IMAGEN), "otro")).thenReturn(0L);
            assertThrows(HiloDeComentarios.ImagenesNoValidas.class,
                    () -> servicio.exigirDisponibles(List.of(IMAGEN), "otro"));
        }

        @Test
        @DisplayName("sin imagenes no se pregunta nada")
        void sinImagenes() {
            servicio.exigirDisponibles(List.of(), AUTOR);
            servicio.asociar(List.of(), AUTOR, COMENTARIO);
            verifyNoInteractions(repositorio);
        }

        @Test
        @DisplayName("si otra publicacion ya uso alguna, la asociacion toca menos filas y se rechaza (se deshace todo)")
        void carrera() {
            when(repositorio.asociar(List.of(IMAGEN), AUTOR, COMENTARIO)).thenReturn(0);
            assertThrows(HiloDeComentarios.ImagenesNoValidas.class,
                    () -> servicio.asociar(List.of(IMAGEN), AUTOR, COMENTARIO));

            when(repositorio.asociar(List.of(IMAGEN), AUTOR, "com-2")).thenReturn(1);
            servicio.asociar(List.of(IMAGEN), AUTOR, "com-2");
        }
    }

    @Nested
    @DisplayName("quien la ve")
    class Visibilidad {

        private final Solicitante anonimo = Solicitante.ANONIMO;
        private final Solicitante autor = new Solicitante(AUTOR, false);
        private final Solicitante otroJugador = new Solicitante("otro-uid", false);
        private final Solicitante moderadora = new Solicitante("mod-uid", true);

        @Test
        @DisplayName("de un comentario PUBLICADO: cualquiera, y es publica (cache larga)")
        void publicada() {
            when(repositorio.findById(IMAGEN)).thenReturn(Optional.of(imagen(COMENTARIO)));
            when(comentarios.estadoDe(COMENTARIO)).thenReturn(Optional.of(Comentario.Estado.PUBLICADO));

            ServicioDeImagenes.ImagenServida servida = servicio.obtener(IMAGEN, anonimo);

            assertTrue(servida.publica());
            assertEquals(TipoDeImagen.PNG, servida.tipo());
        }

        @Test
        @DisplayName("pendiente o de un comentario no publicado: solo su autor y moderacion; los demas, 404")
        void noPublica() {
            when(repositorio.findById(IMAGEN)).thenReturn(Optional.of(imagen(null)));

            assertThrows(ImagenNoEncontrada.class, () -> servicio.obtener(IMAGEN, anonimo));
            assertThrows(ImagenNoEncontrada.class, () -> servicio.obtener(IMAGEN, otroJugador));
            assertFalse(servicio.obtener(IMAGEN, autor).publica());
            assertFalse(servicio.obtener(IMAGEN, moderadora).publica());
            verify(comentarios, never()).estadoDe(anyString());

            when(repositorio.findById("4f1c2b4a-1111-4222-8333-944455566677")).thenReturn(Optional.of(imagen(COMENTARIO)));
            when(comentarios.estadoDe(COMENTARIO)).thenReturn(Optional.of(Comentario.Estado.OCULTO));
            assertThrows(ImagenNoEncontrada.class,
                    () -> servicio.obtener("4f1c2b4a-1111-4222-8333-944455566677", anonimo));
            assertFalse(servicio.obtener("4f1c2b4a-1111-4222-8333-944455566677", moderadora).publica());
        }

        @Test
        @DisplayName("un id que no es un UUID o que no existe es 404, sin ir a la base si no hace falta")
        void inexistente() {
            assertThrows(ImagenNoEncontrada.class, () -> servicio.obtener("../../etc/passwd", moderadora));
            verify(repositorio, never()).findById(anyString());

            when(repositorio.findById(IMAGEN)).thenReturn(Optional.empty());
            assertThrows(ImagenNoEncontrada.class, () -> servicio.obtener(IMAGEN, moderadora));
        }
    }

    @Test
    @DisplayName("la limpieza borra las pendientes de mas de 24 h, contadas desde el reloj del servicio")
    void limpieza() {
        when(repositorio.borrarPendientesAnterioresA(AHORA.minus(Duration.ofHours(24)))).thenReturn(3);

        assertEquals(3, servicio.limpiarPendientes());
        verify(repositorio).borrarPendientesAnterioresA(any());
    }

    @Test
    @DisplayName("la tarea programada nunca revienta: si la limpieza falla, lo dice y espera a la siguiente vuelta")
    void tareaTolerante() {
        when(repositorio.borrarPendientesAnterioresA(any())).thenThrow(new IllegalStateException("base caida"));
        LimpiezaDeImagenesPendientes tarea = new LimpiezaDeImagenesPendientes(servicio);

        tarea.limpiar();

        verify(repositorio).borrarPendientesAnterioresA(any());
    }
}
