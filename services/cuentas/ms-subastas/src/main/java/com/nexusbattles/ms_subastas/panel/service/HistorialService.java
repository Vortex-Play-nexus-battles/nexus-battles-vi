package com.nexusbattles.ms_subastas.panel.service;

import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * «Historial de transacciones» del panel de 7.7.9: compras con fecha y monto,
 * ventas con la ganancia obtenida, comisiones pagadas, el balance de lo ganado
 * y lo gastado en subastas, y la opcion de exportarlo.
 *
 * <p>Sale de lo que este servicio registro —subastas y pujas—, no del libro de
 * ms-finanzas: es el historial de las subastas, no el del saldo.
 */
@Service
public class HistorialService {

    static final String CABECERA_CSV = "tipo,subastaId,producto,monto,fecha";

    private final SubastaRepository subastas;
    private final PujaRepository pujas;

    public HistorialService(SubastaRepository subastas, PujaRepository pujas) {
        this.subastas = Objects.requireNonNull(subastas);
        this.pujas = Objects.requireNonNull(pujas);
    }

    @Transactional(readOnly = true)
    public PanelDtos.Historial de(UUID jugadorId) {
        List<PanelDtos.Movimiento> movimientos = new ArrayList<>();

        // Compras: sus pujas ganadoras (subasta ganada al vencer o compra inmediata).
        List<Puja> ganadoras = pujas.findByJugadorIdAndEstado(jugadorId, EstadoPuja.GANADORA);
        Map<UUID, Subasta> compradas = ganadoras.isEmpty() ? Map.of()
                : subastas.findAllById(ganadoras.stream().map(Puja::getSubastaId).toList()).stream()
                        .collect(Collectors.toMap(Subasta::getId, Function.identity()));
        for (Puja ganadora : ganadoras) {
            Subasta subasta = compradas.get(ganadora.getSubastaId());
            movimientos.add(new PanelDtos.Movimiento("COMPRA", ganadora.getSubastaId(),
                    subasta == null ? null : subasta.getNombreProducto(), ganadora.getMonto(),
                    subasta != null && subasta.getCerradaEn() != null ? subasta.getCerradaEn() : ganadora.getCreadaEn()));
        }

        // Ventas, comisiones y penalizaciones: sus publicaciones.
        for (Subasta subasta : subastas.findByVendedorIdOrderByFechaFinDesc(jugadorId)) {
            if (subasta.getEstado() == EstadoSubasta.ADJUDICADA) {
                movimientos.add(new PanelDtos.Movimiento("VENTA", subasta.getId(), subasta.getNombreProducto(),
                        subasta.getOfertaVigente(), fechaDe(subasta.getCerradaEn(), subasta.getFechaFin())));
            }
            if (positivo(subasta.getComisionCobrada())) {
                movimientos.add(new PanelDtos.Movimiento("COMISION", subasta.getId(), subasta.getNombreProducto(),
                        subasta.getComisionCobrada(), fechaDe(subasta.getFechaPublicacion(), subasta.getFechaFin())));
            }
            if (positivo(subasta.getPenalizacionCobrada())) {
                movimientos.add(new PanelDtos.Movimiento("PENALIZACION", subasta.getId(), subasta.getNombreProducto(),
                        subasta.getPenalizacionCobrada(), fechaDe(subasta.getCerradaEn(), subasta.getFechaFin())));
            }
        }

        movimientos.sort(Comparator.comparing(PanelDtos.Movimiento::fecha).reversed());

        BigDecimal ganado = suma(movimientos, "VENTA");
        BigDecimal comisiones = suma(movimientos, "COMISION").add(suma(movimientos, "PENALIZACION"));
        BigDecimal gastado = suma(movimientos, "COMPRA").add(comisiones);
        return new PanelDtos.Historial(movimientos, ganado, gastado, comisiones, ganado.subtract(gastado));
    }

    /** «Opcion de exportar historial»: CSV en UTF-8 con cabecera. */
    @Transactional(readOnly = true)
    public String comoCsv(UUID jugadorId) {
        StringBuilder csv = new StringBuilder(CABECERA_CSV).append('\n');
        for (PanelDtos.Movimiento movimiento : de(jugadorId).movimientos()) {
            csv.append(movimiento.tipo()).append(',')
                    .append(movimiento.subastaId()).append(',')
                    .append(celda(movimiento.nombreProducto())).append(',')
                    .append(movimiento.monto().toPlainString()).append(',')
                    .append(movimiento.fecha()).append('\n');
        }
        return csv.toString();
    }

    /**
     * Un texto libre en CSV: entre comillas y con las comillas dobladas. Un
     * nombre que empiece por =, +, - o @ se prefija con un apostrofo para que
     * una hoja de calculo no lo ejecute como formula (inyeccion CSV).
     */
    static String celda(String texto) {
        if (texto == null) {
            return "";
        }
        String seguro = texto.isEmpty() || "=+-@".indexOf(texto.charAt(0)) < 0 ? texto : "'" + texto;
        return '"' + seguro.replace("\"", "\"\"") + '"';
    }

    private static BigDecimal suma(List<PanelDtos.Movimiento> movimientos, String tipo) {
        return movimientos.stream()
                .filter(m -> m.tipo().equals(tipo))
                .map(PanelDtos.Movimiento::monto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean positivo(BigDecimal monto) {
        return monto != null && monto.signum() > 0;
    }

    private static Instant fechaDe(Instant preferida, Instant respaldo) {
        return preferida != null ? preferida : respaldo;
    }
}
