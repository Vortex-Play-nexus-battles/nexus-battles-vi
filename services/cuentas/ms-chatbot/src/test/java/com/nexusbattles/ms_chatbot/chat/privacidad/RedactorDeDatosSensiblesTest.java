package com.nexusbattles.ms_chatbot.chat.privacidad;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

// 7.4.8 «no almacenamiento de informacion sensible»: que tapa la redaccion,
// que deja igual y que garantiza (no alarga, se puede repetir, una sola).
class RedactorDeDatosSensiblesTest {

    private final RedaccionDeDatosSensibles redaccion = new RedactorDeDatosSensibles();

    @ParameterizedTest
    @ValueSource(strings = {
        "Hola, ¿cómo subo de nivel a mi héroe?",
        "Olvidé mi contraseña y no puedo entrar",
        "Me dice que mi contraseña es incorrecta.",
        "Mi contraseña nueva no funciona",
        "La contraseña es muy larga",
        "My password is wrong",
        "La clave del juego es practicar",
        "La clave es la paciencia",
        "El token es inválido",
        "El pin del mapa no aparece",
        "Tengo 1500 créditos y 3 héroes",
        "Mi pedido 12345 no llegó",
        "Llámame al +57 300 123 4567",
        "La partida 550e8400-e29b-41d4-a716-446655440000 se cortó",
        "4111 1111 1111 1112",
        "1234 5678 9012 3452",
        "El cvv no lo sé. Tengo 1500 créditos",
        "Eres un idiota",
        "Gané 3.000.000 de créditos el 12/27",
    })
    void loQueNoEsSensibleQuedaIgual(String texto) {
        assertThat(redaccion.redactar(texto)).isEqualTo(texto);
    }

    @Test
    void nullYVacioQuedanIgual() {
        assertThat(redaccion.redactar(null)).isNull();
        assertThat(redaccion.redactar("")).isEmpty();
    }

    static Stream<Arguments> contrasenas() {
        return Stream.of(
            Arguments.of("contraseña: Hunter2", "contraseña: ***"),
            Arguments.of("Mi contraseña es dragon", "Mi contraseña es ***"),
            Arguments.of("mi contrasena es 123456 y no entra", "mi contrasena es *** y no entra"),
            Arguments.of("CONTRASEÑA: abc", "CONTRASEÑA: ***"),
            Arguments.of("password=abc", "password=***"),
            Arguments.of("My password is S3cret", "My password is ***"),
            Arguments.of("la contraseña de mi cuenta es Hunter2", "la contraseña de mi cuenta es ***"),
            Arguments.of("mi contraseña es: hunter2", "mi contraseña es: ***"),
            Arguments.of("mi clave: 1a2b3c", "mi clave: ***"),
            Arguments.of("clave de acceso es perro", "clave de acceso es ***"),
            Arguments.of("PIN 4321", "PIN ***"),
            Arguments.of("mi contraseña Hunter2024 no funciona", "mi contraseña *** no funciona"),
            Arguments.of("contraseña es \"mi perro 2020\"", "contraseña es \"***\""),
            Arguments.of("contraseña:\nabc", "contraseña:\n***"),
            Arguments.of("contraseña: abc123.", "contraseña: ***."),
            Arguments.of("pwd=ab", "pwd=**"),
            Arguments.of("contraseña contraseña: abc", "contraseña contraseña: ***"),
            Arguments.of("usuario: juan, contraseña: x1, correo: a@b.co", "usuario: juan, contraseña: **, correo: a@b.co"),
            Arguments.of("pin: 4111 1111 1111 1111", "pin: ***"));
    }

    @ParameterizedTest
    @MethodSource("contrasenas")
    void tapaLaContrasenaYDejaLaPalabraQueLaAnuncia(String texto, String esperado) {
        assertThat(redaccion.redactar(texto)).isEqualTo(esperado);
    }

    static Stream<Arguments> tarjetas() {
        return Stream.of(
            Arguments.of("mi tarjeta es 4111 1111 1111 1111", "mi tarjeta es [tarjeta]"),
            Arguments.of("4111-1111-1111-1111", "[tarjeta]"),
            Arguments.of("pagué con 5555555555554444.", "pagué con [tarjeta]."),
            Arguments.of("amex 3782 822463 10005", "amex [tarjeta]"),
            Arguments.of("visa 4222222222222", "visa [tarjeta]"),
            Arguments.of("4111 1111 1111 1111 123", "[tarjeta] ***"),
            Arguments.of("4111111111111111 12/27 123", "[tarjeta] 12/27 ***"),
            Arguments.of("4111111111111111, vence 12/27, cvv 123", "[tarjeta], vence 12/27, cvv ***"),
            Arguments.of("pedido 12345 4111 1111 1111 1111", "pedido 12345 [tarjeta]"),
            Arguments.of("dos: 4111111111111111 y 5555 5555 5555 4444", "dos: [tarjeta] y [tarjeta]"),
            Arguments.of("cvv y tarjeta: 4111 1111 1111 1111", "cvv y tarjeta: [tarjeta]"),
            Arguments.of("el cvv es 4111 1111 1111 1111", "el cvv es [tarjeta]"),
            Arguments.of("cvv: 123", "cvv: ***"),
            Arguments.of("CVC 1234", "CVC ***"),
            Arguments.of("el código de seguridad es 987", "el código de seguridad es ***"),
            Arguments.of("el cvv de mi tarjeta es 321", "el cvv de mi tarjeta es ***"));
    }

    @ParameterizedTest
    @MethodSource("tarjetas")
    void tapaLaTarjetaYSuCvv(String texto, String esperado) {
        assertThat(redaccion.redactar(texto)).isEqualTo(esperado);
    }

    static Stream<Arguments> tokens() {
        return Stream.of(
            Arguments.of("Authorization: Bearer abcdefghijklmnop", "Authorization: Bearer [token]"),
            Arguments.of("mi token es eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.abcdefghij", "mi token es [token]"),
            Arguments.of("token: abc123", "token: ***"),
            Arguments.of("token=Bearer abcdefgh12", "token=*** [token]"),
            Arguments.of("access_token=abc&refresh_token=def", "access_token=***"),
            Arguments.of("api_key=sk_live_ABCDEFGHIJKLMNOP1234", "api_key=***"),
            Arguments.of("la clave AKIAABCDEFGHIJKLMNOP es de AWS", "la clave [token] es de AWS"),
            Arguments.of("ghp_" + "a".repeat(36), "[token]"),
            Arguments.of("-----BEGIN RSA PRIVATE KEY-----\nMIIEabc\n-----END RSA PRIVATE KEY-----", "[clave privada]"),
            Arguments.of("-----BEGIN PRIVATE KEY-----\nMIIEabc cortada", "[clave privada]"));
    }

    @ParameterizedTest
    @MethodSource("tokens")
    void tapaTokensYClavesPrivadas(String texto, String esperado) {
        assertThat(redaccion.redactar(texto)).isEqualTo(esperado);
    }

    @Test
    void laModeracionSigueViendoElInsulto() {
        String texto = "Eres un idiota, mi contraseña: abc123 y mi tarjeta 4111 1111 1111 1111, cvv 123";

        assertThat(redaccion.redactar(texto))
            .isEqualTo("Eres un idiota, mi contraseña: *** y mi tarjeta [tarjeta], cvv ***");
    }

    @Test
    void nuncaAlargaElTextoYRedactarDosVecesNoCambiaNada() {
        String[] piezas = {
            "contraseña", ":", " es ", "4111 1111 1111 1111", "cvv", " 123", "1234", "Bearer ", "abcdefgh12",
            "token", "=", "pin", " ", "eyJabcdef.eyJabcdef.xyz", "\"", "hola", "12/27", ",", "\n",
            "AKIAABCDEFGHIJKLMNOP", "incorrecta", "Hunter2", "clave", " de acceso", "5555", "*", "]",
            "-----BEGIN PRIVATE KEY-----", "123Bearer", "vence", "«", "»"};
        Random azar = new Random(748);
        for (int i = 0; i < 20_000; i++) {
            StringBuilder texto = new StringBuilder();
            int piezasDelTexto = azar.nextInt(12);
            for (int j = 0; j < piezasDelTexto; j++) {
                texto.append(piezas[azar.nextInt(piezas.length)]);
            }
            String original = texto.toString();
            String redactado = redaccion.redactar(original);

            assertThat(redactado.length()).as(original).isLessThanOrEqualTo(original.length());
            assertThat(redaccion.redactar(redactado)).as(original).isEqualTo(redactado);
        }
    }

    @Test
    void lasMarcasNoAlarganLoQueTapan() {
        assertThat(RedactorDeDatosSensibles.tapar("ab", RedactorDeDatosSensibles.TAPADO)).isEqualTo("**");
        assertThat(RedactorDeDatosSensibles.tapar("abcdefgh", RedactorDeDatosSensibles.TOKEN)).isEqualTo("[token]");
    }

    @Test
    void luhn() {
        assertThat(RedactorDeDatosSensibles.cumpleLuhn("4111111111111111")).isTrue();
        assertThat(RedactorDeDatosSensibles.cumpleLuhn("4111111111111112")).isFalse();
    }

    // Una sola abstraccion: nadie mas implementa la redaccion ni declara otra
    // interfaz con su nombre (la provisional de soporte se borra al conectarla).
    // Solo codigo de produccion: el build usa build.nosync, que el filtro
    // predefinido de ArchUnit no reconoce como carpeta de pruebas.
    @Test
    void hayUnaSolaRedaccion() {
        JavaClasses clases = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ubicacion -> !ubicacion.contains("/test/"))
            .importPackages("com.nexusbattles.ms_chatbot");

        classes().that().implement(RedaccionDeDatosSensibles.class)
            .should().haveFullyQualifiedName(RedactorDeDatosSensibles.class.getName())
            .check(clases);
        noClasses().that().haveSimpleName(RedaccionDeDatosSensibles.class.getSimpleName())
            .should().resideOutsideOfPackage(RedaccionDeDatosSensibles.class.getPackageName())
            .allowEmptyShould(true)
            .check(clases);
    }
}
