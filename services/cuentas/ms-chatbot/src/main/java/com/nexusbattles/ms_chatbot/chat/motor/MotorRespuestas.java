package com.nexusbattles.ms_chatbot.chat.motor;

import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.enriquecido.RespuestaEnriquecida;
import com.nexusbattles.ms_chatbot.chat.enriquecido.VistaDelChat;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.preferencias.PreferenciasDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.texto.DistanciaDeEdicion;
import com.nexusbattles.ms_chatbot.chat.texto.IdiomaDelTexto;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Motor de respuestas del chatbot (HU-CHA-004): un motor basado en reglas e
 * intenciones (no en un modelo de lenguaje, decisión D-CHA-01) que reconoce la
 * intención de un mensaje comparándolo contra las palabras clave de la base de
 * conocimiento, tolerando errores ortográficos mediante distancia de edición
 * (desde 1.5.0 con transposición de letras vecinas: «subatsa» es «subasta»).
 *
 * <p>Desde HU-CHA-012 responde con la versión de la base de conocimiento que
 * está en producción, y {@link #responderCon} permite evaluar cualquier otra
 * versión (la candidata) con exactamente la misma lógica, sin activarla.
 *
 * <p>Desde 7.4.9 (ms-chatbot.yaml 1.9.0) los temas de producción salen de
 * {@link FuenteDeLaBase} ({@link TemasEnProduccion}: leídos una vez por
 * versión, no en cada mensaje) y la respuesta de la base se guarda en
 * {@link CacheDeRespuestas}. Solo la de {@link #generarRespuesta(String,
 * VistaDelChat, PreferenciasDeRespuesta)}: los seguimientos
 * ({@link #responderTema}, {@link #relacionadosCon}) y la evaluación de una
 * candidata ({@link #responderCon}) se calculan siempre.
 */
@Service
public class MotorRespuestas {

    /** Puntaje mínimo para considerar que un tema resuelve la consulta sin escalar. */
    private static final int UMBRAL_CONFIANZA = 3;

    private static final int PUNTAJE_FRASE_EXACTA = 3;
    private static final int PUNTAJE_PALABRA_CORTA = 1;
    private static final int PUNTAJE_PALABRA_LARGA = 2;

    private static final int LONGITUD_PALABRA_LARGA = 5;

    /**
     * En una frase, una palabra con un error de tecleo solo cuenta si tiene al
     * menos estas letras: «registor» es «registro» mal escrito, pero «duela»
     * no es «duelo» (son palabras distintas y la frase entera vale 3 puntos,
     * lo que basta para responder).
     */
    private static final int LONGITUD_TOLERADA_EN_FRASE = 6;

    /**
     * Palabras gramaticales (stopwords) en español e inglés que no aportan
     * intención por sí solas y por lo tanto no deben sumar puntaje al
     * compararlas palabra por palabra (sí pueden formar parte de una frase
     * completa evaluada como coincidencia exacta).
     */
    private static final Set<String> PALABRAS_VACIAS = Set.of(
        "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "a",
        "ante", "con", "contra", "desde", "en", "entre", "hacia", "hasta", "para",
        "por", "segun", "sin", "sobre", "tras", "que", "como", "cuando", "donde",
        "quien", "quienes", "cual", "cuales", "es", "son", "ser", "esta", "estan",
        "esto", "ese", "esa", "eso", "esos", "esas", "mi", "tu", "su", "me", "te",
        "se", "nos", "os", "les", "lo", "no", "si", "ya", "mas", "pero", "del", "al",
        "yo", "ella", "ellos", "ellas", "nosotros", "ustedes",
        "the", "an", "is", "are", "was", "were", "be", "been", "of", "to", "in",
        "on", "for", "and", "or", "this", "that", "these", "those", "i", "you", "he",
        "she", "it", "we", "they", "do", "does", "did", "how", "what", "when",
        "where", "who", "which", "my", "your", "his", "her", "its", "our", "their",
        "with", "at", "by", "from", "as", "but", "if", "so", "not", "can"
    );

    /**
     * 1.5.0: el orden en que se recorren los temas, igual en producción y en
     * la evaluación de una candidata: a igual puntaje, vista y prioridad
     * decide la clave, no el orden en que la base devolvió las filas.
     */
    public static final Comparator<TemaConocimiento> ORDEN_DE_TEMAS =
        Comparator.comparingInt(TemaConocimiento::getPrioridad).reversed()
            .thenComparing(TemaConocimiento::getClave, Comparator.nullsLast(Comparator.naturalOrder()));

    // 7.4.3 «cuando el chatbot no pueda proporcionar una respuesta satisfactoria».
    static final String ESCALAMIENTO_ES = "No estoy seguro de haber entendido tu consulta. "
        + "Puedo ponerte en contacto con soporte humano para resolverla; "
        + "mientras tanto, esta pregunta quedó registrada para mejorar mis respuestas.";
    static final String ESCALAMIENTO_EN = "I'm not sure I understood your question. "
        + "I can put you in touch with human support to solve it; "
        + "meanwhile, your question was recorded to improve my answers.";

    private final FuenteDeLaBase fuente;
    private final CacheDeRespuestas cache;

    public MotorRespuestas(FuenteDeLaBase fuente, CacheDeRespuestas cache) {
        this.fuente = fuente;
        this.cache = cache;
    }

    /** Responde con la versión de la base de conocimiento que está en producción. */
    public ResultadoMotor generarRespuesta(String mensajeUsuario) {
        return generarRespuesta(mensajeUsuario, null);
    }

    /**
     * ms-chatbot.yaml 1.3.4: como {@link #generarRespuesta(String)}, sabiendo
     * en qué sección está el jugador. La vista desempata entre temas con el
     * mismo puntaje y, si la consulta se escala, aporta preguntas de su tema.
     */
    public ResultadoMotor generarRespuesta(String mensajeUsuario, VistaDelChat vista) {
        return generarRespuesta(mensajeUsuario, vista, PreferenciasDeRespuesta.POR_DEFECTO);
    }

    /**
     * ms-chatbot.yaml 1.3.6 (7.4.5): como {@link #generarRespuesta(String, VistaDelChat)},
     * con las preferencias de quien pregunta: el idioma de la respuesta (si el
     * tema lo tiene) y el nivel de detalle. Con las preferencias por defecto
     * responde exactamente igual que sin ellas.
     *
     * <p>7.4.9: es la única respuesta que se guarda en {@link CacheDeRespuestas},
     * con la versión en producción, la pregunta, las preferencias y la vista
     * como clave: la misma pregunta se responde sin volver a puntuar los temas.
     */
    public ResultadoMotor generarRespuesta(String mensajeUsuario, VistaDelChat vista,
                                           PreferenciasDeRespuesta preferencias) {
        PreferenciasDeRespuesta pedidas = preferencias == null ? PreferenciasDeRespuesta.POR_DEFECTO : preferencias;
        BaseEnProduccion base = fuente.enProduccion();
        return cache.obtener(CacheDeRespuestas.Clave.de(base.versionId(), mensajeUsuario, vista, pedidas),
            () -> responderCon(mensajeUsuario, base.temas(), vista, pedidas));
    }

    /**
     * Responde usando exactamente los temas recibidos (HU-CHA-012): así se
     * evalúa una versión candidata con la misma lógica que la de producción,
     * sin activarla. Solo se tienen en cuenta los temas activos.
     */
    public ResultadoMotor responderCon(String mensajeUsuario, List<TemaConocimiento> temas) {
        return responderCon(mensajeUsuario, temas, null, PreferenciasDeRespuesta.POR_DEFECTO);
    }

    /**
     * 1.5.0 (7.4.2 «contexto», 7.4.5 «recordar el contexto de consultas
     * anteriores»): responde otra vez el tema {@code clave} de producción, con
     * las preferencias dadas («más detalle», «en inglés»). Vacío si ese tema
     * ya no está en producción o está inactivo.
     */
    public Optional<ResultadoMotor> responderTema(String clave, PreferenciasDeRespuesta preferencias,
                                                  boolean preguntaEnIngles) {
        if (clave == null) {
            return Optional.empty();
        }
        List<TemaConocimiento> temas = ordenados(fuente.enProduccion().temas());
        return temas.stream()
            .filter(tema -> tema.isActivo() && clave.equals(tema.getClave()))
            .findFirst()
            .map(tema -> deTema(tema, preguntaEnIngles, temas,
                preferencias == null ? PreferenciasDeRespuesta.POR_DEFECTO : preferencias));
    }

    /**
     * 1.5.0 — la respuesta del tema {@code clave} no le sirvió al jugador: se
     * le dice {@code texto}, con otras preguntas de ese tema, el enlace a su
     * sección y la oferta de soporte humano. Cuenta como escalamiento en las
     * analíticas (se ofrece soporte), sin tema que la respondiera; no es una
     * brecha de conocimiento, eso lo decide {@code ChatService}.
     */
    public Optional<ResultadoMotor> relacionadosCon(String clave, String texto, boolean ingles) {
        if (clave == null) {
            return Optional.empty();
        }
        List<TemaConocimiento> temas = ordenados(fuente.enProduccion().temas());
        return temas.stream()
            .filter(tema -> tema.isActivo() && clave.equals(tema.getClave()))
            .findFirst()
            .map(tema -> new ResultadoMotor(texto, tema.getCategoria(), TipoRespuesta.CONTEXTUAL, true, List.of(),
                null, Enriquecedor.paraNoSirvio(tema, temas, ingles), ingles, ResultadoDeRespuesta.NO_SIRVIO));
    }

    private static List<TemaConocimiento> ordenados(List<TemaConocimiento> temas) {
        List<TemaConocimiento> copia = new ArrayList<>(temas);
        copia.sort(ORDEN_DE_TEMAS);
        return copia;
    }

    // El vocabulario de la ultima lista de temas: la de produccion es la misma
    // instancia mientras no cambie la version (TemasEnProduccion), asi que no
    // se recalcula en cada mensaje.
    private record Vocabulario(List<TemaConocimiento> de, Set<String> palabras) {
    }

    private volatile Vocabulario ultimoVocabulario;

    private Set<String> vocabularioCacheado(List<TemaConocimiento> temas) {
        Vocabulario actual = ultimoVocabulario;
        if (actual != null && actual.de() == temas) {
            return actual.palabras();
        }
        Set<String> palabras = Set.copyOf(vocabularioDe(temas));
        ultimoVocabulario = new Vocabulario(temas, palabras);
        return palabras;
    }

    private ResultadoMotor responderCon(String mensajeUsuario, List<TemaConocimiento> temasRecibidos,
                                        VistaDelChat vista, PreferenciasDeRespuesta preferencias) {
        List<TemaConocimiento> temas = ordenados(temasRecibidos);
        Set<String> vocabulario = vocabularioCacheado(temasRecibidos);
        Categoria preferida = vista == null ? null : vista.categoria();
        String mensajeNormalizado = NormalizadorTexto.normalizar(mensajeUsuario);
        Optional<TemaConocimiento> porTitulo = temaConElTitulo(mensajeNormalizado, temas);
        if (porTitulo.isPresent()) {
            return deTema(porTitulo.get(), false, temas, preferencias);
        }
        List<String> palabrasMensaje = List.of(mensajeNormalizado.split(" "));

        Coincidencia mejor = null;
        List<Coincidencia> todas = new ArrayList<>();

        for (TemaConocimiento tema : temas) {
            if (!tema.isActivo()) {
                continue;
            }
            int puntajeEs = puntuar(palabrasMensaje, mensajeNormalizado, tema.getPalabrasClaveEs(), vocabulario);
            int puntajeEn = puntuar(palabrasMensaje, mensajeNormalizado, tema.getPalabrasClaveEn(), vocabulario);

            boolean esIngles = puntajeEn > puntajeEs;
            int puntaje = esIngles ? puntajeEn : puntajeEs;

            if (puntaje > 0) {
                Coincidencia coincidencia = new Coincidencia(tema, puntaje, esIngles);
                todas.add(coincidencia);
                if (esMejor(coincidencia, mejor, preferida)) {
                    mejor = coincidencia;
                }
            }
        }

        if (mejor != null && mejor.puntaje() >= UMBRAL_CONFIANZA) {
            return deTema(mejor.tema(), mejor.esIngles(), temas, preferencias);
        }

        // Los temas de cortesia (saludo, despedida) no son "preguntas
        // relacionadas": nadie necesita que le sugieran decir "hola".
        List<String> sugerencias = todas.stream()
            .filter(c -> !Enriquecedor.esDeCortesia(c.tema()))
            .sorted(Comparator.comparingInt(Coincidencia::puntaje).reversed())
            .map(c -> c.tema().getTitulo())
            .distinct()
            .limit(3)
            .toList();

        boolean ingles = switch (preferencias.idioma()) {
            case ES -> false;
            case EN -> true;
            case AUTOMATICO -> IdiomaDelTexto.esIngles(mensajeNormalizado);
        };
        return ResultadoMotor.escalado(ingles ? ESCALAMIENTO_EN : ESCALAMIENTO_ES, sugerencias)
            .conEnriquecido(Enriquecedor.paraEscalamiento(sugerencias, temas, vista, ingles))
            .enIngles(ingles);
    }

    // 1.3.4: la respuesta de un tema lleva sus pasos, el enlace a su sección y
    // otras preguntas de su categoría.
    // 1.3.6: el idioma lo decide la preferencia (AUTOMATICO: el de la
    // pregunta) y, si el tema no tiene texto en ingles, queda en espanol. Lo
    // enriquecido se arma con el texto completo; el nivel de detalle solo
    // cambia el texto que se muestra.
    private static ResultadoMotor deTema(TemaConocimiento tema, boolean preguntaEnIngles, List<TemaConocimiento> temas,
                                         PreferenciasDeRespuesta preferencias) {
        boolean ingles = enIngles(preguntaEnIngles, preferencias.idioma());
        String texto = ingles ? tema.getContenidoRespuestaEn() : tema.getContenidoRespuestaEs();
        if (texto == null || texto.isBlank()) {
            texto = tema.getContenidoRespuestaEs();
            ingles = false;
        }
        RespuestaEnriquecida enriquecida = Enriquecedor.paraTema(tema, texto, temas, ingles);
        String mostrado = AjusteDeDetalle.aplicar(texto, tema.getTipoRespuesta(), preferencias.nivelDetalle(),
            enriquecida.respuestasRapidas(), ingles);
        return ResultadoMotor.deTema(mostrado, tema.getCategoria(), tema.getTipoRespuesta(), tema.getClave())
            .conEnriquecido(enriquecida)
            .enIngles(ingles);
    }

    private static boolean enIngles(boolean preguntaEnIngles, IdiomaPreferido idioma) {
        return switch (idioma) {
            case ES -> false;
            case EN -> true;
            case AUTOMATICO -> preguntaEnIngles;
        };
    }

    // ms-chatbot.yaml 1.3.3: el titulo de un tema, tal cual (sin importar
    // tildes ni mayusculas), es una pregunta de ese tema. Es lo que manda la
    // ventana al pulsar una sugerencia (GET /chat/sugerencias) o una de las
    // "preguntas relacionadas" de un escalamiento, que tambien son titulos.
    // Sin esto, pulsar una sugerencia podia no alcanzar el umbral y escalar.
    private static Optional<TemaConocimiento> temaConElTitulo(String mensajeNormalizado,
                                                              List<TemaConocimiento> temas) {
        if (mensajeNormalizado.isEmpty()) {
            return Optional.empty();
        }
        return temas.stream()
            .filter(TemaConocimiento::isActivo)
            .filter(tema -> mensajeNormalizado.equals(NormalizadorTexto.normalizar(tema.getTitulo())))
            .max(Comparator.comparingInt(TemaConocimiento::getPrioridad));
    }

    // HU-CHA-012: a igual puntaje gana el tema de mayor prioridad. Sin esto,
    // un empate lo decidia el orden en que la base devolvia las filas, que no
    // esta garantizado y podia cambiar entre una consulta y otra.
    // 1.3.4: con la vista del jugador, a igual puntaje gana primero el tema de
    // la categoria de esa vista (en «Subastas», una duda sobre "comprar" es de
    // subastas antes que de la tienda); despues, la prioridad.
    // 1.6.0 (7.4.10): un tema es de la vista si esa es su categoria o una de
    // sus intenciones adicionales.
    private static boolean esMejor(Coincidencia candidata, Coincidencia actual, Categoria preferida) {
        if (actual == null || candidata.puntaje() > actual.puntaje()) {
            return true;
        }
        if (candidata.puntaje() != actual.puntaje()) {
            return false;
        }
        boolean candidataEsDeLaVista = candidata.tema().atiende(preferida);
        boolean actualEsDeLaVista = actual.tema().atiende(preferida);
        if (candidataEsDeLaVista != actualEsDeLaVista) {
            return candidataEsDeLaVista;
        }
        return candidata.tema().getPrioridad() > actual.tema().getPrioridad();
    }

    // Segunda auditoria del 7.4 (7.4.2 «errores ortograficos comunes»): una
    // variante cuenta como frase tambien con un error de tecleo en sus
    // palabras largas («como me registor», «como funsionan las subatsas»).
    // Antes solo la frase exacta sumaba 3 y una palabra aproximada sola sumaba
    // 2, por debajo del umbral: la pregunta mal escrita escalaba.
    private int puntuar(List<String> palabrasMensaje, String mensajeNormalizado, String palabrasClaveCrudas,
                        Set<String> vocabulario) {
        if (palabrasClaveCrudas == null || palabrasClaveCrudas.isBlank()) {
            return 0;
        }

        int puntaje = 0;
        Set<String> palabrasClaveUnicas = new LinkedHashSet<>();

        for (String fraseClave : palabrasClaveCrudas.split(",")) {
            String fraseNormalizada = NormalizadorTexto.normalizar(fraseClave);
            if (fraseNormalizada.isBlank()) {
                continue;
            }

            if (contieneFrase(palabrasMensaje, mensajeNormalizado, fraseNormalizada, vocabulario)) {
                puntaje += PUNTAJE_FRASE_EXACTA;
            }

            for (String palabra : fraseNormalizada.split(" ")) {
                if (!palabra.isBlank() && !PALABRAS_VACIAS.contains(palabra)) {
                    palabrasClaveUnicas.add(palabra);
                }
            }
        }

        for (String palabraClave : palabrasClaveUnicas) {
            for (String palabraMensaje : palabrasMensaje) {
                if (coincidenAproximadamente(palabraClave, palabraMensaje, vocabulario)) {
                    puntaje += esPalabraLarga(palabraClave) ? PUNTAJE_PALABRA_LARGA : PUNTAJE_PALABRA_CORTA;
                    break;
                }
            }
        }

        return puntaje;
    }

    // La frase tal cual, o como palabras seguidas del mensaje que coinciden
    // una a una (aproximadamente, con la misma regla que las palabras sueltas).
    private static boolean contieneFrase(List<String> palabrasMensaje, String mensajeNormalizado, String frase,
                                         Set<String> vocabulario) {
        if (mensajeNormalizado.contains(frase)) {
            return true;
        }
        String[] palabrasFrase = frase.split(" ");
        for (int inicio = 0; inicio + palabrasFrase.length <= palabrasMensaje.size(); inicio++) {
            boolean todas = true;
            for (int k = 0; k < palabrasFrase.length && todas; k++) {
                String deLaFrase = palabrasFrase[k];
                String delMensaje = palabrasMensaje.get(inicio + k);
                todas = deLaFrase.equals(delMensaje)
                    || (deLaFrase.length() >= LONGITUD_TOLERADA_EN_FRASE
                        && coincidenAproximadamente(deLaFrase, delMensaje, vocabulario));
            }
            if (todas) {
                return true;
            }
        }
        return false;
    }

    // Iguales, o un error de tecleo en una palabra larga (DistanciaDeEdicion:
    // las cortas solo coinciden exactas). Una palabra del mensaje que ya es
    // otra palabra de la base no es un error de tecleo: «formas» no es
    // «normas» mal escrito (segunda auditoria del 7.4: «¿de qué formas puedo
    // jugar?» caia en las normas de conducta).
    private static boolean coincidenAproximadamente(String palabraClave, String palabraMensaje,
                                                    Set<String> vocabulario) {
        if (palabraClave.isBlank() || palabraMensaje.isBlank()) {
            return false;
        }
        if (palabraClave.equals(palabraMensaje)) {
            return true;
        }
        if (vocabulario.contains(palabraMensaje) && !esSuPlural(palabraClave, palabraMensaje)) {
            return false;
        }
        return DistanciaDeEdicion.coinciden(palabraClave, palabraMensaje);
    }

    // «recompensa» y «recompensas» son la misma palabra aunque las dos esten
    // en la base.
    private static boolean esSuPlural(String una, String otra) {
        return una.equals(otra + "s") || otra.equals(una + "s");
    }

    // Las palabras de todas las variantes de los temas activos, en los dos
    // idiomas: lo que la base ya reconoce como palabra.
    static Set<String> vocabularioDe(List<TemaConocimiento> temas) {
        Set<String> vocabulario = new java.util.HashSet<>();
        for (TemaConocimiento tema : temas) {
            if (!tema.isActivo()) {
                continue;
            }
            for (String crudas : new String[] {tema.getPalabrasClaveEs(), tema.getPalabrasClaveEn()}) {
                if (crudas == null || crudas.isBlank()) {
                    continue;
                }
                for (String palabra : NormalizadorTexto.normalizar(crudas.replace(',', ' ')).split(" ")) {
                    if (!palabra.isBlank()) {
                        vocabulario.add(palabra);
                    }
                }
            }
        }
        return vocabulario;
    }

    private static boolean esPalabraLarga(String palabra) {
        return palabra.length() >= LONGITUD_PALABRA_LARGA;
    }

    /** Coincidencia interna de un tema con su puntaje e idioma detectado. */
    private record Coincidencia(TemaConocimiento tema, int puntaje, boolean esIngles) {
    }
}
