package nexus.inventario.aplicacion;

import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.EquipamientoHeroe;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.ParteArmadura;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.stereotype.Service;

/**
 * Equipamiento de un heroe propio (HU-INV-005).
 *
 * <p>Reglas, todas del documento (6.1.2) o de los bloqueos del inventario:
 * dos armas, dos items y una armadura por parte del cuerpo (seis partes); solo
 * ARMA, ARMADURA e ITEM ocupan ranura; el heroe y el elemento son del mismo
 * jugador; un elemento en subasta o equipado en otro heroe no se equipa, y un
 * heroe en subasta no cambia de equipo (B4).
 *
 * <p><b>La parte de una armadura la decide el catalogo</b> (B4): al equipar se
 * consulta el producto y se usa su {@code parte}, no la que guardo el elemento
 * —hasta B4 la mandaba el cliente al crearlo y nadie la comparaba—. Si difiere,
 * la guardada se corrige en la misma escritura.
 *
 * <p><b>Lo que no se comprueba, a proposito:</b> que el objeto sea "del" tipo
 * de heroe. Las Tablas 8 a 19 asocian cada arma, armadura e item a un tipo de
 * heroe, pero el documento no enuncia una regla que impida equipar los de otro
 * tipo (la unica afinidad con efecto que define es la de las epicas, Tabla 20).
 * No se inventa: queda como decision del PO, y la asociacion ya esta en
 * {@code contracts/esquemas/catalogo-oficial.yaml} para cuando se decida.
 */
@Service
public class GestionarEquipamiento {

    private final RepositorioDeInventarios repositorio;
    private final ResolutorDeProducto productos;

    public GestionarEquipamiento(RepositorioDeInventarios repositorio, ResolutorDeProducto productos) {
        this.repositorio = repositorio;
        this.productos = productos;
    }

    public EquipamientoHeroe consultar(String identidad, String heroeId) {
        Inventario inventario = inventarioPropio(identidad, heroeId);
        return inventario.equipamiento(heroeId);
    }

    public EquipamientoHeroe equipar(String identidad, String heroeId, String elementoId) {
        Inventario inventario = inventarioPropio(identidad, heroeId);
        exigirElementoPropio(inventario, elementoId);
        ParteArmadura parte = parteDelCatalogo(inventario.elemento(elementoId));
        Inventario guardado = repositorio.guardar(inventario.equipar(heroeId, elementoId, parte));
        return guardado.equipamiento(heroeId);
    }

    public EquipamientoHeroe desequipar(String identidad, String heroeId, String elementoId) {
        Inventario inventario = inventarioPropio(identidad, heroeId);
        exigirElementoPropio(inventario, elementoId);
        Inventario guardado = repositorio.guardar(inventario.desequipar(heroeId, elementoId));
        return guardado.equipamiento(heroeId);
    }

    /**
     * La parte que ocupa una armadura segun su producto; nulo si el elemento
     * no es una armadura o si el catalogo no la declara (entonces vale la
     * guardada). Un producto que ya no existe es 404, y un catalogo que no
     * responde, 503: equipar a ciegas es lo que dejaba elegir la ranura al
     * cliente.
     */
    private ParteArmadura parteDelCatalogo(ElementoInventario elemento) {
        if (elemento.tipo() != TipoElementoInventario.ARMADURA) {
            return null;
        }
        try {
            return productos.resolver(elemento.productoId()).parteArmadura();
        } catch (ProductoNoEncontradoException noExiste) {
            throw noExiste;
        } catch (ResolutorDeProductoException caido) {
            throw new CatalogoNoDisponibleException(caido);
        }
    }

    private Inventario inventarioPropio(String identidad, String heroeId) {
        String propietarioId = exigirIdentidad(identidad);
        Inventario inventario = repositorio.buscarPorElementoId(heroeId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventario.propietarioId().equalsIgnoreCase(propietarioId)) {
            throw new InventarioAjenoException();
        }
        return inventario;
    }

    private void exigirElementoPropio(Inventario inventario, String elementoId) {
        Inventario inventarioDelElemento = repositorio.buscarPorElementoId(elementoId)
                .orElseThrow(ElementoNoEncontradoException::new);
        if (!inventarioDelElemento.propietarioId().equalsIgnoreCase(inventario.propietarioId())) {
            throw new InventarioAjenoException();
        }
    }

    private String exigirIdentidad(String identidad) {
        if (identidad == null || identidad.isBlank()) {
            throw new IdentidadRequeridaException();
        }
        return identidad.trim();
    }
}
