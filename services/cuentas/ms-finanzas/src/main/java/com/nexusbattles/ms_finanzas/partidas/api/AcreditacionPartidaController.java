package com.nexusbattles.ms_finanzas.partidas.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexusbattles.ms_finanzas.partidas.AcreditacionPartidaService;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaRequest;
import com.nexusbattles.ms_finanzas.partidas.ResultadoPartidaResponse;

/**
 * Punto único de entrada de HU-JUE-012: recibe el resultado de una partida
 * y acredita créditos + entrega cofres al que corresponda.
 *
 * <p>Consumido por ms-salas-partidas al finalizar la partida. La ruta queda
 * en {@code /partidas/resultado} y NO bajo {@code /creditos/**} — este
 * endpoint es la responsabilidad de acreditación de HU-JUE-012, distinto
 * de los endpoints de HU-PAG-001 que son consumidos por otros flujos.
 *
 * <p><b>Seguridad:</b> el {@code SecurityConfig} de este servicio deja
 * {@code /partidas/**} sin restricción por ahora, en coherencia con
 * {@code /creditos/**} — ambos endpoints tienen que ser llamados por otros
 * microservicios (ms-salas-partidas para éste), y hasta que infra registre
 * el cliente m2m en el realm Keycloak y ms-plataforma adopte
 * {@code TokenDeServicio} (ADR-001), cerrarlos rompería las integraciones.
 * Cuando el cliente esté registrado, se cambia una línea del
 * {@code SecurityConfig} y estas rutas exigen token de servicio.
 */
@RestController
@RequestMapping("/partidas")
public class AcreditacionPartidaController {

    private final AcreditacionPartidaService servicio;

    public AcreditacionPartidaController(AcreditacionPartidaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/resultado")
    public ResponseEntity<ResultadoPartidaResponse> resultado(@RequestBody ResultadoPartidaRequest req) {
        return ResponseEntity.ok(servicio.procesarResultadoPartida(req));
    }
}
