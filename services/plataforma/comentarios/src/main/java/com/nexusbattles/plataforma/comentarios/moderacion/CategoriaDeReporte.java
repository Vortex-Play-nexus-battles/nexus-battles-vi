package com.nexusbattles.plataforma.comentarios.moderacion;

/**
 * Las seis categorias de violacion de RF-COM-006, en el orden de la ficha.
 *
 * <p>No es una lista abierta ni un texto libre: la ficha las enumera una por
 * una ("spam o publicidad no solicitada, contenido ofensivo o abusivo, acoso o
 * intimidacion, informacion falsa o enganosa, contenido inapropiado y
 * violacion de derechos de autor"). Ampliarla es cambiar el requisito, no la
 * API — y por eso esta el enum y no un String: un reporte con una categoria
 * inventada no llega ni al dominio.
 *
 * <p>Sirven tambien para que el moderador vea de que se acusa al comentario
 * agrupado por motivo, sin leer seis descripciones sueltas.
 */
public enum CategoriaDeReporte {
    SPAM,
    CONTENIDO_OFENSIVO,
    ACOSO,
    INFORMACION_FALSA,
    CONTENIDO_INAPROPIADO,
    VIOLACION_DE_DERECHOS
}
