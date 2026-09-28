package nexus.combate.reglas;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Un efecto por crear: su valor todavia es una tirada. Al aplicarse se tira y
 * queda un {@link EfectoActivo} con un numero.
 *
 * @param codigo codigo estable del efecto
 * @param nombre como lo nombra el documento
 * @param tipo   tipo de efecto
 * @param valor  tirada del valor (ya con el multiplicador de nivel si toca)
 * @param turnos turnos propios del portador que dura
 */
public record PlantillaDeEfecto(String codigo, String nombre, TipoDeEfecto tipo, Tirada valor, int turnos) {

    public PlantillaDeEfecto {
        Objects.requireNonNull(codigo);
        Objects.requireNonNull(tipo);
        valor = valor == null ? Tirada.NINGUNA : valor;
    }

    /** La misma plantilla con su valor multiplicado por el nivel. */
    public PlantillaDeEfecto porNivel(int nivel) {
        return new PlantillaDeEfecto(codigo, nombre, tipo, valor.porNivel(nivel), turnos);
    }

    /** La misma plantilla con otro valor fijo (el sangrado doble de la Mancuerna yugular). */
    public PlantillaDeEfecto conValor(Tirada otro) {
        return new PlantillaDeEfecto(codigo, nombre, tipo, otro, turnos);
    }

    public EfectoActivo crear(RandomGenerator azar, String origen) {
        return new EfectoActivo(codigo, nombre, tipo, valor.tirar(azar), turnos, origen);
    }
}
