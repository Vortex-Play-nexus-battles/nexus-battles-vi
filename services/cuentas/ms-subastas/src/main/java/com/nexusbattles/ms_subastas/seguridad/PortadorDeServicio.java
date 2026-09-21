package com.nexusbattles.ms_subastas.seguridad;

import java.net.http.HttpRequest;
import java.util.Objects;
import java.util.Optional;

import com.nexusbattles.comun.seguridad.servicio.CredencialDeServicioNoDisponible;
import com.nexusbattles.comun.seguridad.servicio.TokenDeServicio;

/**
 * Pone la credencial de este servicio en las peticiones salientes construidas
 * con {@code java.net.http.HttpClient} — ADR-001 / ADR-005.
 *
 * <p>Es el equivalente, para los adaptadores de este servicio, del
 * {@code InterceptorDePortadorDeServicio} que la biblioteca compartida ofrece
 * a quien usa {@code RestClient}: la biblioteca lo dice explícitamente
 * («quien no usa RestClient hace lo mismo a mano»). La credencial identifica
 * <b>a ms-subastas</b>, nunca al jugador: el jugador afectado viaja en el
 * cuerpo de cada petición ({@code jugadorUid}, {@code propietarioUid}), como
 * fija ADR-001.
 *
 * <p>{@link #ninguno()} existe para los dobles y las pruebas de los
 * adaptadores, y para los modos {@code fake}. En producción, la configuración
 * de cada cliente HTTP exige la credencial al arrancar: una llamada a
 * ms-finanzas o a inventario sin ella ya no pasa (401), y descubrirlo en la
 * primera puja sería tarde.
 */
public final class PortadorDeServicio {

    private static final PortadorDeServicio NINGUNO = new PortadorDeServicio(null);

    private final TokenDeServicio token;

    private PortadorDeServicio(TokenDeServicio token) {
        this.token = token;
    }

    public static PortadorDeServicio de(TokenDeServicio token) {
        return new PortadorDeServicio(Objects.requireNonNull(token, "token"));
    }

    /** Sin credencial: las peticiones salen sin {@code Authorization}. */
    public static PortadorDeServicio ninguno() {
        return NINGUNO;
    }

    public boolean presente() {
        return token != null;
    }

    /**
     * Añade {@code Authorization: Bearer ...} si hay credencial.
     *
     * @throws CredencialDeServicioNoDisponible si la hay pero el emisor no la
     *         entrega; el adaptador la traduce a su «servicio no disponible»
     */
    public HttpRequest.Builder firmar(HttpRequest.Builder peticion) {
        return Optional.ofNullable(token)
                .map(t -> peticion.header("Authorization", "Bearer " + t.portador()))
                .orElse(peticion);
    }
}
