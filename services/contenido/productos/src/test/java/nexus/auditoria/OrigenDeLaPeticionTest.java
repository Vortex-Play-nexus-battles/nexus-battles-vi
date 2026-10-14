package nexus.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

@DisplayName("La IP de origen de una operación del catálogo (SA-18, §7.3.5)")
class OrigenDeLaPeticionTest {

        private static MockHttpServletRequest peticion(String real, String reenviada, String remota) {
                MockHttpServletRequest peticion = new MockHttpServletRequest();
                if (real != null) {
                        peticion.addHeader("X-Real-IP", real);
                }
                if (reenviada != null) {
                        peticion.addHeader("X-Forwarded-For", reenviada);
                }
                peticion.setRemoteAddr(remota);
                return peticion;
        }

        @Test
        @DisplayName("primero la que fija el borde, después la primera reenviada y por último la remota")
        void orden() {
                assertThat(OrigenDeLaPeticion.ipDe(peticion(" 181.52.10.4 ", "1.1.1.1", "10.0.0.7")))
                                .isEqualTo("181.52.10.4");
                assertThat(OrigenDeLaPeticion.ipDe(peticion(null, "203.0.113.9, 10.0.0.1", "10.0.0.7")))
                                .isEqualTo("203.0.113.9");
                assertThat(OrigenDeLaPeticion.ipDe(peticion(null, null, "10.0.0.7"))).isEqualTo("10.0.0.7");
        }

        @Test
        @DisplayName("lo que no parece una IP no se guarda como si lo fuera")
        void sinIp() {
                assertThat(OrigenDeLaPeticion.ipDe(peticion("<script>", "desconocido", "10.0.0.7")))
                                .isEqualTo("10.0.0.7");
                assertThat(OrigenDeLaPeticion.ipDe(peticion(null, null, "x".repeat(50)))).isNull();
                assertThat(OrigenDeLaPeticion.pareceUnaIp("")).isFalse();
                assertThat(OrigenDeLaPeticion.pareceUnaIp("2800:e2:2b00::1")).isTrue();
        }

        @Test
        @DisplayName("sin petición en curso no hay IP")
        void sinPeticion() {
                assertThat(OrigenDeLaPeticion.ipActual()).isNull();
        }
}
