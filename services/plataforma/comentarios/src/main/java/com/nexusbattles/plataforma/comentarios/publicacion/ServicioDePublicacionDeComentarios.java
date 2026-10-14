package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.DeteccionAutomatica;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.ResumenDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.SolicitudDePublicacion;
import com.nexusbattles.plataforma.comentarios.calificacion.ServicioDeCalificaciones;
import com.nexusbattles.plataforma.comentarios.catalogo.CatalogoDeProductos;
import com.nexusbattles.plataforma.comentarios.imagenes.ServicioDeImagenes;
import com.nexusbattles.plataforma.comentarios.moderacion.DeteccionRepository;
import com.nexusbattles.plataforma.comentarios.moderacion.RegistroDeDeteccion;
import com.nexusbattles.plataforma.comentarios.publicacion.FiltroDeContenido.VeredictoDelFiltro;

/**
 * Publica, retira y lee los comentarios de un producto — HU-COM-001..004,
 * reescrito en B3 (contrato 1.5.0).
 *
 * <h2>Publicar, en este orden</h2>
 *
 * <ol>
 *   <li>La solicitud se valida en su forma ({@link SolicitudDePublicacion}):
 *       texto, estrellas, identificadores de imagen. 400 sin llamar a nadie.</li>
 *   <li>Las imagenes tienen que ser del autor y estar sin usar: una consulta
 *       local, tambien antes de llamar a nadie.</li>
 *   <li>El producto tiene que existir en el catalogo (404; 503 si el catalogo
 *       no contesta: no se acepta a ciegas).</li>
 *   <li>La sancion del autor (403 si esta silenciado; 503 si sanciones no
 *       contesta) y, solo si puede publicar, el filtro de la lista negra
 *       (EN_REVISION si lo senala o si no contesta). Lo decide
 *       {@link HiloDeComentarios#publicar}.</li>
 *   <li>Se guarda el comentario, se le asocian sus imagenes, si traia
 *       estrellas se registra la calificacion y, si el filtro lo retuvo, por
 *       que (HU-COM-007 CA-01) — todo en la misma transaccion.</li>
 * </ol>
 *
 * <p>El comentario ya no guarda estrellas. Si traia y el jugador aun no habia
 * calificado, pasan a ser su calificacion; si ya habia calificado, el
 * comentario entra igual y la respuesta lo dice ({@code
 * calificacionDescartada}, D-07). Las estrellas que se ensenan junto a cada
 * comentario son las de la calificacion de su autor.
 *
 * <h2>Leer, sin traer el hilo entero</h2>
 *
 * <p>Hasta B3 cada lectura y cada publicacion cargaban en memoria todos los
 * comentarios del producto. Ahora el hilo se pagina en la base y una pagina
 * cuesta un numero fijo de consultas, crezca lo que crezca el producto: la
 * pagina y su total, las imagenes de la pagina (en lote), las estrellas de sus
 * autores (una consulta) y el resumen de calificaciones (una agregacion).
 */
@Service
public class ServicioDePublicacionDeComentarios {

    private static final Logger BITACORA = LoggerFactory.getLogger(ServicioDePublicacionDeComentarios.class);

    /** Contrato 1.4.0: 16 por pagina por omision («como la vitrina»), 50 como mucho. */
    public static final int TAMANO_POR_OMISION = 16;
    public static final int TAMANO_MAXIMO = 50;

    /**
     * Del mas reciente al mas antiguo, y a igual fecha por id: sin el segundo
     * criterio dos comentarios del mismo instante podrian salir en las dos
     * paginas o en ninguna.
     */
    private static final Sort ORDEN_DEL_HILO =
            Sort.by(Sort.Order.desc("fechaPublicacion"), Sort.Order.desc("id"));

    private final ComentarioRepository repositorio;
    private final FiltroDeContenido filtro;
    private final ConsultaDeSanciones sanciones;
    private final CatalogoDeProductos catalogo;
    private final ServicioDeCalificaciones calificaciones;
    private final ServicioDeImagenes imagenes;
    private final DeteccionRepository detecciones;
    private final TransactionOperations transaccion;
    private final Clock reloj;

    public ServicioDePublicacionDeComentarios(
            ComentarioRepository repositorio,
            FiltroDeContenido filtro,
            ConsultaDeSanciones sanciones,
            CatalogoDeProductos catalogo,
            ServicioDeCalificaciones calificaciones,
            ServicioDeImagenes imagenes,
            DeteccionRepository detecciones,
            TransactionOperations transaccion,
            Clock reloj) {
        this.repositorio = repositorio;
        this.filtro = filtro;
        this.sanciones = sanciones;
        this.catalogo = catalogo;
        this.calificaciones = calificaciones;
        this.imagenes = imagenes;
        this.detecciones = detecciones;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * Publica un comentario sobre un producto.
     *
     * <p>Las preguntas a otros servicios (catalogo, sanciones, lista negra) se
     * hacen ANTES de abrir la transaccion, y solo las escrituras van dentro. Al
     * reves, cada publicacion retendria una conexion del pool mientras espera a
     * otro host —hasta varios segundos con los tiempos de los clientes—, y
     * bastarian unas cuantas publicaciones con una dependencia lenta para dejar
     * sin conexiones al resto del servicio, lecturas del hilo incluidas.
     *
     * @return el comentario tal como quedo guardado —en revision si el filtro
     *     lo senalo—, las estrellas de su autor sobre el producto y si las que
     *     traia se descartaron
     * @throws HiloDeComentarios.PublicacionRechazada si el autor esta silenciado
     * @throws HiloDeComentarios.ImagenesNoValidas    si alguna imagen no se puede adjuntar
     */
    public Publicado publicar(
            String productoId,
            String autorId,
            String apodoAutor,
            String texto,
            List<String> imagenesPedidas,
            Integer estrellas) {

        SolicitudDePublicacion solicitud = new SolicitudDePublicacion(
                UUID.randomUUID().toString(), autorId, apodoAutor, texto,
                imagenesPedidas, estrellas, Instant.now(reloj));
        imagenes.exigirDisponibles(solicitud.imagenes(), autorId);
        catalogo.exigirExistente(productoId);

        // El hilo sigue decidiendo cuando se consulta el filtro (despues de la
        // sancion); aqui solo se recuerda el veredicto para guardar su deteccion.
        AtomicReference<VeredictoDelFiltro> veredicto = new AtomicReference<>();
        Comentario comentario = HiloDeComentarios.publicar(
                productoId, solicitud, sanciones.estadoDe(autorId), () -> {
                    VeredictoDelFiltro dado = filtro.verificar(texto);
                    veredicto.set(dado);
                    return dado.resultado();
                });
        DeteccionAutomatica deteccion = veredicto.get() == null ? null : veredicto.get().deteccion();

        return transaccion.execute(estado -> {
            repositorio.saveAndFlush(RegistroDeComentario.desde(comentario));
            if (deteccion != null) {
                detecciones.save(new RegistroDeDeteccion(comentario.id(), deteccion));
            }
            imagenes.asociar(solicitud.imagenes(), autorId, comentario.id());

            boolean descartada = estrellas != null
                    && !calificaciones.registrarDesdeComentario(productoId, autorId, estrellas);
            Integer estrellasDelAutor = calificaciones.estrellasDe(productoId, Set.of(autorId)).get(autorId);
            return new Publicado(comentario, estrellasDelAutor, descartada);
        });
    }

    /**
     * Lo que quedo publicado, las estrellas de la calificacion de su autor
     * (nulas si no ha calificado) y si las que traia el comentario se
     * descartaron por ser la segunda calificacion (RF-COM-002, D-07).
     */
    public record Publicado(Comentario comentario, Integer estrellas, boolean calificacionDescartada) {
    }

    /**
     * Retira un comentario propio — HU-COM-004.
     *
     * <p>El autor es el {@code uid} del token, nunca el cuerpo (CA-02). Se
     * carga ese comentario y ninguno mas. La calificacion del autor no se toca
     * (7.1). Se deja asiento en la bitacora (JSON a stdout, regla 6).
     *
     * @return el comentario retirado
     * @throws HiloDeComentarios.ComentarioNoEncontrado si no esta en el hilo del producto
     * @throws HiloDeComentarios.ComentarioAjeno        si es de otro jugador
     */
    @Transactional
    public Comentario eliminar(String productoId, String comentarioId, String autorId) {
        Comentario actual = repositorio.findById(comentarioId)
                .filter(registro -> registro.getProductoId().equals(productoId))
                .map(RegistroDeComentario::aDominio)
                .orElseThrow(() -> new HiloDeComentarios.ComentarioNoEncontrado(comentarioId));

        Comentario retirado = HiloDeComentarios.retirar(actual, autorId);
        if (retirado != actual) {
            repositorio.save(RegistroDeComentario.desde(retirado));
            BITACORA.info("Comentario retirado por su autor: producto={} comentario={} autor={}",
                    productoId, comentarioId, autorId);
        }
        return retirado;
    }

    /**
     * Una pagina del hilo de un producto tal como lo ven los jugadores.
     *
     * <p>Solo los PUBLICADOS, del mas reciente al mas antiguo. Un producto sin
     * comentarios, o una pagina mas alla del final, es una lista vacia y no un
     * error. No pregunta al catalogo: leer el hilo no depende de otro servicio
     * (contrato 1.4.0), y el promedio sale de la tabla de calificaciones.
     *
     * @param pagina desde 0
     * @param tamano de 1 a 50; lo que pase de 50 se recorta: el cliente no
     *               decide cuanto carga el servidor
     * @throws IllegalArgumentException si la pagina es negativa o el tamano menor que 1
     */
    @Transactional(readOnly = true)
    public HiloConsultado consultarHilo(String productoId, int pagina, int tamano) {
        if (pagina < 0) {
            throw new IllegalArgumentException("la pagina empieza en 0, llego " + pagina);
        }
        if (tamano < 1) {
            throw new IllegalArgumentException("el tamano de pagina es al menos 1, llego " + tamano);
        }
        int tamanoEfectivo = Math.min(tamano, TAMANO_MAXIMO);

        Page<RegistroDeComentario> leida = repositorio.findByProductoIdAndEstado(
                productoId, Comentario.Estado.PUBLICADO, PageRequest.of(pagina, tamanoEfectivo, ORDEN_DEL_HILO));
        List<Comentario> comentarios = leida.getContent().stream()
                .map(RegistroDeComentario::aDominio)
                .toList();

        Set<String> autores = new LinkedHashSet<>();
        comentarios.forEach(comentario -> autores.add(comentario.autorId()));

        return new HiloConsultado(
                productoId,
                comentarios,
                calificaciones.estrellasDe(productoId, autores),
                pagina,
                tamanoEfectivo,
                leida.getTotalElements(),
                leida.getTotalPages(),
                calificaciones.resumen(productoId));
    }

    /**
     * Resultado de la consulta del hilo.
     *
     * @param productoId        producto consultado
     * @param comentarios       los publicados de esta pagina, del mas reciente al mas antiguo
     * @param estrellasPorAutor la calificacion de cada autor de la pagina que califico
     * @param pagina            pagina servida (desde 0)
     * @param tamano            tamano efectivo de pagina
     * @param total             comentarios publicados del producto, en todas las paginas
     * @param totalPaginas      cuantas paginas hay con ese tamano
     * @param resumen           las calificaciones del producto; de ahi salen el
     *                          promedio y su total, que la ficha pinta como
     *                          «sin calificaciones» si no hay ninguna, nunca como cero
     */
    public record HiloConsultado(
            String productoId,
            List<Comentario> comentarios,
            Map<String, Integer> estrellasPorAutor,
            int pagina,
            int tamano,
            long total,
            int totalPaginas,
            ResumenDeCalificaciones resumen) {
    }
}
