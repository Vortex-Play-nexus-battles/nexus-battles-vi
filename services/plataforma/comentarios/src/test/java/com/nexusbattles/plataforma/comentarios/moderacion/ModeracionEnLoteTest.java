package com.nexusbattles.plataforma.comentarios.moderacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;

/**
 * HU-COM-008, CA-02: la moderacion en lote. Repositorios respaldados por
 * colecciones, como {@link FlujoDeModeracionTest}: lo que importa es que, tras
 * un lote rechazado, las colecciones quedan EXACTAMENTE como estaban.
 */
@DisplayName("HU-COM-008 CA-02: el lote es todo o nada")
class ModeracionEnLoteTest {

    private static final Instant AHORA = Instant.parse("2026-09-23T10:00:00Z");
    private static final String IP = "203.0.113.7";
    private static final int MAXIMO = 5;

    private Map<String, RegistroDeComentario> filasDeComentarios;
    private List<AsientoDeModeracion> filasDeAsientos;
    private List<String> avisos;
    private List<String> auditados;
    private boolean avisoSale;
    private ServicioDeModeracion servicio;
    private RegistroDeAuditoria auditoria;
    private ModeracionEnLote lote;

    @BeforeEach
    void montar() {
        filasDeComentarios = new LinkedHashMap<>();
        filasDeAsientos = new ArrayList<>();
        avisos = new ArrayList<>();
        auditados = new ArrayList<>();
        avisoSale = true;

        ComentarioRepository comentarios = mock(ComentarioRepository.class);
        when(comentarios.save(any(RegistroDeComentario.class))).thenAnswer(inv -> {
            RegistroDeComentario r = inv.getArgument(0);
            filasDeComentarios.put(r.aDominio().id(), r);
            return r;
        });
        when(comentarios.findAllById(anyIterable())).thenAnswer(inv -> {
            List<RegistroDeComentario> encontrados = new ArrayList<>();
            for (String id : inv.<Iterable<String>>getArgument(0)) {
                RegistroDeComentario r = filasDeComentarios.get(id);
                if (r != null) {
                    encontrados.add(r);
                }
            }
            return encontrados;
        });
        AsientoRepository asientos = mock(AsientoRepository.class);
        when(asientos.save(any(AsientoDeModeracion.class))).thenAnswer(inv -> {
            AsientoDeModeracion a = inv.getArgument(0);
            filasDeAsientos.add(a);
            return a;
        });

        AvisoAlAutor aviso = (c, a) -> {
            if (avisoSale) {
                avisos.add(c.autorId() + ":" + a.accion());
            }
            return avisoSale;
        };
        auditoria = a -> auditados.add(a.comentarioId() + ":" + a.accion());

        servicio = new ServicioDeModeracion(comentarios,
                mock(ReporteRepository.class), asientos, aviso, auditoria,
                Clock.fixed(AHORA, ZoneOffset.UTC), 3, 0);
        lote = new ModeracionEnLote(servicio, aviso, auditoria, MAXIMO);
    }

    private void sembrar(String id, Comentario.Estado estado) {
        Comentario c = new Comentario(id, "prod-1", "autor-" + id, "apodo", "texto", List.of(),
                AHORA.minusSeconds(3600), estado);
        filasDeComentarios.put(id, RegistroDeComentario.desde(c));
    }

    private Comentario leido(String id) {
        return filasDeComentarios.get(id).aDominio();
    }

    private ModeracionEnLote.Lote aplicar(AccionDeModeracion accion, String... ids) {
        return lote.resolver(List.of(ids), "mod-1", "moderadora", accion, "motivo del lote", IP);
    }

    /** Foto de todo lo que un lote rechazado no puede tocar. */
    private record Foto(Map<String, RegistroDeComentario> comentarios, int asientos, int avisos, int auditados) {
    }

    private Foto foto() {
        return new Foto(new LinkedHashMap<>(filasDeComentarios), filasDeAsientos.size(), avisos.size(),
                auditados.size());
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("lote feliz, una prueba por cada accion admitida")
    class Feliz {

        @Test
        @DisplayName("APROBAR: EN_REVISION -> PUBLICADO")
        void aprobar() {
            sembrar("a", Comentario.Estado.EN_REVISION);
            sembrar("b", Comentario.Estado.EN_REVISION);
            verificar(AccionDeModeracion.APROBAR, Comentario.Estado.EN_REVISION, Comentario.Estado.PUBLICADO);
        }

        @Test
        @DisplayName("OCULTAR: PUBLICADO y EN_REVISION -> OCULTO")
        void ocultar() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            sembrar("b", Comentario.Estado.EN_REVISION);
            aplicar(AccionDeModeracion.OCULTAR, "a", "b");
            assertEquals(Comentario.Estado.OCULTO, leido("a").estado());
            assertEquals(Comentario.Estado.OCULTO, leido("b").estado());
            assertEquals(Comentario.Estado.PUBLICADO, filasDeAsientos.get(0).estadoAnterior());
            assertEquals(Comentario.Estado.EN_REVISION, filasDeAsientos.get(1).estadoAnterior());
        }

        @Test
        @DisplayName("ELIMINAR: -> ELIMINADO")
        void eliminar() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            sembrar("b", Comentario.Estado.OCULTO);
            verificar(AccionDeModeracion.ELIMINAR, null, Comentario.Estado.ELIMINADO);
        }

        @Test
        @DisplayName("RESTAURAR: OCULTO -> PUBLICADO")
        void restaurar() {
            sembrar("a", Comentario.Estado.OCULTO);
            sembrar("b", Comentario.Estado.OCULTO);
            verificar(AccionDeModeracion.RESTAURAR, Comentario.Estado.OCULTO, Comentario.Estado.PUBLICADO);
        }

        @Test
        @DisplayName("MARCAR y DESMARCAR: cambian la marca, no el estado, y no avisan al autor")
        void marcarYDesmarcar() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            sembrar("b", Comentario.Estado.EN_REVISION);

            ModeracionEnLote.Lote marcados = aplicar(AccionDeModeracion.MARCAR, "a", "b");
            assertTrue(leido("a").marcado() && leido("b").marcado());
            assertEquals(Comentario.Estado.EN_REVISION, leido("b").estado());
            assertTrue(marcados.resultados().stream().noneMatch(ServicioDeModeracion.Resuelto::autorNotificado));

            aplicar(AccionDeModeracion.DESMARCAR, "a", "b");
            assertFalse(leido("a").marcado() || leido("b").marcado());

            assertTrue(avisos.isEmpty(), "la marca es una nota interna");
            assertEquals(4, filasDeAsientos.size());
            assertEquals(4, auditados.size(), "pero si se audita");
        }

        private void verificar(AccionDeModeracion accion, Comentario.Estado anterior, Comentario.Estado nuevo) {
            ModeracionEnLote.Lote resultado = aplicar(accion, "a", "b");

            assertEquals(2, resultado.total());
            assertEquals(List.of("a", "b"), resultado.resultados().stream().map(r -> r.comentario().id()).toList(),
                    "en el orden pedido");
            for (String id : List.of("a", "b")) {
                assertEquals(nuevo, leido(id).estado());
            }
            assertEquals(2, filasDeAsientos.size(), "un asiento por comentario");
            for (AsientoDeModeracion a : filasDeAsientos) {
                assertEquals("mod-1", a.moderadorId());
                assertEquals("moderadora", a.apodoModerador());
                assertEquals(accion, a.accion());
                assertEquals("motivo del lote", a.motivo());
                assertEquals(nuevo, a.estadoNuevo());
                assertEquals(IP, a.ipOrigen());
                assertEquals(AHORA, a.fecha());
                if (anterior != null) {
                    assertEquals(anterior, a.estadoAnterior());
                }
            }
            assertEquals(List.of("autor-a:" + accion, "autor-b:" + accion), avisos, "un aviso por autor");
            assertEquals(List.of("a:" + accion, "b:" + accion), auditados);
            assertTrue(resultado.resultados().stream().allMatch(ServicioDeModeracion.Resuelto::autorNotificado));
        }
    }

    @Nested
    @DisplayName("un comentario invalido rechaza el lote entero")
    class Rechazado {

        @Test
        @DisplayName("uno invalido entre tres: cero cambios, cero asientos, cero avisos, cero auditados")
        void unoInvalido() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            sembrar("b", Comentario.Estado.ELIMINADO);
            sembrar("c", Comentario.Estado.PUBLICADO);
            Foto antes = foto();

            ServicioDeModeracion.LoteRechazado rechazo = assertThrows(ServicioDeModeracion.LoteRechazado.class,
                    () -> aplicar(AccionDeModeracion.OCULTAR, "a", "b", "c"));

            assertEquals(1, rechazo.fallidos().size());
            ServicioDeModeracion.Fallo fallo = rechazo.fallidos().get(0);
            assertEquals("b", fallo.comentarioId());
            assertEquals(ServicioDeModeracion.Fallo.Motivo.TRANSICION_INVALIDA, fallo.motivo());
            assertTrue(fallo.detalle().contains("ELIMINADO"), fallo.detalle());
            assertEquals(antes, foto(), "ni comentarios, ni asientos, ni avisos, ni auditados");
            assertEquals(Comentario.Estado.PUBLICADO, leido("a").estado());
        }

        @Test
        @DisplayName("un id inexistente va en fallidos como COMENTARIO_NO_ENCONTRADO y nada cambia")
        void inexistente() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            Foto antes = foto();

            ServicioDeModeracion.LoteRechazado rechazo = assertThrows(ServicioDeModeracion.LoteRechazado.class,
                    () -> aplicar(AccionDeModeracion.OCULTAR, "a", "fantasma"));

            assertEquals(List.of(new ServicioDeModeracion.Fallo("fantasma",
                    ServicioDeModeracion.Fallo.Motivo.COMENTARIO_NO_ENCONTRADO, "No existe el comentario fantasma")),
                    rechazo.fallidos());
            assertEquals(antes, foto());
        }

        @Test
        @DisplayName("fallidos lista TODOS los que fallan, en el orden pedido, con la marca incluida")
        void todosLosFallidos() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            sembrar("b", Comentario.Estado.ELIMINADO);
            sembrar("c", Comentario.Estado.PUBLICADO);
            filasDeComentarios.put("c", RegistroDeComentario.desde(leido("c").conMarca(true)));
            Foto antes = foto();

            ServicioDeModeracion.LoteRechazado rechazo = assertThrows(ServicioDeModeracion.LoteRechazado.class,
                    () -> aplicar(AccionDeModeracion.MARCAR, "c", "a", "b", "x"));

            assertEquals(List.of("c", "b", "x"),
                    rechazo.fallidos().stream().map(ServicioDeModeracion.Fallo::comentarioId).toList());
            assertTrue(rechazo.fallidos().get(0).detalle().contains("ya esta marcado"));
            assertEquals(antes, foto(), "el comentario a era valido y tampoco se toca");
        }
    }

    @Nested
    @DisplayName("la peticion mal formada es 400 y no toca nada")
    class Invalida {

        private void rechaza(List<String> ids, AccionDeModeracion accion, String motivo) {
            sembrar("a", Comentario.Estado.PUBLICADO);
            Foto antes = foto();
            assertThrows(ServicioDeModeracion.DecisionIncompleta.class,
                    () -> lote.resolver(ids, "mod-1", "moderadora", accion, motivo, IP));
            assertEquals(antes, foto());
        }

        @Test
        @DisplayName("lista vacia o nula")
        void vacio() {
            rechaza(List.of(), AccionDeModeracion.OCULTAR, "motivo valido");
            rechaza(null, AccionDeModeracion.OCULTAR, "motivo valido");
        }

        @Test
        @DisplayName("mas del maximo configurado; justo el maximo vale")
        void mayorAlMaximo() {
            rechaza(List.of("a", "b", "c", "d", "e", "f"), AccionDeModeracion.OCULTAR, "motivo valido");

            for (String id : List.of("b", "c", "d", "e")) {
                sembrar(id, Comentario.Estado.PUBLICADO);
            }
            assertEquals(MAXIMO, aplicar(AccionDeModeracion.OCULTAR, "a", "b", "c", "d", "e").total());
        }

        @Test
        @DisplayName("ids repetidos")
        void duplicados() {
            rechaza(List.of("a", "a"), AccionDeModeracion.OCULTAR, "motivo valido");
        }

        @Test
        @DisplayName("ids en blanco o nulos")
        void blancos() {
            rechaza(List.of("a", "  "), AccionDeModeracion.OCULTAR, "motivo valido");
            rechaza(Arrays.asList("a", null), AccionDeModeracion.OCULTAR, "motivo valido");
        }

        @Test
        @DisplayName("EDITAR no va en lote; sin accion tampoco")
        void editarYSinAccion() {
            rechaza(List.of("a"), AccionDeModeracion.EDITAR, "motivo valido");
            rechaza(List.of("a"), null, "motivo valido");
        }

        @Test
        @DisplayName("motivo de menos de 3 caracteres, en blanco, nulo o de mas de 500")
        void motivo() {
            rechaza(List.of("a"), AccionDeModeracion.OCULTAR, "ok");
            rechaza(List.of("a"), AccionDeModeracion.OCULTAR, "   ");
            rechaza(List.of("a"), AccionDeModeracion.OCULTAR, null);
            rechaza(List.of("a"), AccionDeModeracion.OCULTAR, "x".repeat(501));
        }

        @Test
        @DisplayName("el servicio rechaza ids repetidos aunque le lleguen directo: cero asientos, cero cambios")
        void servicioRechazaDuplicados() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            Foto antes = foto();

            assertThrows(ServicioDeModeracion.DecisionIncompleta.class, () -> servicio.aplicarLote(
                    List.of("a", "a"), "mod-1", "m", AccionDeModeracion.OCULTAR, "motivo valido", IP));

            assertEquals(antes, foto());
            assertEquals(Comentario.Estado.PUBLICADO, leido("a").estado());
        }

        @Test
        @DisplayName("el servicio tambien se protege de EDITAR aunque le llegue directo")
        void servicioRechazaEditar() {
            ServicioDeModeracion servicio = new ServicioDeModeracion(mock(ComentarioRepository.class),
                    mock(ReporteRepository.class), mock(AsientoRepository.class), (c, a) -> true, a -> { },
                    Clock.fixed(AHORA, ZoneOffset.UTC), 3, 0);
            assertThrows(ServicioDeModeracion.DecisionIncompleta.class, () -> servicio.aplicarLote(
                    List.of("a"), "mod-1", "m", AccionDeModeracion.EDITAR, "motivo valido", IP));
            assertThrows(ServicioDeModeracion.DecisionIncompleta.class, () -> servicio.aplicarLote(
                    List.of("a"), "mod-1", "m", null, "motivo valido", IP));
        }
    }

    @Nested
    @DisplayName("aviso y auditoria: despues de confirmar, y fail-open")
    class AvisosYAuditoria {

        @Test
        @DisplayName("un aviso que devuelve false da autorNotificado=false y lo demas queda intacto")
        void avisoCaido() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            sembrar("b", Comentario.Estado.PUBLICADO);
            avisoSale = false;

            ModeracionEnLote.Lote resultado = aplicar(AccionDeModeracion.OCULTAR, "a", "b");

            assertTrue(resultado.resultados().stream().noneMatch(ServicioDeModeracion.Resuelto::autorNotificado));
            assertEquals(Comentario.Estado.OCULTO, leido("a").estado());
            assertEquals(Comentario.Estado.OCULTO, leido("b").estado());
            assertEquals(2, filasDeAsientos.size());
            assertEquals(2, auditados.size());
        }

        @Test
        @DisplayName("un aviso o una auditoria que revientan no esconden el resultado ni frenan al resto")
        void reventones() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            sembrar("b", Comentario.Estado.PUBLICADO);
            List<String> auditadosOk = new ArrayList<>();
            AvisoAlAutor aviso = (c, a) -> {
                if (c.id().equals("a")) {
                    throw new IllegalStateException("avisos caidos");
                }
                return true;
            };
            RegistroDeAuditoria auditoria = a -> {
                if (a.comentarioId().equals("a")) {
                    throw new IllegalStateException("bitacora caida");
                }
                auditadosOk.add(a.comentarioId());
            };
            ServicioDeModeracion servicio = mock(ServicioDeModeracion.class);
            List<ServicioDeModeracion.Aplicado> aplicados = List.of(
                    new ServicioDeModeracion.Aplicado(leido("a").con(Comentario.Estado.OCULTO), asientoDe("a")),
                    new ServicioDeModeracion.Aplicado(leido("b").con(Comentario.Estado.OCULTO), asientoDe("b")));
            when(servicio.aplicarLote(any(), any(), any(), any(), any(), any())).thenReturn(aplicados);

            ModeracionEnLote enLote = new ModeracionEnLote(servicio, aviso, auditoria, MAXIMO);
            ModeracionEnLote.Lote resultado = enLote.resolver(List.of("a", "b"), "mod-1", "m",
                    AccionDeModeracion.OCULTAR, "motivo valido", IP);

            assertEquals(List.of(false, true),
                    resultado.resultados().stream().map(ServicioDeModeracion.Resuelto::autorNotificado).toList());
            assertEquals(List.of("b"), auditadosOk, "el fallo de a no impidio auditar b");
        }

        @Test
        @DisplayName("un lote rechazado no avisa ni audita a nadie")
        void rechazadoNoAvisa() {
            sembrar("a", Comentario.Estado.PUBLICADO);
            assertThrows(ServicioDeModeracion.LoteRechazado.class,
                    () -> aplicar(AccionDeModeracion.RESTAURAR, "a"));
            assertTrue(avisos.isEmpty());
            assertTrue(auditados.isEmpty());
        }

        private AsientoDeModeracion asientoDe(String id) {
            return new AsientoDeModeracion("asi-" + id, id, "mod-1", "m", AccionDeModeracion.OCULTAR,
                    "motivo valido", Comentario.Estado.PUBLICADO, Comentario.Estado.OCULTO, AHORA);
        }
    }

    @Nested
    @DisplayName("el corte de avisos: tras 3 fallos seguidos no se avisa mas")
    class CorteDeAvisos {

        /** Un aviso que responde lo que diga el guion, en orden: Boolean o RuntimeException. */
        private List<String> llamados;
        private List<Object> guion;

        private ModeracionEnLote conGuion(Object... pasos) {
            llamados = new ArrayList<>();
            guion = List.of(pasos);
            AvisoAlAutor guionado = (c, a) -> {
                // Si se intentara un aviso de mas, el guion se queda sin pasos y el test lo delata.
                Object paso = guion.get(llamados.size());
                llamados.add(c.id());
                if (paso instanceof RuntimeException e) {
                    throw e;
                }
                return (Boolean) paso;
            };
            return new ModeracionEnLote(servicio, guionado, auditoria, 20);
        }

        private List<String> sembrarN(int n) {
            List<String> ids = new ArrayList<>();
            for (int i = 1; i <= n; i++) {
                sembrar("c" + i, Comentario.Estado.PUBLICADO);
                ids.add("c" + i);
            }
            return ids;
        }

        private ModeracionEnLote.Lote ocultar(ModeracionEnLote enLote, List<String> ids) {
            return enLote.resolver(ids, "mod-1", "moderadora", AccionDeModeracion.OCULTAR, "motivo del lote", IP);
        }

        private List<Boolean> notificados(ModeracionEnLote.Lote resultado) {
            return resultado.resultados().stream().map(ServicioDeModeracion.Resuelto::autorNotificado).toList();
        }

        @Test
        @DisplayName("el corte ocurre exactamente tras el tercer fallo seguido: no hay cuarta llamada")
        void cortaTrasElTercero() {
            List<String> ids = sembrarN(6);
            ModeracionEnLote enLote = conGuion(false, false, false);

            ModeracionEnLote.Lote resultado = ocultar(enLote, ids);

            assertEquals(List.of("c1", "c2", "c3"), llamados, "tres intentos y ninguno mas");
            assertEquals(List.of(false, false, false, false, false, false), notificados(resultado));
        }

        @Test
        @DisplayName("tras el corte los avisos saltados no se llaman, aunque habrian salido")
        void losSaltadosNoLlaman() {
            List<String> ids = sembrarN(5);
            ModeracionEnLote enLote = conGuion(false, false, false, true, true);

            ModeracionEnLote.Lote resultado = ocultar(enLote, ids);

            assertEquals(List.of("c1", "c2", "c3"), llamados);
            assertEquals(List.of(false, false, false, false, false), notificados(resultado),
                    "c4 y c5 no se avisan: autorNotificado=false");
        }

        @Test
        @DisplayName("un exito intercalado reinicia la cuenta: rachas de dos fallos no cortan")
        void elExitoReinicia() {
            List<String> ids = sembrarN(9);
            // F F T F F T F F F: el corte llegaria tras el noveno, el ultimo.
            ModeracionEnLote enLote = conGuion(false, false, true, false, false, true, false, false, false);

            ModeracionEnLote.Lote resultado = ocultar(enLote, ids);

            assertEquals(9, llamados.size(), "las rachas de dos no cortan");
            assertEquals(List.of(false, false, true, false, false, true, false, false, false),
                    notificados(resultado));
        }

        @Test
        @DisplayName("una racha de tres corta aunque antes hubiera habido exitos")
        void cortaTrasUnaRachaPosterior() {
            List<String> ids = sembrarN(8);
            ModeracionEnLote enLote = conGuion(true, false, false, true, false, false, false);

            ModeracionEnLote.Lote resultado = ocultar(enLote, ids);

            assertEquals(List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7"), llamados);
            assertEquals(List.of(true, false, false, true, false, false, false, false), notificados(resultado),
                    "c8 se salta tras la racha c5-c7");
        }

        @Test
        @DisplayName("una excepcion cuenta como fallo igual que un false")
        void laExcepcionCuenta() {
            List<String> ids = sembrarN(5);
            ModeracionEnLote enLote = conGuion(new IllegalStateException("a"), false,
                    new IllegalStateException("b"));

            ModeracionEnLote.Lote resultado = ocultar(enLote, ids);

            assertEquals(List.of("c1", "c2", "c3"), llamados);
            assertEquals(List.of(false, false, false, false, false), notificados(resultado));
        }

        @Test
        @DisplayName("el resultado trae todos los comentarios, con asientos y auditoria, aunque se corte")
        void elResultadoEsCompleto() {
            List<String> ids = sembrarN(6);
            ModeracionEnLote enLote = conGuion(false, false, false);

            ModeracionEnLote.Lote resultado = ocultar(enLote, ids);

            assertEquals(6, resultado.total());
            assertEquals(ids, resultado.resultados().stream().map(r -> r.comentario().id()).toList());
            assertEquals(6, filasDeAsientos.size());
            for (String id : ids) {
                assertEquals(Comentario.Estado.OCULTO, leido(id).estado());
            }
            assertEquals(6, auditados.size(), "la auditoria no se corta");
        }

        @Test
        @DisplayName("con dos fallos nada se corta y el ultimo aviso sale")
        void dosFallosNoCortan() {
            List<String> ids = sembrarN(3);
            ModeracionEnLote enLote = conGuion(false, false, true);

            ModeracionEnLote.Lote resultado = ocultar(enLote, ids);

            assertEquals(3, llamados.size());
            assertEquals(List.of(false, false, true), notificados(resultado));
        }

        @Test
        @DisplayName("MARCAR no avisa: no llama ni cuenta, y el lote no se corta por eso")
        void marcarNoCuenta() {
            List<String> ids = sembrarN(4);
            ModeracionEnLote enLote = conGuion();

            ModeracionEnLote.Lote resultado = enLote.resolver(ids, "mod-1", "moderadora",
                    AccionDeModeracion.MARCAR, "motivo del lote", IP);

            assertTrue(llamados.isEmpty());
            assertEquals(4, resultado.total());
        }
    }
}
