package nexus.inventario.aplicacion;

import java.util.Map;
import nexus.inventario.dominio.ModificadorEstadisticas;

/**
 * Tabla de efectos de equipamiento sobre estadisticas del portador, derivada
 * de RN-EQP-008 (armas), RN-EQP-009 (armaduras) y RN-EQP-010 (items) —
 * REQUERIMIENTOS_EMPRESA_A.docx, que a su vez cita las Tablas 8 a 19 del
 * Proyecto Integrador II, §6.1.2, pp.28-31.
 *
 * <p>Cada entrada de RN-EQP-008/009/010 se clasifico a mano en dos grupos:
 *
 * <p><b>Incluidas aqui</b> — el efecto es un cambio permanente sobre las
 * estadisticas del propio portador mientras el objeto esta equipado.
 *
 * <p><b>Excluidas</b> (comentadas abajo, NO estan en el mapa) — por dos
 * razones, documentadas en cada caso:
 * <ul>
 *   <li><b>Afecta al oponente</b>: no es una estadistica del heroe que lo
 *       porta, es una regla de combate.</li>
 *   <li><b>Condicionado a turnos de combate</b> ("por dos turnos", "cada dos
 *       turnos"): solo evaluable durante la resolucion de un combate.</li>
 * </ul>
 * Las exclusiones no se pierden: quedan como comentario junto a su motivo,
 * listas para que motor-combate las retome cuando corresponda. Este
 * catalogo NUNCA debe crecer "adivinando" una representacion para una
 * exclusion; si Cesar confirma que alguna debe tratarse distinto, se mueve
 * explicitamente de la lista de exclusiones al mapa.
 */
public final class CatalogoEfectosEquipamiento {

    private static final Map<String, ModificadorEstadisticas> EFECTOS = Map.ofEntries(

            // ===== RN-EQP-008: armas =====
            Map.entry("Espada de una mano", mod(0, 0, 1, 0, 0)),      // Guerrero Tanque: +1 ataque (+1% crítico excluido, no es estadística de Estadisticas)
            Map.entry("Escudo de dragón", mod(0, 1, 0, 0, 0)),        // Guerrero Tanque: +1 defensa
            Map.entry("Espada de dos manos", mod(0, 0, 1, 0, 0)),     // Guerrero Armas: +1 ataque (+3% crítico excluido)
            Map.entry("Piedra de afilar", mod(0, 0, 0, 2, 0)),        // Guerrero Armas: +2 daño
            Map.entry("Orbe de manos ardientes", mod(0, 0, 0, 1, 0)), // Mago Fuego: +1 daño (+3% crítico excluido)
            Map.entry("Fuego fatuo", mod(0, 0, 1, 0, 0)),             // Mago Fuego: +1 ataque
            // "Báculo de Permafrost": −1 daño y −2% crítico DEL OPONENTE -> excluido, afecta al oponente
            Map.entry("Venas heladas", mod(0, 0, 0, 1, 0)),           // Mago Hielo: +1 daño
            // "Daga purulenta": +1 daño POR DOS TURNOS -> excluido, condicionado a turnos
            // "Visión borrosa": −1 ataque DEL OPONENTE -> excluido, afecta al oponente
            Map.entry("Machete vendito", mod(0, 0, 0, 2, 0)),         // Pícaro Machete: +2 daño (+2% crítico excluido)
            // "Cierra sangrienta": +2 daño POR DOS TURNOS -> excluido, condicionado a turnos
            Map.entry("Raíz china", mod(0, 0, 0, 0, 2)),              // Chamán: +(2d4) sanación -> 2 dados extra
            // "Yerbabuena": +2 sanación POR DOS TURNOS -> excluido, condicionado a turnos
            Map.entry("Kit de urgencias", mod(0, 0, 0, 0, 2)),        // Médico: +(2d6) sanación -> 2 dados extra
            Map.entry("Reanimador", mod(0, 0, 0, 0, 4)),              // Médico: +(4d6) sanación -> 4 dados extra

            // ===== RN-EQP-009: armaduras (todas permanentes, sin exclusiones) =====
            Map.entry("Defensa del enfurecido", mod(2, 2, 0, 0, 0)),          // Guerrero Tanque (pecho)
            Map.entry("Magma Ardiente", mod(1, 2, 0, 0, 0)),                  // Guerrero Tanque (casco)
            Map.entry("Puño lúcido", mod(1, 2, 0, 0, 0)),                     // Guerrero Armas (guantes)
            Map.entry("Puños en llamas", mod(1, 1, 0, 0, 0)),                 // Guerrero Armas (brazaletes)
            Map.entry("Túnica arcana", mod(2, 1, 0, 0, 0)),                   // Mago Fuego (pecho)
            Map.entry("Caída de fuego", mod(1, 1, 0, 0, 0)),                  // Mago Fuego (pantalón)
            Map.entry("Corona de hielo", mod(1, 1, 0, 0, 0)),                 // Mago Hielo (casco)
            Map.entry("Ventisca", mod(2, 1, 0, 0, 0)),                       // Mago Hielo (pecho)
            Map.entry("Mano del desterrado", mod(1, 2, 0, 0, 0)),             // Pícaro Veneno (guantes)
            Map.entry("Atadura carmesí", mod(2, 1, 0, 0, 0)),                 // Pícaro Veneno (pecho)
            Map.entry("Pie de atleta", mod(1, 2, 0, 0, 0)),                   // Pícaro Machete (zapatos)
            Map.entry("Sangre cruel", mod(1, 1, 0, 0, 0)),                    // Pícaro Machete (brazaletes)
            Map.entry("Piel de Caminante del Bosque", mod(2, 1, 0, 0, 0)),    // Chamán (pecho)
            Map.entry("Casco de Ecos Ancestrales", mod(2, 1, 0, 0, 0)),       // Chamán (casco)
            Map.entry("Bata de Cirujano", mod(2, 1, 0, 0, 0)),                // Médico (pecho)
            Map.entry("Pantalón de Expedición Médica", mod(2, 1, 0, 0, 0)),   // Médico (pantalón)

            // ===== RN-EQP-010: ítems (solo los sin condición de turno/oponente) =====
            Map.entry("Anillo para Piro-explosión", mod(0, 0, 0, 3, 0)),  // Mago Fuego: +3 daño, sin condición
            Map.entry("Libro de la ventisca helada", mod(0, 0, 0, 2, 0)) // Mago Hielo: +2 daño, sin condición
            // "Pinchos de escudo" (Guerrero Tanque): condicionado a comparar ataque del oponente -> excluido
            // "Empuñadura de Furia" (Guerrero Armas): +1 daño POR DOS TURNOS -> excluido, condicionado a turnos
            // "Veneno lacerante" (Pícaro Veneno): −1 poder DEL OPONENTE, cada dos turnos -> excluido, doble motivo
            // "Mancuerna yugular" (Pícaro Machete): multiplica un efecto EN EL OPONENTE -> excluido, afecta al oponente
            // "Pluma sanadora" (Chamán): multiplica la sanación x2 -> excluido a proposito, ver nota abajo
            // "Benditas" (Médico): sana POR TRES TURNOS -> excluido, condicionado a turnos
    );

    /*
     * Nota sobre "Pluma sanadora": a diferencia de los demas items excluidos,
     * no tiene condicion de turnos ni afecta al oponente — multiplica por 2
     * la sanacion propia. Se excluyo de todas formas porque ModificadorEstadisticas
     * solo modela deltas aditivos, no multiplicadores, y forzarlo como delta
     * seria un dato incorrecto. Si se confirma que debe incluirse, hace falta
     * antes extender ModificadorEstadisticas con un factor multiplicativo.
     */

    private CatalogoEfectosEquipamiento() {
    }

    public static ModificadorEstadisticas efectoDe(String nombreObjeto) {
        return EFECTOS.getOrDefault(nombreObjeto, ModificadorEstadisticas.NULO);
    }

    private static ModificadorEstadisticas mod(
            int deltaVida, int deltaDefensa, int deltaAtaqueBase, int deltaDanoBase, int dadosSanarExtra) {
        return new ModificadorEstadisticas(deltaVida, deltaDefensa, deltaAtaqueBase, deltaDanoBase, dadosSanarExtra);
    }
}
