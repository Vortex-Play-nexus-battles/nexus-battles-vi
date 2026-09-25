package com.nexusbattles.plataforma.comentarios.publicacion;

import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.HABILITADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.EstadoDeAutor.SILENCIADO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.LIMPIO;
import static com.nexusbattles.plataforma.comentarios.HiloDeComentarios.ResultadoDelFiltro.SENALADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.ResumenDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.calificacion.ServicioDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoDeProductos;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoNoDisponible;
import com.nexusbattles.plataforma.comentarios.catalogo.ProductoInexistente;
import com.nexusbattles.plataforma.comentarios.imagenes.ServicioDeImagenes;

/**
 * Pruebas de la orquestacion de la publicacion sobre el dominio ya probado —
 * HU-COM-001..004, B3.
 *
 * <p>Lo que se verifica aqui es lo que agrega esta capa: el orden de las
 * comprobaciones (lo local antes que lo remoto), que un rechazo no deja rastro,
 * que las estrellas del comentario pasan a la calificacion una sola vez, y que
 * leer el hilo es una pagina de la base y no el producto entero en memoria.
 */
@ExtendWith(MockitoExtension.class)
class ServicioDePublicacionDeComentariosTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T15:00:00Z");
    private static final String PRODUCTO = "espada-del-alba";
    private static final String IMAGEN = "3f1c2b4a-1111-4222-8333-944455566677";

    @Mock
    private ComentarioRepository repositorio;

    @Mock
    private FiltroDeContenido filtro;

    @Mock
    private ConsultaDeSanciones sanciones;

    @Mock
    private CatalogoDeProductos catalogo;

    @Mock
    private ServicioDeCalificaciones calificaciones;

    @Mock
    private ServicioDeImagenes imagenes;

    private ServicioDePublicacionDeComentarios servicio;

    @BeforeEach
    void crearServicio() {
        servicio = new ServicioDePublicacionDeComentarios(repositorio, filtro, sanciones, catalogo,
                calificaciones, imagenes, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Nested
    @DisplayName("publicar")
    class Publicar {

        @Test
        @DisplayName("un comentario limpio se guarda publicado, sin estrellas propias, con sus imagenes y su calificacion")
        void publicaLimpio() {
            when(sanciones.estadoDe("jugador-1")).thenReturn(HABILITADO);
            when(filtro.verificar("Muy buena espada")).thenReturn(LIMPIO);
            when(calificaciones.registrarDesdeComentario(PRODUCTO, "jugador-1", 4)).thenReturn(true);
            when(calificaciones.estrellasDe(PRODUCTO, Set.of("jugador-1"))).thenReturn(Map.of("jugador-1", 4));

            ServicioDePublicacionDeComentarios.Publicado publicado = servicio.publicar(
                    PRODUCTO, "jugador-1", "LyraRoja", "Muy buena espada", List.of(IMAGEN), 4);

            assertTrue(publicado.comentario().estaPublicado());
            assertEquals(AHORA, publicado.comentario().fechaPublicacion());
            assertEquals(4, publicado.estrellas());
            assertFalse(publicado.calificacionDescartada());

            ArgumentCaptor<RegistroDeComentario> guardado = ArgumentCaptor.forClass(RegistroDeComentario.class);
            verify(repositorio).saveAndFlush(guardado.capture());
            assertEquals(PRODUCTO, guardado.getValue().getProductoId());
            assertEquals(List.of(IMAGEN), guardado.getValue().aDominio().imagenes());
            verify(imagenes).asociar(List.of(IMAGEN), "jugador-1", publicado.comentario().id());
        }

        @Test
        @DisplayName("lo local va antes que lo remoto: imagenes, luego catalogo, luego sancion y filtro")
        void ordenDeLasComprobaciones() {
            when(sanciones.estadoDe("jugador-1")).thenReturn(HABILITADO);
            when(filtro.verificar(anyString())).thenReturn(LIMPIO);

            servicio.publicar(PRODUCTO, "jugador-1", "LyraRoja", "texto", List.of(IMAGEN), null);

            InOrder orden = inOrder(imagenes, catalogo, sanciones, filtro, repositorio);
            orden.verify(imagenes).exigirDisponibles(List.of(IMAGEN), "jugador-1");
            orden.verify(catalogo).exigirExistente(PRODUCTO);
            orden.verify(sanciones).estadoDe("jugador-1");
            orden.verify(filtro).verificar("texto");
            orden.verify(repositorio).saveAndFlush(any(RegistroDeComentario.class));
            orden.verify(imagenes).asociar(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("si ya habia calificado, el comentario entra igual y la respuesta lo dice con sus estrellas de antes (D-07)")
        void segundaCalificacionDescartada() {
            when(sanciones.estadoDe("jugador-1")).thenReturn(HABILITADO);
            when(filtro.verificar(anyString())).thenReturn(LIMPIO);
            when(calificaciones.registrarDesdeComentario(PRODUCTO, "jugador-1", 2)).thenReturn(false);
            when(calificaciones.estrellasDe(PRODUCTO, Set.of("jugador-1"))).thenReturn(Map.of("jugador-1", 5));

            ServicioDePublicacionDeComentarios.Publicado publicado = servicio.publicar(
                    PRODUCTO, "jugador-1", "LyraRoja", "Sigue siendo buena", List.of(), 2);

            assertTrue(publicado.calificacionDescartada());
            assertEquals(5, publicado.estrellas(), "las estrellas que se ensenan son las de su calificacion");
            verify(repositorio).saveAndFlush(any(RegistroDeComentario.class));
        }

        @Test
        @DisplayName("sin estrellas no se toca la calificacion y no hay nada que descartar")
        void sinEstrellas() {
            when(sanciones.estadoDe("jugador-1")).thenReturn(HABILITADO);
            when(filtro.verificar(anyString())).thenReturn(LIMPIO);
            when(calificaciones.estrellasDe(PRODUCTO, Set.of("jugador-1"))).thenReturn(Map.of());

            ServicioDePublicacionDeComentarios.Publicado publicado = servicio.publicar(
                    PRODUCTO, "jugador-1", "LyraRoja", "solo opino", null, null);

            assertFalse(publicado.calificacionDescartada());
            assertNull(publicado.estrellas());
            verify(calificaciones, never()).registrarDesdeComentario(anyString(), anyString(), anyInt());
        }

        @Test
        @DisplayName("lo senalado por el filtro se guarda en revision; su calificacion cuenta igual (no es contenido)")
        void senaladoEnRevision() {
            when(sanciones.estadoDe("jugador-2")).thenReturn(HABILITADO);
            when(filtro.verificar("texto senalado")).thenReturn(SENALADO);
            when(calificaciones.registrarDesdeComentario(PRODUCTO, "jugador-2", 3)).thenReturn(true);

            Comentario comentario = servicio.publicar(
                    PRODUCTO, "jugador-2", "Korrigan", "texto senalado", List.of(), 3).comentario();

            assertEquals(Comentario.Estado.EN_REVISION, comentario.estado());
            verify(calificaciones).registrarDesdeComentario(PRODUCTO, "jugador-2", 3);
        }

        @Test
        @DisplayName("el rechazo por sancion no guarda nada y ni consulta el filtro")
        void rechazoPorSancion() {
            when(sanciones.estadoDe("jugador-3")).thenReturn(SILENCIADO);

            assertThrows(HiloDeComentarios.PublicacionRechazada.class, () -> servicio.publicar(
                    PRODUCTO, "jugador-3", "Umbra", "da igual", List.of(), 2));

            verifyNoInteractions(filtro, calificaciones);
            verify(repositorio, never()).saveAndFlush(any(RegistroDeComentario.class));
        }

        @Test
        @DisplayName("un producto que no existe es 404 y no se pregunta a nadie mas")
        void productoInexistente() {
            doThrow(new ProductoInexistente(PRODUCTO)).when(catalogo).exigirExistente(PRODUCTO);

            assertThrows(ProductoInexistente.class, () -> servicio.publicar(
                    PRODUCTO, "jugador-1", "Lyra", "texto", List.of(), 5));

            verifyNoInteractions(sanciones, filtro, calificaciones);
            verify(repositorio, never()).saveAndFlush(any(RegistroDeComentario.class));
        }

        @Test
        @DisplayName("sin catalogo no se publica a ciegas: 503 y nada guardado")
        void catalogoCaido() {
            doThrow(new CatalogoNoDisponible("caido")).when(catalogo).exigirExistente(PRODUCTO);

            assertThrows(CatalogoNoDisponible.class, () -> servicio.publicar(
                    PRODUCTO, "jugador-1", "Lyra", "texto", List.of(), null));

            verify(repositorio, never()).saveAndFlush(any(RegistroDeComentario.class));
        }

        @Test
        @DisplayName("una imagen ajena o usada corta antes de preguntar al catalogo")
        void imagenAjena() {
            doThrow(new HiloDeComentarios.ImagenesNoValidas("ajena"))
                    .when(imagenes).exigirDisponibles(List.of(IMAGEN), "jugador-1");

            assertThrows(HiloDeComentarios.ImagenesNoValidas.class, () -> servicio.publicar(
                    PRODUCTO, "jugador-1", "Lyra", "texto", List.of(IMAGEN), null));

            verifyNoInteractions(catalogo, sanciones, filtro);
        }

        @Test
        @DisplayName("un nombre de archivo corta aun antes: ni imagenes ni catalogo se enteran")
        void nombreDeArchivo() {
            assertThrows(HiloDeComentarios.ImagenesNoValidas.class, () -> servicio.publicar(
                    PRODUCTO, "jugador-1", "Lyra", "texto", List.of("captura.jpg"), null));

            verifyNoInteractions(imagenes, catalogo, sanciones, filtro, repositorio);
        }
    }

    @Nested
    @DisplayName("retirar (HU-COM-004)")
    class Retirar {

        private RegistroDeComentario guardado(String autorId, Comentario.Estado estado) {
            return RegistroDeComentario.desde(new Comentario("com-1", PRODUCTO, autorId, "Lyra",
                    "texto", List.of(), AHORA, estado));
        }

        @Test
        @DisplayName("el propio se guarda ELIMINADO y la calificacion no se toca (7.1)")
        void retiraElPropio() {
            when(repositorio.findById("com-1")).thenReturn(Optional.of(guardado("jugador-1", Comentario.Estado.PUBLICADO)));

            Comentario retirado = servicio.eliminar(PRODUCTO, "com-1", "jugador-1");

            assertEquals(Comentario.Estado.ELIMINADO, retirado.estado());
            ArgumentCaptor<RegistroDeComentario> captor = ArgumentCaptor.forClass(RegistroDeComentario.class);
            verify(repositorio).save(captor.capture());
            assertEquals(Comentario.Estado.ELIMINADO, captor.getValue().aDominio().estado());
            verifyNoInteractions(calificaciones);
        }

        @Test
        @DisplayName("retirar uno ya retirado no escribe nada (idempotente)")
        void yaRetirado() {
            when(repositorio.findById("com-1")).thenReturn(Optional.of(guardado("jugador-1", Comentario.Estado.ELIMINADO)));

            Comentario retirado = servicio.eliminar(PRODUCTO, "com-1", "jugador-1");

            assertTrue(retirado.estaEliminado());
            verify(repositorio, never()).save(any(RegistroDeComentario.class));
        }

        @Test
        @DisplayName("el de otro, el de otro producto o uno inexistente no tocan la base")
        void noRetiraLoAjenoNiLoInexistente() {
            when(repositorio.findById("com-1")).thenReturn(Optional.of(guardado("jugador-2", Comentario.Estado.PUBLICADO)));
            when(repositorio.findById("no-existe")).thenReturn(Optional.empty());

            assertThrows(HiloDeComentarios.ComentarioAjeno.class,
                    () -> servicio.eliminar(PRODUCTO, "com-1", "jugador-1"));
            assertThrows(HiloDeComentarios.ComentarioNoEncontrado.class,
                    () -> servicio.eliminar("otro-producto", "com-1", "jugador-2"));
            assertThrows(HiloDeComentarios.ComentarioNoEncontrado.class,
                    () -> servicio.eliminar(PRODUCTO, "no-existe", "jugador-1"));
            verify(repositorio, never()).save(any(RegistroDeComentario.class));
        }
    }

    @Nested
    @DisplayName("leer el hilo, paginado")
    class Leer {

        private RegistroDeComentario guardado(String id, String autor, Instant fecha) {
            return RegistroDeComentario.desde(new Comentario(id, PRODUCTO, autor, "Apodo-" + autor,
                    "texto", List.of(), fecha, Comentario.Estado.PUBLICADO));
        }

        @Test
        @DisplayName("pide a la base una pagina de PUBLICADOS, del mas reciente al mas antiguo y luego por id")
        void pideLaPaginaOrdenada() {
            ResumenDeCalificaciones resumen = ResumenDeCalificaciones.de(PRODUCTO, Map.of(4, 1L, 5, 1L));
            List<RegistroDeComentario> pagina = List.of(
                    guardado("c3", "jugador-3", AHORA),
                    guardado("c2", "jugador-1", AHORA.minusSeconds(60)));
            when(repositorio.findByProductoIdAndEstado(eq(PRODUCTO), eq(Comentario.Estado.PUBLICADO), any(Pageable.class)))
                    .thenAnswer(inv -> new PageImpl<>(pagina, inv.getArgument(2), 5));
            when(calificaciones.estrellasDe(eq(PRODUCTO), any())).thenReturn(Map.of("jugador-1", 4));
            when(calificaciones.resumen(PRODUCTO)).thenReturn(resumen);

            ServicioDePublicacionDeComentarios.HiloConsultado hilo = servicio.consultarHilo(PRODUCTO, 1, 2);

            ArgumentCaptor<Pageable> pedido = ArgumentCaptor.forClass(Pageable.class);
            verify(repositorio).findByProductoIdAndEstado(eq(PRODUCTO), eq(Comentario.Estado.PUBLICADO), pedido.capture());
            assertEquals(PageRequest.of(1, 2,
                    Sort.by(Sort.Order.desc("fechaPublicacion"), Sort.Order.desc("id"))), pedido.getValue());

            assertEquals(List.of("c3", "c2"), hilo.comentarios().stream().map(Comentario::id).toList());
            assertEquals(1, hilo.pagina());
            assertEquals(2, hilo.tamano());
            assertEquals(5, hilo.total());
            assertEquals(3, hilo.totalPaginas());
            assertEquals(Map.of("jugador-1", 4), hilo.estrellasPorAutor());
            assertSame(resumen, hilo.resumen());
            verify(calificaciones).estrellasDe(PRODUCTO, Set.of("jugador-3", "jugador-1"));
            verifyNoInteractions(catalogo);
        }

        @Test
        @DisplayName("un tamano de mas de 50 se recorta: el cliente no decide cuanto carga el servidor")
        void tamanoRecortado() {
            when(repositorio.findByProductoIdAndEstado(eq(PRODUCTO), eq(Comentario.Estado.PUBLICADO), any(Pageable.class)))
                    .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(2), 0));
            when(calificaciones.estrellasDe(eq(PRODUCTO), any())).thenReturn(Map.of());
            when(calificaciones.resumen(PRODUCTO)).thenReturn(ResumenDeCalificaciones.vacio(PRODUCTO));

            ServicioDePublicacionDeComentarios.HiloConsultado hilo = servicio.consultarHilo(PRODUCTO, 0, 5000);

            assertEquals(50, hilo.tamano());
            assertTrue(hilo.comentarios().isEmpty());
            assertEquals(0, hilo.totalPaginas());
            assertNull(hilo.resumen().promedio());
        }

        @Test
        @DisplayName("una pagina negativa o un tamano menor que 1 es 400")
        void parametrosInvalidos() {
            assertThrows(IllegalArgumentException.class, () -> servicio.consultarHilo(PRODUCTO, -1, 16));
            assertThrows(IllegalArgumentException.class, () -> servicio.consultarHilo(PRODUCTO, 0, 0));
            verifyNoInteractions(repositorio);
        }
    }
}
