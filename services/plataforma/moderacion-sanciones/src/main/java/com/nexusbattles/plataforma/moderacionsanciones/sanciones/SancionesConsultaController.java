package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code GET /sanciones/usuarios/{uid}/activa}, {@code GET /sanciones/metricas}
 * y {@code GET /sanciones/limites} — moderacion-sanciones-consulta.yaml 1.3.0.
 */
@RestController
@RequestMapping("/api/v1/sanciones")
public class SancionesConsultaController {

    private final ConsultaSancionActivaService service;
    private final SancionesService sanciones;
    private final Clock reloj;

    public SancionesConsultaController(ConsultaSancionActivaService service, SancionesService sanciones, Clock reloj) {
        this.service = service;
        this.sanciones = sanciones;
        this.reloj = reloj;
    }

    /**
     * Agregados de moderacion de un periodo (HU-MET-001). Solo cuentas, sin
     * identificadores: por eso vive junto a la consulta publica y no en el
     * contrato de administracion. Sin {@code desde}/{@code hasta}: los ultimos
     * 30 dias.
     */
    @GetMapping("/metricas")
    public MetricasDeModeracion metricas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        OffsetDateTime fin = hasta == null ? OffsetDateTime.now(reloj) : hasta;
        OffsetDateTime inicio = desde == null ? fin.minusDays(30) : desde;
        return sanciones.metricas(inicio, fin);
    }

    /**
     * Los limites vigentes (HU-ADM-001 CA-04): rango de la suspension y plazo
     * de apelacion, tal como los aplica el servicio en este momento.
     *
     * <p>Existe porque la interfaz los tenia escritos a mano en cinco sitios
     * —«30 dias», {@code max="720"}— mientras el servicio los leia de
     * admin-parametros. Bajar el plazo a 7 dejaba al jugador con un boton
     * «Apelar» que el servicio rechazaba, y un formulario de suspension que
     * admitia 720 horas para recibir un 400. Con estos numeros la pantalla dice
     * lo mismo que hace el servicio, sin que nadie tenga que acordarse de
     * cambiar dos sitios.
     *
     * <p>Es de solo lectura y no lleva ningun identificador de jugador; aun asi
     * exige sesion, como el resto de {@code /api/v1/sanciones/**}, porque las
     * dos vistas que lo consumen son de usuario autenticado.
     *
     * <p>La ruta literal gana a {@code /sanciones/{sancionId}} de
     * SancionesAdminController, igual que ya ocurre con {@code /metricas}.
     */
    @GetMapping("/limites")
    public LimitesResponse limites() {
        LimitesDeSancion vigentes = sanciones.limitesVigentes();
        return new LimitesResponse(
                vigentes.suspensionMinima().toHours(),
                vigentes.suspensionMaxima().toHours(),
                vigentes.suspensionMaxima().toDays(),
                vigentes.plazoDeApelacion().toDays());
    }

    @GetMapping("/usuarios/{usuarioId}/activa")
    public SancionActivaResponse consultarActiva(@PathVariable UUID usuarioId) {
        var resultado = service.consultar(usuarioId);
        return new SancionActivaResponse(resultado.sancionActiva(), resultado.motivo(), resultado.vigenteHasta(),
                resultado.tipo());
    }

    public record SancionActivaResponse(boolean sancionActiva, String motivo, OffsetDateTime vigenteHasta,
                                        String tipo) {
    }

    /**
     * El rango de la suspension va en horas <b>y</b> en dias a proposito: el
     * formulario necesita las horas para el {@code max} de su campo y el texto
     * necesita los dias para leerse como lo dice la ficha de HU-USR-005. Que la
     * conversion la haga el servicio evita que la vista la repita mal.
     */
    public record LimitesResponse(long suspensionMinimaHoras, long suspensionMaximaHoras,
                                  long suspensionMaximaDias, long apelacionPlazoDias) {
    }
}
