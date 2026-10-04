package nexus.misiones.dominio.simulacion;

import java.util.List;

/**
 * Lo que sale de simular una mision: el resultado que cuenta el reporte
 * (7.8.8) y, aparte, los turnos que lo produjeron. El resultado se guarda en la
 * ejecucion; los eventos, en su propia coleccion.
 */
public record Simulacion(ResultadoDeMision resultado, List<EventoDeCombate> eventos) {

    public Simulacion {
        eventos = List.copyOf(eventos);
    }
}
