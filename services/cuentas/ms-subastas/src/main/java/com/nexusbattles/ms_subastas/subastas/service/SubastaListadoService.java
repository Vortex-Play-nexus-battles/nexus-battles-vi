package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.FiltrosSubasta;
import com.nexusbattles.ms_subastas.subastas.dto.PaginaDeSubastasResponse;
import com.nexusbattles.ms_subastas.subastas.dto.SubastaResumenResponse;
import com.nexusbattles.ms_subastas.subastas.dto.SugerenciasResponse;
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
import java.util.LinkedHashSet;
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

    public SugerenciasResponse sugerir(String texto, int limite) {
        List<Subasta> subastas = subastaRepository.buscarSugeridasPorTexto(
            texto, PageRequest.of(0, limite));

        LinkedHashSet<String> nombresUnicos = new LinkedHashSet<>();
        for (Subasta subasta : subastas) {
            nombresUnicos.add(subasta.getNombreProducto());
        }

        return new SugerenciasResponse(List.copyOf(nombresUnicos));
    }

    /**
     * Confirmado con Edwin (RF-SUB-010 / HU-SUB-010 del SRS, cita literal:
     * "prioridad en el ordenamiento por defecto"): el Maestro de Juego solo
     * va primero cuando NO hay un orden explicito del usuario. En cuanto el
     * usuario elige un criterio (precio, tiempo, pujas, fecha, popularidad),
     * el orden es global y se mezclan todas las subastas -- si se siguiera
     * agrupando por MdJ, "ordenar por precio" dejaria de ser un orden real
     * por precio.
     */
    private Sort construirOrden(String ordenarPor) {
        if (ordenarPor == null || ordenarPor.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "esMaestroDeJuego")
                .and(Sort.by(Sort.Direction.DESC, "fechaPublicacion"));
        }

        return switch (ordenarPor) {
            case "PRECIO_ASC" -> Sort.by(Sort.Direction.ASC, "ofertaVigente");
            case "PRECIO_DESC" -> Sort.by(Sort.Direction.DESC, "ofertaVigente");
            case "TIEMPO_RESTANTE" -> Sort.by(Sort.Direction.ASC, "fechaFin");
            case "PUJAS" -> Sort.by(Sort.Direction.DESC, "cantidadPujas");
            case "POPULARIDAD" -> Sort.by(Sort.Direction.DESC, "vistas");
            default -> Sort.by(Sort.Direction.DESC, "esMaestroDeJuego")
                .and(Sort.by(Sort.Direction.DESC, "fechaPublicacion"));
        };
    }
}
