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

    @Test
    void noDejaLineasEnBlancoDeMas() {
        String texto = TextoPlanoDeCorreo.desdeHtml("<p>A</p><br><br><br><p>B</p>");

        assertThat(texto).doesNotContain("\n\n\n");
    }
}