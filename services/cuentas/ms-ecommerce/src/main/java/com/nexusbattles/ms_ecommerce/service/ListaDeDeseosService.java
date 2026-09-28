package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.catalogo.CatalogoMaestro;
import com.nexusbattles.ms_ecommerce.catalogo.CatalogoNoDisponibleException;
import com.nexusbattles.ms_ecommerce.catalogo.CopiaDelCatalogo;
import com.nexusbattles.ms_ecommerce.catalogo.ProductoDelCatalogo;
import com.nexusbattles.ms_ecommerce.dto.EntradaListaDeseosDto;
import com.nexusbattles.ms_ecommerce.model.ListaDeseos;
import com.nexusbattles.ms_ecommerce.repository.ListaDeseosRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * La lista de deseos del jugador (7.5, RF-CAR-005), persistente y por
 * {@code uid} del token: la misma en cualquier navegador y sesion.
 *
 * <p>Anadir y quitar son idempotentes: la lista es un conjunto. Anadir exige
 * que el catalogo tenga el producto (un id que no existe no se guarda); quitar
 * no pregunta a nadie, ni siquiera si estaba.
 */
@Service
public class ListaDeDeseosService {

    private final ListaDeseosRepository repositorio;
    private final CatalogoMaestro catalogo;
    private final CopiaDelCatalogo copia;
    private final Clock reloj;

    public ListaDeDeseosService(ListaDeseosRepository repositorio, CatalogoMaestro catalogo, CopiaDelCatalogo copia,
                                Clock reloj) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.copia = copia;
        this.reloj = reloj;
    }

    /** La lista, de lo mas reciente a lo mas antiguo, con nombre e imagen del catalogo si responde. */
    public List<EntradaListaDeseosDto> de(String usuarioId) {
        List<ListaDeseos> guardados = repositorio
                .findByUsuarioIdAndProductoRefIsNotNullOrderByAgregadoEnDescIdDesc(usuarioId);
        Map<String, ProductoDelCatalogo> catalogoActual = copia.siDisponible().orElse(Map.of());
        return guardados.stream().map(guardado -> aDto(guardado, catalogoActual)).toList();
    }

    /** Los productos de la lista, para marcar la vitrina. */
    public Set<String> productosDe(String usuarioId) {
        return new HashSet<>(repositorio.productosDe(usuarioId));
    }

    /**
     * Anade el producto (o confirma que ya estaba).
     *
     * @throws ProductoNoEncontradoException si el catalogo no lo tiene
     * @throws CatalogoNoDisponibleException si el catalogo no se pudo consultar
     */
    public EntradaListaDeseosDto anadir(String usuarioId, String productoId) {
        ProductoDelCatalogo producto = catalogo.producto(productoId)
                .orElseThrow(() -> new ProductoNoEncontradoException("El producto no existe en el catalogo."));
        String referencia = producto.id() != null ? producto.id() : productoId;
        repositorio.anadirSiNoEsta(usuarioId, referencia, producto.nombre(), reloj.instant());
        ListaDeseos guardado = repositorio.findByUsuarioIdAndProductoRef(usuarioId, referencia)
                .orElseThrow(() -> new IllegalStateException("La lista de deseos no guardo " + referencia));
        return aDto(guardado, Map.of(referencia, producto));
    }

    /** Quita el producto; si no estaba, no pasa nada. */
    public void quitar(String usuarioId, String productoId) {
        repositorio.quitar(usuarioId, productoId);
    }

    private static EntradaListaDeseosDto aDto(ListaDeseos guardado, Map<String, ProductoDelCatalogo> catalogo) {
        Optional<ProductoDelCatalogo> producto = Optional.ofNullable(catalogo.get(guardado.getProductoRef()));
        Instant agregadoEn = guardado.getAgregadoEn();
        return new EntradaListaDeseosDto(
                guardado.getProductoRef(),
                producto.map(ProductoDelCatalogo::nombre).orElse(guardado.getProductoNombre()),
                producto.map(ProductoDelCatalogo::imagen).orElse(null),
                agregadoEn);
    }
}
