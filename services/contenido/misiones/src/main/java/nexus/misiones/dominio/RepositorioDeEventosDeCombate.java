package nexus.misiones.dominio;

import java.util.List;
import java.util.UUID;
import nexus.misiones.dominio.simulacion.EventoDeCombate;

/**
 * Donde quedan los turnos de combate de cada mision simulada (HU-SIM-003). Es
 * una coleccion aparte de las ejecuciones: una mision completa son cientos de
 * turnos y la ejecucion es el documento que se lee en el tablon y el historial.
 */
public interface RepositorioDeEventosDeCombate {

    /**
     * Deja como unicos eventos de la ejecucion los dados. La simulacion se
     * repite entera si algo falla a mitad (con otra tirada de dados), asi que
     * lo que un intento anterior hubiera dejado se borra: jamas se mezclan los
     * turnos de dos intentos.
     */
    void reemplazar(UUID ejecucionId, List<EventoDeCombate> eventos);

    /** Los eventos de una ejecucion, en el orden en que ocurrieron. */
    List<EventoDeCombate> de(UUID ejecucionId);
}
