package nexus.misiones.ia;

/**
 * Lo que dice el {@code modelo.json} que acompana al {@code modelo.onnx}.
 *
 * @param version                version del modelo, la que queda en el evento de combate
 * @param sintetico              verdadero si se entreno con eventos de prueba (no es un modelo de produccion)
 * @param versionDeCaracteristicas version de la definicion de caracteristicas con que se entreno
 * @param dimension              largo del vector de caracteristicas
 * @param entrada                nombre de la entrada del grafo ONNX
 * @param salida                 nombre de la salida del grafo ONNX
 * @param sha256                 hash del .onnx que describe; nulo si el json no lo trae
 */
public record MetadatosDelModelo(String version, boolean sintetico, int versionDeCaracteristicas, int dimension,
                                 String entrada, String salida, String sha256) {
}
