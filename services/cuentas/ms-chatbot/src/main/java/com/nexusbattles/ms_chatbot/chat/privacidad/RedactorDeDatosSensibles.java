package com.nexusbattles.ms_chatbot.chat.privacidad;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 7.4.8 — la implementacion de RedaccionDeDatosSensibles. Sin configuracion ni
// dependencias: expresiones fijas, sin estado y segura entre hilos. No escribe
// nada en la bitacora.
//
// Que tapa y con que marca (la palabra que lo anuncia se queda):
//
//   bloque PEM de clave privada ........................ [clave privada]
//   Bearer <token>, JWT, claves de AWS, GitHub, Stripe
//   o Slack ............................................. [token]
//   CVV tras su palabra o justo tras la tarjeta ......... ***
//   tarjeta: 13 a 19 cifras de Visa, Mastercard, Amex,
//   Diners, Discover... (empiezan por 2-6) y cumplen Luhn [tarjeta]
//   valor de contrasena, clave, pin, token o secret ..... ***
//
// Una marca nunca es mas larga que lo que tapa (si lo fuera, van asteriscos
// del mismo largo): el texto redactado cabe donde cabia el original.
//
// Una contrasena se reconoce por la palabra que la anuncia:
//   * con ":" o "=" ("contrasena: x", "token=x"), siempre;
//   * con "es"/"is" ("mi contrasena es x"), salvo que siga una palabra
//     corriente ("es incorrecta", "is wrong"). Solo para las palabras
//     inequivocas (contrasena, password, pin, clave de acceso): "la clave es
//     practicar" o "el token es valido" no traen ningun secreto;
//   * sin separador ("mi contrasena Hunter2"), si parece una contrasena:
//     lleva cifras, simbolos o mayusculas por dentro.
// Entre la palabra y el separador caben hasta tres palabras ("la contrasena
// de mi cuenta es x").
@Component
public class RedactorDeDatosSensibles implements RedaccionDeDatosSensibles {

    static final String TAPADO = "***";
    static final String TARJETA = "[tarjeta]";
    static final String TOKEN = "[token]";
    static final String CLAVE_PRIVADA = "[clave privada]";

    private static final int SIN_MAYUSCULAS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String ANTES = "(?<![\\p{L}\\p{N}_])";
    private static final String DESPUES = "(?![\\p{L}\\p{N}_])";

    private static final Pattern CLAVE_PEM = Pattern.compile(
        "-----BEGIN [A-Z ]{0,40}PRIVATE KEY-----.*?(?:-----END [A-Z ]{0,40}PRIVATE KEY-----|\\z)",
        Pattern.DOTALL);

    private static final Pattern BEARER = Pattern.compile(
        ANTES + "(bearer\\s+)([A-Za-z0-9._~+/=-]{8,})", SIN_MAYUSCULAS);

    private static final Pattern JWT = Pattern.compile(
        "(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{5,}\\.eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

    private static final Pattern TOKEN_CONOCIDO = Pattern.compile(
        "(?<![A-Za-z0-9])(?:AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]{22,}"
            + "|[sr]k_(?:live|test)_[A-Za-z0-9]{16,}|xox[abposr]-[A-Za-z0-9-]{10,})(?![A-Za-z0-9])");

    private static final Pattern CVV = Pattern.compile(
        ANTES + "(?<palabra>cvv2?|cvc2?|c[o\u00f3]digo\\s+(?:de\\s+)?seguridad|security\\s+code)" + DESPUES
            + "(?<medio>[^\\p{N}\\n.!?*\\[\\]]{0,30}?)(?<![\\p{L}\\p{N}])(?<cvv>\\d{3,4})(?![\\p{L}\\p{N}])(?![ -]\\d)",
        SIN_MAYUSCULAS);

    // Una tira de grupos de cifras separados por un espacio o un guion
    // ("4111 1111 1111 1111", "3782-822463-10005", "4111111111111111").
    private static final Pattern TIRA_DE_CIFRAS = Pattern.compile(
        "(?<![\\p{L}\\p{N}])\\d{3,19}(?:[ -]\\d{3,19}){0,6}(?![\\p{L}\\p{N}])");
    private static final Pattern CIFRAS = Pattern.compile("\\d+");
    // Lo que suele seguir a una tarjeta pegada: vencimiento y CVV.
    private static final Pattern CVV_TRAS_LA_TARJETA = Pattern.compile(
        "(?:[\\s,;|]+(?:\\p{L}{1,8}\\.?[ \\t]*:?[ \\t]*)?\\d{1,2}[ \\t]*/[ \\t]*\\d{2,4})?"
            + "[\\s,;|]+(?<cvv>\\d{3,4})(?![\\p{L}\\p{N}/])");

    private static final String INEQUIVOCA =
        "contrase(?:\u00f1|n)as?|passwords?|passwd|pwd|pin|claves?\\s+(?:de\\s+acceso|secretas?)";
    private static final String AMBIGUA =
        "claves?|api[_-]?keys?|apikeys?|access[_-]?tokens?|refresh[_-]?tokens?|client[_-]?secrets?"
            + "|secrets?|tokens?|private[_-]?keys?";
    private static final String VERBO = "es|era|ser[a\u00e1]|ser[i\u00ed]a|is|was";

    private static final Pattern SECRETO_ANUNCIADO = Pattern.compile(
        ANTES + "(?:(?<inequivoca>" + INEQUIVOCA + ")|(?<ambigua>" + AMBIGUA + "))" + DESPUES
            + "(?:(?:[ \\t]+\\p{L}{1,15}){0,3}?"
            + "(?:(?<asigna>[ \\t]*[:=]\\s*)|(?<verbo>[ \\t]+(?:" + VERBO + ")(?:[ \\t]*[:=]\\s*|[ \\t]+)))"
            + "|(?<suelto>\\s+))"
            + "(?<valor>\"[^\"\\n]{1,200}\"|'[^'\\n]{1,200}'|\u00ab[^\u00bb\\n]{1,200}\u00bb"
            + "|\u201c[^\u201d\\n]{1,200}\u201d|\\S+)",
        SIN_MAYUSCULAS);

    // Sin "]": las marcas ([tarjeta], [token]) acaban asi y se tapan enteras.
    private static final Pattern PUNTUACION_FINAL = Pattern.compile("[.,;:!?)\u00bb\u201d]+$");
    private static final Pattern BORDES =
        Pattern.compile("^[\u00bf\u00a1(\\[{\u00ab\u201c\"']+|[.,;:!?)\\]}\u00bb\u201d\"']+$");

    // Lo que sigue a "mi contrasena es" cuando se describe la contrasena en
    // vez de escribirla. Sin tildes y en minusculas (ver comoPalabra).
    private static final Set<String> PALABRAS_CORRIENTES = Set.of(
        "a", "al", "algo", "anterior", "antigua", "antiguo", "actual", "buena", "bueno", "caducada",
        "como", "con", "correcta", "correcto", "corta", "corto", "de", "debil", "del", "demasiado",
        "demasiada", "diferente", "dificil", "distinta", "distinto", "el", "en", "erronea", "erroneo",
        "esa", "ese", "eso", "esta", "este", "esto", "expirada", "facil", "fuerte", "igual",
        "incorrecta", "incorrecto", "insegura", "inseguro", "invalida", "invalido", "la", "larga",
        "largo", "lo", "mala", "malo", "mas", "menos", "mi", "mia", "mio", "misma", "mismo", "muy",
        "nada", "necesaria", "necesario", "no", "nueva", "nuevo", "o", "obligatoria", "obligatorio",
        "otra", "otro", "para", "personal", "por", "privada", "privado", "provisional", "que",
        "requerida", "requerido", "secreta", "secreto", "segura", "seguro", "si", "sin", "su",
        "temporal", "toda", "todo", "tu", "un", "una", "valida", "valido", "vencida", "vieja",
        "viejo", "y",
        "an", "correct", "different", "expired", "incorrect", "invalid", "my", "new", "not", "old",
        "private", "required", "same", "secret", "strong", "the", "too", "valid", "very", "weak",
        "wrong");

    @Override
    public String redactar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        // El orden importa: primero lo que se reconoce por su forma, luego la
        // tarjeta entera (para que "pin: 4111 1111 1111 1111" o "cvv y
        // tarjeta: 4111..." no dejen cifras sueltas), su CVV y al final lo que
        // anuncia una palabra. Ninguna regla cruza una marca ya puesta, asi que
        // redactar otra vez no cambia nada.
        String redactado = reemplazar(CLAVE_PEM, texto, m -> tapar(m.group(), CLAVE_PRIVADA));
        redactado = reemplazar(BEARER, redactado, m -> m.group(1) + tapar(m.group(2), TOKEN));
        redactado = reemplazar(JWT, redactado, m -> tapar(m.group(), TOKEN));
        redactado = reemplazar(TOKEN_CONOCIDO, redactado, m -> tapar(m.group(), TOKEN));
        redactado = taparTarjetas(redactado);
        redactado = reemplazar(CVV, redactado,
            m -> m.group("palabra") + m.group("medio") + tapar(m.group("cvv"), TAPADO));
        return taparSecretosAnunciados(redactado);
    }

    private static String reemplazar(Pattern patron, String texto, Function<MatchResult, String> marca) {
        return patron.matcher(texto).replaceAll(m -> Matcher.quoteReplacement(marca.apply(m)));
    }

    // La marca, o asteriscos del mismo largo si la marca no cabe.
    static String tapar(CharSequence original, String marca) {
        return marca.length() <= original.length() ? marca : "*".repeat(original.length());
    }

    // ---- Tarjetas ----

    private static String taparTarjetas(String texto) {
        Matcher tira = TIRA_DE_CIFRAS.matcher(texto);
        StringBuilder salida = new StringBuilder(texto.length());
        int copiado = 0;
        while (tira.find()) {
            List<int[]> grupos = gruposDe(texto, tira.start(), tira.end());
            int i = 0;
            while (i < grupos.size() && grupos.get(i)[0] < copiado) {
                i++;
            }
            while (i < grupos.size()) {
                int[] tarjeta = buscarTarjeta(texto, grupos, i);
                if (tarjeta == null) {
                    break;
                }
                int inicio = grupos.get(tarjeta[0])[0];
                int fin = grupos.get(tarjeta[1] - 1)[1];
                salida.append(texto, copiado, inicio).append(tapar(texto.substring(inicio, fin), TARJETA));
                copiado = fin;
                i = tarjeta[1];
                if (i < grupos.size()) {
                    int[] siguiente = grupos.get(i);
                    if (siguiente[1] - siguiente[0] <= 4) {
                        salida.append(texto, copiado, siguiente[0])
                            .append(tapar(texto.substring(siguiente[0], siguiente[1]), TAPADO));
                        copiado = siguiente[1];
                        i++;
                    }
                } else {
                    Matcher cola = CVV_TRAS_LA_TARJETA.matcher(texto).region(fin, texto.length());
                    if (cola.lookingAt()) {
                        salida.append(texto, copiado, cola.start("cvv")).append(tapar(cola.group("cvv"), TAPADO));
                        copiado = cola.end("cvv");
                    }
                }
            }
        }
        return salida.append(texto, copiado, texto.length()).toString();
    }

    private static List<int[]> gruposDe(String texto, int inicio, int fin) {
        List<int[]> grupos = new ArrayList<>();
        Matcher cifras = CIFRAS.matcher(texto).region(inicio, fin);
        while (cifras.find()) {
            grupos.add(new int[] {cifras.start(), cifras.end()});
        }
        return grupos;
    }

    // {primer grupo, grupo siguiente al ultimo} de la primera tarjeta valida
    // desde el grupo "desde", la mas larga si hay varias; o null.
    private static int[] buscarTarjeta(String texto, List<int[]> grupos, int desde) {
        for (int primero = desde; primero < grupos.size(); primero++) {
            if ("23456".indexOf(texto.charAt(grupos.get(primero)[0])) < 0) {
                continue;
            }
            StringBuilder cifras = new StringBuilder(19);
            int hasta = -1;
            for (int k = primero; k < grupos.size(); k++) {
                int[] grupo = grupos.get(k);
                cifras.append(texto, grupo[0], grupo[1]);
                if (cifras.length() > 19) {
                    break;
                }
                if (cifras.length() >= 13 && cumpleLuhn(cifras)) {
                    hasta = k + 1;
                }
            }
            if (hasta > 0) {
                return new int[] {primero, hasta};
            }
        }
        return null;
    }

    static boolean cumpleLuhn(CharSequence cifras) {
        int suma = 0;
        boolean doblar = false;
        for (int i = cifras.length() - 1; i >= 0; i--) {
            int cifra = cifras.charAt(i) - '0';
            if (doblar) {
                cifra *= 2;
                if (cifra > 9) {
                    cifra -= 9;
                }
            }
            suma += cifra;
            doblar = !doblar;
        }
        return suma % 10 == 0;
    }

    // ---- Lo que anuncia una palabra ----

    private static String taparSecretosAnunciados(String texto) {
        Matcher m = SECRETO_ANUNCIADO.matcher(texto);
        StringBuilder salida = new StringBuilder(texto.length());
        int copiado = 0;
        int desde = 0;
        while (desde < texto.length() && m.find(desde)) {
            String valor = m.group("valor");
            if (debeTaparse(m, valor)) {
                salida.append(texto, copiado, m.start("valor")).append(taparValor(valor));
                copiado = m.end();
                desde = m.end();
            } else {
                // Lo que seguia no era un secreto, pero puede anunciar otro.
                desde = m.start("valor");
            }
        }
        return salida.append(texto, copiado, texto.length()).toString();
    }

    private static boolean debeTaparse(Matcher m, String valor) {
        if (m.group("asigna") != null) {
            return true;
        }
        if (m.group("inequivoca") == null) {
            return false;
        }
        return m.group("verbo") != null ? !esPalabraCorriente(valor) : pareceContrasena(valor);
    }

    // Entre comillas se tapa lo de dentro; si no, el valor sin la puntuacion
    // que lo cierra ("contrasena: abc." -> "contrasena: ***.").
    private static String taparValor(String valor) {
        if (estaEntreComillas(valor)) {
            return valor.charAt(0) + tapar(valor.substring(1, valor.length() - 1), TAPADO)
                + valor.charAt(valor.length() - 1);
        }
        Matcher puntuacion = PUNTUACION_FINAL.matcher(valor);
        int corte = puntuacion.find() && puntuacion.start() > 0 ? puntuacion.start() : valor.length();
        return tapar(valor.substring(0, corte), TAPADO) + valor.substring(corte);
    }

    private static boolean estaEntreComillas(String valor) {
        if (valor.length() < 2) {
            return false;
        }
        char abre = valor.charAt(0);
        char cierra = valor.charAt(valor.length() - 1);
        return (abre == '"' && cierra == '"') || (abre == '\'' && cierra == '\'')
            || (abre == '\u00ab' && cierra == '\u00bb') || (abre == '\u201c' && cierra == '\u201d');
    }

    private static boolean esPalabraCorriente(String valor) {
        return !estaEntreComillas(valor) && PALABRAS_CORRIENTES.contains(comoPalabra(valor));
    }

    private static String comoPalabra(String valor) {
        String sinBordes = BORDES.matcher(valor).replaceAll("");
        String sinTildes = Normalizer.normalize(sinBordes, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    // Cifras, simbolos (o espacios, si iba entre comillas) o mayusculas por
    // dentro: "Hunter2", "p@ss", "dRaGoN". "nueva" o "OTP" no lo parecen.
    private static boolean pareceContrasena(String valor) {
        String palabra = BORDES.matcher(valor).replaceAll("");
        boolean minuscula = false;
        boolean mayusculaPorDentro = false;
        for (int i = 0; i < palabra.length(); i++) {
            char c = palabra.charAt(i);
            if (!Character.isLetter(c)) {
                return true;
            }
            if (Character.isLowerCase(c)) {
                minuscula = true;
            } else if (i > 0 && Character.isUpperCase(c)) {
                mayusculaPorDentro = true;
            }
        }
        return minuscula && mayusculaPorDentro;
    }
}
