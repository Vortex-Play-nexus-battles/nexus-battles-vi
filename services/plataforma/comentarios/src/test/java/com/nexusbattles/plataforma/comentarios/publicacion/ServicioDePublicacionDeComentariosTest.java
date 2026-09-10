package com.nexusbattles.plataforma.comentarios.publicacion;

import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.HABILITADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.SILENCIADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.LIMPIO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.SENALADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;

/**
 * Pruebas de la orquestacion de HU-COM-001 sobre el dominio ya probado.
 *
 * Las reglas del hilo tienen sus propias pruebas desde el PR 163; aqui se
 * verifica lo que agrega esta capa: que la historia del producto se carga de
 * la base antes de decidir, que lo decidido se guarda, y que un rechazo no
 * deja rastro en la base.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDePublicacionDeComentariosTest {

    private static final Instant AYER = Instant.parse("2026-08-29T15:00:00Z");

    @Mock
    private ComentarioRepository repositorio;

    @Mock
    private FiltroDeContenido filtro;

    @Mock
    private ConsultaDeSanciones sanciones;

    private ServicioDePublicacionDeComentarios servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new ServicioDePublicacionDeComentarios(
                repositorio, filtro, sanciones, List.of("jpg", "png", "webp"));
    }

    private static RegistroDeComentario guardado(String autorId, Integer estrellas) {
        return RegistroDeComentario.desde(new Comentario(
                "com-previo", "espada-del-alba", autorId, "LyraRoja",
                "La use toda la temporada y aguanta bien.",
                List.of(), estrellas, AYER, Comentario.Estado.PUBLICADO));
    }

    @Test
    @DisplayName("un comentario limpio se publica y queda guardado con sus datos")
    void publicaYPersisteUnComentarioLimpio() {
        when(repositorio.findByProductoIdOrderByFechaPublicacionAsc("espada-del-alba"))
                .thenReturn(List.of());
        when(sanciones.estadoDe("jugador-1")).thenReturn(HABILITADO);
        when(filtro.verificar("Muy buena espada")).thenReturn(LIMPIO);

        Comentario comentario = servicio.publicar(
                "espada-del-alba", "jugador-1", "LyraRoja",
                "Muy buena espada", List.of("captura.jpg"), 4);

        assertTrue(comentario.estaPublicado());
        assertEquals(4, comentario.calificacion().orElseThrow());

        ArgumentCaptor<RegistroDeComentario> captor =
                ArgumentCaptor.forClass(RegistroDeComentario.class);
        verify(repositorio).save(captor.capture());
        assertEquals("espada-del-alba", captor.getValue().getProductoId());
    }

    @Test
    @DisplayName("la calificacion previa cargada de la base descarta las estrellas nuevas")
    void descartaLaSegundaCalificacionDelMismoAutor() {
        when(repositorio.findByProductoIdOrderByFechaPublicacionAsc("espada-del-alba"))
                .thenReturn(List.of(guardado("jugador-1", 5)));
        when(sanciones.estadoDe("jugador-1")).thenReturn(HABILITADO);
        when(filtro.verificar("Sigue siendo buena")).thenReturn(LIMPIO);

        Comentario comentario = servicio.publicar(
                "espada-del-alba", "jugador-1", "LyraRoja",
                "Sigue siendo buena", List.of(), 3);

        assertTrue(comentario.calificacion().isEmpty());
        assertTrue(comentario.estaPublicado());
    }

    @Test
    @DisplayName("lo senalado por el filtro se guarda retenido en revision")
    void guardaEnRevisionLoQueElFiltroSenala() {
        when(repositorio.findByProductoIdOrderByFechaPublicacionAsc("espada-del-alba"))
                .thenReturn(List.of());
        when(sanciones.estadoDe("jugador-2")).thenReturn(HABILITADO);
        when(filtro.verificar("texto senalado")).thenReturn(SENALADO);

        Comentario comentario = servicio.publicar(
                "espada-del-alba", "jugador-2", "Korrigan",
                "texto senalado", List.of(), 3);

        assertEquals(Comentario.Estado.EN_REVISION, comentario.estado());
        verify(repositorio).save(any(RegistroDeComentario.class));
    }

    @Test
    @DisplayName("el rechazo por sancion no guarda nada en la base")
    void elRechazoPorSancionNoDejaRastro() {
        when(repositorio.findByProductoIdOrderByFechaPublicacionAsc("espada-del-alba"))
                .thenReturn(List.of());
        when(sanciones.estadoDe("jugador-3")).thenReturn(SILENCIADO);
        when(filtro.verificar("da igual")).thenReturn(LIMPIO);

        assertThrows(
                HiloDeComentarios.PublicacionRechazada.class,
                () -> servicio.publicar(
                        "espada-del-alba", "jugador-3", "Umbra",
                        "da igual", List.of(), 2));

        verify(repositorio, never()).save(any(RegistroDeComentario.class));
    }
}
