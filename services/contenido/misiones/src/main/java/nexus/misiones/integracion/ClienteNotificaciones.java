package nexus.misiones.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import java.time.Instant;
import nexus.misiones.aplicacion.AvisosDeMisiones;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link AvisosDeMisiones} contra la bandeja del jugador ({@code POST
 * /internal/notifications}, notificaciones.yaml 1.2.0), con la credencial de
 * servicio de misiones ({@code ROLE_SERVICIO}, ADR-005). La bandeja guarda el
 * aviso y lo entrega en tiempo real a las sesiones abiertas del jugador; si no
 * tiene ninguna, queda pendiente hasta que vuelva (HU-NOT-006).
 *
 * <p><b>409 es «ya estaba».</b> El contrato reserva el 409 para un {@code id}
 * ya recibido: es el reintento de un aviso que si llego, y cuenta como
 * entregado. Cualquier otro 4xx (un 400 por un campo de mas largo) es un
 * rechazo definitivo; lo que no responde, un 5xx o un 401/408/429 se reintenta
 * ({@link Contestacion}).
 *
 * <p>Ni el titulo ni el cuerpo se escriben en la bitacora: son del jugador.
 */
public class ClienteNotificaciones implements AvisosDeMisiones {

    static final String DEPENDENCIA = "notificaciones";

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteNotificaciones(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public void avisar(String jugadorUid, String id, String titulo, String cuerpo, Instant creadaEn) {
        Aviso aviso = new Aviso(jugadorUid, id, TIPO, titulo, cuerpo, creadaEn.toString());
        Contestacion<Object> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/internal/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .body(aviso)
                .retrieve()
                .toBodilessEntity());
        if (c.rechazada() && c.estado() != 409) {
            throw c.comoRechazo(DEPENDENCIA);
        }
    }

    /** {@code EmitirNotificacionRequest} del contrato: los seis campos, todos obligatorios. */
    record Aviso(String usuarioId, String id, String tipo, String titulo, String cuerpo, String creadaEn) {
    }
}
