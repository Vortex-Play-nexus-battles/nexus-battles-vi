package nexus.dominio;

import java.util.List;

/**
 * Los ocho prototipos con los que arranca el desarrollo. NO son un limite:
 * el cliente anuncio que se agregaran mas (acta 2026-08-13, "no son ocho").
 * Datos textuales de las Tablas 5, 6 y 7 del documento del cliente
 * (Boveda/02-producto/reglas/heroes.md y habilidades-y-mejoras.md).
 */
public final class PrototiposIniciales {

    private PrototiposIniciales() {
    }

    public static final List<Prototipo> LISTA = List.of(
            new Prototipo(
                    "Guerrero Tanque", "Guerrero",
                    "Este personaje se distingue por su alta resistencia a los ataques enemigos, aunque su capacidad ofensiva es limitada.",
                    false,
                    new Estadisticas(10, 44, 11, new Formula(10, 1, 6), new Formula(0, 1, 4), null),
                    List.of(
                            new Accion("Golpe con escudo", 2, "+2 al ataque"),
                            new Accion("Mano de piedra", 4, "+12 a la defensa"),
                            new Accion("Defensa feroz", 6, "Inmune al daño físico y (3d6) al daño mágico"))),
            new Prototipo(
                    "Guerrero Armas", "Guerrero",
                    "Aunque también es un guerrero, su especialidad radica en infligir el máximo daño posible, a expensas de su defensa.",
                    false,
                    new Estadisticas(8, 44, 11, new Formula(10, 1, 6), new Formula(0, 1, 6), null),
                    List.of(
                            new Accion("Embate sangriento", 4, "+2 al ataque, +1 de daño"),
                            new Accion("Lanza de los dioses", 4, "+2 al daño"),
                            new Accion("Golpe de tormenta", 6, "+(3d6) al ataque, +2 al daño"))),
            new Prototipo(
                    "Mago Fuego", "Mago",
                    "Entrenado en la magia elemental, este personaje posee la habilidad de causar un daño considerable al oponente.",
                    false,
                    new Estadisticas(8, 40, 10, new Formula(10, 1, 8), new Formula(0, 1, 8), null),
                    List.of(
                            new Accion("Misiles de magma", 2, "+1 al ataque, +2 de daño"),
                            new Accion("Vulcano", 6, "+3 al ataque, +(3d9) al daño"),
                            new Accion("Pare de fuego", 4,
                                    "+1 al ataque y retorna el (0dx) daño causado por el oponente en el turno anterior"))),
            new Prototipo(
                    "Mago Hielo", "Mago",
                    "En contraste con el Mago Fuego, su pericia se centra en debilitar al enemigo para asegurar una victoria estratégica.",
                    false,
                    new Estadisticas(10, 40, 10, new Formula(10, 1, 8), new Formula(0, 1, 6), null),
                    List.of(
                            new Accion("Lluvia de hielo", 2, "+2 al ataque, +2 de daño"),
                            new Accion("Cono de hielo", 6,
                                    "+2 al daño y afecta el ataque del enemigo en un (1d3) durante los dos turnos siguientes"),
                            new Accion("Bola de hielo", 4,
                                    "+2 al ataque y afecta en (0d4) al daño causado por el oponente"))),
            new Prototipo(
                    "Pícaro Veneno", "Pícaro",
                    "Experto en alquimia, puede infligir un daño significativo al adversario, afectando sus habilidades y disminuyendo su precisión.",
                    false,
                    new Estadisticas(8, 36, 8, new Formula(10, 1, 10), new Formula(0, 1, 6), null),
                    List.of(
                            new Accion("Flor de loto", 2, "+(4d8) al daño"),
                            new Accion("Agonía", 4, "+(2d9) de daño"),
                            new Accion("Piquete", 4, "+1 al ataque por dos turnos, +2 al daño por 1 turno"))),
            new Prototipo(
                    "Pícaro Machete", "Pícaro",
                    "Con entrenamiento en armas cortantes, es un especialista en la provocación de heridas letales.",
                    false,
                    new Estadisticas(8, 36, 8, new Formula(10, 1, 10), new Formula(0, 1, 8), null),
                    List.of(
                            new Accion("Cortada", 2, "+2 al daño por dos turnos"),
                            new Accion("Machetazo", 4, "+(2d8) al daño, +1 al ataque"),
                            new Accion("Planazo", 4, "+(2d8) al ataque, +1 al daño"))),
            new Prototipo(
                    "Chamán", "Sanador",
                    "Este es un sanador que ha dedicado su estudio a la naturaleza y ha comprendido su interrelación con los seres racionales. Posee la habilidad de sanar a sus congéneres. Sin embargo, carece de capacidad ofensiva, ya que le está vedado infligir daño a cualquier otro ser vivo.",
                    true,
                    new Estadisticas(10, 28, 4, null, null, new Formula(6, 1, 6)),
                    List.of(
                            new Accion("Toque de la Vida", 2, "+2 de sanación"),
                            new Accion("Vínculo Natural", 4, "+2 de sanación por dos turnos"),
                            new Accion("Canto del Bosque", 6, "Sana a todo el grupo +(2d6) durante dos turnos"))),
            new Prototipo(
                    "Médico", "Sanador",
                    "Es un especialista en ciencias de la salud, cuya función principal es la recuperación y el apoyo a sus aliados. A diferencia del Chamán, cuya especialización se centra en la naturaleza y la curación pasiva, el Médico aplica conocimientos científicos y habilidades prácticas para la restauración activa y eficiente de la salud de sus compañeros.",
                    true,
                    new Estadisticas(10, 28, 4, null, null, new Formula(4, 1, 8)),
                    List.of(
                            new Accion("Curación Directa", 2, "+2 de sanación"),
                            new Accion("Neutralización de Efectos", 4, "+2 y +(2d4) de sanación"),
                            new Accion("Reanimación", null, "Sana el 100% de la vida del compañero"))));
}
