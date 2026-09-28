package nexus.inventario.aplicacion;

import nexus.inventario.dominio.TablaDeNiveles;

/**
 * De donde sale la tabla de niveles: el servicio de heroes, que es el dueno de
 * la regla (1.6.0, B9).
 */
public interface FuenteDeTablaDeNiveles {

    /** @throws ProgresionNoDisponibleException si heroes no responde o responde algo que no se entiende */
    TablaDeNiveles tabla();
}
