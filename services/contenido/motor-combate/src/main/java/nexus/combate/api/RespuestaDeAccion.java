package nexus.combate.api;

import nexus.combate.CategoriaEfecto;
import nexus.combate.reglas.DetalleDeAtaque;
import nexus.combate.reglas.Evento;
import nexus.combate.reglas.ResultadoDeAccion;
import nexus.combate.reglas.TipoDeAccion;

import java.util.List;

/**
 * Espejo del esquema {@code ResultadoDeAccion} de {@code motor-combate.yaml} 1.2.0.
 *
 * @param accion          la pedida; con {@code DECISION_DE_LA_MAQUINA}, la que decidio el motor
 * @param accionEjecutada la que se jugo: distinta cuando falto poder (§6.1.1)
 * @param enValorBase     falto poder y el turno se jugo con el valor base
 * @param combatientes    el estado nuevo de todos, con sus acciones y recargas
 */
public record RespuestaDeAccion(String accion, String accionEjecutada, boolean enValorBase, String ejecutor,
                                String objetivo, TipoDeAccion tipo, boolean esEpica, boolean potenciada,
                                Golpe ataque, List<Afectado> afectados, List<Evento> eventos,
                                List<EstadoDeCombatiente> combatientes) {

    static RespuestaDeAccion de(ResultadoDeAccion r) {
        return new RespuestaDeAccion(r.accion(), r.accionEjecutada(), r.enValorBase(), r.ejecutor(),
                r.objetivo(), r.tipo(), r.esEpica(), r.potenciada(), Golpe.de(r.ataque()),
                Afectado.de(r.afectados()), r.eventos(),
                TraductorDeCombate.deDominio(r.combatientes(), r.acciones(), r.recargas()));
    }

    /** Esquema {@code DetalleDeAtaque}: la tirada contra la defensa y la fila de la tabla. */
    public record Golpe(int ataqueResuelto, int defensaObjetivo, boolean acierta, CategoriaEfecto categoria,
                        Integer indiceTabla, int porcentajeDano, int danoBase, int danoAplicado) {

        static Golpe de(DetalleDeAtaque d) {
            return d == null ? null
                    : new Golpe(d.ataqueResuelto(), d.defensaObjetivo(), d.acierta(), d.categoria(),
                    d.indiceTabla(), d.porcentajeDano(), d.danoBase(), d.danoAplicado());
        }
    }

    /** Esquema {@code Afectado}: con la diferencia ya calculada, negativa si perdio vida. */
    public record Afectado(String id, int vidaAntes, int vidaDespues, int diferencia) {

        static List<Afectado> de(List<nexus.combate.reglas.Afectado> afectados) {
            return afectados.stream()
                    .map(a -> new Afectado(a.id(), a.vidaAntes(), a.vidaDespues(), a.diferencia()))
                    .toList();
        }
    }
}
