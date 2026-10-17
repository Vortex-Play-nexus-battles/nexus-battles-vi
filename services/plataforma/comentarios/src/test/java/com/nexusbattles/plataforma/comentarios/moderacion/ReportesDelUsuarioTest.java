package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository.ComentarioReportado;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository.IdDeComentario;

/**
 * Los reportes de una cuenta para su ficha administrativa — contrato 1.12.0.
 *
 * <p>«Realizados» salen del reportante; «recibidos», de los comentarios de la
 * cuenta. Cada reporte va con su comentario, y del recibido nunca con quien lo
 * hizo.
 */
@DisplayName("ReportesDelUsuario · reportes hechos y recibidos (comentarios.yaml 1.12.0)")
class ReportesDelUsuarioTest {

    private static final String ANA = "7a1e1c4e-2d2b-4b6e-9a0f-0d1c2b3a4f55";
    private static final Instant CUANDO = Instant.parse("2026-10-10T10:00:00Z");

    private final ReporteRepository reportes = mock(ReporteRepository.class);
    private final ComentarioRepository comentarios = mock(ComentarioRepository.class);
    private final ReportesDelUsuario servicio = new ReportesDelUsuario(reportes, comentarios);

    private static IdDeComentario id(String valor) {
        return () -> valor;
    }

    private static ComentarioReportado comentario(String id, String autor, String texto) {
        return new ComentarioReportado() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getProductoId() {
                return "espada-del-alba";
            }

            @Override
            public String getAutorId() {
                return autor;
            }

            @Override
            public String getApodoAutor() {
                return "Autor " + autor;
            }

            @Override
            public String getTexto() {
                return texto;
            }

            @Override
            public Comentario.Estado getEstado() {
                return Comentario.Estado.OCULTO;
            }
        };
    }

    private static RegistroDeReporte reporte(String id, String comentarioId, String reportante) {
        return new RegistroDeReporte(id, comentarioId, reportante, CategoriaDeReporte.ACOSO, "Insulta", CUANDO);
    }

    @Test
    @DisplayName("realizados: los que hizo la cuenta, con su comentario y los dos totales")
    void realizados() {
        when(comentarios.findIdByAutorId(ANA)).thenReturn(List.of(id("propio-1")));
        when(reportes.countByReportanteId(ANA)).thenReturn(1L);
        when(reportes.countByComentarioIdIn(List.of("propio-1"))).thenReturn(4L);
        when(reportes.findByReportanteId(eq(ANA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reporte("r-1", "ajeno-1", ANA))));
        when(comentarios.findByIdIn(List.of("ajeno-1")))
                .thenReturn(List.of(comentario("ajeno-1", "bruno", "Un texto ofensivo")));

        ReportesDelUsuario.Pagina pagina = servicio.consultar(ANA, ReportesDelUsuario.Direccion.REALIZADOS, 0, 20);

        ReportesDelUsuario.Reporte primero = pagina.reportes().get(0);
        assertAll(
                () -> assertEquals(ReportesDelUsuario.Direccion.REALIZADOS, pagina.direccion()),
                () -> assertEquals(1, pagina.total()),
                () -> assertEquals(1, pagina.totalRealizados()),
                () -> assertEquals(4, pagina.totalRecibidos()),
                () -> assertEquals("r-1", primero.id()),
                () -> assertEquals(CategoriaDeReporte.ACOSO, primero.categoria()),
                () -> assertEquals("bruno", primero.comentario().autorId()),
                () -> assertEquals("Un texto ofensivo", primero.comentario().extracto()),
                () -> assertEquals(Comentario.Estado.OCULTO, primero.comentario().estado()));
    }

    @Test
    @DisplayName("recibidos: los de sus comentarios, del mas reciente al mas antiguo y en paginas acotadas")
    void recibidos() {
        when(comentarios.findIdByAutorId(ANA)).thenReturn(List.of(id("propio-1"), id("propio-2")));
        when(reportes.findByComentarioIdIn(eq(List.of("propio-1", "propio-2")), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reporte("r-9", "propio-2", "otra-cuenta"))));
        when(comentarios.findByIdIn(List.of("propio-2"))).thenReturn(List.of(comentario("propio-2", ANA, "Hola")));

        ReportesDelUsuario.Pagina pagina = servicio.consultar(ANA, ReportesDelUsuario.Direccion.RECIBIDOS, -2, 500);

        ArgumentCaptor<Pageable> peticion = ArgumentCaptor.forClass(Pageable.class);
        verify(reportes).findByComentarioIdIn(anyCollection(), peticion.capture());
        assertAll(
                () -> assertEquals(0, pagina.pagina(), "la pagina no es negativa"),
                () -> assertEquals(ReportesDelUsuario.TAMANO_MAXIMO, pagina.tamano()),
                () -> assertEquals(Sort.Direction.DESC, peticion.getValue().getSort().getOrderFor("fecha").getDirection()),
                () -> assertEquals(ANA, pagina.reportes().get(0).comentario().autorId()));
        verify(reportes, never()).findByReportanteId(any(), any());
    }

    @Test
    @DisplayName("sin comentarios propios no hay recibidos, y no se consulta con una lista vacia")
    void sinComentariosPropios() {
        when(comentarios.findIdByAutorId(ANA)).thenReturn(List.of());

        ReportesDelUsuario.Pagina pagina = servicio.consultar(ANA, ReportesDelUsuario.Direccion.RECIBIDOS, 0, 0);

        assertAll(
                () -> assertTrue(pagina.reportes().isEmpty()),
                () -> assertEquals(0, pagina.totalRecibidos()),
                () -> assertEquals(ReportesDelUsuario.TAMANO_POR_DEFECTO, pagina.tamano()));
        verify(reportes, never()).findByComentarioIdIn(anyCollection(), any());
        verify(reportes, never()).countByComentarioIdIn(anyCollection());
        verify(comentarios, never()).findByIdIn(anyCollection());
    }

    @Test
    @DisplayName("un reporte cuyo comentario ya no esta va con el comentario nulo; sin direccion, realizados")
    void comentarioDesaparecido() {
        when(comentarios.findIdByAutorId(ANA)).thenReturn(List.of());
        when(reportes.findByReportanteId(eq(ANA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reporte("r-1", "borrado", ANA))));
        when(comentarios.findByIdIn(List.of("borrado"))).thenReturn(List.of());

        ReportesDelUsuario.Pagina pagina = servicio.consultar(ANA, null, 0, 20);

        assertAll(
                () -> assertEquals(ReportesDelUsuario.Direccion.REALIZADOS, pagina.direccion()),
                () -> assertNull(pagina.reportes().get(0).comentario()));
    }

    @Test
    @DisplayName("sin cuenta no hay consulta")
    void sinCuenta() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultar(" ", ReportesDelUsuario.Direccion.REALIZADOS, 0, 20));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultar(null, ReportesDelUsuario.Direccion.REALIZADOS, 0, 20));
    }

    @Test
    @DisplayName("el extracto se corta por palabra, con puntos suspensivos, y sin espacios de mas")
    void extracto() {
        String largo = "palabra ".repeat(40);
        String corto = ReportesDelUsuario.extracto(largo);

        assertAll(
                () -> assertTrue(corto.endsWith("…")),
                () -> assertTrue(corto.length() <= ReportesDelUsuario.LARGO_DEL_EXTRACTO + 1),
                () -> assertTrue(corto.startsWith("palabra palabra")),
                () -> assertEquals("Hola mundo", ReportesDelUsuario.extracto("  Hola \n  mundo ")),
                () -> assertNull(ReportesDelUsuario.extracto(null)),
                () -> assertEquals("x".repeat(ReportesDelUsuario.LARGO_DEL_EXTRACTO) + "…",
                        ReportesDelUsuario.extracto("x".repeat(400)), "sin espacios, corte exacto"));
    }
}
