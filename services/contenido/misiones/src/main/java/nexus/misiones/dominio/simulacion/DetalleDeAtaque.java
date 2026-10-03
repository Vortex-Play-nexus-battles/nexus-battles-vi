package nexus.misiones.dominio.simulacion;

/**
 * El golpe de una accion de ataque (seccion 6.1.4): la tirada contra la defensa
 * y, si la supera, el efecto sorteado en la tabla de 8.000 filas.
 *
 * @param categoria CAUSAR_DANO, CAUSAR_DANO_CRITICO, EVADIR_EL_GOLPE, RESISTIR_EL_GOLPE, ESCAPAR_AL_GOLPE o SIN_EFECTO
 */
public record DetalleDeAtaque(int ataqueResuelto, int defensaObjetivo, boolean acierta, String categoria,
                              Integer indiceTabla, Integer porcentajeDano, int danoBase, int danoAplicado) {

    public static final String CRITICO = "CAUSAR_DANO_CRITICO";

    public boolean critico() {
        return CRITICO.equals(categoria);
    }
}
