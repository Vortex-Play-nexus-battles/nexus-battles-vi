package com.nexusbattles.plataforma.comentarios.calificacion;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import com.nexusbattles.plataforma.comentarios.Calificacion;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.ResumenDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoDeProductos;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoDeProductos.Existencia;
import com.nexusbattles.plataforma.comentarios.catalogo.ProductoInexistente;
import com.nexusbattles.plataforma.comentarios.publicacion.ConsultaDeSanciones;

/**
 * La calificacion de un producto, separada del comentario — 7.1 del documento
 * del curso, contrato 1.4.0 ({@code /products/{productId}/rating}), B3.
 *
 * <h2>Las tres reglas</h2>
 *
 * <ol>
 *   <li><b>Una sola vez, sin editar ni retirar.</b> La decide la restriccion
 *       unica de la tabla ({@link RepositorioDeCalificaciones#insertarSiNoExiste}),
 *       no una lectura previa: dos POST simultaneos del mismo jugador no se ven
 *       entre si, la restriccion si. El segundo recibe 409.</li>
 *   <li><b>Sobre un producto que existe.</b> Se pregunta al catalogo antes de
 *       escribir; si no contesta, 503 y nada guardado.</li>
 *   <li><b>No califica quien esta sancionado</b> (403), con la misma
 *       comprobacion que ya hacia la publicacion de comentarios: calificar es
 *       opinar en publico, igual que comentar.</li>
 * </ol>
 *
 * <p>El resumen (promedio, total y distribucion) sale siempre de aqui, y lo
 * usan tanto {@code GET /rating} como el hilo de comentarios: una sola fuente
 * para el mismo numero, en vez de dos calculos que acaben difiriendo en un
 * decimal (que es lo que pasaba: el hilo lo calculaba en memoria con dos).
 */
@Service
public class ServicioDeCalificaciones {

    private static final Logger BITACORA = LoggerFactory.getLogger(ServicioDeCalificaciones.class);

    private final RepositorioDeCalificaciones repositorio;
    private final CatalogoDeProductos catalogo;
    private final ConsultaDeSanciones sanciones;
    private final TransactionOperations transaccion;
    private final Clock reloj;

    public ServicioDeCalificaciones(
            RepositorioDeCalificaciones repositorio,
            CatalogoDeProductos catalogo,
            ConsultaDeSanciones sanciones,
            TransactionOperations transaccion,
            Clock reloj) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.sanciones = sanciones;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * {@code POST /products/{productId}/rating}.
     *
     * <p>El catalogo y las sanciones se preguntan antes de abrir la
     * transaccion, que solo envuelve la insercion y el resumen: esperar a otro
     * host con una conexion del pool tomada es la forma de que una dependencia
     * lenta deje sin base al servicio entero.
     *
     * @param autorId el {@code uid} del token, nunca un campo del cuerpo
     * @return la calificacion registrada y el resumen ya actualizado
     * @throws IllegalArgumentException                  estrellas fuera de 1..5 (400)
     * @throws ProductoInexistente                       el producto no existe (404)
     * @throws HiloDeComentarios.PublicacionRechazada    autor sancionado (403)
     * @throws YaCalificado                              ya habia calificado (409)
     */
    public Calificado calificar(String productoId, String autorId, Integer estrellas) {
        int valor = Calificacion.exigirEstrellas(estrellas);
        catalogo.exigirExistente(productoId);
        if (sanciones.estadoDe(autorId) == HiloDeComentarios.EstadoDeAutor.SILENCIADO) {
            throw new HiloDeComentarios.PublicacionRechazada(
                    HiloDeComentarios.MotivoDeRechazo.AUTOR_SILENCIADO,
                    "tu cuenta tiene una sancion activa y no puede calificar productos");
        }
        Calificacion nueva = new Calificacion(
                UUID.randomUUID().toString(), productoId, autorId, valor, Instant.now(reloj));
        Calificado calificado = transaccion.execute(estado -> {
            if (!insertar(nueva)) {
                throw new YaCalificado(productoId);
            }
            return new Calificado(nueva, resumen(productoId));
        });
        BITACORA.info("Calificacion registrada: producto={} autor={} estrellas={}",
                productoId, autorId, valor);
        return calificado;
    }

    /**
     * La calificacion que trae un comentario, por compatibilidad con los
     * clientes anteriores a B3 (contrato 1.4.0: «`estrellas` en el cuerpo del
     * comentario sigue aceptandose»).
     *
     * <p>Corre dentro de la transaccion de la publicacion. Quien llama ya
     * comprobo el producto y la sancion, asi que aqui solo se inserta.
     *
     * @return {@code true} si quedo registrada; {@code false} si el jugador ya
     *         tenia una (el comentario entra igual, con
     *         {@code calificacionDescartada})
     */
    @Transactional
    public boolean registrarDesdeComentario(String productoId, String autorId, int estrellas) {
        Calificacion nueva = new Calificacion(
                UUID.randomUUID().toString(), productoId, autorId,
                Calificacion.exigirEstrellas(estrellas), Instant.now(reloj));
        return insertar(nueva);
    }

    /**
     * {@code GET /products/{productId}/rating}: publico.
     *
     * <p>Un producto que el catalogo no tiene responde 404. Si el catalogo no
     * contesta, se sirve lo que hay en la tabla en vez de fallar: es una
     * lectura, y la ficha puede ensenar sus estrellas aunque el host de
     * contenido este caido (HU-DIS-003). Lo que no se hace nunca es escribir
     * con esa duda.
     *
     * @throws ProductoInexistente si el catalogo dice que no existe
     */
    @Transactional(readOnly = true)
    public ResumenDeCalificaciones resumenPublico(String productoId) {
        Existencia existencia = catalogo.existencia(productoId);
        if (existencia == Existencia.NO_EXISTE) {
            throw new ProductoInexistente(productoId);
        }
        if (existencia == Existencia.DESCONOCIDA) {
            BITACORA.warn("Resumen de {} servido sin confirmar el producto: el catalogo no respondio",
                    productoId);
        }
        return resumen(productoId);
    }

    /**
     * El resumen, sin preguntar al catalogo. Para el hilo, que por contrato no
     * depende del catalogo, y para la respuesta de calificar, que ya lo
     * comprobo.
     */
    @Transactional(readOnly = true)
    public ResumenDeCalificaciones resumen(String productoId) {
        Map<Integer, Long> conteo = new HashMap<>();
        for (RepositorioDeCalificaciones.ConteoPorEstrellas fila : repositorio.contarPorEstrellas(productoId)) {
            conteo.merge(fila.getEstrellas(), fila.getCantidad(), Long::sum);
        }
        return ResumenDeCalificaciones.de(productoId, conteo);
    }

    /** {@code GET /products/{productId}/rating/mia}: la del propio jugador, si existe. */
    @Transactional(readOnly = true)
    public Optional<Calificacion> de(String productoId, String autorId) {
        return repositorio.findByProductoIdAndAutorId(productoId, autorId)
                .map(RegistroDeCalificacion::aDominio);
    }

    /**
     * Las estrellas de cada autor sobre un producto, para pintarlas junto a
     * sus comentarios. Una consulta para toda una pagina del hilo.
     *
     * @return autor -> estrellas; los que no calificaron no aparecen
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> estrellasDe(String productoId, Collection<String> autores) {
        if (autores.isEmpty()) {
            return Map.of();
        }
        return repositorio.findByProductoIdAndAutorIdIn(productoId, autores).stream()
                .collect(Collectors.toUnmodifiableMap(
                        RegistroDeCalificacion::getAutorId, RegistroDeCalificacion::getEstrellas));
    }

    private boolean insertar(Calificacion calificacion) {
        return repositorio.insertarSiNoExiste(
                calificacion.id(), calificacion.productoId(), calificacion.autorId(),
                calificacion.estrellas(), calificacion.creadaEn()) == 1;
    }

    /** Lo que devuelve calificar: la calificacion y el resumen que ya la cuenta. */
    public record Calificado(Calificacion calificacion, ResumenDeCalificaciones resumen) {
    }
}
