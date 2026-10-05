package nexus.misiones.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import nexus.misiones.aplicacion.LibroDeCreditos;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link LibroDeCreditos} contra ms-finanzas ({@code POST /creditos/acreditar},
 * creditos.yaml 1.4.0). La base ya incluye el {@code /api/v1} del servicio,
 * como en salas-partidas y torneos ({@code CREDITOS_URL}).
 */
public class ClienteCreditos implements LibroDeCreditos {

    static final String DEPENDENCIA = "ms-finanzas";

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteCreditos(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public void acreditar(String jugadorUid, int monto, String refId, String concepto) {
        AcreditarCreditos cuerpo = new AcreditarCreditos(jugadorUid, monto, refId, concepto);
        Contestacion<Object> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/creditos/acreditar")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(Object.class));
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
    }

    record AcreditarCreditos(String uid, int monto, String refId, String concepto) {
    }
}
