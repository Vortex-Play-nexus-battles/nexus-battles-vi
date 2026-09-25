package nexus.semilla;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import nexus.api.SolicitudCrearProducto;
import nexus.persistencia.ProductoRepository;
import nexus.semilla.CatalogoInicial.EntradaCatalogo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Siembra el catalogo inicial (heroes, armas, armaduras, items y epicas de las
 * reglas del curso) al arrancar, SOLO si {@code catalogo.semilla.habilitada}
 * es true.
 *
 * <p>Reglas de la semilla:
 * <ul>
 *   <li>Solo crea lo que falta: si el id ya existe, no lo toca. Un
 *       administrador pudo haber editado ese producto.</li>
 *   <li>Usa {@code insert}, nunca {@code save}: aun en carrera con otra
 *       instancia, un documento existente no se reemplaza (la base responde
 *       clave duplicada y se cuenta como existente).</li>
 *   <li>Cada entrada pasa por las validaciones del alta. La que no las cumple
 *       se rechaza y queda en la bitacora; el servicio arranca igual.</li>
 * </ul>
 */
@Component
public class SemillaDelCatalogo implements ApplicationRunner {

    private static final Logger BITACORA = LoggerFactory.getLogger(SemillaDelCatalogo.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ProductoRepository repositorio;
    private final MapeadorDelCatalogo mapeador;
    private final Validator validador;
    private final boolean habilitada;
    private final Resource archivo;

    public SemillaDelCatalogo(
            ProductoRepository repositorio,
            MapeadorDelCatalogo mapeador,
            Validator validador,
            @Value("${catalogo.semilla.habilitada:false}") boolean habilitada,
            @Value("classpath:semilla/catalogo-inicial.json") Resource archivo) {
        this.repositorio = repositorio;
        this.mapeador = mapeador;
        this.validador = validador;
        this.habilitada = habilitada;
        this.archivo = archivo;
    }

    /** Lee el catalogo inicial (UTF-8). */
    public static CatalogoInicial leer(InputStream json) {
        return JSON.readValue(json, CatalogoInicial.class);
    }

    /**
     * Punto de entrada del arranque. La semilla es un extra para la demo: si
     * falla (Mongo caido, JSON ilegible), el servicio debe arrancar igual, asi
     * que aqui se registra y no se propaga. {@link #sembrar()} si propaga.
     */
    @Override
    public void run(ApplicationArguments argumentos) {
        try {
            sembrar();
        } catch (RuntimeException e) {
            BITACORA.error("Semilla del catalogo fallida: el servicio arranca sin sembrar", e);
        }
    }

    /**
     * Siembra lo que falte y devuelve el resumen.
     *
     * @throws RuntimeException si falla la lectura del JSON o el acceso a la
     *         base (salvo clave duplicada, que cuenta como existente)
     */
    public ResultadoSemilla sembrar() {
        if (!habilitada) {
            return ResultadoSemilla.deshabilitada();
        }

        CatalogoInicial catalogo = leerArchivo();
        Instant ahora = Instant.now();
        List<String> insertados = new ArrayList<>();
        List<String> existentes = new ArrayList<>();
        List<String> rechazados = new ArrayList<>();

        for (EntradaCatalogo entrada : catalogo.todas()) {
            String id = MapeadorDelCatalogo.identificador(entrada.id());
            if (repositorio.existsById(id)) {
                existentes.add(id);
                continue;
            }
            SolicitudCrearProducto solicitud;
            try {
                solicitud = mapeador.aSolicitud(entrada, catalogo);
            } catch (IllegalArgumentException e) {
                rechazados.add(entrada.id() + ": " + e.getMessage());
                continue;
            }
            Set<ConstraintViolation<SolicitudCrearProducto>> violaciones =
                    validador.validate(solicitud);
            if (!violaciones.isEmpty()) {
                rechazados.add(entrada.id() + ": " + violaciones.stream()
                        .map(ConstraintViolation::getMessage)
                        .sorted()
                        .collect(Collectors.joining("; ")));
                continue;
            }
            try {
                repositorio.insert(mapeador.aProducto(id, solicitud, ahora));
                insertados.add(id);
            } catch (DuplicateKeyException e) {
                existentes.add(id);
            }
        }

        BITACORA.info("Semilla del catalogo: {} insertados, {} ya existian, {} rechazados",
                insertados.size(), existentes.size(), rechazados.size());
        rechazados.forEach(motivo -> BITACORA.warn("Semilla del catalogo, rechazado: {}", motivo));
        return new ResultadoSemilla(true, insertados, existentes, rechazados);
    }

    private CatalogoInicial leerArchivo() {
        try (InputStream json = archivo.getInputStream()) {
            return leer(json);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el catalogo inicial", e);
        }
    }
}
