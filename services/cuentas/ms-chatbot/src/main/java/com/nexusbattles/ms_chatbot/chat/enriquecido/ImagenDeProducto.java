package com.nexusbattles.ms_chatbot.chat.enriquecido;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * ms-chatbot.yaml 1.13.0 (§7.4.3 «respuestas enriquecidas: incluyendo
 * imagenes, enlaces y tarjetas informativas»): que imagen de un producto
 * puede ir en una tarjeta del asistente.
 *
 * <p>El catalogo ({@code productos.yaml}, campo {@code imagen}) guarda una
 * referencia que escribe quien da de alta el producto. Solo pasan dos formas,
 * las mismas que ensena la tienda:
 *
 * <ul>
 *   <li>un recurso grafico del propio catalogo,
 *       {@code /api/v1/productos/recursos/<id>} (HU-PRD-009: se comprueba al
 *       subirlo y se sirve con {@code nosniff} y
 *       {@code Content-Security-Policy: default-src 'none'});</li>
 *   <li>una imagen incrustada pequena ({@code data:image/png|jpeg|webp|gif|svg+xml},
 *       como mucho {@value #LARGO_MAXIMO} caracteres): asi son hoy las de la
 *       semilla. Va en un {@code <img>}, donde un SVG no ejecuta nada.</li>
 * </ul>
 *
 * <p>Nada mas: ni una URL externa (no se le pide nada a otro sitio desde la
 * ventana: ni rastreo ni contenido mixto), ni un nombre suelto como
 * {@code espada.png} que nadie sirve (en DEV hay productos asi: la tienda lo
 * cambia por el simbolo del tipo; aqui la tarjeta va sin imagen), ni otro
 * tipo de {@code data:}. El tope de tamano es porque la tarjeta se guarda con
 * la respuesta.
 */
public final class ImagenDeProducto {

    /** Lo que ocupa como mucho una imagen incrustada en una tarjeta. */
    public static final int LARGO_MAXIMO = 8192;

    private static final Pattern RECURSO_DEL_CATALOGO =
        Pattern.compile("^/api/v1/productos/recursos/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern INCRUSTADA =
        Pattern.compile("^data:image/(png|jpeg|webp|gif|svg\\+xml)(;base64)?,[^\\s\"'<>\\\\]+$");

    private ImagenDeProducto() {
        // Utilidad sin estado.
    }

    /**
     * La referencia tal cual si es una de las dos formas admitidas; vacio si
     * no hay ninguna o es cualquier otra cosa.
     */
    public static Optional<String> segura(String referencia) {
        if (referencia == null) {
            return Optional.empty();
        }
        String limpia = referencia.strip();
        if (limpia.isEmpty() || limpia.length() > LARGO_MAXIMO) {
            return Optional.empty();
        }
        if (RECURSO_DEL_CATALOGO.matcher(limpia).matches()) {
            return Optional.of(limpia);
        }
        String inicio = limpia.substring(0, Math.min(limpia.length(), 40)).toLowerCase(Locale.ROOT);
        if (inicio.startsWith("data:image/") && INCRUSTADA.matcher(conTipoEnMinusculas(limpia)).matches()) {
            return Optional.of(limpia);
        }
        return Optional.empty();
    }

    // «data:image/PNG;base64,…» es valido: el tipo no distingue mayusculas.
    private static String conTipoEnMinusculas(String datos) {
        int coma = datos.indexOf(',');
        if (coma < 0) {
            return datos;
        }
        return datos.substring(0, coma).toLowerCase(Locale.ROOT) + datos.substring(coma);
    }
}
