package nexus.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nexus.dominio.Producto;
import nexus.dominio.RespaldoProducto;
import nexus.dominio.TipoCambioProducto;

/** Entrada de auditoría de solo lectura para el panel administrativo. */
public record CambioProductoVista(
        String id,
        String productoId,
        TipoCambioProducto tipo,
        List<String> campos,
        String autor,
        Instant fecha,
        Integer versionAnterior,
        Integer versionAplicada,
        String reversionDe,
        boolean revertible) {

        public static CambioProductoVista de(RespaldoProducto respaldo, int versionActual) {
                int anterior = respaldo.estadoAnterior() == null ? 0 : respaldo.estadoAnterior().version();
                Integer aplicada = respaldo.estadoAplicado() == null
                        ? anterior + 1
                        : respaldo.estadoAplicado().version();
                return new CambioProductoVista(
                                respaldo.id(),
                                respaldo.productoId(),
                                respaldo.tipoCambioNormalizado(),
                                camposCambiados(respaldo.estadoAnterior(), respaldo.estadoAplicado()),
                                respaldo.autor(),
                                respaldo.modificadoEn(),
                                anterior,
                                aplicada,
                                respaldo.reversionDe(),
                                respaldo.estadoAnterior() != null && aplicada == versionActual);
        }

        private static List<String> camposCambiados(Producto anterior, Producto aplicado) {
                if (anterior == null) {
                        return List.of("producto creado");
                }
                if (aplicado == null) {
                        return List.of("estado del producto");
                }
                List<String> campos = new ArrayList<>();
                agregarSiCambio(campos, "nombre", anterior.nombre(), aplicado.nombre());
                agregarSiCambio(campos, "imagen", anterior.imagen(), aplicado.imagen());
                agregarSiCambio(campos, "descripción", anterior.descripcion(), aplicado.descripcion());
                agregarSiCambio(campos, "tiraje", anterior.tiraje(), aplicado.tiraje());
                agregarSiCambio(campos, "precio en créditos", anterior.precioCreditos(), aplicado.precioCreditos());
                agregarSiCambio(campos, "precio real", anterior.precioMonedaReal(), aplicado.precioMonedaReal());
                agregarSiCambio(campos, "premium", anterior.premium(), aplicado.premium());
                agregarSiCambio(campos, "prototipo", anterior.prototipo(), aplicado.prototipo());
                agregarSiCambio(campos, "héroe", anterior.heroe(), aplicado.heroe());
                agregarSiCambio(campos, "costo de poder", anterior.costoPoder(), aplicado.costoPoder());
                agregarSiCambio(campos, "multiplicador", anterior.multiplicadorNivel(), aplicado.multiplicadorNivel());
                agregarSiCambio(campos, "turnos de carga", anterior.turnosCarga(), aplicado.turnosCarga());
                agregarSiCambio(campos, "turnos de recarga", anterior.turnosRecarga(), aplicado.turnosRecarga());
                agregarSiCambio(campos, "efecto general", anterior.efectoGeneral(), aplicado.efectoGeneral());
                agregarSiCambio(campos, "efecto potenciado", anterior.efectoPotenciado(), aplicado.efectoPotenciado());
                agregarSiCambio(campos, "defensa", anterior.defensa(), aplicado.defensa());
                agregarSiCambio(campos, "parte", anterior.parte(), aplicado.parte());
                agregarSiCambio(campos, "efecto", anterior.efecto(), aplicado.efecto());
                agregarSiCambio(campos, "poder de ataque", anterior.poderDeAtaque(), aplicado.poderDeAtaque());
                agregarSiCambio(campos, "tasa de caída", anterior.tasaDeCaida(), aplicado.tasaDeCaida());
                agregarSiCambio(campos, "estado", anterior.estado(), aplicado.estado());
                agregarSiCambio(campos, "promoción", anterior.promocion(), aplicado.promocion());
                return List.copyOf(campos);
        }

        private static void agregarSiCambio(
                        List<String> campos,
                        String nombre,
                        Object anterior,
                        Object aplicado) {
                if (!Objects.equals(anterior, aplicado)) {
                        campos.add(nombre);
                }
        }
}
