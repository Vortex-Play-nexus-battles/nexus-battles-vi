package nexus.inventario.api;

import com.nexusbattles.comun.seguridad.ConversorRolesJwt;
import com.nexusbattles.comun.seguridad.pruebas.EmisorDeTokensDePrueba;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/**
 * Quien llama, para las pruebas de rebanada sin cadena de seguridad
 * ({@code standaloneSetup}): deja en la peticion el mismo {@code Authentication}
 * que dejaria la cadena real a partir de un token firmado de verdad.
 *
 * <p>No es un doble: el token lo firma {@link EmisorDeTokensDePrueba}, lo
 * verifica un decodificador real contra su JWKS y lo traduce el mismo
 * {@link ConversorRolesJwt} de produccion. Lo unico que se salta es el
 * filtro HTTP, que no existe en un {@code standaloneSetup}.
 */
public final class ComoLlamador {

    private static final EmisorDeTokensDePrueba EMISOR = EmisorDeTokensDePrueba.emisor();
    private static final ConversorRolesJwt CONVERSOR = new ConversorRolesJwt();

    private ComoLlamador() {
    }

    /** Un servicio con credencial (ADR-005): el propietario va en X-User-Name. */
    public static RequestPostProcessor servicio() {
        return servicio("salas-partidas");
    }

    public static RequestPostProcessor servicio(String clientId) {
        return con(EMISOR.tokenDeServicio(clientId));
    }

    /** Un jugador: el propietario es el apodo del token. */
    public static RequestPostProcessor jugador(String apodo) {
        return con(EMISOR.tokenDeJugador(apodo, UUID.randomUUID()));
    }

    /** El valor Bearer de un jugador, para las pruebas con cadena real. */
    public static String portadorDeJugador(String apodo) {
        return "Bearer " + EMISOR.tokenDeJugador(apodo, UUID.randomUUID());
    }

    /** El valor Bearer de un servicio, para las pruebas con cadena real. */
    public static String portadorDeServicio(String clientId) {
        return "Bearer " + EMISOR.tokenDeServicio(clientId);
    }

    private static RequestPostProcessor con(String token) {
        Authentication autenticacion = CONVERSOR.convert(EMISOR.decodificador().decode(token));
        return peticion -> {
            peticion.setUserPrincipal(autenticacion);
            return peticion;
        };
    }
}
