package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La version en texto plano del correo.
 *
 * <p>Un mensaje solo-HTML pesa en casi todos los filtros de correo no
 * deseado. Esta clase es la que hace que cada correo salga con las dos
 * versiones sin mantener dos plantillas que se desincronizan.
 */
class TextoPlanoDeCorreoTest {

    @Test
    void quitaElMarcadoYDejaElTexto() {
        String html = "<p>Hola, <strong>Ana</strong>.</p><p>Tu codigo es 123456.</p>";

        String texto = TextoPlanoDeCorreo.desdeHtml(html);

        assertThat(texto).doesNotContain("<").doesNotContain(">");
        assertThat(texto).contains("Hola, Ana.");
        assertThat(texto).contains("Tu codigo es 123456.");
    }

    /** El codigo de recuperacion tiene que sobrevivir a la conversion. */
    @Test
    void conservaElCodigoAunqueVengaEnUnaCelda() {
        String html = "<table><tr><td style=\"font-size:32px\">482913</td></tr></table>";

        assertThat(TextoPlanoDeCorreo.desdeHtml(html)).contains("482913");
    }

    @Test
    void losSaltosDeParrafoSeMantienen() {
        String texto = TextoPlanoDeCorreo.desdeHtml("<p>Primera</p><p>Segunda</p>");

        assertThat(texto.lines().filter(l -> !l.isBlank()).toList())
                .containsExactly("Primera", "Segunda");
    }

    /** El CSS y los scripts del maquetado no pueden acabar leyendose. */
    @Test
    void noArrastraEstilosNiScripts() {
        String html =
                "<head><style>.x{color:red}</style></head><body><script>var a=1;</script><p>Hola</p></body>";

        String texto = TextoPlanoDeCorreo.desdeHtml(html);

        assertThat(texto).isEqualTo("Hola");
    }

    @Test
    void devuelveLasEntidadesMasComunes() {
        assertThat(TextoPlanoDeCorreo.desdeHtml("<p>Caf&eacute; &amp; t&eacute;&nbsp;listo</p>"))
                .isEqualTo("Cafe & te listo");
    }

    @Test
    void unHtmlVacioONuloDaTextoVacio() {
        assertThat(TextoPlanoDeCorreo.desdeHtml(null)).isEmpty();
        assertThat(TextoPlanoDeCorreo.desdeHtml("   ")).isEmpty();
    }

    /** El detalle de una compra no puede salir con las celdas pegadas. */
    @Test
    void separaLasCeldasDeUnaTabla() {
        String html = "<table><tr><th>Producto</th><th>Cantidad</th></tr>"
                + "<tr><td>Espada Legendaria</td><td>2</td><td>200.00 COP</td></tr></table>";

        assertThat(TextoPlanoDeCorreo.desdeHtml(html).lines().toList())
                .containsExactly("Producto | Cantidad", "Espada Legendaria | 2 | 200.00 COP");
    }

    /**
     * Las plantillas parten las frases y ponen cada celda en su linea para
     * leerse bien; en HTML esos saltos son espacios y en el texto tambien.
     */
    @Test
    void elSangradoDelFuenteNoParteNiLasFrasesNiLasFilas() {
        String html = """
                <div>
                  Hola, <span>Ana</span>. Recibimos una solicitud para
                  restablecer la contraseña.
                </div>
                <table><tr>
                  <td>Espada Legendaria</td>
                  <td>2</td>
                </tr></table>
                """;

        assertThat(TextoPlanoDeCorreo.desdeHtml(html).lines().toList())
                .containsExactly(
                        "Hola, Ana. Recibimos una solicitud para restablecer la contraseña.",
                        "Espada Legendaria | 2");
    }

    /** Un enlace con varios parametros se lee tal cual, sin la entidad del HTML. */
    @Test
    void elEnlaceDeUnCorreoSaleConSuAmpersand() {
        String html = "<span>https://x.co/verificar#codigo=1&amp;correo=a%40b.co</span>";

        assertThat(TextoPlanoDeCorreo.desdeHtml(html)).isEqualTo("https://x.co/verificar#codigo=1&correo=a%40b.co");
    }

    @Test
    void unaEntidadEscapadaNoSeDesescapaDosVeces() {
        assertThat(TextoPlanoDeCorreo.desdeHtml("<p>&amp;lt;b&amp;gt;</p>")).isEqualTo("&lt;b&gt;");
    }

    @Test
    void noDejaLineasEnBlancoDeMas() {
        String texto = TextoPlanoDeCorreo.desdeHtml("<p>A</p><br><br><br><p>B</p>");

        assertThat(texto).doesNotContain("\n\n\n");
    }
}