package nexus.misiones.ia;

import java.nio.file.Path;

/**
 * Donde esta, en el repositorio, el modelo entrenado que viaja en la imagen del servicio ({@code ia/modelos/<version>/}).
 * Al publicar una version nueva se cambia {@link #VERSION} aqui (y la carpeta que copia el Dockerfile): las pruebas
 * que dependen de ella fallan hasta que las dos cosas coinciden. Ver ia/README.md, "Publicar una version nueva".
 */
public final class ModeloVersionado {

    /** La version que la imagen lleva hoy. */
    public static final String VERSION = "1.0.0";

    private ModeloVersionado() {
    }

    /** La carpeta del modulo de misiones; Gradle la pasa como propiedad, y sin ella se asume que se corre desde alli. */
    public static Path modulo() {
        return Path.of(System.getProperty("misiones.modulo", ".")).toAbsolutePath().normalize();
    }

    /** {@code ia/modelos/<version>/}. */
    public static Path carpeta() {
        return modulo().resolve("ia").resolve("modelos").resolve(VERSION);
    }

    /** El {@code modelo.onnx} versionado; su {@code modelo.json} esta a su lado. */
    public static Path onnx() {
        return carpeta().resolve("modelo.onnx");
    }
}
