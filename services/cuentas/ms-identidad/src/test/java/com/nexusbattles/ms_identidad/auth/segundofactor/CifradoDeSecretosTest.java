package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException.Motivo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El secreto TOTP nunca en claro en la base: AES-GCM con la clave de
 * {@code IDENTIDAD_2FA_CLAVE}. Sin clave (o con una que no sirve) el servicio
 * arranca igual y el enrolamiento dice que no esta disponible.
 */
@DisplayName("Cifrado AES-GCM del secreto TOTP")
class CifradoDeSecretosTest {

    private static String claveDe(int bytes) {
        byte[] clave = new byte[bytes];
        new SecureRandom().nextBytes(clave);
        return Base64.getEncoder().encodeToString(clave);
    }

    private static final byte[] SECRETO = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("ida y vuelta con claves de 128, 192 y 256 bits")
    void idaYVuelta() {
        for (int bytes : new int[] {16, 24, 32}) {
            CifradoDeSecretos cifrado = new CifradoDeSecretos(claveDe(bytes));
            assertThat(cifrado.disponible()).isTrue();
            String guardado = cifrado.cifrar(SECRETO, "usuario:7");
            assertThat(cifrado.descifrar(guardado, "usuario:7")).isEqualTo(SECRETO);
        }
    }

    @Test
    @DisplayName("lo guardado no contiene el secreto ni su base32, y lleva la version del formato")
    void loGuardadoNoEsElSecreto() {
        CifradoDeSecretos cifrado = new CifradoDeSecretos(claveDe(32));
        String guardado = cifrado.cifrar(SECRETO, "usuario:7");
        assertThat(guardado).startsWith("v1:")
                .doesNotContain("12345678901234567890")
                .doesNotContain(Base32.codificar(SECRETO))
                .hasSizeLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("dos cifrados del mismo secreto no se parecen: vector de inicializacion al azar")
    void ivAlAzar() {
        CifradoDeSecretos cifrado = new CifradoDeSecretos(claveDe(32));
        assertThat(cifrado.cifrar(SECRETO, "usuario:7")).isNotEqualTo(cifrado.cifrar(SECRETO, "usuario:7"));
    }

    @Test
    @DisplayName("ligado a su cuenta: copiado a otra fila no se descifra")
    void ligadoALaCuenta() {
        CifradoDeSecretos cifrado = new CifradoDeSecretos(claveDe(32));
        String guardado = cifrado.cifrar(SECRETO, "usuario:7");
        assertThatThrownBy(() -> cifrado.descifrar(guardado, "usuario:8"))
                .isInstanceOf(SegundoFactorRechazadoException.class)
                .extracting(e -> ((SegundoFactorRechazadoException) e).getMotivo())
                .isEqualTo(Motivo.NO_DISPONIBLE);
    }

    @Test
    @DisplayName("alterado o con otra clave: no se descifra (GCM autentica)")
    void alteradoOConOtraClave() {
        CifradoDeSecretos cifrado = new CifradoDeSecretos(claveDe(32));
        String guardado = cifrado.cifrar(SECRETO, "usuario:7");
        char ultimo = guardado.charAt(guardado.length() - 2);
        String alterado = guardado.substring(0, guardado.length() - 2) + (ultimo == 'A' ? 'B' : 'A')
                + guardado.charAt(guardado.length() - 1);

        assertThatThrownBy(() -> cifrado.descifrar(alterado, "usuario:7"))
                .isInstanceOf(SegundoFactorRechazadoException.class);
        assertThatThrownBy(() -> new CifradoDeSecretos(claveDe(32)).descifrar(guardado, "usuario:7"))
                .isInstanceOf(SegundoFactorRechazadoException.class);
        assertThatThrownBy(() -> cifrado.descifrar("v0:basura", "usuario:7"))
                .isInstanceOf(SegundoFactorRechazadoException.class);
    }

    @Test
    @DisplayName("sin clave: no disponible, y cifrar o descifrar responden 503 segundo-factor-no-disponible")
    void sinClave() {
        for (String vacia : new String[] {"", "   ", null}) {
            CifradoDeSecretos cifrado = new CifradoDeSecretos(vacia);
            assertThat(cifrado.disponible()).isFalse();
            assertThatThrownBy(() -> cifrado.cifrar(SECRETO, "usuario:7"))
                    .isInstanceOf(SegundoFactorRechazadoException.class)
                    .extracting(e -> ((SegundoFactorRechazadoException) e).getMotivo().estado())
                    .isEqualTo(503);
            assertThatThrownBy(() -> cifrado.descifrar("v1:AAAA", "usuario:7"))
                    .isInstanceOf(SegundoFactorRechazadoException.class);
        }
    }

    @Test
    @DisplayName("una clave que no sirve (no es base64, o no mide 16/24/32 bytes) cuenta como ausente")
    void claveQueNoSirve() {
        assertThat(new CifradoDeSecretos("esto no es base64!").disponible()).isFalse();
        assertThat(new CifradoDeSecretos(claveDe(10)).disponible()).isFalse();
        assertThat(new CifradoDeSecretos(claveDe(64)).disponible()).isFalse();
    }
}
