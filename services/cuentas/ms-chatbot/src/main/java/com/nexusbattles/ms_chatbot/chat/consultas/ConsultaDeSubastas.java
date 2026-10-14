package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaDeSubastasDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.SubastaActivaDto;
import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.enriquecido.ImagenDeProducto;
import com.nexusbattles.ms_chatbot.chat.enriquecido.TarjetaInformativa;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.texto.DistanciaDeEdicion;
import com.nexusbattles.ms_chatbot.chat.texto.IdiomaDelTexto;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ms-chatbot.yaml 1.16.0 — §7.4.4 «Acceso a información en tiempo real: el
 * Chatbot debe tener capacidad de consultar […] estado de productos en la
 * subasta». Lo que hay ahora en la subasta, para visitantes y jugadores: el
 * listado es público ({@code ms-subastas-listado.yaml} 1.2.0,
 * {@code GET /api/v1/subastas}, sin credencial y sin nada del vendedor).
 *
 * <ul>
 *   <li>«¿Qué hay ahora en la subasta?», «¿cuántas subastas hay?», «what's on
 *       auction right now?»: cuántas hay abiertas y las que cierran antes, con
 *       su oferta vigente, sus pujas y su cierre;</li>
 *   <li>«¿hay espadas en subasta?», «¿cómo va la subasta del Hacha…?»: las de
 *       ese producto, buscadas sin tildes y con un error de tecleo entre las
 *       {@value #ACTIVAS_QUE_SE_MIRAN} que cierran antes (y, si hay más
 *       abiertas, con la búsqueda del propio listado).</li>
 * </ul>
 *
 * <p>No es esta: lo suyo («¿cómo van mis subastas?», que responde
 * {@link MotorConsultasAsistidas} con su token), las reglas y los
 * procedimientos («¿cómo funciona la subasta?», «¿cuánto cuesta publicar?»,
 * «¿puedo cancelar…?»), que responde la base, ni ir a la sección («llévame a
 * la subasta»), que es navegación. Nunca puja ni compra: solo consulta.
 */
@Service
public class ConsultaDeSubastas {

    /** Cuántas se nombran y van en tarjetas. */
    static final int A_MOSTRAR = 5;
    /** Entre cuántas abiertas (las que cierran antes) se busca un producto por su nombre. */
    static final int ACTIVAS_QUE_SE_MIRAN = 100;
    private static final int LARGO_MINIMO_DE_PALABRA = 4;

    private static final Set<String> PALABRAS_DE_SUBASTA = Set.of(
        "subasta", "subastas", "subastan", "subastando", "subastado", "subastados", "subastada", "subastadas",
        "auction", "auctions", "auctioned", "auctioning");

    // Lo que hace de la pregunta una por lo que hay ahora.
    private static final List<String> PIDE_LO_QUE_HAY = List.of(
        "que hay", "hay", "cuales hay", "cuales son", "cuantas", "que se subasta", "que se esta subastando",
        "que estan subastando", "que subastan", "activa", "activas", "abierta", "abiertas", "en curso", "ahora",
        "hoy", "en este momento", "como va", "como van", "como esta", "como estan", "estado de", "cierra antes",
        "cierran antes", "cierra pronto", "cierran pronto", "termina pronto", "terminan pronto", "por cerrar",
        "lista de", "listado de", "muestrame", "ver las", "whats on", "what s on", "what is on", "whats in",
        "what s in", "what is in", "is there", "are there", "any", "active", "open", "current", "currently", "now",
        "today", "how is", "how are", "status of", "ending soon", "closing soon", "list of", "show me");

    // Lo suyo lo consulta MotorConsultasAsistidas, con su token.
    private static final Set<String> PIDE_LO_SUYO = Set.of("mi", "mis", "tengo", "my", "mine");

    // Reglas y procedimientos: los responde la base de conocimiento. Raices
    // que no forman nombres de productos, y palabras enteras para las demas
    // («cobre» o «Máximo» podrian ser parte de un nombre).
    private static final List<String> RAICES_DE_REGLAS = List.of(
        "funcion", "comision", "limit", "regla", "durac", "increment", "penaliz", "penalt", "requisit", "cuest",
        "public", "publish", "cancel", "recog", "recoj", "collect", "work");
    private static final Set<String> PALABRAS_DE_REGLAS = Set.of(
        "tope", "topes", "maximo", "maxima", "maximos", "maximas", "minimo", "minima", "minimos", "minimas",
        "rule", "rules", "duration", "fee", "fees", "cost", "costs", "cobra", "cobran", "cobro");
    private static final List<String> PREGUNTA_QUE_ES = List.of(
        "que es", "que son", "what is a", "what is an", "what are");

    // Palabras que no nombran un producto: la pregunta, la subasta y su estado.
    private static final Set<String> NO_NOMBRAN_UN_PRODUCTO = Set.of(
        "cual", "cuales", "cuantas", "cuantos", "como", "esta", "estan", "ahora", "momento", "este", "estado",
        "activa", "activas", "abierta", "abiertas", "curso", "cierra", "cierran", "termina", "terminan", "pronto",
        "antes", "cerrar", "lista", "listado", "muestrame", "unas", "unos", "alguna", "algunas", "alguno",
        "algunos", "algun", "algo", "para", "subasta", "subastas", "subastan", "subastando", "subastado",
        "subastados", "subastada", "subastadas", "puja", "pujas", "oferta", "ofertas", "producto", "productos",
        "objeto", "objetos", "venta", "vender", "venden", "whats", "what", "which", "there", "auction", "auctions",
        "auctioned", "auctioning", "active", "open", "current", "currently", "right", "today", "status", "ending",
        "closing", "soon", "list", "show", "some", "item", "items", "product", "products", "offer", "offers",
        "bids", "sale");

    // El tipo de producto tambien nombra lo que se busca («¿hay armas en subasta?»).
    private static final Map<String, List<String>> PALABRAS_DEL_TIPO = Map.of(
        "HEROE", List.of("heroe", "personaje", "hero"),
        "ARMA", List.of("arma", "weapon"),
        "ARMADURA", List.of("armadura", "armor", "armour"),
        "ITEM", List.of("item", "objeto"),
        "EPICA", List.of("epica", "epic"),
        "HABILIDAD", List.of("habilidad", "ability", "skill"));

    private final SubastasClient subastas;

    public ConsultaDeSubastas(SubastasClient subastas) {
        this.subastas = subastas;
    }

    public Optional<ResultadoMotor> responder(String mensajeUsuario, IdiomaPreferido idioma) {
        String mensaje = NormalizadorTexto.normalizar(mensajeUsuario);
        if (!pideLoQueHay(mensaje)) {
            return Optional.empty();
        }
        TextosDeConsultas t = TextosDeConsultas.en(enIngles(mensaje, idioma));
        List<String> buscadas = palabrasDelProducto(mensaje);
        try {
            PaginaDeSubastasDto abiertas = subastas.activas(null,
                buscadas.isEmpty() ? A_MOSTRAR : ACTIVAS_QUE_SE_MIRAN);
            if (buscadas.isEmpty()) {
                return Optional.of(listado(abiertas, t));
            }
            List<SubastaActivaDto> encontradas = deLoBuscado(buscadas, abiertas.subastas());
            long deEsas = encontradas.size();
            if (encontradas.isEmpty() && abiertas.totalElementos() > abiertas.subastas().size()) {
                // Hay mas abiertas de las que se miraron: la busqueda del propio listado.
                PaginaDeSubastasDto delListado = subastas.activas(paraElListado(buscadas), A_MOSTRAR);
                encontradas = delListado.subastas();
                deEsas = delListado.totalElementos();
            }
            return Optional.of(deUnProducto(String.join(" ", buscadas), encontradas, deEsas,
                abiertas.totalElementos(), t));
        } catch (RestClientException noRespondio) {
            return Optional.of(ResultadoMotor.deTema(t.subastaNoDisponible(), Categoria.SUBASTA_Y_COMERCIO,
                    TipoRespuesta.DIRECTA)
                .conEnriquecido(Enriquecedor.conEnlace("subastas", t.ingles()))
                .enIngles(t.ingles())
                .conResultado(ResultadoDeRespuesta.NO_DISPONIBLE));
        }
    }

    /**
     * Si pregunta por lo que hay ahora en la subasta: habla de la subasta,
     * pide su estado y no es lo suyo, ni una regla, ni un procedimiento.
     * Sobre el texto ya normalizado.
     */
    static boolean pideLoQueHay(String normalizado) {
        if (normalizado == null || normalizado.isBlank()) {
            return false;
        }
        List<String> palabras = List.of(normalizado.split(" "));
        boolean deLaSubasta = palabras.stream().anyMatch(p -> PALABRAS_DE_SUBASTA.contains(p)
            || DistanciaDeEdicion.coinciden("subasta", p) || DistanciaDeEdicion.coinciden("subastas", p));
        if (!deLaSubasta || palabras.stream().anyMatch(PIDE_LO_SUYO::contains)) {
            return false;
        }
        String conBordes = " " + normalizado + " ";
        if (conBordes.contains(" i have ") || conBordes.contains(" have i ")
            || IntencionDeConsulta.pideReglas(normalizado)
            || PREGUNTA_QUE_ES.stream().anyMatch(frase -> conBordes.contains(" " + frase + " "))
            || palabras.stream().anyMatch(p -> PALABRAS_DE_REGLAS.contains(p)
                || RAICES_DE_REGLAS.stream().anyMatch(p::startsWith))) {
            return false;
        }
        return PIDE_LO_QUE_HAY.stream().anyMatch(frase -> conBordes.contains(" " + frase + " "));
    }

    // Lo que nombra el producto: las palabras de 4 letras o mas que no son
    // de la pregunta ni de la subasta.
    static List<String> palabrasDelProducto(String normalizado) {
        List<String> palabras = new ArrayList<>();
        for (String palabra : normalizado.split(" ")) {
            if (palabra.length() >= LARGO_MINIMO_DE_PALABRA && !NO_NOMBRAN_UN_PRODUCTO.contains(palabra)
                && !palabras.contains(palabra)) {
                palabras.add(palabra);
            }
        }
        return palabras;
    }

    // Las abiertas que nombra: el nombre del producto o su tipo, sin tildes,
    // en singular o plural y con un error de tecleo. Gana la que mas palabras
    // comparte; en el orden del listado (las que cierran antes primero).
    static List<SubastaActivaDto> deLoBuscado(List<String> buscadas, List<SubastaActivaDto> abiertas) {
        Map<SubastaActivaDto, Integer> puntajes = new LinkedHashMap<>();
        for (SubastaActivaDto subasta : abiertas) {
            List<String> suyas = palabrasDe(subasta);
            int comunes = (int) buscadas.stream()
                .filter(buscada -> formas(buscada).stream().anyMatch(forma -> suyas.stream()
                    .anyMatch(suya -> DistanciaDeEdicion.coinciden(suya, forma))))
                .count();
            if (comunes > 0) {
                puntajes.put(subasta, comunes);
            }
        }
        int mejor = puntajes.values().stream().max(Comparator.naturalOrder()).orElse(0);
        return puntajes.entrySet().stream()
            .filter(e -> e.getValue() == mejor)
            .map(Map.Entry::getKey)
            .toList();
    }

    private static List<String> palabrasDe(SubastaActivaDto subasta) {
        List<String> palabras = new ArrayList<>(List.of(NormalizadorTexto.normalizar(subasta.nombreProducto())
            .split(" ")));
        if (subasta.tipoProducto() != null) {
            palabras.addAll(PALABRAS_DEL_TIPO.getOrDefault(subasta.tipoProducto(), List.of()));
        }
        return palabras.stream().filter(p -> p.length() >= LARGO_MINIMO_DE_PALABRA).toList();
    }

    // La palabra y su singular: «espadas» → «espada», «pociones» → «pocion».
    static List<String> formas(String palabra) {
        List<String> formas = new ArrayList<>(List.of(palabra));
        if (palabra.length() > LARGO_MINIMO_DE_PALABRA && palabra.endsWith("s")) {
            formas.add(palabra.substring(0, palabra.length() - 1));
        }
        if (palabra.length() > LARGO_MINIMO_DE_PALABRA + 1 && palabra.endsWith("es")) {
            formas.add(palabra.substring(0, palabra.length() - 2));
        }
        return formas;
    }

    // Para la busqueda del listado (subcadena, sin tildes del lado del
    // usuario): la palabra mas larga, en singular.
    static String paraElListado(List<String> buscadas) {
        String masLarga = buscadas.stream().max(Comparator.comparingInt(String::length)).orElse("");
        List<String> formas = formas(masLarga);
        return formas.get(Math.min(1, formas.size() - 1));
    }

    private static ResultadoMotor listado(PaginaDeSubastasDto abiertas, TextosDeConsultas t) {
        List<SubastaActivaDto> mostradas = abiertas.subastas().stream().limit(A_MOSTRAR).toList();
        if (mostradas.isEmpty()) {
            return consultado(t.sinSubastasAbiertas(), List.of(), t);
        }
        return consultado(t.subastasAbiertas(Math.max(abiertas.totalElementos(), mostradas.size()),
            enLista(mostradas, t)), mostradas, t);
    }

    private static ResultadoMotor deUnProducto(String buscado, List<SubastaActivaDto> encontradas, long deEsas,
                                               long abiertas, TextosDeConsultas t) {
        List<SubastaActivaDto> mostradas = encontradas.stream().limit(A_MOSTRAR).toList();
        if (mostradas.isEmpty()) {
            return consultado(t.ningunaDe(buscado, abiertas), List.of(), t);
        }
        return consultado(t.subastasDe(buscado, (int) Math.max(deEsas, mostradas.size()), enLista(mostradas, t)),
            mostradas, t);
    }

    private static String enLista(List<SubastaActivaDto> mostradas, TextosDeConsultas t) {
        return mostradas.stream().map(t::enLista).collect(Collectors.joining("; "));
    }

    // Una tarjeta por subasta nombrada, con la imagen del producto si es
    // segura (ImagenDeProducto), y el enlace a «Subasta».
    private static ResultadoMotor consultado(String texto, List<SubastaActivaDto> mostradas, TextosDeConsultas t) {
        List<TarjetaInformativa> tarjetas = mostradas.stream()
            .map(s -> new TarjetaInformativa(s.nombreProducto(), t.tarjetaDeSubasta(s), null,
                ImagenDeProducto.segura(s.miniaturaUrl()).orElse(null)))
            .toList();
        return ResultadoMotor.deTema(texto, Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.CONTEXTUAL)
            .conEnriquecido(tarjetas.isEmpty()
                ? Enriquecedor.conEnlace("subastas", t.ingles())
                : Enriquecedor.conTarjetas(tarjetas, "subastas", t.ingles()))
            .enIngles(t.ingles())
            .conResultado(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
    }

    private static boolean enIngles(String mensaje, IdiomaPreferido idioma) {
        return switch (idioma == null ? IdiomaPreferido.AUTOMATICO : idioma) {
            case ES -> false;
            case EN -> true;
            case AUTOMATICO -> IdiomaDelTexto.esIngles(mensaje);
        };
    }
}
