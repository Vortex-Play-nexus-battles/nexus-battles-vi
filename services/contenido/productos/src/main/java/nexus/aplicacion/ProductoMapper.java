package nexus.aplicacion;

import java.time.Instant;

import nexus.api.ProductoCreado;
import nexus.api.SolicitudCrearProducto;
import nexus.dominio.Producto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductoMapper {

        @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID().toString())")
        @Mapping(target = "estado", constant = "ACTIVO")
        @Mapping(target = "version", constant = "1")
        @Mapping(target = "creadoEn", source = "ahora")
        @Mapping(target = "modificadoEn", source = "ahora")
        Producto aProducto(SolicitudCrearProducto solicitud, Instant ahora);

        ProductoCreado aRespuesta(Producto producto);
}
