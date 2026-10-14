package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;

@DisplayName("El historico de moderacion (SA-4, §7.3.3)")
class HistorialDeModeracionTest {

    private static final Instant CUANDO = Instant.parse("2026-10-14T15:00:00Z");

    private AsientoRepository asientos;
    private ComentarioRepository comentarios;
    private HistorialDeModeracion historial;

    @BeforeEach
    void montar() {
        asientos = mock(AsientoRepository.class);
        comentarios = mock(ComentarioRepository.class);
        historial = new HistorialDeModeracion(asientos, comentarios);
    }

    private static AsientoDeModeracion asiento(String id, String comentarioId, AccionDeModeracion accion) {
        return new AsientoDeModeracion(id, comentarioId, "mod-1", "AdaLaJusta", accion, "motivo",
                Comentario.Estado.EN_REVISION, Comentario.Estado.OCULTO, CUANDO);
    }

    private static RegistroDeComentario comentario(String id, String texto) {
        return RegistroDeComentario.desde(new Comentario(id, "espada", "autor-1", "LyraRoja", texto, List.of(),
                CUANDO.minusSeconds(60), Comentario.Estado.OCULTO));
    }

    @SuppressWarnings("unchecked")
    private Pageable pedido() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(asientos).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("de la mas reciente a la mas antigua, dieciseis por pagina, y nunca mas de cien")
    @SuppressWarnings("unchecked")
    void ordenYTamano() {
        when(asientos.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        historial.consultar(new HistorialDeModeracion.Filtro(null, null, null, null), -2, 0);
        assertThat(pedido()).isEqualTo(PageRequest.of(0, 16, Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));

        montar();
        when(asientos.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        historial.consultar(new HistorialDeModeracion.Filtro(null, null, null, null), 3, 1000);
        assertThat(pedido().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("los comentarios de la pagina se leen de una vez y cada fila lleva el suyo, con el extracto recortado")
    @SuppressWarnings("unchecked")
    void comentariosDeUnaVez() {
        String largo = "x".repeat(200);
        when(asientos.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(asiento("a-1", "c-1", AccionDeModeracion.OCULTAR), asiento("a-2", "c-1", AccionDeModeracion.MARCAR),
                        asiento("a-3", "c-2", AccionDeModeracion.APROBAR)),
                PageRequest.of(0, 16), 3));
        when(comentarios.findAllById(any())).thenReturn(List.of(comentario("c-1", "  corto  "), comentario("c-2", largo)));

        HistorialDeModeracion.Pagina pagina =
                historial.consultar(new HistorialDeModeracion.Filtro(null, null, null, null), 0, 16);

        ArgumentCaptor<Iterable<String>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(comentarios).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactly("c-1", "c-2");
        assertThat(pagina.filas()).extracting(HistorialDeModeracion.Fila::extracto)
                .containsExactly("corto", "corto", "x".repeat(159) + "…");
        assertThat(pagina.total()).isEqualTo(3);
        assertThat(pagina.totalPaginas()).isEqualTo(1);
    }

    @Test
    @DisplayName("una pagina vacia no pregunta por comentarios; un asiento sin comentario va sin extracto")
    @SuppressWarnings("unchecked")
    void sinComentarios() {
        when(asientos.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        historial.consultar(new HistorialDeModeracion.Filtro(null, null, null, null), 0, 16);
        verify(comentarios, never()).findAllById(any());

        assertThat(new HistorialDeModeracion.Fila(asiento("a-9", "c-9", AccionDeModeracion.ELIMINAR), null).extracto())
                .isNull();
    }

    @Test
    @DisplayName("un periodo al reves no es un filtro: 400; un apodo en blanco es «sin filtro»")
    void filtroInvalido() {
        assertThatThrownBy(() -> new HistorialDeModeracion.Filtro(null, null, CUANDO, CUANDO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("«desde» tiene que ser anterior a «hasta»");
        assertThat(new HistorialDeModeracion.Filtro(null, "   ", null, null).moderador()).isNull();
        assertThat(new HistorialDeModeracion.Filtro(null, "  Ada ", null, null).moderador()).isEqualTo("Ada");
    }
}
