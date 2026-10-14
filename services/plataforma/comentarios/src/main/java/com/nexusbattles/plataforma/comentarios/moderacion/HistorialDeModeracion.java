package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.RegistroDeComentario;

/**
 * El historico de moderacion — §7.3.3 «Histórico de moderación: registro de
 * todas las acciones de moderación realizadas» (SA-4, comentarios.yaml 1.14.0).
 *
 * <p>Cada decision ya dejaba su asiento (quien, cuando, accion, motivo, estado
 * anterior y nuevo, texto anterior y nuevo si se edito), pero solo se leia
 * comentario por comentario, desde su detalle. Aqui se leen todos: de la mas
 * reciente a la mas antigua, con filtros por accion, por moderador (su apodo,
 * o parte) y por periodo, y con lo minimo del comentario para reconocerlo
 * (producto, autor y un extracto). La IP de origen se guarda pero no sale.
 *
 * <p>Los comentarios de la pagina se leen en una sola consulta, no uno por
 * asiento.
 */
@Component
public class HistorialDeModeracion {

    /** RNF-USA-001: dieciseis por pagina. */
    static final int TAMANO_POR_OMISION = 16;

    /** Pedir mil filas no es paginar. */
    static final int TAMANO_MAXIMO = 100;

    /** Lo que se ensena del texto del comentario en una fila. */
    static final int LARGO_DEL_EXTRACTO = 160;

    private final AsientoRepository asientos;
    private final ComentarioRepository comentarios;

    public HistorialDeModeracion(AsientoRepository asientos, ComentarioRepository comentarios) {
        this.asientos = asientos;
        this.comentarios = comentarios;
    }

    /**
     * Los filtros del historico; todos opcionales y combinados por Y.
     *
     * @param accion    solo esa accion
     * @param moderador el apodo de quien decidio, o parte de el, sin distinguir mayusculas
     * @param desde     desde ese instante (incluido)
     * @param hasta     antes de ese instante (excluido)
     */
    public record Filtro(AccionDeModeracion accion, String moderador, Instant desde, Instant hasta) {

        public Filtro {
            moderador = moderador == null || moderador.isBlank() ? null : moderador.strip();
            if (desde != null && hasta != null && !desde.isBefore(hasta)) {
                throw new IllegalArgumentException("«desde» tiene que ser anterior a «hasta»");
            }
        }

        /** La consulta con los filtros puestos. */
        Specification<AsientoDeModeracion> especificacion() {
            Specification<AsientoDeModeracion> consulta = (raiz, q, cb) -> cb.conjunction();
            if (accion != null) {
                consulta = consulta.and((raiz, q, cb) -> cb.equal(raiz.get("accion"), accion));
            }
            if (moderador != null) {
                String patron = "%" + escapar(moderador.toLowerCase(Locale.ROOT)) + "%";
                consulta = consulta.and((raiz, q, cb) ->
                        cb.like(cb.lower(raiz.get("apodoModerador")), patron, '\\'));
            }
            if (desde != null) {
                consulta = consulta.and((raiz, q, cb) -> cb.greaterThanOrEqualTo(raiz.<Instant>get("fecha"), desde));
            }
            if (hasta != null) {
                consulta = consulta.and((raiz, q, cb) -> cb.lessThan(raiz.<Instant>get("fecha"), hasta));
            }
            return consulta;
        }

        private static String escapar(String texto) {
            return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        }
    }

    /** Un asiento con el comentario sobre el que se decidio (null si ya no esta). */
    public record Fila(AsientoDeModeracion asiento, Comentario comentario) {

        /** El texto del comentario recortado para una fila; null sin comentario. */
        public String extracto() {
            if (comentario == null || comentario.texto() == null) {
                return null;
            }
            String texto = comentario.texto().strip();
            return texto.length() <= LARGO_DEL_EXTRACTO ? texto : texto.substring(0, LARGO_DEL_EXTRACTO - 1) + "…";
        }
    }

    public record Pagina(List<Fila> filas, int pagina, int tamano, long total, int totalPaginas) {
    }

    @Transactional(readOnly = true)
    public Pagina consultar(Filtro filtro, int pagina, int tamano) {
        int numero = Math.max(pagina, 0);
        int filas = tamano <= 0 ? TAMANO_POR_OMISION : Math.min(tamano, TAMANO_MAXIMO);
        Page<AsientoDeModeracion> encontrados = asientos.findAll(filtro.especificacion(),
                PageRequest.of(numero, filas, Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));

        List<String> ids = encontrados.getContent().stream().map(AsientoDeModeracion::comentarioId).distinct().toList();
        Map<String, Comentario> porId = ids.isEmpty()
                ? Map.of()
                : comentarios.findAllById(ids).stream().map(RegistroDeComentario::aDominio)
                        .collect(Collectors.toMap(Comentario::id, Function.identity(), (a, b) -> a));

        return new Pagina(
                encontrados.getContent().stream().map(a -> new Fila(a, porId.get(a.comentarioId()))).toList(),
                numero, filas, encontrados.getTotalElements(), encontrados.getTotalPages());
    }
}
