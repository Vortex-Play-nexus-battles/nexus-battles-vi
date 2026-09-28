package nexus.semilla;

import java.util.List;

/**
 * Lo que hizo una corrida de la semilla, para la bitacora y las pruebas.
 *
 * @param version     version del contenido de la semilla que se aplico
 * @param insertados  productos que faltaban y se crearon
 * @param actualizados productos sembrados con una version anterior que nadie
 *                    edito y se pusieron al dia (B4)
 * @param existentes  productos que ya estaban al dia, o que no son de la
 *                    semilla, y no se tocaron
 * @param respetados  productos de una version anterior que un administrador
 *                    edito: se dejan como estan y se avisa (B4)
 * @param rechazados  entradas del archivo que no pasan las validaciones del alta
 */
public record ResultadoSemilla(
        boolean habilitada,
        int version,
        List<String> insertados,
        List<String> actualizados,
        List<String> existentes,
        List<String> respetados,
        List<String> rechazados) {

    public static ResultadoSemilla deshabilitada() {
        return new ResultadoSemilla(false, 0, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
