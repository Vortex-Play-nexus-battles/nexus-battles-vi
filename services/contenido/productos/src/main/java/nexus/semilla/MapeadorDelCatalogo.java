package nexus.semilla;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nexus.api.SolicitudCrearProducto;
import nexus.dominio.EstadoProducto;
import nexus.dominio.ParteArmadura;
import nexus.dominio.Producto;
import nexus.dominio.TipoProducto;
import nexus.semilla.CatalogoInicial.EntradaCatalogo;
import org.springframework.stereotype.Component;

/**
 * Convierte una entrada del catalogo inicial en la misma solicitud que usaria
 * un administrador para darla de alta, y de ahi en {@link Producto}. Pasar por
 * {@link SolicitudCrearProducto} es a proposito: la semilla queda sujeta a las
 * mismas validaciones del alta, sin copiarlas.
 *
 * <p>Decisiones que no salen del JSON, todas visibles aqui:
 * <ul>
 *   <li>El id es un UUID derivado del slug ({@link #identificador}): el
 *       contrato publica ids con formato UUID y la EPICA referencia a su heroe
 *       por UUID, asi que un slug no cabe; derivarlo lo hace estable.</li>
 *   <li>ARMA sin "+N al ataque" en la regla: {@link #PODER_DE_ATAQUE_NEUTRO},
 *       el minimo que acepta el alta ({@code @Positive}).</li>
 *   <li>EPICA: dos turnos de recarga, regla general de epicas.md ("Tienen dos
 *       turnos de recarga").</li>
 *   <li>Imagen: una figura SVG embebida por tipo, hasta que el administrador
 *       cargue la real.</li>
 * </ul>
 */
@Component
public class MapeadorDelCatalogo {

    /** Minimo que acepta el alta para un ARMA cuya regla no suma ataque. */
    public static final int PODER_DE_ATAQUE_NEUTRO = 1;

    /** epicas.md, "Reglas de uso": las habilidades especiales tienen dos turnos de recarga. */
    static final int TURNOS_RECARGA_EPICA = 2;

    private static final String ESPACIO_DE_NOMBRES = "nexus-battles-vi/catalogo-inicial/";

    private static final Pattern TABLA = Pattern.compile("Tabla \\d+");

    private static final Map<TipoProducto, String> ETIQUETA = Map.of(
            TipoProducto.HEROE, "Héroe",
            TipoProducto.HABILIDAD, "Habilidad",
            TipoProducto.ARMA, "Arma",
            TipoProducto.ARMADURA, "Armadura",
            TipoProducto.ITEM, "Ítem",
            TipoProducto.EPICA, "Épica");

    /**
     * UUID estable para un slug del catalogo: version 3 (MD5 del nombre), el
     * mismo en cada corrida y en cada maquina. Asi inventario puede guardar la
     * referencia antes de que el producto exista.
     */
    public static String identificador(String slug) {
        return UUID.nameUUIDFromBytes(
                (ESPACIO_DE_NOMBRES + slug).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * @throws IllegalArgumentException si la entrada no se puede mapear sin
     *         inventar datos (tipo desconocido, porcentaje ilegible, parte de
     *         armadura desconocida, epica de un heroe que no esta en el catalogo)
     */
    public SolicitudCrearProducto aSolicitud(EntradaCatalogo entrada, CatalogoInicial catalogo) {
        TipoProducto tipo = tipoDe(entrada);
        Integer precio = catalogo.preciosDemostracion().creditos().get(tipo.name());
        int tiraje = catalogo.preciosDemostracion().tiraje();
        boolean premium = catalogo.preciosDemostracion().premium();

        return switch (tipo) {
            case HEROE -> new SolicitudCrearProducto(
                    entrada.nombre(), imagen(tipo), descripcionHeroe(entrada), tipo,
                    tiraje, precio, null, premium,
                    entrada.prototipo(), null, null, null, null, null, null, null,
                    null, null, null, null, null);
            case ARMA -> new SolicitudCrearProducto(
                    entrada.nombre(), imagen(tipo), descripcionEquipo(entrada, tipo), tipo,
                    tiraje, precio, null, premium,
                    null, null, null, null, null, null, null, null,
                    null, null, null,
                    NumerosDeLaRegla.bonificacionAlAtaque(entrada.efectos())
                            .orElse(PODER_DE_ATAQUE_NEUTRO),
                    NumerosDeLaRegla.porcentaje(entrada.probabilidadCaida()));
            case ARMADURA -> new SolicitudCrearProducto(
                    entrada.nombre(), imagen(tipo), descripcionEquipo(entrada, tipo), tipo,
                    tiraje, precio, null, premium,
                    null, null, null, null, null, null, null, null,
                    NumerosDeLaRegla.bonificacionALaDefensa(entrada.efectos()).orElse(null),
                    parteDe(entrada.parte()), null, null,
                    NumerosDeLaRegla.porcentaje(entrada.probabilidadCaida()));
            case ITEM -> new SolicitudCrearProducto(
                    entrada.nombre(), imagen(tipo), descripcionEquipo(entrada, tipo), tipo,
                    tiraje, precio, null, premium,
                    null, null, null, null, null, null, null, null,
                    null, null, entrada.efectos(), null,
                    NumerosDeLaRegla.porcentaje(entrada.probabilidadCaida()));
            case EPICA -> new SolicitudCrearProducto(
                    entrada.nombre(), imagen(tipo), descripcionEpica(entrada), tipo,
                    tiraje, precio, null, premium,
                    null, heroeDeLaEpica(entrada, catalogo), null, null, null,
                    TURNOS_RECARGA_EPICA, entrada.efectoGeneral(), entrada.efectoPotenciado(),
                    null, null, null, null, null);
            case HABILIDAD -> throw new IllegalArgumentException(
                    "El catalogo inicial no trae habilidades: " + entrada.id());
        };
    }

    /** El producto que se guarda: los datos de la solicitud, ACTIVO, version 1. */
    public Producto aProducto(String id, SolicitudCrearProducto s, Instant ahora) {
        return new Producto(
                id, s.nombre(), s.imagen(), s.descripcion(), s.tipo(),
                s.tiraje(), s.precioCreditos(), s.precioMonedaReal(), s.premium(),
                s.prototipo(), s.heroe(), s.costoPoder(), s.multiplicadorNivel(),
                s.turnosCarga(), s.turnosRecarga(), s.efectoGeneral(), s.efectoPotenciado(),
                s.defensa(), s.parte(), s.efecto(), s.poderDeAtaque(), s.tasaDeCaida(),
                EstadoProducto.ACTIVO, 1, ahora, ahora);
    }

    // ------------------------------------------------------------ ayudantes

    private static TipoProducto tipoDe(EntradaCatalogo entrada) {
        try {
            return TipoProducto.valueOf(entrada.tipo());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Tipo desconocido en " + entrada.id() + ": " + entrada.tipo());
        }
    }

    private static ParteArmadura parteDe(String parte) {
        if (parte == null) {
            return null;
        }
        String sinTildes = Normalizer.normalize(parte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
        try {
            return ParteArmadura.valueOf(sinTildes);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Parte de armadura desconocida: " + parte);
        }
    }

    private static String heroeDeLaEpica(EntradaCatalogo epica, CatalogoInicial catalogo) {
        List<EntradaCatalogo> heroes = catalogo.heroes() == null ? List.of() : catalogo.heroes();
        return heroes.stream()
                .filter(h -> h.prototipo() != null && h.prototipo().equals(epica.heroe()))
                .findFirst()
                .map(h -> identificador(h.id()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "La epica " + epica.id() + " es de un heroe que no esta en el catalogo: "
                                + epica.heroe()));
    }

    private static String descripcionHeroe(EntradaCatalogo heroe) {
        StringJoiner nivel1 = new StringJoiner(", ");
        if (heroe.estadisticasNivel1() != null) {
            heroe.estadisticasNivel1().forEach((nombre, valor) -> {
                if (valor != null && !valor.isBlank() && !"-".equals(valor.trim())) {
                    nivel1.add(nombre + " " + valor);
                }
            });
        }
        return "Héroe de tipo " + heroe.prototipo() + ". Nivel 1: " + nivel1 + "."
                + fuente(heroe);
    }

    private static String descripcionEquipo(EntradaCatalogo entrada, TipoProducto tipo) {
        StringBuilder texto = new StringBuilder(ETIQUETA.get(tipo))
                .append(" de ").append(entrada.heroe());
        if (entrada.grupo() != null) {
            texto.append(" (").append(entrada.grupo()).append(")");
        }
        if (entrada.parte() != null) {
            texto.append(", parte ").append(entrada.parte());
        }
        texto.append('.');
        if (tipo != TipoProducto.ITEM) {
            // El ITEM tiene campo propio para su efecto; ARMA y ARMADURA no.
            texto.append(" Efectos: ").append(entrada.efectos()).append('.');
        }
        texto.append(" Probabilidad de caída: ").append(entrada.probabilidadCaida()).append('.');
        return texto + fuente(entrada);
    }

    private static String descripcionEpica(EntradaCatalogo epica) {
        return "Habilidad épica de " + epica.heroe()
                + ". Probabilidad de encontrar a su Máster en una misión: "
                + epica.probabilidadMaster() + "." + fuente(epica);
    }

    private static String fuente(EntradaCatalogo entrada) {
        if (entrada.fuente() == null) {
            return "";
        }
        Matcher tabla = TABLA.matcher(entrada.fuente());
        return tabla.find() ? " Reglas del juego, " + tabla.group() + "." : "";
    }

    /**
     * Figura provisional: un recuadro con el nombre del tipo, en los colores
     * de la paleta (cromo #1C2340, borde #9FABC9). Va embebida para que la
     * ficha nunca muestre una imagen rota; el administrador la reemplaza.
     */
    private static String imagen(TipoProducto tipo) {
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 160 160'>"
                + "<rect x='4' y='4' width='152' height='152' rx='16' fill='#1C2340'"
                + " stroke='#9FABC9' stroke-width='4'/>"
                + "<text x='80' y='88' font-family='sans-serif' font-size='22'"
                + " fill='#FFFFFF' text-anchor='middle'>" + ETIQUETA.get(tipo) + "</text></svg>";
        return "data:image/svg+xml," + URLEncoder.encode(svg, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
