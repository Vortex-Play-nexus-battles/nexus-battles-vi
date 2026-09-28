package nexus.misiones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Servicio de misiones (seccion 7.8 del documento, M11): tablon, matricula,
 * simulacion en segundo plano contra el entorno, reporte e historial, con la
 * progresion del heroe persistida en el inventario.
 */
@SpringBootApplication
public class ServicioMisionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioMisionesApplication.class, args);
    }
}
