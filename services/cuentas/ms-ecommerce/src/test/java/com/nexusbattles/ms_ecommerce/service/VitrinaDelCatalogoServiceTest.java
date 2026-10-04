package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.CopiaDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.PromocionDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.ConsultaDeVitrina;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.PaginaDeVitrina;
import com.nexusbattles.ms_ecommerce.dto.ProductoEnVentaDto;
import com.nexusbattles.ms_ecommerce.integracion.inventario.ProductosPropios;
import com.nexusbattles.ms_ecommerce.precios.Moneda;
import com.nexusbattles.ms_ecommerce.precios.Tarifa;
import com.nexusbattles.ms_ecommerce.precios.TasasDeCambio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conEstado;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conHabilidades;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conPrecio;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.conTiraje;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.enVenta;
import static com.nexusbattles.ms_ecommerce.catalogo.ProductosDePrueba.soloEnCreditos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Vitrina: proyeccion del catalogo maestro")
class VitrinaDelCatalogoServiceTest {

    private CatalogoMaestro catalogo;
    private RelojAjustable reloj;
    private TasasDeCambio tasas;
    private ListaDeDeseosService deseos;
    private ProductosPropios propios;
    private VitrinaDelCatalogoService vitrina;

    @BeforeEach
    void preparar() {
        catalogo = mock(CatalogoMaestro.class);
        reloj = new RelojAjustable(Instant.parse("2026-09-24T12:00:00Z"));
        tasas = mock(TasasDeCambio.class);
        deseos = mock(ListaDeDeseosService.class);
        propios = mock(ProductosPropios.class);
        when(tasas.tarifa(Moneda.COP)).thenReturn(Tarifa.enPesos());
        when(tasas.disponibles()).thenReturn(Set.of(Moneda.COP));
        vitrina = new VitrinaDelCatalogoService(new CopiaDelCatalogo(catalogo, reloj), tasas, deseos, propios, reloj);
    }

    private void catalogoCon(ProductoDelCatalogo... productos) {
        when(catalogo.productosEnVenta()).thenReturn(List.of(productos));
    }

    private List<String> idsDe(PaginaDeVitrina pagina) {
        return pagina.content().stream().map(ProductoEnVentaDto::id).toList();
    }

    @Nested
    @DisplayName("que se vende")
    class QueSeVende {

        @Test
        @DisplayName("solo ACTIVO y UNICO; SUSPENDIDO, sin estado o con un estado desconocido no")
        void soloEstadosDeVenta() {
            catalogoCon(
                    conEstado(enVenta("activo"), "ACTIVO"),
                    conEstado(enVenta("unico"), "UNICO"),
                    conEstado(enVenta("suspendido"), "SUSPENDIDO"),
                    conEstado(enVenta("sin-estado"), null),
                    conEstado(enVenta("borrador"), "BORRADOR"));

            assertThat(idsDe(vitrina.pagina(0, 16, null))).containsExactly("activo", "unico");
        }

        @Test
        @DisplayName("sin precio en moneda real no aparece; nunca se ensena a 0")
        void sinPrecioEnMonedaReal() {
            catalogoCon(
                    enVenta("con-precio"),
                    soloEnCreditos(enVenta("solo-creditos"), 300),
                    conPrecio(enVenta("sin-precio"), null));

            PaginaDeVitrina pagina = vitrina.pagina(0, 16, null);

            assertThat(idsDe(pagina)).containsExactly("con-precio");
            assertThat(pagina.content()).allSatisfy(p -> assertThat(p.precioFinal()).isNotNull());
        }

        @Test
        @DisplayName("un precio en moneda real de 0 no se vende en la tienda: nunca «0 COP»")
        void precioCeroNoSeMuestra() {
            catalogoCon(
                    conPrecio(enVenta("a-cero"), BigDecimal.ZERO),
                    conPrecio(enVenta("a-uno"), BigDecimal.ONE));

            assertThat(idsDe(vitrina.pagina(0, 16, null))).containsExactly("a-uno");
        }

        @Test
        @DisplayName("agotado (tiraje 0) no aparece; ilimitado (-1) y con unidades si")
        void existencias() {
            catalogoCon(
                    conTiraje(enVenta("ilimitado"), -1),
                    conTiraje(enVenta("quedan-3"), 3),
                    conTiraje(enVenta("agotado"), 0),
                    conTiraje(enVenta("sin-tiraje"), null),
                    conTiraje(enVenta("tiraje-invalido"), -7));

            assertThat(idsDe(vitrina.pagina(0, 16, null))).containsExactly("ilimitado", "quedan-3");
        }

        @Test
        @DisplayName("conserva el orden en que el catalogo publica")
        void conservaElOrden() {
            catalogoCon(enVenta("c"), enVenta("a"), enVenta("b"));

            assertThat(idsDe(vitrina.pagina(0, 16, null))).containsExactly("c", "a", "b");
        }
    }

    @Nested
    @DisplayName("filtro por tipo")
    class FiltroPorTipo {

        @BeforeEach
        void catalogoMixto() {
            catalogoCon(
                    enVenta("espada", "ARMA", "6000"),
                    enVenta("casco", "ARMADURA", "4000"),
                    enVenta("hacha", "ARMA", "7000"),
                    enVenta("guerrero", "HEROE", "20000"));
        }

        @Test
        @DisplayName("devuelve solo ese tipo, en el orden del catalogo")
        void soloEseTipo() {
            PaginaDeVitrina armas = vitrina.pagina(0, 16, "ARMA");

            assertThat(idsDe(armas)).containsExactly("espada", "hacha");
            assertThat(armas.totalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("no distingue mayusculas ni espacios alrededor")
        void sinDistinguirMayusculas() {
            assertThat(idsDe(vitrina.pagina(0, 16, " arma "))).containsExactly("espada", "hacha");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("sin tipo, o en blanco, devuelve todos")
        void sinTipo(String tipo) {
            assertThat(vitrina.pagina(0, 16, tipo).totalElements()).isEqualTo(4);
        }

        @Test
        @DisplayName("un tipo que no existe es una vitrina vacia, no un error")
        void tipoDesconocido() {
            PaginaDeVitrina pagina = vitrina.pagina(0, 16, "NAVE");

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.totalElements()).isZero();
            assertThat(pagina.last()).isTrue();
        }
    }

    @Nested
    @DisplayName("paginacion local")
    class Paginacion {

        @BeforeEach
        void veinteProductos() {
            ProductoDelCatalogo[] productos = IntStream.range(0, 20)
                    .mapToObj(i -> enVenta("p%02d".formatted(i)))
                    .toArray(ProductoDelCatalogo[]::new);
            catalogoCon(productos);
        }

        @Test
        @DisplayName("16 por pagina: la primera va llena y no es la ultima")
        void primeraPagina() {
            PaginaDeVitrina pagina = vitrina.pagina(0, 16, null);

            assertThat(pagina.content()).hasSize(16);
            assertThat(pagina.content().get(0).id()).isEqualTo("p00");
            assertThat(pagina.number()).isZero();
            assertThat(pagina.size()).isEqualTo(16);
            assertThat(pagina.totalElements()).isEqualTo(20);
            assertThat(pagina.totalPages()).isEqualTo(2);
            assertThat(pagina.last()).isFalse();
        }

        @Test
        @DisplayName("la ultima pagina trae el resto y lo dice")
        void ultimaPagina() {
            PaginaDeVitrina pagina = vitrina.pagina(1, 16, null);

            assertThat(idsDe(pagina)).containsExactly("p16", "p17", "p18", "p19");
            assertThat(pagina.number()).isEqualTo(1);
            assertThat(pagina.last()).isTrue();
        }

        @Test
        @DisplayName("una pagina fuera de rango sale vacia, con los totales de verdad")
        void fueraDeRango() {
            PaginaDeVitrina pagina = vitrina.pagina(7, 16, null);

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.number()).isEqualTo(7);
            assertThat(pagina.totalElements()).isEqualTo(20);
            assertThat(pagina.totalPages()).isEqualTo(2);
            assertThat(pagina.last()).isTrue();
        }

        @Test
        @DisplayName("una pagina enorme no desborda el calculo del desplazamiento")
        void paginaEnorme() {
            PaginaDeVitrina pagina = vitrina.pagina(Integer.MAX_VALUE, 50, null);

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.last()).isTrue();
        }

        @Test
        @DisplayName("con el catalogo vacio: cero paginas y es la ultima")
        void catalogoVacio() {
            when(catalogo.productosEnVenta()).thenReturn(List.of());

            PaginaDeVitrina pagina = vitrina.pagina(0, 16, null);

            assertThat(pagina.content()).isEmpty();
            assertThat(pagina.totalElements()).isZero();
            assertThat(pagina.totalPages()).isZero();
            assertThat(pagina.last()).isTrue();
        }

        @Test
        @DisplayName("una pagina negativa o un tamano no positivo no son paginas")
        void parametrosInvalidos() {
            assertThatThrownBy(() -> vitrina.pagina(-1, 16, null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> vitrina.pagina(0, 0, null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("mapeo a la tarjeta de la vitrina")
    class Mapeo {

        @Test
        @DisplayName("cada campo sale del catalogo; precio en COP, sin promocion inventada")
        void camposDelCatalogo() {
            ProductoDelCatalogo espada = new ProductoDelCatalogo("5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a",
                    "Espada de fuego", "img/espada.png", "Arde al golpear", "ARMA", 12, 150,
                    new BigDecimal("6000.00"), true, "UNICO", "Golpe ardiente");
            catalogoCon(espada);

            ProductoEnVentaDto tarjeta = vitrina.pagina(0, 16, null).content().get(0);

            assertThat(tarjeta.id()).isEqualTo("5b0a3c1e-8d7f-4e2a-9c6b-1f0e2d3c4b5a");
            assertThat(tarjeta.nombre()).isEqualTo("Espada de fuego");
            assertThat(tarjeta.imagenUrl()).isEqualTo("img/espada.png");
            assertThat(tarjeta.descripcion()).isEqualTo("Arde al golpear");
            assertThat(tarjeta.habilidades()).isEqualTo("Golpe ardiente");
            assertThat(tarjeta.tipo()).isEqualTo("ARMA");
            assertThat(tarjeta.precioFinal()).isEqualByComparingTo("6000.00");
            assertThat(tarjeta.precioOriginal()).isEqualByComparingTo("6000.00");
            assertThat(tarjeta.moneda()).isEqualTo("COP");
            assertThat(tarjeta.enPromocion()).isFalse();
            assertThat(tarjeta.porcentajeDescuento()).isNull();
            assertThat(tarjeta.esPropio()).isFalse();
            assertThat(tarjeta.enListaDeseos()).isFalse();
        }

        @Test
        @DisplayName("D-44: el precio en creditos del catalogo, con la promocion vigente; premium o sin precio: nulo")
        void precioEnCreditos() {
            ProductoDelCatalogo hacha = new ProductoDelCatalogo("hacha", "Hacha", null, null, "ARMA", -1, 300,
                    new BigDecimal("6000"), false, "ACTIVO", null);
            ProductoDelCatalogo escudo = new ProductoDelCatalogo("escudo", "Escudo", null, null, "ARMADURA", -1, 250,
                    new BigDecimal("5000"), false, "ACTIVO", null,
                    new PromocionDelCatalogo(20, Instant.parse("2026-09-01T00:00:00Z"),
                            Instant.parse("2026-10-01T00:00:00Z"), true));
            ProductoDelCatalogo heroe = new ProductoDelCatalogo("heroe", "Heroe", null, null, "HEROE", -1, null,
                    new BigDecimal("20000"), true, "ACTIVO", null);
            ProductoDelCatalogo sinPrecio = new ProductoDelCatalogo("sin", "Anillo", null, null, "ITEM", -1, null,
                    new BigDecimal("3000"), false, "ACTIVO", null);
            catalogoCon(hacha, escudo, heroe, sinPrecio);

            Map<String, Long> precios = new java.util.HashMap<>();
            vitrina.pagina(0, 16, null).content().forEach(p -> precios.put(p.id(), p.precioCreditos()));

            assertThat(precios).containsEntry("hacha", 300L)
                    .as("250 - 20 % = 200").containsEntry("escudo", 200L)
                    .as("premium: solo moneda real").containsEntry("heroe", null)
                    .containsEntry("sin", null);
        }

        @Test
        @DisplayName("habilidades como lista se une con comas; elementos vacios u objetos no se muestran")
        void habilidadesComoLista() {
            catalogoCon(conHabilidades(enVenta("lista"),
                    Arrays.asList("Bloqueo", null, "  ", 3, Map.of("nombre", "oculta"), List.of("anidada"), " Carga ")));

            assertThat(vitrina.pagina(0, 16, null).content().get(0).habilidades()).isEqualTo("Bloqueo, 3, Carga");
        }

        @Test
        @DisplayName("sin habilidades, vacias o con una forma inesperada: nulo")
        void habilidadesAusentes() {
            List<ProductoDelCatalogo> productos = new ArrayList<>();
            productos.add(conHabilidades(enVenta("ausente"), null));
            productos.add(conHabilidades(enVenta("texto-vacio"), "  "));
            productos.add(conHabilidades(enVenta("lista-vacia"), List.of()));
            productos.add(conHabilidades(enVenta("lista-de-vacios"), List.of("", " ")));
            productos.add(conHabilidades(enVenta("objeto"), Map.of("fuego", 3)));
            productos.add(conHabilidades(enVenta("numero"), 42));
            when(catalogo.productosEnVenta()).thenReturn(productos);

            assertThat(vitrina.pagina(0, 16, null).content())
                    .extracting(ProductoEnVentaDto::habilidades)
                    .containsOnlyNulls();
        }
    }

    @Nested
    @DisplayName("copia del catalogo de 30 segundos")
    class Copia {

        @Test
        @DisplayName("dentro de los 30 s no vuelve a preguntar al catalogo")
        void aciertoDentroDeLaVigencia() {
            catalogoCon(enVenta("a"));

            vitrina.pagina(0, 16, null);
            reloj.avanzar(Duration.ofSeconds(29));
            vitrina.pagina(0, 16, "ARMA");

            verify(catalogo, times(1)).productosEnVenta();
        }

        @Test
        @DisplayName("a los 30 s se refresca, y lo nuevo se ve")
        void refrescoAlCaducar() {
            when(catalogo.productosEnVenta())
                    .thenReturn(List.of(enVenta("a")))
                    .thenReturn(List.of(enVenta("a"), enVenta("b")));

            assertThat(vitrina.pagina(0, 16, null).totalElements()).isEqualTo(1);
            reloj.avanzar(Duration.ofSeconds(30));
            assertThat(vitrina.pagina(0, 16, null).totalElements()).isEqualTo(2);

            verify(catalogo, times(2)).productosEnVenta();
        }

        @Test
        @DisplayName("una suspension en el catalogo desaparece de la vitrina al refrescar (RN-PRD-004)")
        void suspensionVisibleAlRefrescar() {
            when(catalogo.productosEnVenta())
                    .thenReturn(List.of(enVenta("a")))
                    .thenReturn(List.of(conEstado(enVenta("a"), "SUSPENDIDO")));

            assertThat(idsDe(vitrina.pagina(0, 16, null))).containsExactly("a");
            reloj.avanzar(Duration.ofSeconds(31));
            assertThat(idsDe(vitrina.pagina(0, 16, null))).isEmpty();
        }
    }

    @Nested
    @DisplayName("catalogo caido")
    class CatalogoCaido {

        @Test
        @DisplayName("sin copia, el fallo sale tal cual (el controlador lo convierte en 503)")
        void sinCopia() {
            when(catalogo.productosEnVenta()).thenThrow(new CatalogoNoDisponibleException("caido"));

            assertThatThrownBy(() -> vitrina.pagina(0, 16, null)).isInstanceOf(CatalogoNoDisponibleException.class);
        }

        @Test
        @DisplayName("con la copia caducada tampoco se sirve la vieja: podria vender algo ya suspendido")
        void noSirveCopiaCaducada() {
            when(catalogo.productosEnVenta())
                    .thenReturn(List.of(enVenta("a")))
                    .thenThrow(new CatalogoNoDisponibleException("caido"));

            vitrina.pagina(0, 16, null);
            reloj.avanzar(Duration.ofSeconds(30));

            assertThatThrownBy(() -> vitrina.pagina(0, 16, null)).isInstanceOf(CatalogoNoDisponibleException.class);
        }

        @Test
        @DisplayName("un fallo no se guarda: la siguiente peticion vuelve a intentarlo")
        void elFalloNoSeGuarda() {
            when(catalogo.productosEnVenta())
                    .thenThrow(new CatalogoNoDisponibleException("caido"))
                    .thenReturn(List.of(enVenta("a")));

            assertThatThrownBy(() -> vitrina.pagina(0, 16, null)).isInstanceOf(CatalogoNoDisponibleException.class);
            assertThat(idsDe(vitrina.pagina(0, 16, null))).containsExactly("a");
        }
    }

    @Nested
    @DisplayName("B5: precio del servidor, moneda, filtros, busqueda y marcas")
    class PrecioFiltrosYMarcas {

        private ProductoDelCatalogo conPromocion(String id, String precio, int porcentaje, String desde, String hasta) {
            return new ProductoDelCatalogo(id, "Producto " + id, null, "Descripcion de " + id, "ARMA", -1, null,
                    new BigDecimal(precio), true, "ACTIVO", null,
                    new PromocionDelCatalogo(porcentaje, Instant.parse(desde), Instant.parse(hasta), true));
        }

        private ConsultaDeVitrina consulta(Moneda moneda, BigDecimal minimo, BigDecimal maximo, boolean promo,
                                           String busqueda) {
            return new ConsultaDeVitrina(0, 16, null, moneda, minimo, maximo, promo, busqueda);
        }

        @Test
        @DisplayName("una promocion vigente rebaja el precio final y se marca con su porcentaje")
        void promocionVigente() {
            catalogoCon(conPromocion("rebajado", "10000", 20, "2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"));

            ProductoEnVentaDto tarjeta = vitrina.pagina(0, 16, null).content().get(0);

            assertThat(tarjeta.precioFinal()).isEqualByComparingTo("8000");
            assertThat(tarjeta.precioOriginal()).isEqualByComparingTo("10000");
            assertThat(tarjeta.enPromocion()).isTrue();
            assertThat(tarjeta.porcentajeDescuento()).isEqualTo(20);
        }

        @Test
        @DisplayName("una promocion vencida o futura no rebaja nada, aunque el catalogo aun la traiga")
        void promocionFueraDeVigencia() {
            catalogoCon(
                    conPromocion("vencida", "10000", 20, "2026-08-01T00:00:00Z", "2026-09-24T12:00:00Z"),
                    conPromocion("futura", "10000", 20, "2026-09-24T12:00:01Z", "2026-10-01T00:00:00Z"));

            assertThat(vitrina.pagina(0, 16, null).content()).allSatisfy(tarjeta -> {
                assertThat(tarjeta.precioFinal()).isEqualByComparingTo("10000");
                assertThat(tarjeta.enPromocion()).isFalse();
                assertThat(tarjeta.porcentajeDescuento()).isNull();
            });
        }

        @Test
        @DisplayName("en USD convierte con la tasa (pesos por dolar) a centavos, y la pagina dice su moneda")
        void conversionAUsd() {
            when(tasas.tarifa(Moneda.USD)).thenReturn(new Tarifa(Moneda.USD, new BigDecimal("4000")));
            when(tasas.disponibles()).thenReturn(Set.of(Moneda.COP, Moneda.USD));
            catalogoCon(enVenta("a", "ARMA", "45000"),
                    conPromocion("b", "10000", 15, "2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"));

            PaginaDeVitrina pagina = vitrina.pagina(consulta(Moneda.USD, null, null, false, null), null);

            assertThat(pagina.moneda()).isEqualTo("USD");
            assertThat(pagina.monedasDisponibles()).containsExactly("COP", "USD");
            assertThat(pagina.content().get(0).precioFinal()).isEqualByComparingTo("11.25");
            assertThat(pagina.content().get(0).moneda()).isEqualTo("USD");
            // 10000 COP - 15 % = 8500 COP = 2.125 USD -> 2.13 (mitad hacia arriba)
            assertThat(pagina.content().get(1).precioFinal()).isEqualByComparingTo("2.13");
            assertThat(pagina.content().get(1).precioOriginal()).isEqualByComparingTo("2.50");
        }

        @Test
        @DisplayName("filtra por rango de precio en la moneda pedida y solo en promocion")
        void filtrosDePrecioYPromocion() {
            catalogoCon(enVenta("barato", "ARMA", "3000"), enVenta("medio", "ARMA", "6000"),
                    enVenta("caro", "ARMA", "20000"),
                    conPromocion("rebajado", "10000", 50, "2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"));

            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, new BigDecimal("4000"), new BigDecimal("10000"), false,
                    null), null))).containsExactly("medio", "rebajado");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, true, null), null)))
                    .containsExactly("rebajado");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, new BigDecimal("9000"), new BigDecimal("1000"), false,
                    null), null))).isEmpty();
        }

        @Test
        @DisplayName("busca sin tildes ni mayusculas en nombre, tipo, descripcion y habilidades, con todas las palabras")
        void busquedaPorTexto() {
            catalogoCon(
                    conHabilidades(conNombreYTipo(enVenta("espada", "ARMA", "6000"), "Espada de Fuego", "ARMA"),
                            List.of("Golpe ardiente")),
                    conNombreYTipo(enVenta("escudo", "ARMADURA", "4000"), "Escudo de roble", "ARMADURA"),
                    conNombreYTipo(enVenta("pocion", "ITEM", "3000"), "Poción de vida", "ITEM"));

            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "ESPADA"), null)))
                    .containsExactly("espada");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "pocion"), null)))
                    .containsExactly("pocion");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "ardiente"), null)))
                    .containsExactly("espada");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "armadura"), null)))
                    .containsExactly("escudo");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "esp fuego"), null)))
                    .containsExactly("espada");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "espada roble"), null))).isEmpty();
        }

        @Test
        @DisplayName("una palabra de cifras busca en el precio; con letras, no")
        void busquedaPorPrecio() {
            catalogoCon(enVenta("a", "ARMA", "45000"), enVenta("b", "ARMA", "6000"));

            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "45.000"), null)))
                    .containsExactly("a");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "$6 000"), null)))
                    .containsExactly("b");
            assertThat(idsDe(vitrina.pagina(consulta(Moneda.COP, null, null, false, "a4"), null))).isEmpty();
        }

        @Test
        @DisplayName("con sesion marca lo de la lista de deseos y lo que el jugador ya tiene")
        void marcasConSesion() {
            catalogoCon(enVenta("deseado"), enVenta("propio"), enVenta("nada"));
            when(deseos.productosDe("uid-1")).thenReturn(Set.of("deseado"));
            when(propios.de("uid-1")).thenReturn(Set.of("propio"));

            List<ProductoEnVentaDto> tarjetas = vitrina.pagina(ConsultaDeVitrina.de(0, 16, null), "uid-1").content();

            assertThat(tarjetas).extracting(ProductoEnVentaDto::enListaDeseos).containsExactly(true, false, false);
            assertThat(tarjetas).extracting(ProductoEnVentaDto::esPropio).containsExactly(false, true, false);
        }

        @Test
        @DisplayName("sin sesion no pregunta ni a la lista de deseos ni al inventario")
        void sinSesionSinMarcas() {
            catalogoCon(enVenta("a"));

            vitrina.pagina(0, 16, null);

            org.mockito.Mockito.verifyNoInteractions(deseos, propios);
        }

        @Test
        @DisplayName("un porcentaje fuera de 1..99 no rebaja ni tumba la vitrina")
        void promocionRara() {
            catalogoCon(conPromocion("raro", "10000", 100, "2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z"));

            ProductoEnVentaDto tarjeta = vitrina.pagina(0, 16, null).content().get(0);

            assertThat(tarjeta.precioFinal()).isEqualByComparingTo("10000");
            assertThat(tarjeta.enPromocion()).isFalse();
        }
    }

    private static ProductoDelCatalogo conNombreYTipo(ProductoDelCatalogo p, String nombre, String tipo) {
        return new ProductoDelCatalogo(p.id(), nombre, p.imagen(), p.descripcion(), tipo, p.tiraje(),
                p.precioCreditos(), p.precioMonedaReal(), p.premium(), p.estado(), p.habilidades());
    }
}
