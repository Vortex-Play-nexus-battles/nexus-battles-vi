package nexus.combate.arranque;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Prueba de humo del arranque: comprueba que el contexto levanta de verdad.
 *
 * <p>Sin ella, un servicio puede empaquetarse y desplegarse para descubrir en
 * produccion que no arranca. Es barata y cubre justo lo que el Dockerfile
 * asume.</p>
 */
@SpringBootTest
class MotorCombateApplicationTest {

    @Autowired
    private ApplicationContext contexto;

    @Test
    @DisplayName("el contexto del servicio levanta")
    void elContextoLevanta() {
        assertNotNull(contexto);
    }

    @Test
    @DisplayName("el arranque no arrastra el dominio al contexto de Spring")
    void elDominioSigueSiendoJavaPuro() {
        // Si alguien anota una clase de reglas con @Component o mueve el
        // arranque a `nexus.combate`, esta prueba lo detecta: el dominio debe
        // poder usarse sin contenedor.
        assertNotNull(contexto);
        org.junit.jupiter.api.Assertions.assertTrue(
                contexto.getBeansOfType(nexus.combate.ColaTurnos.class).isEmpty(),
                "ColaTurnos no debe ser un bean: el dominio es Java puro");
    }
}
