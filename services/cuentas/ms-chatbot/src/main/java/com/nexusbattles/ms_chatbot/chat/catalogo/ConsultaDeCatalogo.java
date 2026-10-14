package com.nexusbattles.ms_chatbot.chat.catalogo;

import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.texto.DistanciaDeEdicion;
import com.nexusbattles.ms_chatbot.chat.texto.IdiomaDelTexto;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 1.10.0 — preguntas sobre el catálogo con los datos de hoy (7.4.1 «productos
 * disponibles», 7.4.2 «información de productos, estadísticas, precios», 7.4.5
 * «actualización automática con nueva información del sistema»). Para
 * visitantes y jugadores: es dato público.
 *
 * <ul>
 *   <li>«¿Qué productos hay?», «¿qué armas venden en la tienda?», «what can I
 *       buy?»: cuántos hay por tipo y algunos con su precio;</li>
 *   <li>«¿cuánto cuesta la espada de una mano?», «how much is the fire orb?»:
 *       el precio del producto que se nombra;</li>
 *   <li>«¿qué hace el Orbe de manos ardientes?»: su descripción y su precio.</li>
 * </ul>
 *
 * <p>Si la pregunta de precio o de ficha no nombra ningún producto del
 * catálogo («¿cuánto cuesta publicar una subasta?»), responde vacío y la
 * contesta la base de conocimiento, igual que una pregunta de cómo comprar
 * («¿cómo compro en la tienda?»), que es de orientación y no de datos.
 */
@Service
public class ConsultaDeCatalogo {

    static final int EJEMPLOS = 5;
    static final int MAXIMO_DE_COINCIDENCIAS = 3;
    // Una palabra del nombre que aparece en mas productos que esto no
    // distingue a ninguno («de», «mano»...).
    static final int PRODUCTOS_POR_PALABRA_DISTINTIVA = 3;
    private static final int LARGO_MINIMO_DE_PALABRA = 4;

    private static final List<String> PALABRAS_DE_CATALOGO = List.of(
        "productos", "producto", "catalogo", "tienda", "venden", "venta", "comprar", "compro",
        "products", "product", "catalog", "catalogue", "store", "shop", "buy", "sale");
    private static final Set<String> PREGUNTA_POR_LISTA = Set.of(
        "que", "cuales", "cuantos", "cuantas", "lista", "muestrame", "listame",
        "what", "which", "list", "show", "many");
    // Preguntas de como hacer algo, de que pasa si o de que es algo: son de la
    // base de conocimiento, aunque nombren la tienda o un producto.
    private static final List<String> PIDE_ORIENTACION = List.of(
        "como", "que pasa", "que sucede", "que ocurre", "que es", "que son", "how do", "how to", "how can",
        "how does", "what happens", "what if", "what is", "what are");
    private static final List<String> PIDE_PRECIO = List.of(
        "cuanto cuesta", "cuanto cuestan", "cuanto vale", "cuanto valen", "cuanto sale", "precio de", "precio del",
        "precio de la", "precios de", "how much is", "how much are", "how much does", "how much do", "price of",
        "prices of", "cost of");
    private static final List<String> PIDE_FICHA = List.of(
        "que hace", "que hacen", "para que sirve", "para que sirven", "efecto de", "efectos de", "efecto del",
        "efectos del", "informacion de", "informacion del", "info de", "ficha de", "ficha del", "describe",
        "what does", "what do", "tell me about", "info on", "information about", "details of");

    private static final Map<String, List<String>> PALABRAS_POR_TIPO = Map.of(
        "HEROE", List.of("heroe", "heroes", "personaje", "personajes", "hero", "heroes"),
        "ARMA", List.of("arma", "armas", "weapon", "weapons"),
        "ARMADURA", List.of("armadura", "armaduras", "armor", "armors", "armour", "armours"),
        "ITEM", List.of("item", "items", "objeto", "objetos"),
        "EPICA", List.of("epica", "epicas", "epic", "epics"),
        "HABILIDAD", List.of("habilidad", "habilidades", "ability", "abilities", "skill", "skills"));

    private static final Set<String> PALABRAS_VACIAS = Set.of(
        "de", "del", "la", "las", "el", "los", "una", "uno", "unos", "unas", "con", "por", "para", "sin",
        "the", "of", "and", "with", "for");

    private final CatalogoEnVivo catalogo;

    public ConsultaDeCatalogo(CatalogoEnVivo catalogo) {
        this.catalogo = catalogo;
    }

    public Optional<ResultadoMotor> responder(String mensajeUsuario, IdiomaPreferido idioma) {
        String mensaje = NormalizadorTexto.normalizar(mensajeUsuario);
        if (mensaje.isBlank()) {
            return Optional.empty();
        }
        TextosDelCatalogo t = TextosDelCatalogo.en(enIngles(mensaje, idioma));
        boolean precio = contieneAlguna(mensaje, PIDE_PRECIO);
        boolean ficha = !precio && contieneAlguna(mensaje, PIDE_FICHA);
        boolean listado = !precio && !ficha && pideListado(mensaje);
        if (!precio && !ficha && !listado) {
            return Optional.empty();
        }
        Optional<List<ProductoDelCatalogoDto>> productos = catalogo.productos();
        if (listado) {
            return Optional.of(productos.map(lista -> listar(lista, tipoPedido(mensaje), t))
                .orElseGet(() -> noDisponible(t)));
        }
        // Precio o ficha: sin catalogo o sin un producto nombrado, la base responde.
        List<ProductoDelCatalogoDto> nombrados = productos.map(lista -> nombrados(mensaje, lista)).orElse(List.of());
        if (nombrados.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ficha ? describir(nombrados, t) : precios(nombrados, t));
    }

    private static boolean enIngles(String mensaje, IdiomaPreferido idioma) {
        return switch (idioma == null ? IdiomaPreferido.AUTOMATICO : idioma) {
            case ES -> false;
            case EN -> true;
            case AUTOMATICO -> IdiomaDelTexto.esIngles(mensaje);
        };
    }

    // «Que productos hay», «que venden en la tienda», «what can I buy»: una
    // palabra del catalogo y una de pedir la lista, sin ser una pregunta de
    // como hacer algo («como compro en la tienda»).
    static boolean pideListado(String mensaje) {
        if (contieneAlguna(mensaje, PIDE_ORIENTACION)) {
            return false;
        }
        List<String> palabras = List.of(mensaje.split(" "));
        boolean deCatalogo = palabras.stream().anyMatch(p -> PALABRAS_DE_CATALOGO.stream()
            .anyMatch(c -> DistanciaDeEdicion.coinciden(c, p)));
        boolean pideLista = palabras.stream().anyMatch(PREGUNTA_POR_LISTA::contains);
        return deCatalogo && pideLista;
    }

    static Optional<String> tipoPedido(String mensaje) {
        List<String> palabras = List.of(mensaje.split(" "));
        return PALABRAS_POR_TIPO.entrySet().stream()
            .filter(e -> palabras.stream().anyMatch(p -> e.getValue().contains(p)))
            .map(Map.Entry::getKey)
            .sorted()
            .findFirst();
    }

    // Los productos que nombra el mensaje: el nombre completo, o sus palabras
    // distintivas (de 4 letras o mas, en pocos productos), con un error de
    // tecleo como mucho. Gana el que mas palabras comparte.
    static List<ProductoDelCatalogoDto> nombrados(String mensaje, List<ProductoDelCatalogoDto> productos) {
        List<String> palabrasDelMensaje = List.of(mensaje.split(" "));
        Map<String, Long> productosPorPalabra = productos.stream()
            .flatMap(p -> palabrasSignificativas(p.nombre()).stream().distinct())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        Map<ProductoDelCatalogoDto, Integer> puntajes = new LinkedHashMap<>();
        for (ProductoDelCatalogoDto producto : productos) {
            String nombre = NormalizadorTexto.normalizar(producto.nombre());
            List<String> significativas = palabrasSignificativas(producto.nombre());
            if (significativas.isEmpty()) {
                continue;
            }
            if (DistanciaDeEdicion.contieneFrase(mensaje, nombre)) {
                puntajes.put(producto, 100 + significativas.size());
                continue;
            }
            int comunes = 0;
            boolean distintiva = false;
            for (String palabra : significativas) {
                if (palabrasDelMensaje.stream().anyMatch(p -> DistanciaDeEdicion.coinciden(palabra, p))) {
                    comunes++;
                    distintiva |= productosPorPalabra.getOrDefault(palabra, 0L) <= PRODUCTOS_POR_PALABRA_DISTINTIVA;
                }
            }
            if (comunes > 0 && (distintiva || comunes >= 2)) {
                puntajes.put(producto, comunes);
            }
        }
        if (puntajes.isEmpty()) {
            return List.of();
        }
        int mejor = puntajes.values().stream().max(Integer::compare).orElse(0);
        return puntajes.entrySet().stream()
            .filter(e -> e.getValue() == mejor)
            .map(Map.Entry::getKey)
            .toList();
    }

    private static List<String> palabrasSignificativas(String nombre) {
        List<String> palabras = new ArrayList<>();
        for (String palabra : NormalizadorTexto.normalizar(nombre).split(" ")) {
            if (palabra.length() >= LARGO_MINIMO_DE_PALABRA && !PALABRAS_VACIAS.contains(palabra)) {
                palabras.add(palabra);
            }
        }
        return palabras;
    }

    private ResultadoMotor precios(List<ProductoDelCatalogoDto> nombrados, TextosDelCatalogo t) {
        if (nombrados.size() == 1) {
            return consultado(t.unPrecio(nombrados.get(0)), t);
        }
        String lista = nombrados.stream().limit(MAXIMO_DE_COINCIDENCIAS).map(t::conPrecio)
            .collect(Collectors.joining("; "));
        return consultado(nombrados.size() > MAXIMO_DE_COINCIDENCIAS
            ? t.demasiados(nombrados.size(), lista)
            : t.variosPrecios(nombrados.size(), lista), t);
    }

    private ResultadoMotor describir(List<ProductoDelCatalogoDto> nombrados, TextosDelCatalogo t) {
        if (nombrados.size() == 1) {
            return consultado(t.ficha(nombrados.get(0)), t);
        }
        return precios(nombrados, t);
    }

    private ResultadoMotor listar(List<ProductoDelCatalogoDto> productos, Optional<String> tipo, TextosDelCatalogo t) {
        if (tipo.isPresent()) {
            List<ProductoDelCatalogoDto> deTipo = productos.stream()
                .filter(p -> tipo.get().equals(p.tipo()))
                .toList();
            if (deTipo.isEmpty()) {
                return consultado(t.ningunoDeTipo(t.tipo(tipo.get(), true)), t);
            }
            return consultado(t.listadoDeTipo(deTipo.size(), t.tipo(tipo.get(), true), ejemplos(deTipo, t)), t);
        }
        if (productos.isEmpty()) {
            return consultado(t.catalogoVacio(), t);
        }
        Map<String, Long> porTipo = new HashMap<>();
        productos.forEach(p -> porTipo.merge(p.tipo() == null ? "" : p.tipo(), 1L, Long::sum));
        String resumen = porTipo.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry.comparingByKey()))
            .map(e -> e.getValue() + " " + t.tipo(e.getKey(), e.getValue() != 1))
            .collect(Collectors.joining(", "));
        return consultado(t.listado(productos.size(), resumen, ejemplos(productos, t)), t);
    }

    private static String ejemplos(List<ProductoDelCatalogoDto> productos, TextosDelCatalogo t) {
        return productos.stream().limit(EJEMPLOS)
            .map(p -> "«" + p.nombre() + "» (" + t.precio(p) + ")")
            .collect(Collectors.joining(", "));
    }

    private static boolean contieneAlguna(String mensaje, List<String> frases) {
        String conEspacios = " " + mensaje + " ";
        return frases.stream().anyMatch(f -> conEspacios.contains(" " + f + " "));
    }

    private static ResultadoMotor consultado(String texto, TextosDelCatalogo t) {
        return ResultadoMotor.deTema(texto, Categoria.PRODUCTO, TipoRespuesta.CONTEXTUAL)
            .conEnriquecido(Enriquecedor.conEnlace("tienda", t.ingles()))
            .enIngles(t.ingles())
            .conResultado(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
    }

    private static ResultadoMotor noDisponible(TextosDelCatalogo t) {
        return ResultadoMotor.deTema(t.noDisponible(), Categoria.PRODUCTO, TipoRespuesta.DIRECTA)
            .conEnriquecido(Enriquecedor.conEnlace("tienda", t.ingles()))
            .enIngles(t.ingles())
            .conResultado(ResultadoDeRespuesta.NO_DISPONIBLE);
    }
}
