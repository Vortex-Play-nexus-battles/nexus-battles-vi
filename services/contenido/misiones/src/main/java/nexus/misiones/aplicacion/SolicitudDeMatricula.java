package nexus.misiones.aplicacion;

import java.util.List;
import nexus.misiones.dominio.Escalon;

/**
 * Lo que pide el jugador al enviar un heroe. El jugador NO va aqui: sale de su
 * token.
 *
 * @param rotaciones nulas = la estrategia guardada del heroe, o ataque basico
 * @param escalon    nulo = Normal
 */
public record SolicitudDeMatricula(
        String misionId,
        String heroeId,
        List<List<String>> rotaciones,
        Escalon escalon,
        String claveIdempotencia) {
}
