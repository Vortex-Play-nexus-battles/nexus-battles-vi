package nexus.misiones.ia;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import tools.jackson.databind.json.JsonMapper;

/**
 * La red propia de IA de combate, ejecutada con ONNX Runtime (HU-SIM-008). Se carga de un {@code modelo.onnx} con su
 * {@code modelo.json} al lado, y solo se acepta si todo cuadra: la version y la dimension de las caracteristicas son
 * las de {@link Caracteristicas}, el hash es el del archivo, el grafo tiene la entrada y la salida esperadas y
 * las entradas de ejemplo dan, al correrlas aqui, lo mismo que dieron en PyTorch. Cualquier otra cosa es
 * {@link ModeloNoUtilizable}: mejor sin modelo (decide la regla) que con uno equivocado.
 *
 * <p>La red es de unos 4.500 parametros (~18 KB). Se configura para gastar lo minimo en la instancia de contenido
 * (que tiene la memoria justa): un hilo, sin arena de memoria propia ni patrones de reserva.
 */
public final class PuntuadorOnnx implements Puntuador, AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final float TOLERANCIA_DE_EJEMPLOS = 1e-4f;

    private final OrtEnvironment entorno;
    private final OrtSession sesion;
    private final MetadatosDelModelo metadatos;
    private final AtomicBoolean cerrado = new AtomicBoolean();

    private PuntuadorOnnx(OrtEnvironment entorno, OrtSession sesion, MetadatosDelModelo metadatos) {
        this.entorno = entorno;
        this.sesion = sesion;
        this.metadatos = metadatos;
    }

    /**
     * @param onnx la ruta del {@code modelo.onnx}; su {@code modelo.json} debe estar en la misma carpeta
     * @throws ModeloNoUtilizable si no existe, no cuadra con sus metadatos o ONNX Runtime no puede con el
     */
    public static PuntuadorOnnx cargar(Path onnx) {
        if (!Files.isRegularFile(onnx)) {
            throw new ModeloNoUtilizable("No existe el archivo del modelo: " + onnx);
        }
        Path jsonDelModelo = onnx.resolveSibling("modelo.json");
        if (!Files.isRegularFile(jsonDelModelo)) {
            throw new ModeloNoUtilizable("Falta el modelo.json junto a " + onnx
                    + ": sin el no se sabe de que version es ni con que caracteristicas se entreno.");
        }
        byte[] bytes;
        Map<String, Object> json;
        try {
            bytes = Files.readAllBytes(onnx);
            json = leerJson(jsonDelModelo);
        } catch (IOException | RuntimeException e) {
            throw new ModeloNoUtilizable("No se pudo leer el modelo " + onnx + ": " + e.getMessage(), e);
        }
        MetadatosDelModelo metadatos = metadatos(json, onnx);
        if (metadatos.sha256() != null && !metadatos.sha256().equalsIgnoreCase(Hashes.sha256(bytes))) {
            throw new ModeloNoUtilizable("El " + onnx.getFileName() + " no es el que describe su modelo.json "
                    + "(el sha256 no coincide): se copio uno sin el otro.");
        }
        try {
            OrtEnvironment entorno = OrtEnvironment.getEnvironment();
            OrtSession sesion = abrir(entorno, bytes, metadatos);
            PuntuadorOnnx puntuador = new PuntuadorOnnx(entorno, sesion, metadatos);
            try {
                puntuador.comprobarEjemplos(json.get("ejemplos"));
            } catch (RuntimeException e) {
                puntuador.close();
                throw e;
            }
            return puntuador;
        } catch (OrtException | LinkageError e) {
            // LinkageError: la biblioteca nativa de ONNX Runtime no cargo en esta plataforma.
            throw new ModeloNoUtilizable("ONNX Runtime no pudo cargar el modelo " + onnx + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> leerJson(Path ruta) throws IOException {
        return JSON.readValue(Files.readString(ruta, StandardCharsets.UTF_8), Map.class);
    }

    @SuppressWarnings("unchecked")
    private static MetadatosDelModelo metadatos(Map<String, Object> json, Path onnx) {
        Object version = json.get("version");
        if (!(version instanceof String v) || v.isBlank()) {
            throw new ModeloNoUtilizable("El modelo.json de " + onnx + " no trae `version`.");
        }
        Object c = json.get("caracteristicas");
        if (!(c instanceof Map<?, ?> caracteristicas)) {
            throw new ModeloNoUtilizable("El modelo.json de " + onnx + " no dice con que caracteristicas se entreno.");
        }
        int versionDeCaracteristicas = entero(((Map<String, Object>) caracteristicas).get("version"));
        int dimension = entero(((Map<String, Object>) caracteristicas).get("dimension"));
        if (versionDeCaracteristicas != Caracteristicas.VERSION) {
            throw new ModeloNoUtilizable("El modelo " + v + " se entreno con la version " + versionDeCaracteristicas
                    + " de las caracteristicas y este servicio usa la " + Caracteristicas.VERSION + ".");
        }
        if (dimension != Caracteristicas.DIMENSION) {
            throw new ModeloNoUtilizable("El modelo " + v + " espera " + dimension + " caracteristicas y este servicio "
                    + "las calcula de " + Caracteristicas.DIMENSION + ".");
        }
        String entrada = texto(json.get("entrada"), "caracteristicas");
        String salida = texto(json.get("salida"), "puntajes");
        String sha256 = null;
        if (json.get("onnx") instanceof Map<?, ?> info && info.get("sha256") instanceof String h) {
            sha256 = h;
        }
        return new MetadatosDelModelo(v, Boolean.TRUE.equals(json.get("sintetico")), versionDeCaracteristicas,
                dimension, entrada, salida, sha256);
    }

    private static int entero(Object valor) {
        return valor instanceof Number n ? n.intValue() : -1;
    }

    private static String texto(Object valor, String porOmision) {
        return valor instanceof String s && !s.isBlank() ? s : porOmision;
    }

    private static OrtSession abrir(OrtEnvironment entorno, byte[] bytes, MetadatosDelModelo metadatos)
            throws OrtException {
        try (OrtSession.SessionOptions opciones = new OrtSession.SessionOptions()) {
            opciones.setIntraOpNumThreads(1);
            opciones.setInterOpNumThreads(1);
            opciones.setCPUArenaAllocator(false);
            opciones.setMemoryPatternOptimization(false);
            OrtSession sesion = entorno.createSession(bytes, opciones);
            if (!sesion.getInputNames().contains(metadatos.entrada())
                    || !sesion.getOutputNames().contains(metadatos.salida())) {
                sesion.close();
                throw new ModeloNoUtilizable("El grafo ONNX debe tener la entrada `" + metadatos.entrada()
                        + "` y la salida `" + metadatos.salida() + "`; tiene " + sesion.getInputNames() + " y "
                        + sesion.getOutputNames() + ".");
            }
            return sesion;
        }
    }

    /** Corre las entradas de ejemplo del json y exige las mismas salidas que dio PyTorch. */
    private void comprobarEjemplos(Object ejemplos) {
        if (!(ejemplos instanceof List<?> lista) || lista.isEmpty()) {
            // Un modelo sin ejemplos se prueba al menos con una fila de ceros: la forma de la salida debe ser la esperada.
            puntuar(new float[][] {new float[Caracteristicas.DIMENSION]});
            return;
        }
        for (int i = 0; i < lista.size(); i++) {
            if (!(lista.get(i) instanceof Map<?, ?> ejemplo) || !(ejemplo.get("entrada") instanceof List<?> entrada)
                    || !(ejemplo.get("salida") instanceof Number esperado)) {
                throw new ModeloNoUtilizable("El ejemplo " + (i + 1) + " del modelo.json esta mal formado.");
            }
            float[] x = new float[entrada.size()];
            for (int j = 0; j < x.length; j++) {
                x[j] = ((Number) entrada.get(j)).floatValue();
            }
            float obtenido;
            try {
                obtenido = puntuar(new float[][] {x})[0];
            } catch (IllegalArgumentException e) {
                throw new ModeloNoUtilizable("El ejemplo " + (i + 1) + " del modelo.json no tiene la dimension "
                        + "esperada.", e);
            }
            if (Math.abs(obtenido - esperado.floatValue()) > TOLERANCIA_DE_EJEMPLOS) {
                throw new ModeloNoUtilizable("El ejemplo " + (i + 1) + " del modelo.json da " + esperado
                        + " en PyTorch y " + obtenido + " en ONNX Runtime: el modelo no se reproduce aqui.");
            }
        }
    }

    public MetadatosDelModelo metadatos() {
        return metadatos;
    }

    @Override
    public String version() {
        return metadatos.version();
    }

    @Override
    public float[] puntuar(float[][] caracteristicas) {
        if (cerrado.get()) {
            throw new IllegalStateException("El modelo ya se cerro.");
        }
        if (caracteristicas == null || caracteristicas.length == 0) {
            throw new IllegalArgumentException("No hay candidatas que puntuar.");
        }
        for (float[] fila : caracteristicas) {
            if (fila == null || fila.length != Caracteristicas.DIMENSION) {
                throw new IllegalArgumentException("Cada candidata debe tener " + Caracteristicas.DIMENSION
                        + " caracteristicas.");
            }
        }
        try (OnnxTensor entrada = OnnxTensor.createTensor(entorno, caracteristicas);
             OrtSession.Result resultado = sesion.run(Map.of(metadatos.entrada(), entrada))) {
            Object valor = resultado.get(metadatos.salida())
                    .orElseThrow(() -> new IllegalStateException("El modelo no devolvio `" + metadatos.salida() + "`."))
                    .getValue();
            if (!(valor instanceof float[][] filas) || filas.length != caracteristicas.length) {
                throw new IllegalStateException("La salida del modelo no tiene la forma [n, 1] esperada.");
            }
            float[] puntajes = new float[filas.length];
            for (int i = 0; i < filas.length; i++) {
                if (filas[i].length != 1) {
                    throw new IllegalStateException("La salida del modelo no tiene la forma [n, 1] esperada.");
                }
                puntajes[i] = filas[i][0];
            }
            return puntajes;
        } catch (OrtException e) {
            throw new IllegalStateException("ONNX Runtime fallo al puntuar: " + e.getMessage(), e);
        }
    }

    /** Libera la sesion. El entorno de ONNX Runtime es de todo el proceso y no se cierra. */
    @Override
    public void close() {
        if (cerrado.compareAndSet(false, true)) {
            try {
                sesion.close();
            } catch (OrtException e) {
                // Cerrar lo que ya no se usa no merece detener a nadie.
            }
        }
    }
}
