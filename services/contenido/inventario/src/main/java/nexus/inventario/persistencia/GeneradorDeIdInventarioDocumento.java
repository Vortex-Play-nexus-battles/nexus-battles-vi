package nexus.inventario.persistencia;

import java.util.UUID;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;

/**
 * Genera el id de {@code InventarioDocumento} ANTES de que Spring Data
 * MongoDB escriba el documento, en vez de depender de que Mongo lo genere
 * y Spring Data se lo asigne de vuelta al objeto despues del insert — ese
 * es justamente el paso que falla, porque {@code InventarioDocumento} (de
 * Nicolay, no se toca) es un record sin setter, wither ni constructor de
 * copia para {@code id}: "Cannot set property id because no setter,
 * wither or copy constructor exists".
 *
 * <p>Al fijar el id aqui, con el mismo constructor canonico publico que ya
 * expone el record, el documento nunca llega a Mongo con id nulo, asi que
 * Spring Data nunca necesita "devolverselo" al objeto — el problema de
 * raiz desaparece sin tocar ningun archivo ajeno.
 *
 * <p>Sin {@code @Component} a proposito: se registra via
 * {@link InventarioPersistenciaAutoConfiguration} (auto-configuracion de
 * Spring Boot) en vez de component-scan, porque
 * {@code RepositorioInventariosMongoIT} (de Nicolay, tampoco se toca) usa
 * {@code @DataMongoTest}, que excluye los {@code @Component} genericos del
 * escaneo salvo que el test los importe explicitamente — y no podemos
 * editar ese test para agregar el import. Las auto-configuraciones si se
 * evaluan en los test slices, sin necesitar ningun cambio en los tests.
 */
public class GeneradorDeIdInventarioDocumento implements BeforeConvertCallback<InventarioDocumento> {

    @Override
    public InventarioDocumento onBeforeConvert(InventarioDocumento documento, String coleccion) {
        if (documento.id() != null) {
            return documento;
        }
        return new InventarioDocumento(
                UUID.randomUUID().toString(),
                documento.propietarioId(),
                documento.elementos(),
                documento.equipamientos());
    }
}
