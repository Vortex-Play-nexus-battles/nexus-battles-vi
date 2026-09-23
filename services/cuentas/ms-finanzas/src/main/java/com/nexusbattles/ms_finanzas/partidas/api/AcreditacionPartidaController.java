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
 * <p><b>Seguridad:</b> el {@code SecurityConfig} cierra {@code /partidas/**}
 * con {@code authenticated()} — este endpoint CREA saldo (acredita créditos
 * a los participantes) y sin auth cualquiera podría inventar un resultado
 * de partida con su propio uid como ganador y regalarse créditos, saltándose
 * el cierre de {@code /creditos/acreditar} porque el service se llama como
 * bean local. Consecuencia: ms-salas-partidas necesita un token de servicio
 * ({@code rol=SERVICIO}) para llamar. ADR-001 lo decidió contra Keycloak, pero
 * Keycloak nunca se aprovisionó: desde ADR-005 lo emite {@code ms-identidad}
 * por {@code POST /api/v1/auth/token} con {@code grant_type=client_credentials},
 * y {@code salas-partidas} ya lleva su credencial propia. Ya no se prueba con
 * un JWT de jugador: uno de jugador recibe 403.
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
