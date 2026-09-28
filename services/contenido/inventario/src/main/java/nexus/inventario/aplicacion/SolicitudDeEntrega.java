package nexus.inventario.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import nexus.inventario.dominio.LineaDeEntrega;
import nexus.inventario.dominio.OrigenDeEntrega;

/**
 * Lo que pide quien entrega (cuerpo de {@code POST /entregas}) — B4.
 *
 * @param uid        jugador que recibe
 * @param origen     canal (compra, cofre, premio...)
 * @param referencia el hecho que causa la entrega (orden, cofre, torneo...)
 */
public record SolicitudDeEntrega(UUID uid, OrigenDeEntrega origen, String referencia, List<LineaDeEntrega> productos) {

    public SolicitudDeEntrega {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(origen, "origen");
        productos = List.copyOf(Objects.requireNonNull(productos, "productos"));
    }

    /**
     * Resumen del cuerpo, para saber si una clave repetida trae la MISMA
     * peticion. El orden de las lineas no cuenta: dos cuerpos con los mismos
     * productos en otro orden son la misma entrega.
     */
    public String huella() {
        String lineas = productos.stream()
                .sorted(Comparator.comparing(LineaDeEntrega::productoId).thenComparing(LineaDeEntrega::cantidad))
                .map(linea -> linea.productoId() + "x" + linea.cantidad())
                .collect(Collectors.joining(","));
        String canonico = uid + "|" + origen + "|" + referencia + "|" + lineas;
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException sinSha256) {
            throw new IllegalStateException("La JVM no trae SHA-256", sinSha256);
        }
    }
}
