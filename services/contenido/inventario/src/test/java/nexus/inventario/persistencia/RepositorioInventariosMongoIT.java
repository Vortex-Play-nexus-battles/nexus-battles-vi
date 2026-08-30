package nexus.inventario.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.FalloPersistenciaInventarioException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataMongoTest
@Import(RepositorioInventariosMongo.class)
class RepositorioInventariosMongoIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

    @Autowired
    private RepositorioDeInventarios repositorio;

    @Autowired
    private RepositorioInventariosSpringData documentos;

    @BeforeEach
    void limpiarColeccion() {
        documentos.deleteAll();
    }

    @Test
    @DisplayName("guarda y recupera el inventario completo por propietario")
    void guardaYRecupera() {
        Inventario inventario = Inventario.vacio("jugador-A")
                .agregar(new ElementoInventario(
                        "elemento-1", "heroe-1", TipoElementoInventario.HEROE, "Mi guerrero"))
                .agregar(new ElementoInventario(
                        "elemento-2", "item-1", TipoElementoInventario.ITEM, "Amuleto de Niebla"));

        Inventario guardado = repositorio.guardar(inventario);

        Inventario recuperado = repositorio.buscarPorPropietario("jugador-A").orElseThrow();
        assertEquals(guardado.id(), recuperado.id());
        assertEquals("jugador-A", recuperado.propietarioId());
        assertEquals(inventario.elementos(), recuperado.elementos());
    }

    @Test
    @DisplayName("solo existe un inventario por propietario")
    void propietarioUnico() {
        repositorio.guardar(Inventario.vacio("jugador-A"));

        assertThrows(FalloPersistenciaInventarioException.class,
                () -> repositorio.guardar(Inventario.vacio("jugador-A")));
    }

    @Test
    @DisplayName("un propietario sin inventario produce una respuesta vacia")
    void propietarioSinInventario() {
        assertEquals(List.of(), repositorio.buscarPorPropietario("jugador-inexistente").stream().toList());
    }

    @Test
    @DisplayName("recupera el inventario propietario a partir de un elemento anidado")
    void buscarPorElemento() {
        Inventario inventario = Inventario.vacio("jugador-A")
                .agregar(new ElementoInventario(
                        "elemento-1", "item-1", TipoElementoInventario.ITEM, "Amuleto"));
        repositorio.guardar(inventario);

        Inventario recuperado = repositorio.buscarPorElementoId("elemento-1").orElseThrow();

        assertEquals("jugador-A", recuperado.propietarioId());
        assertEquals("elemento-1", recuperado.elementos().getFirst().id());
    }
}
