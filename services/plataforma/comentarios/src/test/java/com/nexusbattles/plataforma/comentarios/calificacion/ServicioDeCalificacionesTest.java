package com.nexusbattles.plataforma.comentarios.calificacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nexusbattles.plataforma.comentarios.Calificacion;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.ResumenDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoDeProductos;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoNoDisponible;
import com.nexusbattles.plataforma.comentarios.catalogo.ProductoInexistente;
import com.nexusbattles.plataforma.comentarios.publicacion.ConsultaDeSanciones;

/**
 * La calificacion separada del comentario — 7.1, contrato 1.4.0, B3.
 *
 * <p>La restriccion unica de la base es la que decide «una sola vez»; aqui se
 * simula con lo que devuelve {@code insertarSiNoExiste} (1 si inserto, 0 si
 * ya habia una), que es exactamente lo que la base contesta tambien cuando la
 * otra fila la metio una peticion simultanea. Que la base lo haga de verdad lo
 * comprueba {@code ComunidadDeProductoIT} con dos peticiones a la vez.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDeCalificacionesTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final String PRODUCTO = "1647b2ea-096d-37e7-b580-0172e4c62313";

    @Mock
    private RepositorioDeCalificaciones repositorio;

    @Mock
    private CatalogoDeProductos catalogo;

    @Mock
    private ConsultaDeSanciones sanciones;

    private ServicioDeCalificaciones servicio;

    @BeforeEach
    void crear() {
        servicio = new ServicioDeCalificaciones(repositorio, catalogo, sanciones, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private static RepositorioDeCalificaciones.ConteoPorEstrellas conteo(int estrellas, long cantidad) {
        return new RepositorioDeCalificaciones.ConteoPorEstrellas() {
            @Override
            public Integer getEstrellas() {
                return estrellas;
            }

            @Override
            public Long getCantidad() {
                return cantidad;
            }
        };
    }

    @Nested
    @DisplayName("calificar")
    class Calificar {

        @Test
        @DisplayName("la primera vez se registra con el autor del token y devuelve el resumen que ya la cuenta")
        void primeraVez() {
            when(sanciones.estadoDe("jugador-1")).thenReturn(HiloDeComentarios.EstadoDeAutor.HABILITADO);
            when(repositorio.insertarSiNoExiste(anyString(), eq(PRODUCTO), eq("jugador-1"), eq(4), eq(AHORA)))
                    .thenReturn(1);
            when(repositorio.contarPorEstrellas(PRODUCTO)).thenReturn(List.of(conteo(4, 1), conteo(2, 1)));

            ServicioDeCalificaciones.Calificado calificado = servicio.calificar(PRODUCTO, "jugador-1", 4);

            assertEquals(4, calificado.calificacion().estrellas());
            assertEquals(AHORA, calificado.calificacion().creadaEn());
            assertEquals(3.0, calificado.resumen().promedio());
            assertEquals(2, calificado.resumen().total());
            verify(catalogo).exigirExistente(PRODUCTO);
        }

        @Test
        @DisplayName("la segunda es 409 ya-calificado: no se edita ni se sustituye (7.1)")
        void segundaVez() {
            when(sanciones.estadoDe("jugador-1")).thenReturn(HiloDeComentarios.EstadoDeAutor.HABILITADO);
            when(repositorio.insertarSiNoExiste(anyString(), anyString(), anyString(), anyInt(), any())).thenReturn(0);

            assertThrows(YaCalificado.class, () -> servicio.calificar(PRODUCTO, "jugador-1", 5));
            verify(repositorio, never()).contarPorEstrellas(anyString());
        }

        @Test
        @DisplayName("un jugador sancionado recibe 403 y no se escribe nada")
        void sancionado() {
            when(sanciones.estadoDe("jugador-1")).thenReturn(HiloDeComentarios.EstadoDeAutor.SILENCIADO);

            HiloDeComentarios.PublicacionRechazada rechazo = assertThrows(
                    HiloDeComentarios.PublicacionRechazada.class,
                    () -> servicio.calificar(PRODUCTO, "jugador-1", 5));
            assertEquals(HiloDeComentarios.MotivoDeRechazo.AUTOR_SILENCIADO, rechazo.motivo());
            verify(repositorio, never()).insertarSiNoExiste(anyString(), anyString(), anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("estrellas fuera de 1..5 son 400 sin preguntar a nadie")
        void estrellasInvalidas() {
            assertThrows(IllegalArgumentException.class, () -> servicio.calificar(PRODUCTO, "jugador-1", 0));
            assertThrows(IllegalArgumentException.class, () -> servicio.calificar(PRODUCTO, "jugador-1", null));
            verifyNoInteractions(catalogo, sanciones, repositorio);
        }

        @Test
        @DisplayName("un producto inexistente es 404 y uno sin catalogo que lo confirme, 503: no se acepta a ciegas")
        void productoNoConfirmado() {
            doThrow(new ProductoInexistente(PRODUCTO)).when(catalogo).exigirExistente(PRODUCTO);
            assertThrows(ProductoInexistente.class, () -> servicio.calificar(PRODUCTO, "jugador-1", 3));

            doThrow(new CatalogoNoDisponible("caido")).when(catalogo).exigirExistente("otro");
            assertThrows(CatalogoNoDisponible.class, () -> servicio.calificar("otro", "jugador-1", 3));

            verifyNoInteractions(sanciones, repositorio);
        }
    }

    @Nested
    @DisplayName("desde un comentario (compatibilidad 1.4.0)")
    class DesdeComentario {

        @Test
        @DisplayName("si aun no califico, las estrellas del comentario pasan a ser su calificacion")
        void registra() {
            when(repositorio.insertarSiNoExiste(anyString(), eq(PRODUCTO), eq("jugador-1"), eq(5), eq(AHORA)))
                    .thenReturn(1);
            assertTrue(servicio.registrarDesdeComentario(PRODUCTO, "jugador-1", 5));
        }

        @Test
        @DisplayName("si ya califico (o gano otra peticion simultanea), se descarta sin error")
        void descarta() {
            when(repositorio.insertarSiNoExiste(anyString(), anyString(), anyString(), anyInt(), any())).thenReturn(0);
            assertFalse(servicio.registrarDesdeComentario(PRODUCTO, "jugador-1", 5));
        }
    }

    @Nested
    @DisplayName("leer")
    class Leer {

        @Test
        @DisplayName("el resumen publico de un producto que existe sale de la tabla")
        void resumenPublico() {
            when(catalogo.existencia(PRODUCTO)).thenReturn(CatalogoDeProductos.Existencia.EXISTE);
            when(repositorio.contarPorEstrellas(PRODUCTO)).thenReturn(List.of(conteo(5, 3), conteo(1, 1)));

            ResumenDeCalificaciones resumen = servicio.resumenPublico(PRODUCTO);

            assertEquals(4.0, resumen.promedio());
            assertEquals(4, resumen.total());
            assertEquals(Map.of(1, 1L, 2, 0L, 3, 0L, 4, 0L, 5, 3L), resumen.distribucion());
        }

        @Test
        @DisplayName("de un producto que el catalogo no tiene es 404")
        void resumenDeInexistente() {
            when(catalogo.existencia(PRODUCTO)).thenReturn(CatalogoDeProductos.Existencia.NO_EXISTE);
            assertThrows(ProductoInexistente.class, () -> servicio.resumenPublico(PRODUCTO));
            verifyNoInteractions(repositorio);
        }

        @Test
        @DisplayName("con el catalogo caido se degrada: se sirve lo que hay en vez de fallar (HU-DIS-003)")
        void resumenDegradado() {
            when(catalogo.existencia(PRODUCTO)).thenReturn(CatalogoDeProductos.Existencia.DESCONOCIDA);
            when(repositorio.contarPorEstrellas(PRODUCTO)).thenReturn(List.of());

            ResumenDeCalificaciones resumen = servicio.resumenPublico(PRODUCTO);

            assertNull(resumen.promedio());
            assertEquals(0, resumen.total());
        }

        @Test
        @DisplayName("la propia, si existe")
        void propia() {
            when(repositorio.findByProductoIdAndAutorId(PRODUCTO, "jugador-1")).thenReturn(Optional.of(
                    RegistroDeCalificacion.desde(new Calificacion("c-1", PRODUCTO, "jugador-1", 2, AHORA))));
            when(repositorio.findByProductoIdAndAutorId(PRODUCTO, "jugador-2")).thenReturn(Optional.empty());

            assertEquals(2, servicio.de(PRODUCTO, "jugador-1").orElseThrow().estrellas());
            assertTrue(servicio.de(PRODUCTO, "jugador-2").isEmpty());
        }

        @Test
        @DisplayName("las estrellas de los autores de una pagina, en una consulta; sin autores ni se pregunta")
        void estrellasDeAutores() {
            when(repositorio.findByProductoIdAndAutorIdIn(PRODUCTO, Set.of("a", "b"))).thenReturn(List.of(
                    RegistroDeCalificacion.desde(new Calificacion("c-1", PRODUCTO, "a", 5, AHORA))));

            assertEquals(Map.of("a", 5), servicio.estrellasDe(PRODUCTO, Set.of("a", "b")));
            assertEquals(Map.of(), servicio.estrellasDe(PRODUCTO, Set.of()));
            verify(repositorio).findByProductoIdAndAutorIdIn(PRODUCTO, Set.of("a", "b"));
        }
    }
}
