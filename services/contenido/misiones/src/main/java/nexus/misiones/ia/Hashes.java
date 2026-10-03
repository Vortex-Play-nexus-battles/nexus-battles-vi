package nexus.misiones.ia;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 en hexadecimal, para comprobar que el .onnx es el que describe su modelo.json. */
final class Hashes {

    private Hashes() {
    }

    static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Toda JVM trae SHA-256.", e);
        }
    }
}
