package nexus.misiones.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import java.util.Optional;
import nexus.misiones.aplicacion.DirectorioDeJugadores;
import org.springframework.web.client.RestClient;

/**
 * {@link DirectorioDeJugadores} contra ms-identidad
 * ({@code GET /api/v1/internal/usuarios/{uid}/contacto}, ms-identidad-admin.yaml,
 * solo token de servicio). Misiones no guarda el correo de nadie: lo pide justo
 * antes de escribir.
 */
public class ClienteIdentidad implements DirectorioDeJugadores {

    static final String DEPENDENCIA = "ms-identidad";

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteIdentidad(RestClient http, String base, CortaCircuitos corta) {
        this.http = http;
        this.base = ClienteInventario.sinBarraFinal(base);
        this.corta = corta;
    }

    @Override
    public Optional<Contacto> contacto(String jugadorUid) {
        Contestacion<ContactoDeUsuario> c = Contestacion.protegida(corta, () -> http.get()
                .uri(base + "/api/v1/internal/usuarios/{uid}/contacto", jugadorUid)
                .retrieve()
                .body(ContactoDeUsuario.class));
        if (c.rechazada()) {
            if (c.estado() == 404) {
                return Optional.empty();
            }
            throw c.comoRechazo(DEPENDENCIA);
        }
        ContactoDeUsuario contacto = c.cuerpo();
        if (contacto.email() == null || contacto.email().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Contacto(contacto.email(), contacto.apodo()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContactoDeUsuario(String uid, String email, String apodo, String estado) {
    }
}
