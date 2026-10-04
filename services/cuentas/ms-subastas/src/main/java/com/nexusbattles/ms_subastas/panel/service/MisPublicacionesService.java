package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.reglas.ReglasDelDocumento;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * «Mis subastas activas» del panel de 7.7.9, con su historial: estado, pujas
 * recibidas, tiempo restante, visualizaciones y si todavia se puede cancelar
 * (y cuanto costaria).
 */
@Service
public class MisPublicacionesService {

    private final SubastaRepository subastas;
    private final Clock clock;

    public MisPublicacionesService(SubastaRepository subastas, Clock clock) {
        this.subastas = Objects.requireNonNull(subastas);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<PanelDtos.MiPublicacion> de(UUID vendedorId, EstadoSubasta estado) {
        List<Subasta> suyas = estado == null
                ? subastas.findByVendedorIdOrderByFechaFinDesc(vendedorId)
                : subastas.findByVendedorIdAndEstadoOrderByFechaFinDesc(vendedorId, estado);
        Instant ahora = clock.instant();
        return suyas.stream()
                .map(s -> PanelDtos.MiPublicacion.de(s, esCancelable(s, ahora),
                        ReglasDelDocumento.penalizacionDeCancelacion(s.getComisionCobrada())))
                .toList();
    }

    /**
     * Lo mismo que comprueba {@link CancelacionService}, sin tocar nada: activa,
     * sin pujas y a mas de 6 horas del cierre. La cancelacion lo vuelve a
     * comprobar con el candado tomado; esto solo decide si se ofrece el boton.
     */
    static boolean esCancelable(Subasta subasta, Instant ahora) {
        return subasta.estaActiva()
                && !subasta.tieneOfertas()
                && subasta.getCantidadPujas() == 0
                && ahora.isBefore(subasta.getFechaFin().minus(ReglasDelDocumento.CANCELACION_PROHIBIDA_ULTIMAS));
    }
}
