package nexus.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import nexus.dominio.Banner;
import nexus.dominio.EstadoProducto;
import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;
import nexus.dominio.TipoProducto;
import nexus.recursos.ClaseDeRecurso;
import nexus.recursos.FormatoDeRecurso;
import nexus.recursos.RecursoGrafico;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@DisplayName("Registro de operaciones administrativas del catálogo (SA-18, §7.3.5)")
class RegistroDeOperacionesTest {

        private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
        private static final Instant CAMBIO = Instant.parse("2026-10-14T14:59:58.123456Z");
        private static final String ADMIN = "8f14e45f-ceea-467f-a0e6-9ea7c2b1e7a1";
        private static final String PRODUCTO = "550e8400-e29b-41d4-a716-446655440000";

        private final OperacionAdministrativaRepository repositorio = mock(OperacionAdministrativaRepository.class);
        private final RegistroDeOperaciones registro =
                        new RegistroDeOperaciones(repositorio, Clock.fixed(AHORA, ZoneOffset.UTC));

        @AfterEach
        void sinPeticion() {
                RequestContextHolder.resetRequestAttributes();
        }

        private static void peticionDesde(String ipDelBorde) {
                MockHttpServletRequest peticion = new MockHttpServletRequest();
                peticion.setRemoteAddr("10.0.0.7");
                if (ipDelBorde != null) {
                        peticion.addHeader("X-Real-IP", ipDelBorde);
                }
                RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(peticion));
        }

        private OperacionAdministrativa registrada() {
                ArgumentCaptor<OperacionAdministrativa> captor = ArgumentCaptor.forClass(OperacionAdministrativa.class);
                verify(repositorio).insert(captor.capture());
                return captor.getValue();
        }

        static Producto arma(EstadoProducto estado, Integer precio, Integer poderDeAtaque, BigDecimal tasa) {
                return new Producto(
                                PRODUCTO, "Espada solar", "/api/v1/productos/recursos/r-1", "Arma de prueba",
                                TipoProducto.ARMA, -1, precio, null, false, null, null, null, null, null, null,
                                null, null, null, null, null, poderDeAtaque, tasa, estado, 3, CAMBIO, CAMBIO,
                                null, null, null, ADMIN, null, List.of());
        }

        private static RespaldoProducto respaldo(TipoCambioProducto tipo, Producto antes, Producto despues,
                        String autor, String motivo) {
                return new RespaldoProducto("respaldo-1", PRODUCTO, antes, despues, CAMBIO, autor, tipo, null, motivo);
        }

        @Nested
        @DisplayName("un cambio de producto")
        class DeProducto {

                @Test
                @DisplayName("el alta queda con quién, cuándo, desde qué IP y lo que define al producto")
                void alta() {
                        peticionDesde("181.52.10.4");

                        registro.deProducto(respaldo(TipoCambioProducto.CREACION, null,
                                        arma(EstadoProducto.UNICO, 900, 12, BigDecimal.ONE), ADMIN, null));

                        OperacionAdministrativa operacion = registrada();
                        assertThat(operacion.id()).isNotBlank();
                        assertThat(operacion.tipo()).isEqualTo(TipoOperacion.ALTA_PRODUCTO);
                        assertThat(operacion.objeto()).isEqualTo(ObjetoAuditado.PRODUCTO);
                        assertThat(operacion.objetoId()).isEqualTo(PRODUCTO);
                        assertThat(operacion.nombre()).isEqualTo("Espada solar");
                        assertThat(operacion.autor()).isEqualTo(ADMIN);
                        assertThat(operacion.ocurridaEn()).isEqualTo(Instant.parse("2026-10-14T14:59:58.123Z"));
                        assertThat(operacion.ipOrigen()).isEqualTo("181.52.10.4");
                        assertThat(operacion.valorAnterior()).isNull();
                        assertThat(operacion.valorNuevo())
                                        .isEqualTo("Tipo: Arma · Estado: Pieza única · Tiraje: ilimitado · Precio en créditos: 900");
                        assertThat(operacion.motivo()).isNull();
                        assertThat(operacion.descripcion()).isEqualTo("Alta del producto en el catálogo");
                }

                @Test
                @DisplayName("tocar un atributo de combate es un cambio de balance, con el valor de antes y el de después")
                void cambioDeBalance() {
                        registro.deProducto(respaldo(TipoCambioProducto.MODIFICACION,
                                        arma(EstadoProducto.ACTIVO, 900, 12, new BigDecimal("5.0")),
                                        arma(EstadoProducto.ACTIVO, 900, 15, new BigDecimal("5.00")), ADMIN, null));

                        OperacionAdministrativa operacion = registrada();
                        assertThat(operacion.tipo()).isEqualTo(TipoOperacion.CAMBIO_DE_BALANCE);
                        // 5.0 y 5.00 son la misma tasa: no se nombra como cambio.
                        assertThat(operacion.valorAnterior()).isEqualTo("Poder de ataque: 12");
                        assertThat(operacion.valorNuevo()).isEqualTo("Poder de ataque: 15");
                        assertThat(operacion.descripcion()).isEqualTo("Cambio de balance del producto");
                }

                @Test
                @DisplayName("cambiar el precio es una modificación")
                void modificacion() {
                        registro.deProducto(respaldo(TipoCambioProducto.MODIFICACION,
                                        arma(EstadoProducto.ACTIVO, 900, 12, BigDecimal.ONE),
                                        arma(EstadoProducto.ACTIVO, 950, 12, BigDecimal.ONE), ADMIN, null));

                        OperacionAdministrativa operacion = registrada();
                        assertThat(operacion.tipo()).isEqualTo(TipoOperacion.MODIFICACION_PRODUCTO);
                        assertThat(operacion.valorAnterior()).isEqualTo("Precio en créditos: 900");
                        assertThat(operacion.valorNuevo()).isEqualTo("Precio en créditos: 950");
                }

                @Test
                @DisplayName("la suspensión lleva el estado de antes, el de después y el motivo escrito")
                void suspension() {
                        registro.deProducto(respaldo(TipoCambioProducto.SUSPENSION,
                                        arma(EstadoProducto.ACTIVO, 900, 12, BigDecimal.ONE),
                                        arma(EstadoProducto.SUSPENDIDO, 900, 12, BigDecimal.ONE), ADMIN,
                                        "  Rompe el balance de la temporada  "));

                        OperacionAdministrativa operacion = registrada();
                        assertThat(operacion.tipo()).isEqualTo(TipoOperacion.SUSPENSION_PRODUCTO);
                        assertThat(operacion.valorAnterior()).isEqualTo("Estado: Activo");
                        assertThat(operacion.valorNuevo()).isEqualTo("Estado: Suspendido");
                        assertThat(operacion.motivo()).isEqualTo("Rompe el balance de la temporada");
                }

                @Test
                @DisplayName("reactivación, reversión e importación de diseño conservan su tipo")
                void otrosTipos() {
                        assertThat(RegistroDeOperaciones.tipoDe(TipoCambioProducto.REACTIVACION, null, null))
                                        .isEqualTo(TipoOperacion.REACTIVACION_PRODUCTO);
                        assertThat(RegistroDeOperaciones.tipoDe(TipoCambioProducto.REVERSION, null, null))
                                        .isEqualTo(TipoOperacion.REVERSION_PRODUCTO);
                        assertThat(RegistroDeOperaciones.tipoDe(TipoCambioProducto.IMPORTACION_DISENO, null, null))
                                        .isEqualTo(TipoOperacion.IMPORTACION_DE_DISENO);
                }

                @Test
                @DisplayName("lo que no hace una persona (sin autor o «sistema») no es una acción administrativa")
                void sinPersona() {
                        registro.deProducto(respaldo(TipoCambioProducto.CREACION, null,
                                        arma(EstadoProducto.ACTIVO, 1, 1, BigDecimal.ONE), "sistema", null));
                        registro.deProducto(respaldo(TipoCambioProducto.CREACION, null,
                                        arma(EstadoProducto.ACTIVO, 1, 1, BigDecimal.ONE), null, null));
                        registro.deProducto(null);

                        verify(repositorio, never()).insert(any(OperacionAdministrativa.class));
                }

                @Test
                @DisplayName("si la escritura falla, la operación ya hecha no se deshace ni se propaga el error")
                void escrituraFallida() {
                        when(repositorio.insert(any(OperacionAdministrativa.class)))
                                        .thenThrow(new IllegalStateException("MongoDB no responde"));

                        registro.deProducto(respaldo(TipoCambioProducto.CREACION, null,
                                        arma(EstadoProducto.ACTIVO, 1, 1, BigDecimal.ONE), ADMIN, null));

                        verify(repositorio).insert(any(OperacionAdministrativa.class));
                }

                @Test
                @DisplayName("sin petición en curso (una tarea interna) la IP queda vacía, no inventada")
                void sinPeticionNoHayIp() {
                        registro.deProducto(respaldo(TipoCambioProducto.CREACION, null,
                                        arma(EstadoProducto.ACTIVO, 1, 1, BigDecimal.ONE), ADMIN, null));

                        assertThat(registrada().ipOrigen()).isNull();
                }
        }

        @Nested
        @DisplayName("un banner y un recurso gráfico")
        class DeBannerYRecurso {

                private final Banner banner = new Banner("banner-1", "Temporada de héroes",
                                Instant.parse("2026-10-15T00:00:00Z"), Instant.parse("2026-10-31T23:59:00Z"), false,
                                CAMBIO, CAMBIO, ADMIN, ADMIN);

                @Test
                @DisplayName("el retiro de un banner con su motivo y su contenido antes y después")
                void retiroDeBanner() {
                        peticionDesde("2800:e2:2b00::1");
                        Banner retirado = new Banner(banner.id(), banner.contenido(), banner.publicarDesde(),
                                        banner.vigenteHasta(), true, banner.creadoEn(), AHORA, ADMIN, ADMIN);

                        registro.deBanner(TipoOperacion.RETIRO_BANNER, banner, retirado, ADMIN, "Evento cancelado");

                        OperacionAdministrativa operacion = registrada();
                        assertThat(operacion.objeto()).isEqualTo(ObjetoAuditado.BANNER);
                        assertThat(operacion.nombre()).isEqualTo("Temporada de héroes");
                        assertThat(operacion.ocurridaEn()).isEqualTo(AHORA);
                        assertThat(operacion.ipOrigen()).isEqualTo("2800:e2:2b00::1");
                        assertThat(operacion.valorAnterior()).isEqualTo(
                                        "«Temporada de héroes» · Publicación: 2026-10-15 00:00 UTC · Vigente hasta: 2026-10-31 23:59 UTC");
                        assertThat(operacion.valorNuevo()).endsWith(" · Retirado");
                        assertThat(operacion.motivo()).isEqualTo("Evento cancelado");
                        assertThat(operacion.descripcion()).isEqualTo("Retiro de un banner");
                }

                @Test
                @DisplayName("el alta de un recurso gráfico con su clase, formato y tamaño")
                void altaDeRecurso() {
                        RecursoGrafico recurso = new RecursoGrafico("recurso-1", ClaseDeRecurso.ICONO,
                                        FormatoDeRecurso.PNG, 2048, 64, 64, "huella", new byte[] {1}, PRODUCTO,
                                        "icono.png", ADMIN, CAMBIO, null, null, null);

                        registro.deRecurso(TipoOperacion.ALTA_RECURSO, null, recurso, ADMIN, null);

                        OperacionAdministrativa operacion = registrada();
                        assertThat(operacion.objeto()).isEqualTo(ObjetoAuditado.RECURSO_GRAFICO);
                        assertThat(operacion.nombre()).isEqualTo("icono.png");
                        assertThat(operacion.valorNuevo()).isEqualTo(
                                        "Clase: Icono · Formato: PNG · Dimensiones: 64×64 px · Peso: 2048 bytes · Producto: " + PRODUCTO);
                        assertThat(operacion.descripcion()).isEqualTo("Alta de un recurso gráfico en la biblioteca");
                }

                @Test
                @DisplayName("el contenido largo de un banner se recorta como nombre de lo afectado")
                void nombreLargo() {
                        String largo = "Anuncio ".repeat(40);
                        Banner extenso = new Banner("banner-2", largo, banner.publicarDesde(), banner.vigenteHasta(),
                                        false, CAMBIO, CAMBIO, ADMIN, ADMIN);

                        registro.deBanner(TipoOperacion.ALTA_BANNER, null, extenso, ADMIN, null);

                        assertThat(registrada().nombre()).hasSizeLessThanOrEqualTo(RegistroDeOperaciones.LARGO_DEL_NOMBRE)
                                        .endsWith("…");
                }
        }

        @Nested
        @DisplayName("el lote para la bitácora central")
        class Lote {

                private OperacionAdministrativa en(String id, String instante) {
                        return new OperacionAdministrativa(id, Instant.parse(instante), ADMIN,
                                        TipoOperacion.ALTA_BANNER, ObjetoAuditado.BANNER, "b", "b", null, "x", null,
                                        "Publicación de un banner", "1.1.1.1");
                }

                @Test
                @DisplayName("sin desde, desde la primera operación (la bitácora las necesita todas)")
                void sinDesde() {
                        when(repositorio.buscarPrimerasEntre(eq(Instant.EPOCH), eq(AHORA), any(Pageable.class)))
                                        .thenReturn(List.of(en("a", "2026-10-01T00:00:00Z")));

                        LoteDeOperaciones lote = registro.consultarLote(null, 50);

                        assertThat(lote.operaciones()).extracting(OperacionAdministrativa::id).containsExactly("a");
                        assertThat(lote.hasta()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
                        assertThat(lote.completo()).isTrue();
                }

                @Test
                @DisplayName("sin operaciones nuevas, hasta es el propio desde")
                void vacio() {
                        Instant desde = Instant.parse("2026-10-10T00:00:00Z");
                        when(repositorio.buscarPrimerasEntre(eq(desde), eq(AHORA), any(Pageable.class)))
                                        .thenReturn(List.of());

                        LoteDeOperaciones lote = registro.consultarLote(desde, 50);

                        assertThat(lote.operaciones()).isEmpty();
                        assertThat(lote.hasta()).isEqualTo(desde);
                        assertThat(lote.completo()).isTrue();
                }

                @Test
                @DisplayName("al cortar en el límite entra el grupo entero de la última marca y se avisa que falta")
                void corteSinPartirUnaMarca() {
                        Instant desde = Instant.parse("2026-10-10T00:00:00Z");
                        when(repositorio.buscarPrimerasEntre(desde, AHORA, PageRequest.of(0, 3))).thenReturn(List.of(
                                        en("a", "2026-10-11T00:00:00Z"),
                                        en("b", "2026-10-12T00:00:00Z"),
                                        en("c", "2026-10-12T00:00:00Z")));
                        when(repositorio.buscarEntre(desde, Instant.parse("2026-10-12T00:00:00Z"))).thenReturn(List.of(
                                        en("c", "2026-10-12T00:00:00Z"),
                                        en("a", "2026-10-11T00:00:00Z"),
                                        en("b", "2026-10-12T00:00:00Z")));
                        when(repositorio.buscarPrimerasEntre(Instant.parse("2026-10-12T00:00:00Z"), AHORA,
                                        PageRequest.of(0, 1))).thenReturn(List.of(en("d", "2026-10-13T00:00:00Z")));

                        LoteDeOperaciones lote = registro.consultarLote(desde, 2);

                        assertThat(lote.operaciones()).extracting(OperacionAdministrativa::id)
                                        .containsExactly("a", "b", "c");
                        assertThat(lote.hasta()).isEqualTo(Instant.parse("2026-10-12T00:00:00Z"));
                        assertThat(lote.completo()).isFalse();
                }

                @Test
                @DisplayName("un límite fuera de 1..200 no se consulta")
                void limiteFueraDeRango() {
                        assertThatThrownBy(() -> registro.consultarLote(null, 0))
                                        .isInstanceOf(IllegalArgumentException.class);
                        assertThatThrownBy(() -> registro.consultarLote(null, 201))
                                        .isInstanceOf(IllegalArgumentException.class);
                }

                @Test
                @DisplayName("el historial de un objeto lo pide por su clase y su id")
                void historial() {
                        when(repositorio.findByObjetoAndObjetoIdOrderByOcurridaEnDesc(ObjetoAuditado.BANNER, "b"))
                                        .thenReturn(List.of(en("a", "2026-10-11T00:00:00Z")));

                        assertThat(registro.historialDe(ObjetoAuditado.BANNER, "b")).hasSize(1);
                }
        }
}
