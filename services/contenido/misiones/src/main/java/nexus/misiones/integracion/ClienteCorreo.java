package nexus.misiones.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import nexus.misiones.aplicacion.CorreoDeMisiones;
import nexus.misiones.aplicacion.DirectorioDeJugadores;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * {@link CorreoDeMisiones} contra el servicio de correo ({@code POST
 * /correos/mision}, correo.yaml 1.4.0): plantilla corporativa, cola durable e
 * {@code Idempotency-Key}.
 *
 * <p>{@code debeEnviarCorreo} va siempre en verdadero: el documento no define
 * preferencias de notificacion por categoria que misiones pueda leer, y si el
 * correo de misiones esta apagado en un entorno ({@code MISIONES_CORREO_ACTIVO})
 * ni siquiera se llega aqui. La direccion del jugador no se escribe en la
 * bitacora.
 */
public class ClienteCorreo implements CorreoDeMisiones {

    static final String DEPENDENCIA = "correo";

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteCorreo(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public void enviar(DirectorioDeJugadores.Contacto contacto, String asunto, String mensaje,
                       String claveIdempotencia) {
        CorreoMision cuerpo = new CorreoMision(contacto.email(), contacto.apodo(), asunto, mensaje, true);
        Contestacion<Object> c = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/correos/mision")
                .header("Idempotency-Key", claveIdempotencia)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity());
        if (c.rechazada()) {
            throw c.comoRechazo(DEPENDENCIA);
        }
    }

    record CorreoMision(String email, String apodo, String asunto, String mensaje, boolean debeEnviarCorreo) {
    }
}
