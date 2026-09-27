package nexus.aplicacion;

import java.time.Instant;

import nexus.api.ProductoCreado;
import nexus.api.PromocionVista;
import nexus.api.SolicitudCrearProducto;
import nexus.api.SolicitudModificarProducto;
import nexus.api.SolicitudPromocion;
import nexus.dominio.Producto;
import nexus.dominio.Promocion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductoMapper {

        // B4: un alta de la API es de ADMINISTRACION; las marcas de la semilla,
        // el autor de la ultima edicion, el estado anterior a una suspension y
        // las claves de reserva nacen vacios.
        @Mapping(target = "id", expression = "java(java.util.UUID.randomUUID().toString())")
        @Mapping(target = "estado", constant = "ACTIVO")
        @Mapping(target = "version", constant = "1")
        @Mapping(target = "creadoEn", source = "ahora")
        @Mapping(target = "modificadoEn", source = "ahora")
        @Mapping(target = "origen", constant = "ADMINISTRACION")
        @Mapping(target = "semillaVersion", ignore = true)
        @Mapping(target = "modificadoPor", ignore = true)
        @Mapping(target = "estadoAnteriorSuspension", ignore = true)
        @Mapping(target = "reservasRecientes", ignore = true)
        Producto aProducto(SolicitudCrearProducto solicitud, Instant ahora);

        /**
         * Proyeccion completa (B4): lo que ve un servicio o un administrador,
         * con la promocion ya evaluada por {@link ProyeccionDeProductos}.
         */
        @Mapping(target = "promocion", source = "promocionVigente")
        ProductoCreado aRespuestaCompleta(Producto producto, PromocionVista promocionVigente);

        /**
         * Proyeccion publica (B4): sin los campos internos. Quedan nulos y el
         * JSON, que se publica con {@code non_null}, no los trae.
         */
        @Mapping(target = "promocion", source = "promocionVigente")
        @Mapping(target = "version", ignore = true)
        @Mapping(target = "tasaDeCaida", ignore = true)
        @Mapping(target = "origen", ignore = true)
        @Mapping(target = "semillaVersion", ignore = true)
        @Mapping(target = "modificadoPor", ignore = true)
        ProductoCreado aRespuestaPublica(Producto producto, PromocionVista promocionVigente);

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
                        cambios.tasaDeCaida() != null ? cambios.tasaDeCaida() : existente.tasaDeCaida(),
                        cambios.promocion() != null ? cambios.promocion() : comoSolicitud(existente.promocion()));
        }

        /** La promocion guardada, en la forma de la solicitud, para validarla con las mismas reglas. */
        default SolicitudPromocion comoSolicitud(Promocion promocion) {
                return promocion == null
                        ? null
                        : new SolicitudPromocion(promocion.porcentaje(), promocion.desde(), promocion.hasta());
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
        @Mapping(target = "promocion", source = "solicitudFusionada.promocion")
        @Mapping(target = "estado", source = "existente.estado")
        // B4: version se CONSERVA. Desde que Producto la declara @Version, es
        // Spring Data quien la sube al guardar, y solo si nadie escribio desde
        // que se leyo; subirla aqui haria que el guardado buscara una version
        // que no existe y fallara siempre con conflicto.
        @Mapping(target = "version", source = "existente.version")
        @Mapping(target = "creadoEn", source = "existente.creadoEn")
        @Mapping(target = "modificadoEn", source = "ahora")
        @Mapping(target = "origen", source = "existente.origen")
        @Mapping(target = "semillaVersion", source = "existente.semillaVersion")
        // Quien edita el contenido queda anotado: es lo que dice a la semilla
        // versionada que este producto ya no es solo suyo.
        @Mapping(target = "modificadoPor", source = "autor")
        @Mapping(target = "estadoAnteriorSuspension", source = "existente.estadoAnteriorSuspension")
        @Mapping(target = "reservasRecientes", source = "existente.reservasRecientes")
        Producto actualizar(
                        SolicitudCrearProducto solicitudFusionada,
                        Producto existente,
                        Instant ahora,
                        String autor);
}
