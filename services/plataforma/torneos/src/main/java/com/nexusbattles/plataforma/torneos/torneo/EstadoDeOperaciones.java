package com.nexusbattles.plataforma.torneos.torneo;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lo que las operaciones de un torneo significan para la vista
 * (torneos.yaml 1.2.0): en que va el pago de cada equipo
 * ({@code Equipo.estadoPago}) y la entrega del premio ({@code Torneo.premio}).
 * Se calcula aqui, una vez, para que ni el controlador ni la vista
 * reimplementen la regla.
 */
public final class EstadoDeOperaciones {

    public enum Pago { SIN_COSTO, RESERVADO, COBRO_PENDIENTE, COBRADO, DEVOLUCION_PENDIENTE, DEVUELTO, REQUIERE_REVISION }

    public enum EstadoPremio { SIN_CAMPEON, NO_APLICA, PENDIENTE, ENTREGADO, REQUIERE_REVISION }

    public enum EstadoEntrega { PENDIENTE, ENTREGADO, EXCLUIDO_POR_SANCION, REQUIERE_REVISION }

    public record Entrega(UUID uid, EstadoEntrega estado, boolean creditosEntregados, boolean epicaEntregada) { }

    public record Premio(int creditosPorIntegrante, String epicaProductoId, EstadoPremio estado, List<Entrega> entregas) { }

    private EstadoDeOperaciones() {
    }

    /** Nulo si el equipo no esta inscrito o es de la maquina. */
    public static Pago pago(Equipo equipo, List<Operacion> operaciones) {
        if (!equipo.inscrito() || equipo.ia()) {
            return null;
        }
        if (equipo.reservaId() == null) {
            return Pago.SIN_COSTO;
        }
        Optional<Operacion> devolucion = deTipo(equipo, operaciones, Operacion.Tipo.DEVOLUCION_INSCRIPCION);
        if (devolucion.isPresent()) {
            return segun(devolucion.get(), Pago.DEVUELTO, Pago.DEVOLUCION_PENDIENTE);
        }
        Optional<Operacion> cobro = deTipo(equipo, operaciones, Operacion.Tipo.COBRO_INSCRIPCION);
        if (cobro.isPresent()) {
            return segun(cobro.get(), Pago.COBRADO, Pago.COBRO_PENDIENTE);
        }
        return Pago.RESERVADO;
    }

    public static Premio premio(Torneo torneo, PoliticaDePremio.Premio anunciado, List<Operacion> operaciones) {
        List<Operacion> premios = operaciones.stream().filter(o -> o.tipo() == Operacion.Tipo.PREMIO)
                .sorted(Comparator.comparing(Operacion::creadaEn)).toList();
        List<Entrega> entregas = premios.stream().map(EstadoDeOperaciones::entrega).toList();
        return new Premio(anunciado.creditosPorIntegrante(), anunciado.epicaProductoId(),
                estadoDelPremio(torneo, premios), entregas);
    }

    private static EstadoPremio estadoDelPremio(Torneo torneo, List<Operacion> premios) {
        if (torneo.estado() != Torneo.Estado.FINALIZADO) {
            return EstadoPremio.SIN_CAMPEON;
        }
        if (premios.isEmpty()) {
            return EstadoPremio.NO_APLICA;
        }
        if (premios.stream().anyMatch(o -> o.estado() == Operacion.Estado.FALLIDA)) {
            return EstadoPremio.REQUIERE_REVISION;
        }
        if (premios.stream().anyMatch(o -> o.estado().abierta())) {
            return EstadoPremio.PENDIENTE;
        }
        return EstadoPremio.ENTREGADO;
    }

    private static Entrega entrega(Operacion premio) {
        EstadoEntrega estado = switch (premio.estado()) {
            case HECHA -> EstadoEntrega.ENTREGADO;
            case EXCLUIDA -> EstadoEntrega.EXCLUIDO_POR_SANCION;
            case FALLIDA -> EstadoEntrega.REQUIERE_REVISION;
            default -> EstadoEntrega.PENDIENTE;
        };
        return new Entrega(premio.jugadorUid(), estado, premio.creditosEntregados(), premio.epicaEntregada());
    }

    private static Optional<Operacion> deTipo(Equipo equipo, List<Operacion> operaciones, Operacion.Tipo tipo) {
        return operaciones.stream()
                .filter(o -> o.tipo() == tipo && equipo.id().equals(o.equipoId()))
                .findFirst();
    }

    private static Pago segun(Operacion operacion, Pago hecha, Pago pendiente) {
        if (operacion.estado() == Operacion.Estado.HECHA) {
            return hecha;
        }
        if (operacion.estado() == Operacion.Estado.FALLIDA) {
            return Pago.REQUIERE_REVISION;
        }
        return pendiente;
    }
}
