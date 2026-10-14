package nexus.inventario.aplicacion;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Índice invertido de n-gramas de cuatro caracteres (§7.1: «algoritmos de
 * indexación que permitan identificar en pocos caracteres —mínimo cuatro— los
 * productos que el cliente está buscando»).
 *
 * <p>Cada texto se parte en todas sus subcadenas de {@value #N} caracteres y cada
 * una apunta a los ids que la contienen. Un criterio de cuatro o más caracteres
 * se resuelve intersecando las listas de sus propios cuatrigramas —empezando por
 * la más corta— y confirmando la subcadena completa en los pocos candidatos que
 * quedan. Así «brum» encuentra «Amuleto de Bruma», «vulc» encuentra al héroe que
 * tiene la acción «Vulcano» y «sanac» los objetos que hablan de sanación, sin
 * recorrer cada texto en cada búsqueda. Es el mismo principio que el índice de
 * trigramas de PostgreSQL ({@code pg_trgm}), con n = 4 porque ese es el mínimo
 * que fija el documento.
 *
 * <p>Inmutable: se construye entero y se reemplaza de una vez.
 */
final class IndiceDeNgramas {

    /** Tamaño del n-grama: el mínimo de caracteres de §7.1. */
    static final int N = 4;

    private static final IndiceDeNgramas VACIO = new IndiceDeNgramas(Map.of(), Map.of());

    private final Map<String, Set<String>> idsPorGrama;
    private final Map<String, String> textoPorId;

    private IndiceDeNgramas(Map<String, Set<String>> idsPorGrama, Map<String, String> textoPorId) {
        this.idsPorGrama = idsPorGrama;
        this.textoPorId = textoPorId;
    }

    static IndiceDeNgramas vacio() {
        return VACIO;
    }

    /** @param textosPorId texto buscable de cada id; se normaliza aquí */
    static IndiceDeNgramas construir(Map<String, String> textosPorId) {
        Map<String, Set<String>> idsPorGrama = new HashMap<>();
        Map<String, String> textoPorId = new HashMap<>();
        textosPorId.forEach((id, texto) -> {
            String normalizado = TextoBuscable.normalizar(texto);
            if (id == null || normalizado.length() < N) {
                return;
            }
            textoPorId.put(id, normalizado);
            for (String grama : gramasDe(normalizado)) {
                idsPorGrama.computeIfAbsent(grama, ignorado -> new HashSet<>()).add(id);
            }
        });
        Map<String, Set<String>> congelado = new HashMap<>();
        idsPorGrama.forEach((grama, ids) -> congelado.put(grama, Set.copyOf(ids)));
        return new IndiceDeNgramas(Map.copyOf(congelado), Map.copyOf(textoPorId));
    }

    /**
     * Los ids cuyo texto contiene el criterio entero (sin tildes ni mayúsculas).
     * Un criterio de menos de {@value #N} caracteres no encuentra nada: el mínimo
     * lo valida antes el caso de uso.
     */
    Set<String> buscar(String criterio) {
        String buscado = TextoBuscable.normalizar(criterio);
        if (buscado.length() < N || textoPorId.isEmpty()) {
            return Set.of();
        }
        List<Set<String>> listas = gramasDe(buscado).stream()
                .map(grama -> idsPorGrama.getOrDefault(grama, Set.of()))
                .sorted((a, b) -> Integer.compare(a.size(), b.size()))
                .toList();
        if (listas.isEmpty() || listas.getFirst().isEmpty()) {
            return Set.of();
        }
        Set<String> candidatos = new HashSet<>(listas.getFirst());
        for (int i = 1; i < listas.size() && !candidatos.isEmpty(); i++) {
            candidatos.retainAll(listas.get(i));
        }
        candidatos.removeIf(id -> !textoPorId.get(id).contains(buscado));
        return Collections.unmodifiableSet(candidatos);
    }

    int tamanio() {
        return textoPorId.size();
    }

    private static Set<String> gramasDe(String texto) {
        Set<String> gramas = new LinkedHashSet<>();
        for (int i = 0; i + N <= texto.length(); i++) {
            gramas.add(texto.substring(i, i + N));
        }
        return gramas;
    }
}
