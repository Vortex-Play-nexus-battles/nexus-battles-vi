package com.nexusbattles.plataforma.comentarios.publicacion;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
     * @return el comentario tal como quedo guardado: sin estrellas si el autor
     *     ya habia calificado, y en revision si el filtro lo senalo
     * @throws HiloDeComentarios.PublicacionRechazada si el autor esta
     *     silenciado o alguna imagen viene en formato no admitido
     */
    @Transactional
    public Comentario publicar(
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
        return comentario;
    }
}
