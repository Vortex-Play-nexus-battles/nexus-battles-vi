package nexus.inventario.aplicacion;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import nexus.inventario.dominio.ElementoInventario;
import nexus.inventario.dominio.ElementoNoEncontradoException;
import nexus.inventario.dominio.Inventario;
import nexus.inventario.dominio.RepositorioDeInventarios;
import nexus.inventario.dominio.TipoElementoInventario;
import org.springframework.stereotype.Service;

@Service
public class TransferirEquipoPorCombate {

    private static final Set<TipoElementoInventario> TIPOS_TRANSFERIBLES = Set.of(
            TipoElementoInventario.ARMA,
            TipoElementoInventario.ARMADURA,
            TipoElementoInventario.ITEM);

    private final RepositorioDeInventarios repositorio;

    public TransferirEquipoPorCombate(RepositorioDeInventarios repositorio) {
        this.repositorio = Objects.requireNonNull(repositorio, "El repositorio es obligatorio");
    }

    public synchronized List<ElementoInventario> transferir(
            String operacionId,
            List<TransferenciaCombate> transferencias) {
        exigirTexto(operacionId, "operacionId");
        List<TransferenciaCombate> lote = validarLote(transferencias);
        lote.forEach(this::validarEstado);
        return lote.stream().map(this::transferirUna).toList();
    }

    private ElementoInventario transferirUna(TransferenciaCombate transferencia) {
        List<Inventario> propietarios = repositorio.buscarTodosPorElementoId(transferencia.elementoId());
        Inventario destinoExistente = buscar(propietarios, transferencia.propietarioDestinoId());
        Inventario origenExistente = buscar(propietarios, transferencia.propietarioOrigenId());

        if (destinoExistente != null) {
            if (origenExistente != null) {
                repositorio.guardar(extraer(origenExistente, transferencia));
            }
            return destinoExistente.elemento(transferencia.elementoId());
        }

        ElementoInventario elemento = origenExistente.elemento(transferencia.elementoId());
        Inventario destino = repositorio.buscarPorPropietario(transferencia.propietarioDestinoId())
                .orElseGet(() -> Inventario.vacio(transferencia.propietarioDestinoId()));
        Inventario guardado = repositorio.guardar(destino.agregar(elemento));
        repositorio.guardar(extraer(origenExistente, transferencia));
        return guardado.elemento(transferencia.elementoId());
    }

    private void validarEstado(TransferenciaCombate transferencia) {
        List<Inventario> propietarios = repositorio.buscarTodosPorElementoId(transferencia.elementoId());
        if (propietarios.isEmpty()) {
            throw new ElementoNoEncontradoException();
        }
        boolean perteneceSoloALaOperacion = propietarios.stream().allMatch(inventario ->
                inventario.propietarioId().equals(transferencia.propietarioOrigenId())
                        || inventario.propietarioId().equals(transferencia.propietarioDestinoId()));
        if (!perteneceSoloALaOperacion || propietarios.size() > 2) {
            throw new TransferenciaCombateInvalidaException(
                    "El elemento pertenece a un jugador diferente del origen o destino");
        }
        Inventario origen = buscar(propietarios, transferencia.propietarioOrigenId());
        Inventario destino = buscar(propietarios, transferencia.propietarioDestinoId());
        if (origen == null && destino == null) {
            throw new TransferenciaCombateInvalidaException("No se encontro el propietario del elemento");
        }
        if (origen != null) {
            ElementoInventario elemento = origen.elemento(transferencia.elementoId());
            if (!TIPOS_TRANSFERIBLES.contains(elemento.tipo())) {
                throw new TransferenciaCombateInvalidaException(
                        "Solo se puede transferir un arma, una armadura o un item");
            }
            if (!origen.equipamiento(transferencia.heroeOrigenId()).contiene(transferencia.elementoId())) {
                throw new TransferenciaCombateInvalidaException(
                        "El elemento no esta equipado en el heroe derrotado");
            }
        }
    }

    private Inventario extraer(Inventario origen, TransferenciaCombate transferencia) {
        return origen
                .desequipar(transferencia.heroeOrigenId(), transferencia.elementoId())
                .eliminarElemento(transferencia.elementoId());
    }

    private static Inventario buscar(List<Inventario> inventarios, String propietarioId) {
        return inventarios.stream()
                .filter(inventario -> inventario.propietarioId().equals(propietarioId))
                .findFirst()
                .orElse(null);
    }

    private static List<TransferenciaCombate> validarLote(List<TransferenciaCombate> transferencias) {
        Objects.requireNonNull(transferencias, "Las transferencias son obligatorias");
        if (transferencias.isEmpty()) {
            throw new IllegalArgumentException("Debe existir al menos una transferencia");
        }
        List<TransferenciaCombate> copia = List.copyOf(transferencias);
        Set<String> elementos = new HashSet<>();
        for (TransferenciaCombate transferencia : copia) {
            Objects.requireNonNull(transferencia, "Una transferencia no puede ser nula");
            if (!elementos.add(transferencia.elementoId())) {
                throw new IllegalArgumentException("Un elemento no puede transferirse dos veces en la misma operacion");
            }
        }
        return copia;
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
    }
}
