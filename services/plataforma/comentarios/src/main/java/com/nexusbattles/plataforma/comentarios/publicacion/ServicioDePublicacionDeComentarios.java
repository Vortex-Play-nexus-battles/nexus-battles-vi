package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexusbattles.plataforma.comentarios.Comentario;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;
import com.nexusbattles.plataforma.comentarios.SolicitudDePublicacion;

/**
 * Publica comentarios aplicando las reglas de HU-COM-001 sobre datos reales.
 *
 * <p>El flujo por publicacion es: cargar de la base los comentarios que el
 * producto ya tiene, reconstruir el hilo con ellos, pedir el veredicto del
 * filtro y el estado disciplinario del autor, dejar que el dominio decida, y
 * guardar lo que el dominio devuelva. Las reglas no se repiten aqui: viven en
 * {@link HiloDeComentarios} desde el PR 163 y este servicio solo las alimenta.
 *
 * <p>El filtro se consulta siempre, incluso si la publicacion va a terminar
 * rechazada por otra causa, porque el orden de los rechazos es decision del
 * dominio y adelantarse aqui seria duplicar esa logica.
 */
@Service
public class ServicioDePublicacionDeComentarios {

    private static final Logger BITACORA = LoggerFactory.getLogger(ServicioDePublicacionDeComentarios.class);


    private final ComentarioRepository repositorio;
    private final FiltroDeContenido filtro;
    private final ConsultaDeSanciones sanciones;
    private final Set<String> formatosAdmitidos;

    public ServicioDePublicacionDeComentarios(
            ComentarioRepository repositorio,
            FiltroDeContenido filtro,
            ConsultaDeSanciones sanciones,
            @Value("${comentarios.formatos-imagen}") List<String> formatosImagen) {
        this.repositorio = repositorio;
        this.filtro = filtro;
        this.sanciones = sanciones;
        this.formatosAdmitidos = Set.copyOf(formatosImagen);
    }

    /**
     * Publica un comentario sobre un producto.
     *
     * @return el comentario tal como quedo guardado —sin estrellas si el autor
     *     ya habia calificado (y se dice), en revision si el filtro lo senalo—
     * @throws HiloDeComentarios.PublicacionRechazada si el autor esta
     *     silenciado o alguna imagen viene en formato no admitido
     */
    @Transactional
    public Publicado publicar(
            String productoId,
            String autorId,
            String apodoAutor,
            String texto,
            List<String> imagenes,
            Integer estrellas) {

        List<Comentario> existentes = repositorio
                .findByProductoIdOrderByFechaPublicacionAsc(productoId)
                .stream()
                .map(RegistroDeComentario::aDominio)
                .toList();

        HiloDeComentarios hilo =
                HiloDeComentarios.reconstituir(productoId, formatosAdmitidos, existentes);

        SolicitudDePublicacion solicitud = new SolicitudDePublicacion(
                UUID.randomUUID().toString(),
                autorId,
                apodoAutor,
                texto,
                imagenes,
                estrellas,
                Instant.now());

        Comentario comentario = hilo.publicar(
                solicitud,
                sanciones.estadoDe(autorId),
                filtro.verificar(texto));

        repositorio.save(RegistroDeComentario.desde(comentario));
        return new Publicado(comentario, hilo.ultimaCalificacionDescartada());
    }

    /**
     * Lo que quedo publicado y si la calificacion que traia se descarto por
     * ser la segunda del autor sobre el producto (RF-COM-002, D-07).
     */
    public record Publicado(Comentario comentario, boolean calificacionDescartada) {
    }

    /**
     * Retira un comentario propio — HU-COM-004.
     *
     * <p>El autor es el {@code uid} del token, nunca el cuerpo (CA-02). La
     * regla de quien puede retirar que la decide {@link HiloDeComentarios#eliminar};
     * aqui se carga el hilo, se aplica y se guarda. Se deja asiento en la
     * bitacora (JSON a stdout, regla 6) con producto, comentario y autor.
     *
     * @return el comentario retirado
     * @throws HiloDeComentarios.ComentarioNoEncontrado si no esta en el hilo
     * @throws HiloDeComentarios.ComentarioAjeno        si es de otro jugador
     */
    @Transactional
    public Comentario eliminar(String productoId, String comentarioId, String autorId) {
        List<Comentario> existentes = repositorio
                .findByProductoIdOrderByFechaPublicacionAsc(productoId)
                .stream()
                .map(RegistroDeComentario::aDominio)
                .toList();
        HiloDeComentarios hilo =
                HiloDeComentarios.reconstituir(productoId, formatosAdmitidos, existentes);

        Comentario retirado = hilo.eliminar(comentarioId, autorId);
        repositorio.save(RegistroDeComentario.desde(retirado));
        BITACORA.info("Comentario retirado por su autor: producto={} comentario={} autor={}",
                productoId, comentarioId, autorId);
        return retirado;
    }

    /**
     * El hilo de un producto tal como lo ven los jugadores.
     *
     * <p>Lado proveedor de HU-INV-014 (la ficha pinta esto) y lo que hace
     * afirmable el CA-01 de HU-COM-001: un comentario «se suma al hilo» solo
     * se puede comprobar si el hilo se puede leer. Hasta #438 solo existia el
     * POST, asi que lo publicado no lo veia nadie, ni su autor tras recargar.
     *
     * <p>No repite reglas: {@link HiloDeComentarios#visibles()} decide que es
     * publico y {@link HiloDeComentarios#promedio()} que califica. Un
     * comentario retenido por el filtro no sale ni mueve el promedio.
     *
     * <p>Un producto sin comentarios devuelve un hilo vacio, no un error: no
     * tener comentarios es un estado normal de un producto.
     */
    @Transactional(readOnly = true)
    public HiloConsultado consultarHilo(String productoId) {
        List<Comentario> existentes = repositorio
                .findByProductoIdOrderByFechaPublicacionAsc(productoId)
                .stream()
                .map(RegistroDeComentario::aDominio)
                .toList();

        HiloDeComentarios hilo =
                HiloDeComentarios.reconstituir(productoId, formatosAdmitidos, existentes);

        List<Comentario> visibles = hilo.visibles();
        long calificaciones = visibles.stream()
                .filter(comentario -> comentario.calificacion().isPresent())
                .count();

        return new HiloConsultado(productoId, visibles, hilo.promedio(), (int) calificaciones);
    }

    /**
     * Resultado de la consulta del hilo.
     *
     * @param productoId           producto consultado
     * @param comentarios          solo los publicados, del mas antiguo al mas reciente
     * @param calificacionPromedio promedio de estrellas de los publicados que
     *                             calificaron; vacio si nadie lo ha hecho, que la
     *                             ficha debe pintar como «sin valoraciones» y no
     *                             como cero
     * @param totalCalificaciones  cuantos de los publicados traen estrellas
     */
    public record HiloConsultado(
            String productoId,
            List<Comentario> comentarios,
            OptionalDouble calificacionPromedio,
            int totalCalificaciones) {
    }
}
