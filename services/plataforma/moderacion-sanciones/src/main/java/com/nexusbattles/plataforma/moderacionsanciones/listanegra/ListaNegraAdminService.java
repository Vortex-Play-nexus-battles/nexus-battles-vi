package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Administracion de la lista negra — HU-ADM-002 y 7.3.2 («lista negra
 * actualizable»), {@code /lista-negra/terminos} de moderacion-lista-negra.yaml
 * 2.0.x.
 *
 * <p><b>Identidad de un termino = su forma normalizada.</b> Dar de alta
 * «Spider-Man» con «spiderman» ya en la lista es un 409, no un segundo termino
 * que casaria con los mismos textos. El camino de {@code PUT} y {@code DELETE}
 * tambien se busca por su forma normalizada.
 *
 * <p><b>Cache.</b> Cada escritura invalida la cache de terminos activos al
 * terminar ({@link CatalogoDeTerminosActivos#invalidar()}). Los metodos no son
 * transaccionales a proposito: cada operacion del repositorio confirma por su
 * cuenta y la invalidacion ocurre DESPUES de confirmar; con una transaccion
 * alrededor, una lectura concurrente podria volver a llenar la cache con el
 * estado viejo antes del commit. La carrera que queda (una lectura que empezo
 * antes y guarda despues de invalidar) la acota la caducidad de la cache.
 *
 * <p><b>Omitidos en la edicion.</b> {@code PUT} reutiliza {@code TerminoRequest},
 * donde solo {@code termino} es obligatorio: lo que no llega se queda como
 * estaba. La unica excepcion es el modo cuando cambia la forma normalizada y
 * nadie dice cual quiere: se recalcula el de omision, porque un termino que
 * pasa de nueve letras a tres no debe seguir buscandose como subcadena.
 */
@Service
public class ListaNegraAdminService {

    private static final Logger BITACORA = LoggerFactory.getLogger(ListaNegraAdminService.class);

    /** {@code maxLength} de {@code TerminoRequest.termino}. */
    public static final int LONGITUD_MAXIMA_TERMINO = 120;

    /** Menos de esto, tras normalizar, casaria con demasiado (contrato: 400). */
    public static final int LONGITUD_MINIMA_NORMALIZADA = 3;

    public static final int TAMANO_MAXIMO_DE_PAGINA = 200;

    private final TerminoProhibidoRepository repositorio;
    private final CatalogoDeTerminosActivos catalogo;
    private final Clock reloj;

    public ListaNegraAdminService(TerminoProhibidoRepository repositorio, CatalogoDeTerminosActivos catalogo,
                                  Clock reloj) {
        this.repositorio = Objects.requireNonNull(repositorio);
        this.catalogo = Objects.requireNonNull(catalogo);
        this.reloj = Objects.requireNonNull(reloj);
    }

    /** Lo que llega en el cuerpo de un alta o una edicion. */
    public record DatosDeTermino(String termino, CategoriaDeTermino categoria, ModoDeCoincidencia modo,
                                 Boolean activo) {
    }

    /** Filtros y pagina de {@code GET /terminos}. */
    public record Filtro(CategoriaDeTermino categoria, Boolean activo, String buscar, int pagina, int tamano) {
    }

    public Page<TerminoProhibido> listar(Filtro filtro) {
        if (filtro.pagina() < 0) {
            throw new IllegalArgumentException("La página empieza en 0");
        }
        if (filtro.tamano() < 1 || filtro.tamano() > TAMANO_MAXIMO_DE_PAGINA) {
            throw new IllegalArgumentException("El tamaño de página va de 1 a " + TAMANO_MAXIMO_DE_PAGINA);
        }
        return repositorio.findAll(especificacion(filtro),
                PageRequest.of(filtro.pagina(), filtro.tamano(), Sort.by("termino").ascending().and(Sort.by("id"))));
    }

    public TerminoProhibido agregar(DatosDeTermino datos, String autor) {
        String termino = terminoValido(datos.termino());
        String normalizado = normalizadoValido(termino);
        if (repositorio.findByNormalizado(normalizado).isPresent()) {
            throw new TerminoDuplicadoException(termino, normalizado);
        }
        TerminoProhibido nuevo = new TerminoProhibido(termino, normalizado,
                datos.categoria() == null ? CategoriaDeTermino.OTRO : datos.categoria(),
                datos.modo() == null ? ModoDeCoincidencia.porOmision(normalizado) : datos.modo(),
                datos.activo() == null || datos.activo(), recortar(autor), ahora());
        TerminoProhibido guardado = guardar(nuevo, termino, normalizado);
        catalogo.invalidar();
        BITACORA.info("Lista negra: alta del termino {} ({}, {}) por {}", guardado.id(), guardado.categoria(),
                guardado.modo(), guardado.creadoPor());
        return guardado;
    }

    public TerminoProhibido editar(String terminoActual, DatosDeTermino datos, String autor) {
        TerminoProhibido existente = buscar(terminoActual);
        String termino = terminoValido(datos.termino());
        String normalizado = normalizadoValido(termino);
        boolean cambiaLaForma = !normalizado.equals(existente.normalizado());
        if (cambiaLaForma && repositorio.findByNormalizado(normalizado).isPresent()) {
            throw new TerminoDuplicadoException(termino, normalizado);
        }
        ModoDeCoincidencia modo = datos.modo();
        if (modo == null && cambiaLaForma) {
            modo = ModoDeCoincidencia.porOmision(normalizado);
        }
        existente.actualizar(termino, normalizado, datos.categoria(), modo, datos.activo(), ahora());
        TerminoProhibido guardado = guardar(existente, termino, normalizado);
        catalogo.invalidar();
        BITACORA.info("Lista negra: edicion del termino {} ({}, {}, activo={}) por {}", guardado.id(),
                guardado.categoria(), guardado.modo(), guardado.activo(), recortar(autor));
        return guardado;
    }

    public void eliminar(String termino, String autor) {
        TerminoProhibido existente = buscar(termino);
        repositorio.delete(existente);
        catalogo.invalidar();
        BITACORA.info("Lista negra: baja del termino {} ({}) por {}", existente.id(), existente.categoria(),
                recortar(autor));
    }

    private TerminoProhibido buscar(String termino) {
        String normalizado = NormalizadorDeTexto.compacta(termino);
        return repositorio.findByNormalizado(normalizado)
                .orElseThrow(() -> new TerminoNoEncontradoException(termino));
    }

    /** Dos altas simultaneas de la misma forma: la segunda choca con el indice unico (V6). */
    private TerminoProhibido guardar(TerminoProhibido termino, String texto, String normalizado) {
        try {
            return repositorio.save(termino);
        } catch (DataIntegrityViolationException choque) {
            throw new TerminoDuplicadoException(texto, normalizado);
        }
    }

    private static String terminoValido(String termino) {
        if (termino == null || termino.isBlank()) {
            throw new IllegalArgumentException("El término no puede estar vacío");
        }
        String limpio = termino.strip();
        if (limpio.length() > LONGITUD_MAXIMA_TERMINO) {
            throw new IllegalArgumentException(
                    "El término no puede superar los " + LONGITUD_MAXIMA_TERMINO + " caracteres");
        }
        return limpio;
    }

    private static String normalizadoValido(String termino) {
        String normalizado = NormalizadorDeTexto.compacta(termino);
        if (normalizado.length() < LONGITUD_MINIMA_NORMALIZADA) {
            throw new IllegalArgumentException("El término es demasiado corto: normalizado tiene que tener al menos "
                    + LONGITUD_MINIMA_NORMALIZADA + " letras o cifras");
        }
        return normalizado;
    }

    private static Specification<TerminoProhibido> especificacion(Filtro filtro) {
        return (raiz, consulta, criterios) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.categoria() != null) {
                condiciones.add(criterios.equal(raiz.get("categoria"), filtro.categoria()));
            }
            if (filtro.activo() != null) {
                condiciones.add(criterios.equal(raiz.get("activo"), filtro.activo()));
            }
            if (filtro.buscar() != null && !filtro.buscar().isBlank()) {
                String literal = "%" + escaparComodines(filtro.buscar().strip().toLowerCase(Locale.ROOT)) + "%";
                String compacta = NormalizadorDeTexto.compacta(filtro.buscar());
                Predicate porTexto = criterios.like(criterios.lower(raiz.get("termino")), literal, '\\');
                condiciones.add(compacta.isEmpty() ? porTexto : criterios.or(porTexto,
                        criterios.like(raiz.get("normalizado"), "%" + escaparComodines(compacta) + "%", '\\')));
            }
            return criterios.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private static String escaparComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String recortar(String autor) {
        if (autor == null || autor.isBlank()) {
            return null;
        }
        String limpio = autor.strip();
        return limpio.length() > 100 ? limpio.substring(0, 100) : limpio;
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
