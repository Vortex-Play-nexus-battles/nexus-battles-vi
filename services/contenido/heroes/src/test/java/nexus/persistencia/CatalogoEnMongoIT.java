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
import nexus.dominio.PrototiposIniciales;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
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
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8");

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

    // --- B4: semilla versionada ---

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("B4: los prototipos sembrados llevan origen SEMILLA y la version de PrototiposIniciales")
    void sembradosConSusMarcas() {
        PrototipoDocumento tanque = mongoTemplate.findById("Guerrero Tanque", PrototipoDocumento.class);

        assertEquals(PrototipoDocumento.ORIGEN_SEMILLA, tanque.origen);
        assertEquals(PrototiposIniciales.VERSION, tanque.semillaVersion);
    }

    @Test
    @DisplayName("B4: una segunda ejecucion de la misma version no inserta ni actualiza nada")
    void segundaEjecucionNoCambiaNada() {
        CatalogoEnMongo.ResultadoSemilla otra =
                ((CatalogoEnMongo) catalogo).sembrar(PrototiposIniciales.LISTA, PrototiposIniciales.VERSION);

        assertEquals(List.of(), otra.insertados());
        assertEquals(List.of(), otra.actualizados());
        assertEquals(8, otra.alDia().size());
    }

    @Test
    @DisplayName("B4: una version nueva pone al dia lo no editado, respeta lo editado y adopta lo anterior a las marcas")
    void versionNuevaActualizaRespetaYAdopta() {
        CatalogoEnMongo catalogoEnMongo = (CatalogoEnMongo) catalogo;
        PrototipoDocumento editado = mongoTemplate.findById("Mago Hielo", PrototipoDocumento.class);
        editado.modificadoPor = "uid-de-quien-lo-edito";
        editado.descripcion = "Descripcion editada";
        mongoTemplate.save(editado);
        PrototipoDocumento anterior = mongoTemplate.findById("Médico", PrototipoDocumento.class);
        anterior.origen = null;
        anterior.semillaVersion = null;
        mongoTemplate.save(anterior);

        List<Prototipo> v2 = PrototiposIniciales.LISTA.stream()
                .map(p -> new Prototipo(p.nombre(), p.tipo(), p.descripcion() + " (v2)", p.esSanador(),
                        p.estadisticasNivel1(), p.acciones()))
                .toList();
        CatalogoEnMongo.ResultadoSemilla resultado = catalogoEnMongo.sembrar(v2, PrototiposIniciales.VERSION + 1);

        assertEquals(List.of("Mago Hielo"), resultado.respetados());
        assertEquals(7, resultado.actualizados().size());
        assertEquals("Descripcion editada",
                mongoTemplate.findById("Mago Hielo", PrototipoDocumento.class).descripcion);
        PrototipoDocumento adoptado = mongoTemplate.findById("Médico", PrototipoDocumento.class);
        assertEquals(PrototipoDocumento.ORIGEN_SEMILLA, adoptado.origen);
        assertEquals(PrototiposIniciales.VERSION + 1, adoptado.semillaVersion);
        assertTrue(adoptado.descripcion.endsWith("(v2)"));

        // Se deja la coleccion como la dejaria la semilla real, para las demas pruebas.
        mongoTemplate.dropCollection(PrototipoDocumento.class);
        catalogoEnMongo.sembrar(PrototiposIniciales.LISTA, PrototiposIniciales.VERSION);
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
