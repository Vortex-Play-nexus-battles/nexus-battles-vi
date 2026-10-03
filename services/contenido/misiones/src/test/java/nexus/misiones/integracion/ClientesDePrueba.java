package nexus.misiones.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.RegistroDeDegradacion;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Lo comun de las pruebas de los clientes contra {@code DependenciasFalsas}: un RestClient y un corta circuitos. */
final class ClientesDePrueba {

    private ClientesDePrueba() {
    }

    static RestClient rest() {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return RestClient.builder().requestFactory(new JdkClientHttpRequestFactory(http)).build();
    }

    /** Un corta circuitos que no se abre por culpa de la prueba (100 fallos seguidos). */
    static CortaCircuitos corta(String dependencia) {
        return new CortaCircuitos(dependencia, dependencia, 100, Duration.ofSeconds(30), Clock.systemUTC(),
                new RegistroDeDegradacion());
    }
}
