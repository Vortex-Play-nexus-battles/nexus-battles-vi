package nexus.dominio;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * HU-SIM-001: la estrategia con la que un heroe pelea sin su jugador. Seccion
 * 7.8.5, p. 61 (RF-MIS-12): "hasta tres (3) rotaciones" con prioridad Alta,
 * Media y Baja. ERS CU-61 paso 4: "el sistema valida que las habilidades
 * pertenezcan al heroe seleccionado"; E1: si no, "rechaza la rotacion e indica
 * las habilidades validas". Sin rotaciones, la IA "ejecuta ataque basico sin
 * consumir poder" (RF-MIS-15).
 *
 * Las habilidades que el heroe posee son las desbloqueadas en su nivel (RC-01:
 * 1, 4 y 8), no las tres de la Tabla 7. Quien guarda la configuracion es el
 * modulo de misiones (RNF-16); aqui solo vive la regla.
 */
public final class EstrategiaDeCombate {

    public static final String ATAQUE_BASICO = "Ataque básico";
    public static final int ROTACIONES_MAXIMAS = Rotacion.Prioridad.values().length;

    private final Heroe heroe;
    private final List<Rotacion> rotaciones;

    private EstrategiaDeCombate(Heroe heroe, List<Rotacion> rotaciones) {
        this.heroe = heroe;
        this.rotaciones = List.copyOf(rotaciones);
    }

    /**
     * Configura las rotaciones en orden de prioridad (la primera es la Alta).
     * Los pasos se aceptan como los escribe el jugador y se guardan con el
     * nombre exacto de la Tabla 7. Una lista vacia es la estrategia por defecto.
     */
    public static EstrategiaDeCombate configurar(Heroe heroe, List<List<String>> secuencias) {
        List<String> validas = habilidadesValidas(heroe);
        if (secuencias.size() > ROTACIONES_MAXIMAS) {
            throw new RotacionInvalidaException(
                    "Una estrategia admite hasta tres rotaciones: alta, media y baja.", validas);
        }
        List<Rotacion> rotaciones = new ArrayList<>();
        for (int i = 0; i < secuencias.size(); i++) {
            int numero = i + 1;
            List<String> secuencia = secuencias.get(i);
            if (secuencia == null || secuencia.isEmpty()) {
                throw new RotacionInvalidaException("La rotación " + numero + " no tiene pasos.", validas);
            }
            List<String> pasos = new ArrayList<>();
            for (String paso : secuencia) {
                Optional<String> canonico = validas.stream()
                        .filter(h -> normalizar(h).equals(normalizar(paso)))
                        .findFirst();
                if (canonico.isEmpty()) {
                    throw new RotacionInvalidaException(
                            "La rotación " + numero + " usa una habilidad que " + heroe.prototipo().nombre()
                                    + " no posee en nivel " + heroe.nivel() + ": " + paso + ".",
                            validas);
                }
                pasos.add(canonico.get());
            }
            rotaciones.add(new Rotacion(Rotacion.Prioridad.enPosicion(i), pasos));
        }
        return new EstrategiaDeCombate(heroe, rotaciones);
    }

    /** Las acciones desbloqueadas en el nivel del heroe, mas el ataque basico. */
    public static List<String> habilidadesValidas(Heroe heroe) {
        List<String> validas = new ArrayList<>(heroe.accionesDisponibles().stream().map(Accion::nombre).toList());
        validas.add(ATAQUE_BASICO);
        return List.copyOf(validas);
    }

    public Heroe heroe() {
        return heroe;
    }

    public List<Rotacion> rotaciones() {
        return rotaciones;
    }

    public List<String> habilidadesValidas() {
        return habilidadesValidas(heroe);
    }

    /** Sin rotaciones configuradas: la IA usa el comportamiento por defecto. */
    public boolean esPorDefecto() {
        return rotaciones.isEmpty();
    }

    /** RF-MIS-15: "ataque basico sin consumir poder". */
    public String comportamientoPorDefecto() {
        return ATAQUE_BASICO;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
