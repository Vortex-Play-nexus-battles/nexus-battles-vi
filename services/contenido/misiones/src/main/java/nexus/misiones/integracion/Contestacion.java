package nexus.misiones.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import java.util.Map;
import java.util.function.Supplier;
import nexus.misiones.aplicacion.RechazoDelServicio;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lo que contesta una dependencia cuando SI contesta (HU-DIS-003), con el
 * mismo patron que salas-partidas.
 *
 * <p>El corta circuitos cuenta como fallo toda excepcion que salga de la
 * llamada, y eso es lo correcto para lo que no responde (conexion rechazada,
 * tiempo agotado, un 5xx, una credencial que no se pudo obtener). Un 4xx, en
 * cambio, es la dependencia contestando «no»: contarlo como caida abriria el
 * circuito por tres heroes ajenos. Este envoltorio convierte el 4xx en un
 * valor para que atraviese el corta circuitos como lo que es.
 *
 * <p><b>No todo 4xx es un «no».</b> 401, 408 y 429 son un «ahora no»: la
 * credencial de servicio de misiones no valio (se renueva; paso con la clave
 * de firma del emisor, #750), la peticion tardo o el otro servicio pide
 * espacio. Tratarlos como rechazo definitivo dejaba un paso de la liquidacion
 * FALLIDO para siempre: un heroe bloqueado en mision que ya no se liberaba y
 * unos creditos que ya no se abonaban. Se dejan salir como falta de respuesta:
 * el corta circuitos los cuenta y la liquidacion los reintenta.
 *
 * @param cuerpo  lo que devolvio la llamada, si no fue un 4xx
 * @param rechazo el 4xx, si lo hubo
 */
record Contestacion<T>(T cuerpo, HttpClientErrorException rechazo) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * @throws DependenciaDegradada si la dependencia no responde, el circuito
     *                              esta abierto o contesto un 4xx pasajero; la
     *                              causa real va dentro
     */
    static <T> Contestacion<T> protegida(CortaCircuitos corta, Supplier<T> llamada) {
        return corta.ejecutarOFallar(() -> de(llamada));
    }

    static <T> Contestacion<T> de(Supplier<T> llamada) {
        try {
            return new Contestacion<>(llamada.get(), null);
        } catch (HttpClientErrorException rechazo) {
            if (pasajero(rechazo)) {
                throw rechazo;
            }
            return new Contestacion<>(null, rechazo);
        }
    }

    /** 401 (credencial), 408 (tiempo) y 429 (cupo): se reintenta, no es un «no» del otro servicio. */
    static boolean pasajero(HttpClientErrorException rechazo) {
        int estado = rechazo.getStatusCode().value();
        return estado == 401 || estado == 408 || estado == 429;
    }

    boolean rechazada() {
        return rechazo != null;
    }

    /** Codigo HTTP del rechazo. Solo tiene sentido si {@link #rechazada()}. */
    int estado() {
        return rechazo.getStatusCode().value();
    }

    /**
     * El {@code detail} del problem detail que devolvio la dependencia, o el
     * texto del estado si no trae uno. Es lo que se guarda como motivo de un
     * paso fallido: nunca el cuerpo entero, que podria llevar datos del jugador.
     */
    String detalle() {
        try {
            Map<?, ?> problema = JSON.readValue(rechazo.getResponseBodyAsString(), Map.class);
            Object detalle = problema.get("detail");
            if (detalle instanceof String texto && !texto.isBlank()) {
                return texto;
            }
        } catch (RuntimeException sinCuerpoLegible) {
            // Sin cuerpo JSON: queda el texto del estado.
        }
        return rechazo.getStatusText();
    }

    /** El rechazo como excepcion de la aplicacion: definitivo, no se reintenta. */
    RechazoDelServicio comoRechazo(String servicio) {
        return new RechazoDelServicio(servicio, estado(), detalle());
    }
}
