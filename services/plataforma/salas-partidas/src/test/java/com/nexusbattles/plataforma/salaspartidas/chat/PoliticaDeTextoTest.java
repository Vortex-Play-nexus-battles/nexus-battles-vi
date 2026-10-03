package com.nexusbattles.plataforma.salaspartidas.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Auditoria de DEV del 30-sep: «el chat general dejo pasar [...] un bloque
 * enorme de ASCII art». Lo que la lista negra no ve —porque no es una
 * palabra— y lo que una conversacion normal no puede perder por el arreglo.
 */
@DisplayName("PoliticaDeTexto · el mensaje es texto, no un dibujo ni una inundacion")
class PoliticaDeTextoTest {

    private static final PoliticaDeTexto.Limites LIMITES = PoliticaDeTexto.Limites.POR_OMISION;

    /** Un dibujo como el del informe: lineas de simbolos. */
    private static final String ASCII_ART = String.join("\n",
            "    /\\_/\\  ",
            "   ( o.o ) ",
            "    > ^ <  ",
            "  /|     |\\",
            " (_|     |_)",
            "  ||     || ",
            "  ''     '' ");

    @Nested
    @DisplayName("depurar")
    class Depurar {

        @Test
        @DisplayName("NFKC: las letras de ancho completo pasan a su letra base, como en la lista negra")
        void nfkc() {
            assertEquals("hola", PoliticaDeTexto.depurar("ｈｏｌａ"));
        }

        @Test
        @DisplayName("fuera los invisibles: ancho cero, guion blando y marcas que invierten el texto")
        void invisibles() {
            assertEquals("hola mundo", PoliticaDeTexto.depurar("ho​la­ mun‮do‬"));
        }

        @Test
        @DisplayName("el union de ancho cero se respeta: junta los emojis compuestos")
        void emojisCompuestos() {
            String familia = "👨‍👩‍👧";
            assertEquals("gg " + familia, PoliticaDeTexto.depurar("gg " + familia));
        }

        @Test
        @DisplayName("saltos en una sola forma, nunca mas de una linea en blanco seguida, y sin bordes")
        void saltos() {
            assertEquals("uno\n\ndos\ntres", PoliticaDeTexto.depurar("  uno\r\n\r\n\r\n\n\ndos\rtres  "));
        }

        @Test
        @DisplayName("null es vacio, y un texto solo de invisibles tambien")
        void vacio() {
            assertEquals("", PoliticaDeTexto.depurar(null));
            assertEquals("", PoliticaDeTexto.depurar("​​ \t "));
        }
    }

    @Nested
    @DisplayName("problema")
    class Problema {

        @ParameterizedTest(name = "pasa «{0}»")
        @ValueSource(strings = {
                "hola, ¿alguien para una 1v1?",
                "jajajajajajaja",
                "!!!",
                "¿¡Qué!? No puede ser...",
                "gg wp :)",
                "Busco equipo para el torneo del viernes. Tengo un Guerrero Tanque nivel 3 y un Mago Fuego.",
                "1. ataca\n2. defiende\n3. sana",
                "https://nexusbattles.local/jugar?sala=12",
                "🔥🔥🔥 vamos"
        })
        void unaConversacionNormal(String texto) {
            assertTrue(PoliticaDeTexto.problema(PoliticaDeTexto.depurar(texto), LIMITES).isEmpty(), texto);
        }

        @Test
        @DisplayName("el dibujo del informe no pasa: demasiadas lineas")
        void elDibujoDelInforme() {
            assertTrue(PoliticaDeTexto.problema(PoliticaDeTexto.depurar(ASCII_ART), LIMITES)
                    .orElseThrow().contains("líneas"));
        }

        @Test
        @DisplayName("un dibujo de una sola linea tampoco: casi todo simbolos")
        void dibujoEnUnaLinea() {
            String barra = "(╯°□°)╯︵ " + "┻━┻ ".repeat(12);
            assertTrue(PoliticaDeTexto.problema(PoliticaDeTexto.depurar(barra), LIMITES)
                    .orElseThrow().contains("símbolos"));
        }

        @Test
        @DisplayName("una racha larga del mismo caracter no pasa; hasta 15 si")
        void rachas() {
            assertTrue(PoliticaDeTexto.problema("a".repeat(16), LIMITES).isPresent());
            assertTrue(PoliticaDeTexto.problema("noooo" + "o".repeat(10), LIMITES).isEmpty(),
                    "15 seguidas es el limite");
            assertTrue(PoliticaDeTexto.problema("hola" + " ".repeat(16) + "chao", LIMITES).isPresent(),
                    "los espacios tambien cuentan: con ellos se dibuja");
        }

        @Test
        @DisplayName("seis lineas caben, siete no")
        void lineas() {
            assertTrue(PoliticaDeTexto.problema("a\nb\nc\nd\ne\nf", LIMITES).isEmpty());
            assertTrue(PoliticaDeTexto.problema("a\nb\nc\nd\ne\nf\ng", LIMITES).isPresent());
        }

        @Test
        @DisplayName("lo corto no se mira por proporcion: «:)» o «??» no son dibujos")
        void corto() {
            assertTrue(PoliticaDeTexto.problema(":) :) ??", LIMITES).isEmpty());
        }

        @Test
        @DisplayName("racha mas larga por punto de codigo, tambien con emojis")
        void racha() {
            assertEquals(3, PoliticaDeTexto.rachaMasLarga("abccc"));
            assertEquals(2, PoliticaDeTexto.rachaMasLarga("🔥🔥 a"));
            assertEquals(0, PoliticaDeTexto.rachaMasLarga(""));
        }
    }

    @Test
    @DisplayName("unos limites sin sentido no arrancan el servicio")
    void limitesInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new PoliticaDeTexto.Limites(0, 15, 40, 50));
        assertThrows(IllegalArgumentException.class, () -> new PoliticaDeTexto.Limites(6, 1, 40, 50));
        assertThrows(IllegalArgumentException.class, () -> new PoliticaDeTexto.Limites(6, 15, 40, 101));
    }
}
