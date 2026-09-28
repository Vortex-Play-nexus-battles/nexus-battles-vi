package nexus.misiones.aplicacion;

import java.util.List;

/**
 * El heroe no cumple lo que la mision exige de el (422), con TODOS los motivos
 * a la vez (HU-MIS-008: «si incumple varias condiciones, se muestran todas»).
 */
public class HeroeNoApto extends RuntimeException {

    private final List<String> motivos;

    public HeroeNoApto(List<String> motivos) {
        super(String.join(" ", motivos));
        this.motivos = List.copyOf(motivos);
    }

    public List<String> motivos() {
        return motivos;
    }
}
