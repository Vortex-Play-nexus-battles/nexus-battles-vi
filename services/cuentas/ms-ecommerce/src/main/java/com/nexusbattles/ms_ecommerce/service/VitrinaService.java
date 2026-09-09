package com.nexusbattles.ms_ecommerce.service;

import com.nexusbattles.ms_ecommerce.dto.ProductoVitrinaDto;
import com.nexusbattles.ms_ecommerce.model.Producto;
import com.nexusbattles.ms_ecommerce.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VitrinaService {

    private final ProductoRepository productoRepository;

    public Page<ProductoVitrinaDto> obtenerProductosVitrina(Pageable pageable, String moneda) {
        Page<Producto> productos = productoRepository.findAll(pageable);
        return productos.map(producto -> convertirADto(producto, moneda));
    }

    private ProductoVitrinaDto convertirADto(Producto producto, String moneda) {
        return ProductoVitrinaDto.builder()
                .id(producto.getId())
                .nombre(producto.getNombre())
                .imagenUrl(producto.getImagenUrl())
                .descripcion(producto.getDescripcion())
                .habilidades(producto.getHabilidades())
                .tipo(producto.getTipo())
                .precioOriginal(producto.getPrecioBaseCop())
                .precioFinal(producto.getPrecioBaseCop())
                .moneda(moneda != null ? moneda : "COP")
                .enPromocion(producto.getEnPromocion())
                .porcentajeDescuento(producto.getPorcentajeDescuento())
                .esPropio(false)
                .enListaDeseos(false)
                .build();
    }
}