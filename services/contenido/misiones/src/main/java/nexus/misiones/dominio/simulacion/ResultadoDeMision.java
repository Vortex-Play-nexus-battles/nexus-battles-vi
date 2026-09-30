package nexus.misiones.dominio.simulacion;

import java.util.List;
import nexus.misiones.dominio.Epica;

/**
 * Lo que paso en la mision, tal como lo cuenta el reporte (seccion 7.8.8):
 * resultado, estadisticas de combate, enemigos derrotados, Master y jefe. La
 * experiencia es la de los enemigos derrotados (6.1.1); las recompensas se
 * calculan despues, a partir de esto.
 *
 * @param exito                          todos los rivales cayeron (7.8.7, Completada)
 * @param encuentrosCompletados          rivales derrotados, Master y jefe incluidos
 * @param encuentrosRegularesCompletos   cayeron todos los enemigos regulares
 * @param vidaMinimaPorcentaje           lo mas bajo que llego la vida del heroe, 0-100
 * @param experiencia                    suma de 10 x 1,2^(1d8) por enemigo derrotado
 * @param dados                          los 1d8 que tiro el servidor, en orden (auditoria)
 */
public record ResultadoDeMision(
        boolean exito,
        boolean jefeDerrotado,
        int encuentrosCompletados,
        boolean encuentrosRegularesCompletos,
        int danoInfligido,
        int danoRecibido,
        int turnos,
        int criticos,
        List<UsoDeHabilidad> habilidadesMasUsadas,
        List<EnemigoDerrotado> enemigosDerrotados,
        List<MasterEnfrentado> masters,
        int vidaMinimaPorcentaje,
        double experiencia,
        List<Integer> dados) {

    public ResultadoDeMision {
        habilidadesMasUsadas = List.copyOf(habilidadesMasUsadas);
        enemigosDerrotados = List.copyOf(enemigosDerrotados);
        masters = List.copyOf(masters);
        dados = List.copyOf(dados);
    }

    public boolean derrotoAlgunMaster() {
        return masters.stream().anyMatch(MasterEnfrentado::derrotado);
    }

    public boolean aparecioAlgunMaster() {
        return !masters.isEmpty();
    }

    public record UsoDeHabilidad(String nombre, int usos) {
    }

    public record EnemigoDerrotado(String nombre, int cantidad) {
    }

    /** Un Master que aparecio; {@code derrotado} dice si cayo. */
    public record MasterEnfrentado(String nombre, Epica epica, boolean derrotado) {
    }
}
