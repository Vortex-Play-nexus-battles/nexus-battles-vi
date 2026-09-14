package com.nexusbattles.plataforma.salaspartidas.dominio;

import java.util.List;

/**
 * Una pagina del listado de salas — RF-JUE-002.
 *
 * <p>Los cinco campos son exactamente los del esquema {@code PaginaDeSalas} del
 * contrato OpenAPI. No se anaden comodidades como «hay siguiente» o «es la
 * ultima»: el contrato no las declara, y la interfaz puede deducirlas de lo que
 * ya viaja.
 *
 * <p>Vive en el dominio y no en la capa web porque el puerto del repositorio la
 * devuelve: paginar es responsabilidad del almacen, no del controlador.
 *
 * @param contenido      salas de esta pagina
 * @param pagina         numero de pagina, base 0
 * @param tamano         elementos por pagina; el valor de diseno es 16
 * @param totalElementos salas que cumplen el filtro, en todas las paginas
 * @param totalPaginas   paginas necesarias para recorrerlas
 */
public record PaginaDeSalas(
        List<Sala> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    /** Tamano de pagina del sistema de diseno, ficha {@code --elementos-por-pagina}. */
    public static final int TAMANO_POR_DEFECTO = 16;

    public PaginaDeSalas {
        contenido = contenido == null ? List.of() : List.copyOf(contenido);
    }
}
