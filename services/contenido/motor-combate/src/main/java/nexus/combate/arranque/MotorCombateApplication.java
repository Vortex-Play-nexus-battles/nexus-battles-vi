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
 * <p>El servicio no expone todavia ninguna operacion de negocio: solo salud y
 * metricas de Actuator, como exige la regla 3 de plataforma. Las operaciones
 * llegan cuando se acuerde el contrato del motor, que es el paso siguiente.</p>
 */
@SpringBootApplication
public class MotorCombateApplication {

    public static void main(String[] args) {
        SpringApplication.run(MotorCombateApplication.class, args);
    }
}
