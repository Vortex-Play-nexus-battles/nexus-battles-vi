package com.nexusbattles.ms_chatbot.chat.catalogo;

import com.nexusbattles.ms_chatbot.chat.enriquecido.EnlaceInterno;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 1.10.0 (7.4.1, 7.4.2, 7.4.5): el catalogo de hoy en el chat.
class ConsultaDeCatalogoTest {

    private static final ProductoDelCatalogoDto ESPADA_UNA = new ProductoDelCatalogoDto("1", "Espada de una mano",
        "+1 ataque y +1 % de critico", "ARMA", 120, null, false, null);
    private static final ProductoDelCatalogoDto ESPADA_DOS = new ProductoDelCatalogoDto("2", "Espada de dos manos",
        "+1 ataque y +3 % de critico", "ARMA", 150, null, false,
        new ProductoDelCatalogoDto.Promocion(20, "2026-10-20T00:00:00Z"));
    private static final ProductoDelCatalogoDto ORBE = new ProductoDelCatalogoDto("3", "Orbe de manos ardientes",
        "+1 daño y +3 % de crítico", "ARMA", 90, null, false, null);
    private static final ProductoDelCatalogoDto GUERRERO = new ProductoDelCatalogoDto("4", "Guerrero Tanque",
        "Resiste en primera línea", "HEROE", null, new BigDecimal("25000"), true, null);
    private static final ProductoDelCatalogoDto CASCO = new ProductoDelCatalogoDto("5", "Magma Ardiente",
        "+2 defensa y +1 vida", "ARMADURA", 80, null, false, null);

    private CatalogoEnVivo catalogo;
    private ConsultaDeCatalogo consulta;

    @BeforeEach
    void configurar() {
        catalogo = mock(CatalogoEnVivo.class);
        when(catalogo.productos()).thenReturn(Optional.of(List.of(ESPADA_UNA, ESPADA_DOS, ORBE, GUERRERO, CASCO)));
        consulta = new ConsultaDeCatalogo(catalogo);
    }

    private ResultadoMotor responder(String mensaje) {
        return consulta.responder(mensaje, IdiomaPreferido.AUTOMATICO).orElseThrow();
    }

    @Test
    void elPrecioDeUnProductoNombrado() {
        ResultadoMotor resultado = responder("¿Cuánto cuesta el Orbe de manos ardientes?");

        assertThat(resultado.texto()).isEqualTo("«Orbe de manos ardientes» (arma) cuesta 90 créditos. "
            + "Lo encuentras en la «Tienda».");
        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::destino).containsExactly("tienda");
        assertThat(resultado.requiereEscalamiento()).isFalse();
    }

    @Test
    void conUnaPalabraDistintivaYUnErrorDeTecleoTambienLoEncuentra() {
        assertThat(responder("precio del orbe").texto()).startsWith("«Orbe de manos ardientes»");
        assertThat(responder("cuanto vale el magma ardinete").texto()).startsWith("«Magma Ardiente» (armadura)");
    }

    @Test
    void variosProductosConElMismoNombreSalenTodosConSuPrecio() {
        assertThat(responder("cuanto cuesta una espada").texto())
            .isEqualTo("Encontré 2 productos con ese nombre: «Espada de una mano» (arma): 120 créditos; "
                + "«Espada de dos manos» (arma): 150 créditos, con 20 % de descuento. Están en la «Tienda».");
    }

    @Test
    void unProductoPremiumSeVendeConDineroReal() {
        assertThat(responder("cuanto cuesta el guerrero tanque").texto())
            .isEqualTo("«Guerrero Tanque» (héroe) cuesta 25.000 COP, solo con dinero real. Lo encuentras en la «Tienda».");
    }

    @Test
    void laFichaDeUnProducto() {
        assertThat(responder("¿Qué hace la espada de dos manos?").texto())
            .isEqualTo("«Espada de dos manos» es un arma. +1 ataque y +3 % de critico. Precio: 150 créditos, "
                + "con 20 % de descuento. Su ficha completa está en la «Tienda».");
    }

    @Test
    void elListadoDelCatalogoPorTipo() {
        assertThat(responder("¿Qué productos hay en la tienda?").texto())
            .startsWith("En el catálogo hay 5 productos a la venta (3 armas, 1 armadura, 1 héroe). Algunos: "
                + "«Espada de una mano» (120 créditos)");
        assertThat(responder("que armas venden en la tienda").texto())
            .startsWith("Hay 3 armas a la venta, por ejemplo: «Espada de una mano» (120 créditos)");
        assertThat(responder("que epicas venden").texto()).isEqualTo("Ahora mismo no hay épicas a la venta en la «Tienda».");
    }

    @Test
    void enInglesConLosNombresDeLaTienda() {
        ResultadoMotor resultado = responder("how much is the Espada de una mano");

        assertThat(resultado.ingles()).isTrue();
        assertThat(resultado.texto()).isEqualTo("«Espada de una mano» (weapon) costs 120 credits. "
            + "You'll find it in the «Tienda» (Store).");
        assertThat(responder("what can I buy in the store").texto()).startsWith("The catalog has 5 products for sale");
        assertThat(consulta.responder("cuanto cuesta el orbe", IdiomaPreferido.EN).orElseThrow().texto())
            .startsWith("«Orbe de manos ardientes» (weapon) costs 90 credits.");
    }

    // Lo que no es del catalogo, o es de orientacion, lo responde la base.
    @ParameterizedTest
    @ValueSource(strings = {
        "¿Cuánto cuesta publicar una subasta?",
        "cuanto cuesta un heroe",
        "¿Cómo compro en la tienda?",
        "how do I buy items",
        "¿Qué pasa si compro un producto en una subasta?",
        "¿Qué es un producto premium?",
        "¿Qué hace el bono de bienvenida?",
        "como me registro",
        "hola"
    })
    void loQueNoEsDelCatalogoSigueALaBase(String pregunta) {
        assertThat(consulta.responder(pregunta, IdiomaPreferido.AUTOMATICO)).as(pregunta).isEmpty();
    }

    @Test
    void sinPreguntaDeCatalogoNiSiquieraLoConsulta() {
        consulta.responder("como me registro", IdiomaPreferido.AUTOMATICO);
        consulta.responder("   ", null);

        verifyNoInteractions(catalogo);
    }

    @Test
    void conElCatalogoCaidoElListadoLoDiceYElPrecioSigueALaBase() {
        when(catalogo.productos()).thenReturn(Optional.empty());

        ResultadoMotor listado = responder("que productos hay");
        assertThat(listado.texto()).startsWith("No pude consultar el catálogo en este momento");
        assertThat(listado.resultado()).isEqualTo(ResultadoDeRespuesta.NO_DISPONIBLE);
        assertThat(consulta.responder("cuanto cuesta el orbe", IdiomaPreferido.ES)).isEmpty();
    }

    @Test
    void unCatalogoVacio() {
        when(catalogo.productos()).thenReturn(Optional.of(List.of()));

        assertThat(responder("que productos hay").texto()).isEqualTo("Ahora mismo no hay productos a la venta en la «Tienda».");
    }

    @Test
    void demasiadosParecidosPidenElNombreCompleto() {
        List<ProductoDelCatalogoDto> pociones = List.of(
            new ProductoDelCatalogoDto("a", "Pocion de vida menor", "", "ITEM", 5, null, false, null),
            new ProductoDelCatalogoDto("b", "Pocion de vida mayor", "", "ITEM", 9, null, false, null),
            new ProductoDelCatalogoDto("c", "Pocion de vida suprema", "", "ITEM", 15, null, false, null),
            new ProductoDelCatalogoDto("d", "Pocion de vida eterna", "", "ITEM", 30, null, false, null));
        when(catalogo.productos()).thenReturn(Optional.of(pociones));

        assertThat(responder("cuanto cuesta la pocion de vida").texto())
            .startsWith("Hay 4 productos que se parecen a eso, por ejemplo:")
            .endsWith("Dime el nombre completo y te digo su precio.");
        assertThat(responder("cuanto cuesta la pocion de vida eterna").texto())
            .startsWith("«Pocion de vida eterna» (ítem) cuesta 30 créditos.");
    }

    @Test
    void elTipoQueSePideSeReconoce() {
        assertThat(ConsultaDeCatalogo.tipoPedido("que armaduras venden")).contains("ARMADURA");
        assertThat(ConsultaDeCatalogo.tipoPedido("what heroes are in the store")).contains("HEROE");
        assertThat(ConsultaDeCatalogo.tipoPedido("que productos hay")).isEmpty();
    }
}
