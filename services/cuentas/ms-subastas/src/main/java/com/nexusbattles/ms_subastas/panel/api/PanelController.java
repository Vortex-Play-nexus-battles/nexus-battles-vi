package com.nexusbattles.ms_subastas.panel.api;

import com.nexusbattles.ms_subastas.panel.dto.PanelDtos;
import com.nexusbattles.ms_subastas.panel.service.HistorialService;
import com.nexusbattles.ms_subastas.panel.service.MisPublicacionesService;
import com.nexusbattles.ms_subastas.panel.service.PendientesService;
import com.nexusbattles.ms_subastas.panel.service.SeguimientoService;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.port.IdentidadClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * El «Panel de gestion personal» de 7.7.9 ({@code ms-subastas-panel.yaml}):
 * mis subastas, la lista de seguimiento, los pendientes de recoger y el
 * historial de transacciones. Todo del jugador del token; ninguna ruta
 * recibe un identificador de jugador.
 */
@RestController
@RequestMapping("/mis-subastas")
public class PanelController {

    private final MisPublicacionesService publicaciones;
    private final SeguimientoService seguimiento;
    private final PendientesService pendientes;
    private final HistorialService historial;
    private final IdentidadClient identidad;

    public PanelController(MisPublicacionesService publicaciones, SeguimientoService seguimiento,
                           PendientesService pendientes, HistorialService historial, IdentidadClient identidad) {
        this.publicaciones = publicaciones;
        this.seguimiento = seguimiento;
        this.pendientes = pendientes;
        this.historial = historial;
        this.identidad = identidad;
    }

    @GetMapping("/publicadas")
    public List<PanelDtos.MiPublicacion> misPublicaciones(@RequestParam(required = false) EstadoSubasta estado) {
        return publicaciones.de(jugador(), estado);
    }

    @GetMapping("/seguimiento")
    public List<PanelDtos.SubastaSeguida> miListaDeSeguimiento() {
        return seguimiento.lista(jugador());
    }

    @GetMapping("/pendientes")
    public List<PanelDtos.Pendiente> misPendientes() {
        return pendientes.pendientesDe(jugador());
    }

    @PostMapping("/pendientes/recogida")
    public PanelDtos.ResultadoDeRecogida recogerTodo() {
        return pendientes.recogerTodo(jugador());
    }

    @PostMapping("/pendientes/{subastaId}/recogida")
    public PanelDtos.Pendiente recoger(@PathVariable UUID subastaId) {
        return pendientes.recoger(subastaId, jugador());
    }

    /**
     * El historial en JSON o, con {@code formato=csv}, exportado («Opcion de
     * exportar historial»). El CSV sale como adjunto para que el navegador lo
     * guarde en vez de pintarlo.
     */
    @GetMapping("/historial")
    public ResponseEntity<?> historial(@RequestParam(defaultValue = "json") String formato) {
        UUID jugador = jugador();
        if ("csv".equalsIgnoreCase(formato)) {
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"historial-subastas.csv\"")
                    .body(historial.comoCsv(jugador));
        }
        return ResponseEntity.ok(historial.de(jugador));
    }

    private UUID jugador() {
        return identidad.actual().usuarioId();
    }
}
