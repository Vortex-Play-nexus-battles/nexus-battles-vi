package com.nexusbattles.plataforma.comentarios.imagenes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.SolicitudDePublicacion;
import com.nexusbattles.plataforma.comentarios.publicacion.ComentarioRepository;

/**
 * Las imagenes de los comentarios: subirlas, adjuntarlas y servirlas — 7.1 del
 * documento del curso («Los comentarios permiten incluir texto e imagenes»),
 * contrato 1.4.0, B3.
 *
 * <h2>El ciclo de vida</h2>
 *
 * <pre>
 *   POST /comentarios/imagenes     -> pendiente (del autor, sin comentario)
 *   POST .../comments {imagenes}   -> asociada a un comentario suyo, una vez
 *   24 h pendiente                 -> borrada por la limpieza
 * </pre>
 *
 * <p>Una imagen solo la adjunta su autor y solo una vez: el id de una imagen
 * ajena, o de una ya usada, es 400. Quien la ve depende del comentario: si
 * esta PUBLICADO, cualquiera; si no (pendiente, en revision, oculto,
 * eliminado), solo su autor y moderacion. Para los demas no existe (404).
 *
 * <h2>Por que en PostgreSQL</h2>
 *
 * <p>Se guarda en la propia base del servicio (bytea) y no en un volumen del
 * host: asi viaja con sus respaldos y no se pierde al recrear el contenedor o
 * mover el servicio. El crecimiento esta acotado por el tamano maximo, el
 * tope de tres por comentario y la limpieza de las pendientes.
 */
@Service
public class ServicioDeImagenes {

    private static final Logger BITACORA = LoggerFactory.getLogger(ServicioDeImagenes.class);

    /** Ruta publica relativa de una imagen; la que devuelve la subida como {@code url}. */
    public static final String RUTA_PUBLICA = "/api/v1/comentarios/imagenes/";

    private final RepositorioDeImagenes repositorio;
    private final ComentarioRepository comentarios;
    private final ExaminadorDeImagenes examinador;
    private final Clock reloj;
    private final Duration retencionDeLasPendientes;

    public ServicioDeImagenes(
            RepositorioDeImagenes repositorio,
            ComentarioRepository comentarios,
            Clock reloj,
            @Value("${comentarios.imagenes.formatos:jpg,png,webp}") List<String> formatos,
            @Value("${comentarios.imagenes.retencion-pendientes:24h}") Duration retencionDeLasPendientes) {
        this.repositorio = repositorio;
        this.comentarios = comentarios;
        this.reloj = reloj;
        this.examinador = new ExaminadorDeImagenes(formatosAdmitidos(formatos));
        this.retencionDeLasPendientes = retencionDeLasPendientes;
    }

    /**
     * Examina y guarda una imagen. El nombre original del archivo ni siquiera
     * llega aqui: el controlador solo pasa los bytes.
     *
     * @throws ArchivoAusente        sin bytes (400)
     * @throws ImagenDemasiadoGrande mas de 2 MB o de 4096x4096 (413)
     * @throws ImagenNoAdmitida      no es un JPEG, PNG o WebP valido (415)
     */
    @Transactional
    public ImagenGuardada subir(String autorId, byte[] bytes) {
        ExaminadorDeImagenes.ImagenExaminada examinada = examinador.examinar(bytes);
        String id = UUID.randomUUID().toString();
        repositorio.save(new RegistroDeImagen(
                id, autorId, examinada.tipo(), bytes, huella(bytes), Instant.now(reloj)));
        BITACORA.info("Imagen de comentario subida: id={} autor={} tipo={} tamano={} dimensiones={}x{}",
                id, autorId, examinada.tipo().tipoMime(), bytes.length, examinada.ancho(), examinada.alto());
        return new ImagenGuardada(id, examinada.tipo(), bytes.length);
    }

    /**
     * Comprueba, antes de nada mas, que las imagenes que trae un comentario
     * son del autor y siguen pendientes. Es una cortesia: el 400 sale sin
     * haber preguntado al catalogo ni a sanciones. La garantia de verdad la da
     * {@link #asociar}, dentro de la misma transaccion que guarda el comentario.
     *
     * @throws HiloDeComentarios.ImagenesNoValidas si alguna no lo es (400)
     */
    @Transactional(readOnly = true)
    public void exigirDisponibles(List<String> ids, String autorId) {
        if (!ids.isEmpty() && repositorio.contarDisponibles(ids, autorId) != ids.size()) {
            throw noDisponibles();
        }
    }

    /**
     * Asocia las imagenes al comentario recien guardado. Tiene que correr
     * dentro de la transaccion de la publicacion: si no se pueden asociar
     * todas, se deshace tambien el comentario.
     *
     * @throws HiloDeComentarios.ImagenesNoValidas si otra publicacion ya uso alguna
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void asociar(List<String> ids, String autorId, String comentarioId) {
        if (!ids.isEmpty() && repositorio.asociar(ids, autorId, comentarioId) != ids.size()) {
            throw noDisponibles();
        }
    }

    /**
     * Una imagen para quien la pide, o 404 si no existe o no la puede ver.
     *
     * @return los bytes, su tipo y si es publica (lo que decide la cache)
     * @throws ImagenNoEncontrada si no existe o no es visible para quien la pide
     */
    @Transactional(readOnly = true)
    public ImagenServida obtener(String imagenId, Solicitante solicitante) {
        if (!SolicitudDePublicacion.esIdentificador(imagenId)) {
            throw new ImagenNoEncontrada(imagenId);
        }
        RegistroDeImagen imagen = repositorio.findById(imagenId)
                .orElseThrow(() -> new ImagenNoEncontrada(imagenId));
        boolean publica = imagen.estaAsociada()
                && comentarios.estadoDe(imagen.comentarioId())
                        .filter(estado -> estado == Comentario.Estado.PUBLICADO)
                        .isPresent();
        if (!publica && !solicitante.puedeVerLaPrivadaDe(imagen.autorId())) {
            throw new ImagenNoEncontrada(imagenId);
        }
        return new ImagenServida(imagen.tipo(), imagen.datos(), publica);
    }

    /**
     * Borra las imagenes pendientes con mas antiguedad que la retencion (24 h
     * por contrato). La llama la tarea programada.
     *
     * @return cuantas se borraron
     */
    @Transactional
    public int limpiarPendientes() {
        Instant limite = Instant.now(reloj).minus(retencionDeLasPendientes);
        int borradas = repositorio.borrarPendientesAnterioresA(limite);
        if (borradas > 0) {
            BITACORA.info("Limpieza de imagenes de comentario: {} pendientes sin usar borradas (anteriores a {})",
                    borradas, limite);
        }
        return borradas;
    }

    private static HiloDeComentarios.ImagenesNoValidas noDisponibles() {
        return new HiloDeComentarios.ImagenesNoValidas(
                "alguna imagen no es tuya, ya la usa otro comentario o no existe; subela de nuevo");
    }

    /**
     * Los formatos de {@code COMENTARIOS_FORMATOS_IMAGEN} (pendiente del
     * Product Owner, issue 34), acotados a los tres del contrato. Un nombre
     * desconocido se ignora y se dice; ninguno valido es un error de arranque:
     * un servicio de comentarios que no admite ninguna imagen no esta bien
     * configurado.
     */
    static Set<TipoDeImagen> formatosAdmitidos(List<String> nombres) {
        Set<TipoDeImagen> admitidos = EnumSet.noneOf(TipoDeImagen.class);
        for (String nombre : nombres) {
            Optional<TipoDeImagen> tipo = TipoDeImagen.porNombre(nombre);
            if (tipo.isPresent()) {
                admitidos.add(tipo.get());
            } else if (nombre != null && !nombre.isBlank()) {
                BITACORA.warn("Formato de imagen desconocido en la configuracion, se ignora: {}", nombre);
            }
        }
        if (admitidos.isEmpty()) {
            throw new IllegalStateException(
                    "comentarios.imagenes.formatos no admite ningun formato conocido (jpg, png, webp)");
        }
        return admitidos;
    }

    private static String huella(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("la JVM no trae SHA-256", imposible);
        }
    }

    /** Lo que devuelve la subida. */
    public record ImagenGuardada(String id, TipoDeImagen tipo, int tamano) {

        public String url() {
            return RUTA_PUBLICA + id;
        }
    }

    /** Lo que se sirve. */
    public record ImagenServida(TipoDeImagen tipo, byte[] datos, boolean publica) {
    }
}
