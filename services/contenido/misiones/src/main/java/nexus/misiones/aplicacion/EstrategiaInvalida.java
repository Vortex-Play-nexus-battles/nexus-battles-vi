package nexus.misiones.aplicacion;

import java.util.List;

/**
 * Heroes rechazo la estrategia para el prototipo y el nivel del heroe (422).
 * El motivo es el de heroes, «apto para el jugador» (heroes.yaml).
 */
public class EstrategiaInvalida extends RuntimeException {

    private final List<String> habilidadesValidas;

    public EstrategiaInvalida(String motivo, List<String> habilidadesValidas) {
        super(motivo == null || motivo.isBlank() ? "Esta estrategia no vale para tu héroe." : motivo);
        this.habilidadesValidas = habilidadesValidas == null ? List.of() : List.copyOf(habilidadesValidas);
    }

    public List<String> habilidadesValidas() {
        return habilidadesValidas;
    }
}
