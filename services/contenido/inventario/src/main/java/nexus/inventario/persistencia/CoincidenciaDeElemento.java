package nexus.inventario.persistencia;

import java.text.Normalizer;
import java.util.Locale;
import java.util.stream.Stream;
import nexus.inventario.dominio.ElementoInventario;

final class CoincidenciaDeElemento {

    private CoincidenciaDeElemento() {
    }

    static boolean conTexto(ElementoInventario elemento, String criterio) {
        String buscado = normalizar(criterio);
        return Stream.of(
                        elemento.productoId(),
                        elemento.tipo().name(),
                        elemento.nombrePropio(),
                        elemento.parteArmadura() == null ? "" : elemento.parteArmadura().name())
                .map(CoincidenciaDeElemento::normalizar)
                .anyMatch(valor -> valor.contains(buscado));
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
