package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * {@code POST /lista-negra/verificar} — moderacion-lista-negra.yaml 2.1.x.
 *
 * <p>Normaliza el texto con la misma funcion que los terminos, busca los
 * terminos activos (de la cache, o de PostgreSQL si Redis no esta) y aplica la
 * politica del contexto. El detalle —que termino coincidio, su categoria y el
 * id de la regla— solo sale si quien llama puede verlo (token de servicio o de
 * moderacion): sin eso la respuesta seria un oraculo para recorrer la lista
 * termino a termino desde el formulario de registro.
 *
 * <p><b>Cada deteccion queda registrada aqui, en el origen</b> (HU-COM-007
 * CA-01, «registra la deteccion»; RFINAL-02): una linea de bitacora JSON con
 * las reglas (ids de la lista negra), sus categorias y modos, el contexto, la
 * accion aplicada y el largo del texto. El {@code trazaId} lo pone el MDC de
 * la peticion (biblioteca de observabilidad), y con el se une a la linea del
 * servicio que pregunto. <b>No se registra el texto</b>: puede ser un mensaje
 * privado, y para saber que regla salto basta su id.
 */
@Service
public class VerificacionListaNegraService {

    /** {@code maxLength} del contrato. */
    public static final int LONGITUD_MAXIMA = 2000;

    /** El evento que se busca en la bitacora. */
    static final String EVENTO_DETECCION = "lista-negra.deteccion";

    private static final Logger BITACORA = LoggerFactory.getLogger(VerificacionListaNegraService.class);

    private final CatalogoDeTerminosActivos catalogo;
    private final PoliticaDeModeracion politica;

    public VerificacionListaNegraService(CatalogoDeTerminosActivos catalogo, PoliticaDeModeracion politica) {
        this.catalogo = Objects.requireNonNull(catalogo);
        this.politica = Objects.requireNonNull(politica);
    }

    /**
     * @param conDetalle si la respuesta puede llevar la categoria, los terminos y las reglas
     * @throws IllegalArgumentException texto vacio o de mas de {@value #LONGITUD_MAXIMA} caracteres
     */
    public ResultadoVerificacion verificar(String texto, ContextoDeTexto contexto, boolean conDetalle) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El texto a verificar no puede estar vacío");
        }
        if (texto.length() > LONGITUD_MAXIMA) {
            throw new IllegalArgumentException(
                    "El texto a verificar no puede superar los " + LONGITUD_MAXIMA + " caracteres");
        }
        List<TerminoActivo> encontrados =
                DetectorDeTerminos.coincidencias(NormalizadorDeTexto.normalizar(texto), catalogo.activos());
        if (encontrados.isEmpty()) {
            return ResultadoVerificacion.APROBADO;
        }
        AccionDeModeracion accion = politica.siCoincide(contexto);
        String motivo = politica.motivo(contexto);
        List<TerminoActivo> ordenados = encontrados.stream()
                .sorted(Comparator.comparing(TerminoActivo::termino))
                .toList();
        registrarDeteccion(ordenados, contexto, accion, texto.length());
        if (!conDetalle) {
            return new ResultadoVerificacion(false, accion, motivo, null, null, null);
        }
        return new ResultadoVerificacion(false, accion, motivo, principal(encontrados).categoria(),
                ordenados.stream().map(TerminoActivo::termino).toList(),
                reglasDe(ordenados));
    }

    /**
     * Los ids de las reglas, en el orden de {@code coincidencias}; {@code null}
     * si alguna no tiene fila (un termino construido a mano en una prueba).
     */
    static List<Long> reglasDe(List<TerminoActivo> ordenados) {
        if (ordenados.stream().anyMatch(t -> t.id() == null)) {
            return null;
        }
        return ordenados.stream().map(TerminoActivo::id).toList();
    }

    private static void registrarDeteccion(List<TerminoActivo> ordenados, ContextoDeTexto contexto,
                                           AccionDeModeracion accion, int largoDelTexto) {
        List<String> reglas = ordenados.stream()
                .map(t -> t.id() == null ? "sin-id" : String.valueOf(t.id()))
                .toList();
        List<String> categorias = ordenados.stream().map(t -> t.categoria().name()).distinct().toList();
        List<String> modos = ordenados.stream().map(t -> t.modo().name()).distinct().toList();
        String enQueContexto = contexto == null ? ContextoDeTexto.GENERICO.name() : contexto.name();
        BITACORA.atInfo()
                .addKeyValue("evento", EVENTO_DETECCION)
                .addKeyValue("reglas", reglas)
                .addKeyValue("categorias", categorias)
                .addKeyValue("modos", modos)
                .addKeyValue("contexto", enQueContexto)
                .addKeyValue("accion", accion.name())
                .addKeyValue("largoDelTexto", largoDelTexto)
                .log("{} reglas={} categorias={} contexto={} accion={}", EVENTO_DETECCION, reglas, categorias,
                        enQueContexto, accion);
    }

    /**
     * La categoria que se informa cuando coinciden varios: la del termino mas
     * largo, que es el mas especifico («spiderman» antes que un termino corto
     * que tambien aparezca). Con empate, el primero en el orden del catalogo.
     */
    static TerminoActivo principal(List<TerminoActivo> encontrados) {
        TerminoActivo principal = encontrados.get(0);
        for (TerminoActivo termino : encontrados) {
            if (termino.normalizado().length() > principal.normalizado().length()) {
                principal = termino;
            }
        }
        return principal;
    }

    /**
     * @param categoria     y {@code coincidencias} y {@code reglas}: solo con detalle
     * @param reglas        los ids de la lista negra que coincidieron, en el orden de {@code coincidencias}
     */
    public record ResultadoVerificacion(boolean aprobado, AccionDeModeracion accion, String motivo,
                                        CategoriaDeTermino categoria, List<String> coincidencias,
                                        List<Long> reglas) {

        static final ResultadoVerificacion APROBADO =
                new ResultadoVerificacion(true, AccionDeModeracion.PERMITIR, null, null, null, null);

        /** Sin reglas: la forma de antes de la 2.1.0. */
        public ResultadoVerificacion(boolean aprobado, AccionDeModeracion accion, String motivo,
                                     CategoriaDeTermino categoria, List<String> coincidencias) {
            this(aprobado, accion, motivo, categoria, coincidencias, null);
        }
    }
}
