package nexus.inventario.dominio;

/**
 * Modificador que un arma o armadura equipada aporta a las estadisticas base
 * del heroe (RN-EQP-008, RN-EQP-009; Tablas 8 a 19, Proyecto Integrador II
 * §6.1.2, pp.28-31).
 *
 * <p>Solo se modelan aqui los efectos que son un cambio PERMANENTE sobre las
 * estadisticas del PORTADOR mientras el objeto esta equipado. Quedan fuera,
 * a proposito:
 * <ul>
 *   <li>Efectos que modifican al OPONENTE (ej. "Bastón de Permafrost: −1 daño
 *       del oponente"): son reglas de combate, no estadisticas del heroe.</li>
 *   <li>Efectos condicionados a turnos de combate (ej. "por dos turnos",
 *       "cada dos turnos"): solo tienen sentido evaluandolos durante la
 *       resolucion de un combate, no en un calculo estatico de estadisticas.</li>
 * </ul>
 * Ambos tipos de efecto excluidos quedan documentados en
 * {@link CatalogoEfectosEquipamiento} para que una futura integracion con
 * motor-combate los retome; aqui NO se aplican ni se aproximan.
 */
public record ModificadorEstadisticas(
        int deltaVida,
        int deltaDefensa,
        int deltaAtaqueBase,
        int deltaDanoBase,
        int dadosSanarExtra) {

    public static final ModificadorEstadisticas NULO =
            new ModificadorEstadisticas(0, 0, 0, 0, 0);

    public ModificadorEstadisticas {
        if (deltaVida < 0 || deltaDefensa < 0 || deltaAtaqueBase < 0
                || deltaDanoBase < 0 || dadosSanarExtra < 0) {
            throw new IllegalArgumentException(
                    "Los modificadores de equipamiento conocidos son todos no negativos; "
                            + "un valor negativo indica un dato mal transcrito de RN-EQP");
        }
    }

    public ModificadorEstadisticas combinar(ModificadorEstadisticas otro) {
        return new ModificadorEstadisticas(
                deltaVida + otro.deltaVida,
                deltaDefensa + otro.deltaDefensa,
                deltaAtaqueBase + otro.deltaAtaqueBase,
                deltaDanoBase + otro.deltaDanoBase,
                dadosSanarExtra + otro.dadosSanarExtra);
    }
}
