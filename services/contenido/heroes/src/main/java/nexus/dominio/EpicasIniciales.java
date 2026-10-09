package nexus.dominio;

import java.util.List;

/**
 * Las ocho epicas de la Tabla 20 (epicas.md). Como los prototipos, son los
 * datos iniciales, no un limite. El efecto general "No aplica" de los
 * sanadores se modela como null. La probabilidad del Master va en porcentaje:
 * la Tabla 20 escribe «0.04%» y el PO decidio (2026-10-06) leerlo como el
 * ejemplo del Templo, «0.15% (15% de probabilidad)», es decir, un 4 %.
 */
public final class EpicasIniciales {

    private EpicasIniciales() {
    }

    public static final List<Epica> LISTA = List.of(
            new Epica("Golpe de defensa", "Guerrero Tanque", "+1 al ataque", "+4 al daño, +2% de crítico", 4),
            new Epica("Segundo impulso", "Guerrero Armas", "Recupera 1d4 de vida", "+3 a la vida, +5% de crítico", 1),
            new Epica("Luz cegadora", "Mago Fuego", "+1 a la vida", "+2 al daño, +1% de crítico", 3),
            new Epica("Frio concentrado", "Mago Hielo", "-1 de poder al oponente",
                    "No recibe ningún daño en el siguiente turno", 5),
            new Epica("Toma y lleva", "Pícaro Veneno", "+1 al ataque",
                    "Disminuye a la mitad del daño causado por el oponente y se lo retorna", 2),
            new Epica("Intimidación sangrienta", "Pícaro Machete", "+1 al daño", "+2 a la vida, +2% de crítico", 1),
            new Epica("Té changua", "Chamán", null, "Sana a todos +(4d8)", 10),
            new Epica("Reanimador 3000", "Médico", null,
                    "Se asocia con un compañero. Si este último fallece, se reanima con el 20% de su salud.", 10));

    public static Epica afinA(String nombreDePrototipo) {
        return LISTA.stream()
                .filter(e -> e.tipoDeHeroeAfin().equals(nombreDePrototipo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay épica afín al prototipo \"" + nombreDePrototipo + "\"."));
    }
}
