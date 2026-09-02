package nexus.aplicacion;

import java.time.Instant;

import nexus.api.ProductoCreado;
import nexus.api.SolicitudCrearProducto;
import nexus.api.SolicitudModificarProducto;
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

        default SolicitudCrearProducto fusionar(Producto existente, SolicitudModificarProducto cambios) {
                return new SolicitudCrearProducto(
                        cambios.nombre() != null ? cambios.nombre() : existente.nombre(),
                        cambios.imagen() != null ? cambios.imagen() : existente.imagen(),
                        cambios.descripcion() != null ? cambios.descripcion() : existente.descripcion(),
                        existente.tipo(),
                        cambios.tiraje() != null ? cambios.tiraje() : existente.tiraje(),
                        cambios.precioCreditos() != null ? cambios.precioCreditos() : existente.precioCreditos(),
                        cambios.precioMonedaReal() != null ? cambios.precioMonedaReal() : existente.precioMonedaReal(),
                        cambios.premium() != null ? cambios.premium() : existente.premium(),
                        cambios.prototipo() != null ? cambios.prototipo() : existente.prototipo(),
                        cambios.heroe() != null ? cambios.heroe() : existente.heroe(),
                        cambios.costoPoder() != null ? cambios.costoPoder() : existente.costoPoder(),
                        cambios.multiplicadorNivel() != null ? cambios.multiplicadorNivel() : existente.multiplicadorNivel(),
                        cambios.turnosCarga() != null ? cambios.turnosCarga() : existente.turnosCarga(),
                        cambios.turnosRecarga() != null ? cambios.turnosRecarga() : existente.turnosRecarga(),
                        cambios.efectoGeneral() != null ? cambios.efectoGeneral() : existente.efectoGeneral(),
                        cambios.efectoPotenciado() != null ? cambios.efectoPotenciado() : existente.efectoPotenciado(),
                        cambios.defensa() != null ? cambios.defensa() : existente.defensa(),
                        cambios.parte() != null ? cambios.parte() : existente.parte(),
                        cambios.efecto() != null ? cambios.efecto() : existente.efecto(),
                        cambios.poderDeAtaque() != null ? cambios.poderDeAtaque() : existente.poderDeAtaque(),
                        cambios.tasaDeCaida() != null ? cambios.tasaDeCaida() : existente.tasaDeCaida());
        }

        // DECISION EXPLICITA: solicitudFusionada y existente comparten ~20 nombres
        // de campo identicos (nombre, tiraje, precioCreditos, etc.), asi que
        // MapStruct no puede inferir la fuente por si solo (falla en compilacion
        // con "Several possible source properties"). Se resuelve fijando
        // explicitamente el origen de CADA campo compartido en solicitudFusionada
        // (el resultado ya fusionado y validado), no en existente.
        @Mapping(target = "id", source = "existente.id")
        @Mapping(target = "nombre", source = "solicitudFusionada.nombre")
        @Mapping(target = "imagen", source = "solicitudFusionada.imagen")
        @Mapping(target = "descripcion", source = "solicitudFusionada.descripcion")
        @Mapping(target = "tipo", source = "solicitudFusionada.tipo")
        @Mapping(target = "tiraje", source = "solicitudFusionada.tiraje")
        @Mapping(target = "precioCreditos", source = "solicitudFusionada.precioCreditos")
        @Mapping(target = "precioMonedaReal", source = "solicitudFusionada.precioMonedaReal")
        @Mapping(target = "premium", source = "solicitudFusionada.premium")
        @Mapping(target = "prototipo", source = "solicitudFusionada.prototipo")
        @Mapping(target = "heroe", source = "solicitudFusionada.heroe")
        @Mapping(target = "costoPoder", source = "solicitudFusionada.costoPoder")
        @Mapping(target = "multiplicadorNivel", source = "solicitudFusionada.multiplicadorNivel")
        @Mapping(target = "turnosCarga", source = "solicitudFusionada.turnosCarga")
        @Mapping(target = "turnosRecarga", source = "solicitudFusionada.turnosRecarga")
        @Mapping(target = "efectoGeneral", source = "solicitudFusionada.efectoGeneral")
        @Mapping(target = "efectoPotenciado", source = "solicitudFusionada.efectoPotenciado")
        @Mapping(target = "defensa", source = "solicitudFusionada.defensa")
        @Mapping(target = "parte", source = "solicitudFusionada.parte")
        @Mapping(target = "efecto", source = "solicitudFusionada.efecto")
        @Mapping(target = "poderDeAtaque", source = "solicitudFusionada.poderDeAtaque")
        @Mapping(target = "tasaDeCaida", source = "solicitudFusionada.tasaDeCaida")
        @Mapping(target = "estado", source = "existente.estado")
        // DECISION EXPLICITA: version se incrementa en cada modificacion exitosa.
        // No implementa control de concurrencia optimista (Producto no tiene
        // @Version de Spring Data - ver NOTA en ModificarProductoServicio); es
        // solo un rastro de "cuantas veces se edito", inofensivo porque hoy nada
        // mas lee este campo.
        @Mapping(target = "version", expression = "java(existente.version() + 1)")
        @Mapping(target = "creadoEn", source = "existente.creadoEn")
        @Mapping(target = "modificadoEn", source = "ahora")
        Producto actualizar(SolicitudCrearProducto solicitudFusionada, Producto existente, Instant ahora);
}
