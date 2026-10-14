package com.nexusbattles.plataforma.comentarios.moderacion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los reportes de una cuenta para su ficha administrativa — contrato 1.11.0.
 *
 * <p>Cuelga de {@code /api/v1/comentarios/moderacion}, asi que la guarda de
 * {@code SecurityConfig} es la de toda la moderacion: Moderador, Administrador
 * y Super Administrador. Solo lectura.
 */
@RestController
@RequestMapping("/api/v1/comentarios/moderacion/usuarios")
public class ReportesDelUsuarioController {

    private final ReportesDelUsuario reportes;

    public ReportesDelUsuarioController(ReportesDelUsuario reportes) {
        this.reportes = reportes;
    }

    /**
     * Una pagina de los reportes que hizo la cuenta ({@code REALIZADOS}, por
     * omision) o de los que recibieron sus comentarios ({@code RECIBIDOS}), con
     * los dos totales.
     */
    @GetMapping("/{usuarioId}/reportes")
    public ReportesDelUsuario.Pagina reportesDelUsuario(
            @PathVariable String usuarioId,
            @RequestParam(defaultValue = "REALIZADOS") ReportesDelUsuario.Direccion direccion,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return reportes.consultar(usuarioId, direccion, pagina, tamano);
    }
}
