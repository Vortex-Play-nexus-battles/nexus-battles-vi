package com.nexusbattles.plataforma.moderacionsanciones.listanegra;

/**
 * Por que un termino esta en la lista negra: las categorias del 7.1.1 del
 * documento del curso («palabras ofensivas o nombres respetados o reconocidos
 * politicos, celebridades, dirigentes, o marcas registradas entre otros») y
 * del 7.3.2 (lista negra actualizable). {@code OTRO} es el «entre otros» y la
 * categoria de las filas anteriores a la 2.0.0, que no tenian ninguna.
 */
public enum CategoriaDeTermino {
    OFENSIVO,
    MARCA,
    CELEBRIDAD,
    POLITICO,
    DIRIGENTE,
    OTRO
}
