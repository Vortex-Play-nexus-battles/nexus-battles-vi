package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.FiltrosSubasta;
import com.nexusbattles.ms_subastas.subastas.dto.PaginaDeSubastasResponse;
import com.nexusbattles.ms_subastas.subastas.dto.SubastaResumenResponse;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * HU-SUB-011. Listado paginado de subastas activas, con filtros y orden
 * dinamicos, segun contracts/openapi/ms-subastas-listado.yaml.
 */
@Service
public class SubastaListadoService {

    private final SubastaRepository subastaRepository;
    private final Clock clock;

    public SubastaListadoService(SubastaRepository subastaRepository, Clock clock) {
        this.subastaRepository = subastaRepository;
        this.clock = clock;
    }

    public PaginaDeSubastasResponse listar(FiltrosSubasta filtros, int pagina, int tamano) {
        // Specification.and(null) lanza IllegalArgumentException, asi que se
        // filtran los null antes de combinar. OJO: List.of(...) NO admite
        // elementos null (revienta al construirla, antes de poder filtrar),
        // por eso aqui se usa Stream.of(...), que si los tolera hasta el
        // filter().
        List<Specification<Subasta>> filtrosNoNulos = Stream.of(
            SubastaSpecifications.soloActivas(),
            SubastaSpecifications.textoLibre(filtros.q()),
            SubastaSpecifications.porTipoProducto(filtros.tipoProducto()),
            SubastaSpecifications.porRareza(filtros.rareza()),
            SubastaSpecifications.precioMinimo(filtros.precioMin()),
            SubastaSpecifications.precioMaximo(filtros.precioMax()),
            SubastaSpecifications.tiempoRestante(filtros.tiempoRestante(), Instant.now(clock)),
            SubastaSpecifications.porTipoVenta(filtros.tipoVenta()),
            SubastaSpecifications.porMetodoPago(filtros.metodoPago()),
            SubastaSpecifications.porVendedor(filtros.vendedor())
        ).filter(Objects::nonNull).toList();

        Specification<Subasta> spec = Specification.allOf(filtrosNoNulos);

        Pageable pageable = PageRequest.of(pagina, tamano, construirOrden(filtros.ordenarPor()));

        Page<Subasta> paginaEntidades = subastaRepository.findAll(spec, pageable);
        Page<SubastaResumenResponse> paginaDto = paginaEntidades.map(SubastaResumenResponse::desde);

        return PaginaDeSubastasResponse.desde(paginaDto);
    }

    /**
     * TEMPORAL, pendiente de confirmar con Edwin (SRS 7.7.4): el Maestro de
     * Juego siempre va primero, como criterio de orden previo a cualquier
     * otro. Dentro de cada grupo (MdJ / jugadores) se aplica el orden que
     * pidio el usuario. Si la decision final es otra, este es el unico
     * lugar que hay que tocar.
     */
    private Sort construirOrden(String ordenarPor) {
        Sort prioridadMdj = Sort.by(Sort.Direction.DESC, "esMaestroDeJuego");
        Sort ordenElegido = switch (ordenarPor == null ? "" : ordenarPor) {
            case "PRECIO_ASC" -> Sort.by(Sort.Direction.ASC, "ofertaVigente");
            case "PRECIO_DESC" -> Sort.by(Sort.Direction.DESC, "ofertaVigente");
            case "TIEMPO_RESTANTE" -> Sort.by(Sort.Direction.ASC, "fechaFin");
            case "PUJAS" -> Sort.by(Sort.Direction.DESC, "cantidadPujas");
            case "POPULARIDAD" -> Sort.by(Sort.Direction.DESC, "vistas");
            default -> Sort.by(Sort.Direction.DESC, "fechaPublicacion");
        };
        return prioridadMdj.and(ordenElegido);
    }
}
