package nexus.combate.arranque;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Arranque del servicio de motor de combate.
 *
 * <p>Vive en su propio paquete a proposito. {@code @SpringBootApplication}
 * escanea desde el paquete que lo contiene hacia abajo, asi que estando en
 * {@code nexus.combate.arranque} <b>no escanea {@code nexus.combate}</b>: las
 * clases de reglas del juego siguen siendo Java puro, sin anotaciones y sin
 * dependencia de Spring, y se prueban sin levantar contexto.</p>
 *
 * <p>Desde que existe {@code contracts/openapi/motor-combate.yaml}, el servicio
 * expone la resolucion de ataques ademas de la salud y las metricas de
 * Actuator. La capa web vive en {@code nexus.combate.api}, que se escanea
 * explicitamente: el dominio de {@code nexus.combate} sigue fuera del escaneo y
 * por tanto sigue siendo Java puro.</p>
 */
@SpringBootApplication(scanBasePackages = {"nexus.combate.arranque", "nexus.combate.api"})
public class MotorCombateApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotorCombateApplication.class, args);
    }
}
