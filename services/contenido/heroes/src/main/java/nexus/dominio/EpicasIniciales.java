package nexus.dominio;

import java.util.List;

/**
 * Las ocho epicas de la Tabla 20 (epicas.md). Como los prototipos, son los
 * datos iniciales, no un limite. El efecto general "No aplica" de los
 * sanadores se modela como null.
 */
public final class EpicasIniciales {

    private EpicasIniciales() {
    }

    public static final List<Epica> LISTA = List.of(
            new Epica("Golpe de defensa", "Guerrero Tanque", "+1 al ataque", "+4 al daño, +2% de crítico", 0.04),
            new Epica("Segundo impulso", "Guerrero Armas", "Recupera 1d4 de vida", "+3 a la vida, +5% de crítico", 0.01),
            new Epica("Luz cegadora", "Mago Fuego", "+1 a la vida", "+2 al daño, +1% de crítico", 0.03),
            new Epica("Frio concentrado", "Mago Hielo", "-1 de poder al oponente",
                    "No recibe ningún daño en el siguiente turno", 0.05),
            new Epica("Toma y lleva", "Pícaro Veneno", "+1 al ataque",
                    "Disminuye a la mitad del daño causado por el oponente y se lo retorna", 0.02),
            new Epica("Intimidación sangrienta", "Pícaro Machete", "+1 al daño", "+2 a la vida, +2% de crítico", 0.01),
            new Epica("Té changua", "Chamán", null, "Sana a todos +(4d8)", 0.1),
            new Epica("Reanimador 3000", "Médico", null,
                    "Se asocia con un compañero. Si este último fallece, se reanima con el 20% de su salud.", 0.1));

    public static Epica afinA(String nombreDePrototipo) {
        return LISTA.stream()
                .filter(e -> e.tipoDeHeroeAfin().equals(nombreDePrototipo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay épica afín al prototipo \"" + nombreDePrototipo + "\"."));
    }
}
