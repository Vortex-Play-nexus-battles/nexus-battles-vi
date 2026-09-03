package nexus.inventario.persistencia;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class InventarioDocumentoTest {

    @Test
    @DisplayName("el documento Mongo conserva el agregado sin duplicar datos del catalogo")
    void conversionCompleta() {
        ElementoInventario heroe = new ElementoInventario(
                "heroe-1", "producto-heroe", TipoElementoInventario.HEROE, "Heroe propio");
        ElementoInventario casco = new ElementoInventario(
                "casco-1", "producto-casco", TipoElementoInventario.ARMADURA,
                "Casco propio", ParteArmadura.CASCO);
        Inventario inventario = new Inventario(
                "inventario-1", "jugador-A", java.util.List.of(heroe, casco))
                .equipar(heroe.id(), casco.id());

        InventarioDocumento documento = InventarioDocumento.de(inventario);
        Inventario restaurado = documento.aDominio();

        assertEquals(inventario, restaurado);
    }

    @Test
    @DisplayName("un documento anterior sin equipamiento se migra como lista vacia")
    void documentoAnteriorSinEquipamiento() {
        InventarioDocumento documento = new InventarioDocumento(
                "inventario-1", "jugador-A", java.util.List.of(), null);

        assertEquals(java.util.List.of(), documento.aDominio().equipamientos());
    }

    @Test
    @DisplayName("Spring Data asigna el identificador generado a un documento inmutable")
    void asignaIdentificadorGenerado() throws Exception {
        MongoMappingContext contexto = contextoMongo();
        var entidad = contexto.getRequiredPersistentEntity(InventarioDocumento.class);
        var acceso = entidad.getPropertyAccessor(
                InventarioDocumento.de(Inventario.vacio("jugador-A")));

        acceso.setProperty(entidad.getRequiredIdProperty(), "inventario-1");

        assertEquals("inventario-1", acceso.getBean().id());
    }

    @Test
    @DisplayName("Spring Data reconstruye el record con su constructor de persistencia")
    void reconstruyeDocumentoPersistido() throws Exception {
        MappingMongoConverter convertidor = new MappingMongoConverter(
                NoOpDbRefResolver.INSTANCE, contextoMongo());
        convertidor.afterPropertiesSet();
        Document bson = new Document("_id", "inventario-1")
                .append("propietarioId", "jugador-A")
                .append("elementos", List.of())
                .append("equipamientos", List.of());

        InventarioDocumento documento = convertidor.read(InventarioDocumento.class, bson);

        assertEquals("inventario-1", documento.id());
        assertEquals("jugador-A", documento.propietarioId());
        assertEquals(List.of(), documento.equipamientos());
    }

    private MongoMappingContext contextoMongo() throws Exception {
        MongoMappingContext contexto = new MongoMappingContext();
        contexto.setInitialEntitySet(java.util.Set.of(InventarioDocumento.class));
        contexto.afterPropertiesSet();
        return contexto;
    }
}
