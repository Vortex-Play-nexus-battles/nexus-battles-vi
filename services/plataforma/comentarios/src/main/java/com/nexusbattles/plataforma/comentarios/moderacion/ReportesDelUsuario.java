package com.nexusbattles.plataforma.comentarios.moderacion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository.ComentarioReportado;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository.IdDeComentario;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los reportes de una cuenta, hechos y recibidos — contrato 1.11.0 (§7.3.4,
 * «reportes realizados y recibidos» de la ficha administrativa del usuario).
 *
 * <p>Este servicio es el dueño de los reportes y de los comentarios: la ficha
 * los pide aqui, por el {@code uid} de la cuenta, y no los cruza con ninguna
 * otra base. «Realizados» son los reportes que la cuenta hizo; «recibidos», los
 * que otros hicieron sobre sus comentarios.
 *
 * <p>De un reporte recibido no se devuelve quien lo hizo, igual que en el
 * detalle de la cola ({@code ReporteResponse}): dar a conocer al reportante
 * invita a la represalia, y para decidir sobre una cuenta basta con el motivo,
 * la categoria y el comentario.
 */
@Service
public class ReportesDelUsuario {

    /** Que reportes: los que hizo la cuenta o los que recibieron sus comentarios. */
    public enum Direccion {
        REALIZADOS,
        RECIBIDOS
    }

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    /** Largo del extracto del comentario. */
    static final int LARGO_DEL_EXTRACTO = 160;

    /** Del mas reciente al mas antiguo; el id desempata para que la pagina sea estable. */
    private static final Sort ORDEN = Sort.by(Sort.Order.desc("fecha"), Sort.Order.asc("id"));

    private final ReporteRepository reportes;
    private final ComentarioRepository comentarios;

    public ReportesDelUsuario(ReporteRepository reportes, ComentarioRepository comentarios) {
        this.reportes = Objects.requireNonNull(reportes);
        this.comentarios = Objects.requireNonNull(comentarios);
    }

    /** El comentario de un reporte, tal como lo ve la ficha. */
    public record Comentado(String id, String productoId, String autorId, String apodoAutor, String extracto,
            Comentario.Estado estado) {
    }

    /** Un reporte. {@code comentario} es nulo si el comentario ya no existe. */
    public record Reporte(String id, String comentarioId, CategoriaDeReporte categoria, String descripcion,
            Instant fecha, Comentado comentario) {
    }

    /**
     * Una pagina de reportes en una direccion, con los dos totales para que la
     * ficha diga «3 hechos · 5 recibidos» sin pedir la otra direccion.
     */
    public record Pagina(String usuarioId, Direccion direccion, List<Reporte> reportes, long total, int pagina,
            int tamano, long totalRealizados, long totalRecibidos) {
    }

    @Transactional(readOnly = true)
    public Pagina consultar(String usuarioId, Direccion direccion, int pagina, int tamano) {
        if (usuarioId == null || usuarioId.isBlank()) {
            throw new IllegalArgumentException("Los reportes son de una cuenta.");
        }
        Direccion sentido = direccion == null ? Direccion.REALIZADOS : direccion;
        int paginaValida = Math.max(pagina, 0);
        int tamanoValido = tamano < 1 ? TAMANO_POR_DEFECTO : Math.min(tamano, TAMANO_MAXIMO);
        PageRequest peticion = PageRequest.of(paginaValida, tamanoValido, ORDEN);

        List<String> suyos = comentarios.findIdByAutorId(usuarioId).stream().map(IdDeComentario::getId).toList();
        long totalRealizados = reportes.countByReportanteId(usuarioId);
        long totalRecibidos = suyos.isEmpty() ? 0 : reportes.countByComentarioIdIn(suyos);

        Page<RegistroDeReporte> leida;
        if (sentido == Direccion.REALIZADOS) {
            leida = reportes.findByReportanteId(usuarioId, peticion);
        } else if (suyos.isEmpty()) {
            leida = Page.empty(peticion);
        } else {
            leida = reportes.findByComentarioIdIn(suyos, peticion);
        }

        List<String> ids = leida.getContent().stream().map(RegistroDeReporte::comentarioId).distinct().toList();
        Map<String, ComentarioReportado> porId = ids.isEmpty() ? Map.of()
                : comentarios.findByIdIn(ids).stream()
                        .collect(Collectors.toMap(ComentarioReportado::getId, Function.identity(), (a, b) -> a));

        List<Reporte> contenido = leida.getContent().stream()
                .map(r -> new Reporte(r.id(), r.comentarioId(), r.categoria(), r.descripcion(), r.fecha(),
                        comentado(porId.get(r.comentarioId()))))
                .toList();
        return new Pagina(usuarioId, sentido, contenido, leida.getTotalElements(), paginaValida, tamanoValido,
                totalRealizados, totalRecibidos);
    }

    private static Comentado comentado(ComentarioReportado comentario) {
        if (comentario == null) {
            return null;
        }
        return new Comentado(comentario.getId(), comentario.getProductoId(), comentario.getAutorId(),
                comentario.getApodoAutor(), extracto(comentario.getTexto()), comentario.getEstado());
    }

    /** Las primeras letras del texto, cortadas por palabra y con puntos suspensivos. */
    static String extracto(String texto) {
        if (texto == null) {
            return null;
        }
        String limpio = texto.strip().replaceAll("\\s+", " ");
        if (limpio.length() <= LARGO_DEL_EXTRACTO) {
            return limpio;
        }
        String corte = limpio.substring(0, LARGO_DEL_EXTRACTO);
        int espacio = corte.lastIndexOf(' ');
        return (espacio > LARGO_DEL_EXTRACTO / 2 ? corte.substring(0, espacio) : corte) + "…";
    }
}
