package nexus.inventario.persistencia;

import java.util.List;
import java.util.Map;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.OrigenDeEntrega;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * El inventario de un jugador en la coleccion {@code inventarios}.
 *
 * <p>B4: {@code version} es {@link Version} de Spring Data. Cada guardado
 * reemplaza el documento entero, y ahora solo si nadie lo escribio desde que se
 * leyo; si no, {@code OptimisticLockingFailureException} y el repositorio lo
 * traduce a un conflicto (503 "Inventario no disponible", nada aplicado). Es un
 * {@link Long}: nulo significa "nuevo" (se inserta y nace en 0). Los documentos
 * anteriores a B4 no tienen version; {@link RepositorioInventariosMongo} les
 * pone la 0 antes de su primer guardado. {@code entregas} son las entregas ya
 * aplicadas (ver {@link Inventario}).
 */
@Document(collection = "inventarios")
record InventarioDocumento(
        @Id String id,
        @Indexed(unique = true) String propietarioId,
        List<ElementoDocumento> elementos,
        List<EquipamientoDocumento> equipamientos,
        List<String> entregas,
        @Version Long version) {

    @PersistenceCreator
    InventarioDocumento {
        elementos = elementos == null ? List.of() : List.copyOf(elementos);
        equipamientos = equipamientos == null ? List.of() : List.copyOf(equipamientos);
        entregas = entregas == null ? List.of() : List.copyOf(entregas);
    }

    InventarioDocumento(String id, String propietarioId, List<ElementoDocumento> elementos) {
        this(id, propietarioId, elementos, List.of());
    }

    InventarioDocumento(
            String id,
            String propietarioId,
            List<ElementoDocumento> elementos,
            List<EquipamientoDocumento> equipamientos) {
        this(id, propietarioId, elementos, equipamientos, List.of(), null);
    }

    InventarioDocumento withId(String nuevoId) {
        return new InventarioDocumento(nuevoId, propietarioId, elementos, equipamientos, entregas, version);
    }

    InventarioDocumento withVersion(Long nuevaVersion) {
        return new InventarioDocumento(id, propietarioId, elementos, equipamientos, entregas, nuevaVersion);
    }

    static InventarioDocumento de(Inventario inventario) {
        return new InventarioDocumento(
                inventario.id(),
                inventario.propietarioId(),
                inventario.elementos().stream().map(ElementoDocumento::de).toList(),
                inventario.equipamientos().stream().map(EquipamientoDocumento::de).toList(),
                inventario.entregas(),
                inventario.version());
    }

    Inventario aDominio() {
        return new Inventario(
                id,
                propietarioId,
                elementos.stream().map(ElementoDocumento::aDominio).toList(),
                // DECISION EXPLICITA (no aditiva, a diferencia del resto de este
                // archivo): se descarta el ternario null-safe que traia esta rama
                // aqui, en favor de la version de develop, porque el constructor
                // compacto de arriba ya normaliza equipamientos a List.of() cuando
                // llega null - repetir el chequeo aqui quedaria como codigo muerto
                // despues de esa fusion, no como una proteccion adicional real.
                equipamientos.stream().map(EquipamientoDocumento::aDominio).toList(),
                entregas,
                version);
    }
}

/**
 * Un elemento dentro del documento del jugador. B4 anade {@code origen},
 * {@code referencia}, {@code nivel} y {@code experiencia}; 1.6.0 (B9) anade
 * {@code ejecucionMisionId}, la mision que tiene bloqueado al heroe. Todos
 * aditivos: los documentos anteriores no los traen y se leen como nulos (un
 * heroe, en nivel 1 con 0 de experiencia y sin mision).
 */
record ElementoDocumento(
        String id,
        @TextIndexed String productoId,
        @TextIndexed TipoElementoInventario tipo,
        @TextIndexed(weight = 2) String nombrePropio,
        @TextIndexed ParteArmadura parteArmadura,
        String subastaId,
        OrigenDeEntrega origen,
        String referencia,
        Integer nivel,
        Double experiencia,
        String ejecucionMisionId) {

    @PersistenceCreator
    ElementoDocumento {
    }

    ElementoDocumento(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura) {
        this(id, productoId, tipo, nombrePropio, parteArmadura, null);
    }

    ElementoDocumento(
            String id,
            String productoId,
            TipoElementoInventario tipo,
            String nombrePropio,
            ParteArmadura parteArmadura,
            String subastaId) {
        this(id, productoId, tipo, nombrePropio, parteArmadura, subastaId, null, null, null, null, null);
    }

    static ElementoDocumento de(ElementoInventario elemento) {
        return new ElementoDocumento(
                elemento.id(), elemento.productoId(), elemento.tipo(),
                elemento.nombrePropio(), elemento.parteArmadura(), elemento.subastaId(),
                elemento.origen(), elemento.referencia(), elemento.nivel(), elemento.experiencia(),
                elemento.ejecucionMisionId());
    }

    /** Un heroe guardado antes de B4 no trae nivel ni experiencia: el dominio lo lee en nivel 1. */
    ElementoInventario aDominio() {
        return new ElementoInventario(
                id, productoId, tipo, nombrePropio, parteArmadura, subastaId,
                origen, referencia, nivel, experiencia, ejecucionMisionId);
    }
}

record EquipamientoDocumento(
        String heroeId,
        List<String> armas,
        Map<ParteArmadura, String> armaduras,
        List<String> items) {

    static EquipamientoDocumento de(EquipamientoHeroe equipamiento) {
        return new EquipamientoDocumento(
                equipamiento.heroeId(), equipamiento.armas(),
                equipamiento.armaduras(), equipamiento.items());
    }

    EquipamientoHeroe aDominio() {
        return new EquipamientoHeroe(heroeId, armas, armaduras, items);
    }
}
