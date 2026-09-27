package com.nexusbattles.plataforma.comentarios;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lo que un jugador envia cuando quiere opinar sobre un producto. HU-COM-001.
 *
 * <p>Se separa del comentario ya guardado porque no son lo mismo. Aqui viene lo que
 * el jugador pidio, y puede que traiga estrellas aunque ya haya calificado antes.
 * Es el servicio el que decide que queda registrado al final.
 *
 * <p>Lo que se puede comprobar sin preguntar a nadie se comprueba aqui, al
 * construirla, y por eso se construye antes de llamar al catalogo, a sanciones
 * o a la lista negra: una peticion mal formada responde 400 sin haber gastado
 * tres llamadas a otros servicios.
 *
 * <ul>
 *   <li>El texto es obligatorio (RN-CMT-001).</li>
 *   <li>Las estrellas, si vienen, van de 1 a 5.</li>
 *   <li>Las imagenes son los {@code id} que devolvio {@code POST
 *       /comentarios/imagenes} (contrato 1.4.0), como mucho
 *       {@link HiloDeComentarios#MAXIMO_DE_IMAGENES}, sin repetir. Un nombre de
 *       archivo, que es lo que viajaba hasta B3, no es un id y se rechaza con
 *       400: el servidor no tiene esa imagen y fingir que la adjunta seria
 *       mentir al jugador. Que cada id sea de verdad una imagen de ese autor
 *       aun sin usar lo comprueba el servicio de imagenes contra su tabla.</li>
 * </ul>
 *
 * @param comentarioId identificador que se le asignara al comentario
 * @param autorId jugador que publica
 * @param apodoAutor apodo con el que aparecera
 * @param texto contenido escrito
 * @param imagenes identificadores de imagenes subidas, puede venir vacia
 * @param estrellas calificacion pretendida, o nula si no quiere calificar
 * @param fecha momento del envio
 */
public record SolicitudDePublicacion(
        String comentarioId,
        String autorId,
        String apodoAutor,
        String texto,
        List<String> imagenes,
        Integer estrellas,
        Instant fecha) {

    public SolicitudDePublicacion {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("el texto del comentario es obligatorio");
        }
        if (estrellas != null) {
            Calificacion.exigirEstrellas(estrellas);
        }
        imagenes = imagenes == null ? List.of() : List.copyOf(imagenes);
        exigirIdentificadoresDeImagen(imagenes);
    }

    private static void exigirIdentificadoresDeImagen(List<String> imagenes) {
        if (imagenes.size() > HiloDeComentarios.MAXIMO_DE_IMAGENES) {
            throw new HiloDeComentarios.ImagenesNoValidas(
                    "un comentario lleva como mucho " + HiloDeComentarios.MAXIMO_DE_IMAGENES
                            + " imagenes, llegaron " + imagenes.size());
        }
        Set<String> vistas = new HashSet<>();
        for (String imagen : imagenes) {
            if (!esIdentificador(imagen)) {
                throw new HiloDeComentarios.ImagenesNoValidas(
                        "cada imagen es el id que devolvio la subida de imagenes, no un nombre de archivo");
            }
            if (!vistas.add(imagen)) {
                throw new HiloDeComentarios.ImagenesNoValidas("la misma imagen viene dos veces");
            }
        }
    }

    /**
     * Si el texto es un UUID tal como lo emite el servicio (canonico y en
     * minusculas). Se compara con su propia vuelta a texto porque
     * {@link UUID#fromString} acepta formas no canonicas ({@code 1-1-1-1-1})
     * que no pueden ser un id emitido aqui, y el id se busca en la base tal
     * cual: uno en mayusculas no encontraria su imagen. Publico porque la
     * descarga de imagenes aplica el mismo criterio a su {@code imagenId}.
     */
    public static boolean esIdentificador(String valor) {
        if (valor == null || valor.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(valor).toString().equals(valor);
        } catch (IllegalArgumentException noEsUuid) {
            return false;
        }
    }
}
