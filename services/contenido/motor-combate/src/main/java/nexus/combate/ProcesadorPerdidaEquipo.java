package nexus.combate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ProcesadorPerdidaEquipo implements AlCerrarPartida {

    private final String operacionId;
    private final List<ParticipantePerdidaEquipo> participantes;
    private final InventarioBotin inventario;
    private final CatalogoBotin catalogo;
    private final TransferidorEquipo transferidor;
    private final SelectorGanador selector;
    private ResultadoPerdidaEquipo resultado;

    public ProcesadorPerdidaEquipo(
            String operacionId,
            List<ParticipantePerdidaEquipo> participantes,
            InventarioBotin inventario,
            CatalogoBotin catalogo,
            TransferidorEquipo transferidor,
            SelectorGanador selector) {
        this.operacionId = exigirTexto(operacionId, "operacionId");
        this.participantes = validarParticipantes(participantes);
        this.inventario = Objects.requireNonNull(inventario, "El inventario es obligatorio");
        this.catalogo = Objects.requireNonNull(catalogo, "El catalogo es obligatorio");
        this.transferidor = Objects.requireNonNull(transferidor, "El transferidor es obligatorio");
        this.selector = Objects.requireNonNull(selector, "El selector es obligatorio");
    }

    @Override
    public synchronized void procesar(ResultadoPartida cierre) {
        if (resultado != null) {
            return;
        }
        Objects.requireNonNull(cierre, "El resultado de la partida es obligatorio");
        List<ParticipantePerdidaEquipo> ganadores = participantes.stream()
                .filter(participante -> participante.equipoId().equals(cierre.ganadorEquipoId()))
                .toList();
        if (ganadores.isEmpty()) {
            throw new IllegalArgumentException("El equipo ganador no pertenece a la partida");
        }

        List<TransferenciaEquipo> transferencias = new ArrayList<>();
        List<PerdidaEquipoAsignada> asignaciones = new ArrayList<>();
        for (ParticipantePerdidaEquipo derrotado : participantes) {
            if (derrotado.equipoId().equals(cierre.ganadorEquipoId())) {
                continue;
            }
            seleccionarMayorTasa(derrotado).ifPresent(seleccion -> {
                ParticipantePerdidaEquipo ganador = seleccionarGanador(ganadores);
                transferencias.add(new TransferenciaEquipo(
                        seleccion.elemento().elementoId(),
                        derrotado.propietarioId(),
                        derrotado.heroeInventarioId(),
                        ganador.propietarioId()));
                asignaciones.add(new PerdidaEquipoAsignada(
                        derrotado.combatienteId(),
                        seleccion.elemento().elementoId(),
                        seleccion.producto().id(),
                        ganador.propietarioId(),
                        seleccion.producto().tasaDeCaida()));
            });
        }
        if (!transferencias.isEmpty()) {
            transferidor.transferir(operacionId, List.copyOf(transferencias));
        }
        resultado = new ResultadoPerdidaEquipo(asignaciones);
    }

    public ResultadoPerdidaEquipo resultado() {
        if (resultado == null) {
            throw new IllegalStateException("La partida aun no ha finalizado");
        }
        return resultado;
    }

    private java.util.Optional<Seleccion> seleccionarMayorTasa(ParticipantePerdidaEquipo derrotado) {
        return inventario.listarCandidatos(
                        derrotado.propietarioId(),
                        derrotado.heroeInventarioId()).stream()
                .filter(elemento -> elemento.origen() == OrigenBotin.EQUIPADO)
                .map(elemento -> new Seleccion(elemento, consultarYValidar(elemento)))
                .sorted(Comparator
                        .comparing((Seleccion seleccion) -> seleccion.producto().tasaDeCaida())
                        .reversed()
                        .thenComparing(seleccion -> seleccion.elemento().elementoId()))
                .findFirst();
    }

    private ProductoBotin consultarYValidar(ElementoCandidatoBotin elemento) {
        ProductoBotin producto = catalogo.consultar(elemento.productoId());
        if (!elemento.productoId().equals(producto.id()) || elemento.tipo() != producto.tipo()) {
            throw new IntegracionBotinException("El inventario y el catalogo no describen el mismo producto");
        }
        if (elemento.tipo() == TipoBotin.ARMADURA
                && elemento.parteArmadura() != producto.parteArmadura()) {
            throw new IntegracionBotinException("La parte de armadura no coincide con el catalogo");
        }
        return producto;
    }

    private ParticipantePerdidaEquipo seleccionarGanador(List<ParticipantePerdidaEquipo> ganadores) {
        int indice = selector.seleccionar(ganadores.size());
        if (indice < 0 || indice >= ganadores.size()) {
            throw new IllegalStateException("El selector devolvio un ganador inexistente");
        }
        return ganadores.get(indice);
    }

    private static List<ParticipantePerdidaEquipo> validarParticipantes(
            List<ParticipantePerdidaEquipo> participantes) {
        Objects.requireNonNull(participantes, "Los participantes son obligatorios");
        if (participantes.size() < 2) {
            throw new IllegalArgumentException("La partida requiere al menos dos participantes");
        }
        List<ParticipantePerdidaEquipo> copia = List.copyOf(participantes);
        Set<String> ids = new HashSet<>();
        Set<String> equipos = new HashSet<>();
        for (ParticipantePerdidaEquipo participante : copia) {
            Objects.requireNonNull(participante, "Un participante no puede ser nulo");
            if (!ids.add(participante.combatienteId())) {
                throw new IllegalArgumentException("Los combatientes no se pueden repetir");
            }
            equipos.add(participante.equipoId());
        }
        if (equipos.size() < 2) {
            throw new IllegalArgumentException("La partida requiere al menos dos equipos");
        }
        return copia;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " es obligatorio");
        }
        return valor;
    }

    private record Seleccion(ElementoCandidatoBotin elemento, ProductoBotin producto) {
    }
}
