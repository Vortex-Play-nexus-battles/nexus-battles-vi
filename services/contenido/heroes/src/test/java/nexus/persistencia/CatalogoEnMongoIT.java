package nexus.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import nexus.dominio.Accion;
import nexus.dominio.CatalogoDeHeroes;
import nexus.dominio.Estadisticas;
import nexus.dominio.Formula;
import nexus.dominio.HeroeNoDisponibleException;
import nexus.dominio.Prototipo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integracion real contra MongoDB (seccion 8: persistencia no relacional para
 * personajes e items), con Testcontainers — la herramienta que fija la pila
 * aprobada. Sin Docker disponible (maquinas locales del equipo) la prueba se
 * salta sola; en el CI de GitHub corre siempre.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("mongo")
class CatalogoEnMongoIT {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired
    private CatalogoDeHeroes catalogo;

    @Test
    @DisplayName("al arrancar con la coleccion vacia se siembran los ocho prototipos")
    void siembraLosOchoPrototipos() {
        assertEquals(8, catalogo.listar().size());
    }

    @Test
    @DisplayName("la ficha se consulta desde Mongo con tolerancia a tildes")
    void fichaDesdeMongoConTildes() {
        Prototipo chaman = catalogo.fichaDe("chaman");
        assertEquals("Chamán", chaman.nombre());
        assertEquals("6 + 1d6", chaman.estadisticasNivel1().sanar().texto());
        assertEquals(3, chaman.acciones().size());
    }

    @Test
    @DisplayName("un prototipo inexistente produce el error de dominio")
    void inexistenteProduceErrorDeDominio() {
        assertThrows(HeroeNoDisponibleException.class, () -> catalogo.fichaDe("Nigromante"));
    }

    @Test
    @DisplayName("registrar persiste y el duplicado se rechaza")
    void registrarPersisteYRechazaDuplicados() {
        Prototipo nuevo = new Prototipo(
                "Paladín de prueba", "Guerrero", "Prototipo de prueba de integración.", false,
                new Estadisticas(9, 42, 10, new Formula(10, 1, 6), new Formula(0, 1, 6), null),
                List.of(
                        new Accion("Juicio", 2, "+1 al ataque"),
                        new Accion("Escudo sagrado", 4, "+8 a la defensa"),
                        new Accion("Castigo", 6, "+2 al daño")));
        catalogo.registrar(nuevo);
        assertTrue(catalogo.listar().stream().anyMatch(p -> p.nombre().equals("Paladín de prueba")));
        assertThrows(IllegalArgumentException.class, () -> catalogo.registrar(nuevo));
    }
}
