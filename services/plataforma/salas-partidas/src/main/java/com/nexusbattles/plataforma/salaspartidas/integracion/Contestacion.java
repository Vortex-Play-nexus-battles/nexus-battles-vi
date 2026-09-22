package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.resiliencia.DependenciaDegradada;
import org.springframework.web.client.HttpClientErrorException;

import java.util.function.Supplier;

/**
 * Lo que contesta una dependencia cuando SI contesta — HU-DIS-003.
 *
 * <p>El corta circuitos cuenta como fallo cualquier excepcion que salga de la
 * llamada, y eso es correcto para lo que no responde (conexion rechazada,
 * tiempo agotado, un 5xx, una credencial de servicio que no se pudo obtener).
 * Pero un 4xx es la dependencia <b>contestando</b>: «saldo insuficiente» (422)
 * o «esa reserva ya no existe» (404) son respuestas del negocio, y contarlas
 * como caidas abriria el circuito por tres jugadores sin creditos. Este envoltorio
 * convierte el 4xx en un valor, para que atraviese el corta circuitos como lo que
 * es: una respuesta sana.
 *
 * <p>Uso, siempre igual en los tres clientes:
 *
 * <pre>{@code
 * Contestacion<Reserva> c = Contestacion.protegida(corta, () -> http.post()...body(Reserva.class));
 * if (c.rechazada()) { ... traducir c.estado() ... }
 * Reserva reserva = c.cuerpo();
 * }</pre>
 *
 * @param cuerpo  lo que devolvio la llamada, si no fue un 4xx
 * @param rechazo el 4xx, si lo hubo
 * @param <T>     tipo del cuerpo
 */
record Contestacion<T>(T cuerpo, HttpClientErrorException rechazo) {

    /**
     * Ejecuta la llamada detras del corta circuitos.
     *
     * @throws DependenciaDegradada si la dependencia no responde o el circuito
     *                              esta abierto; la causa real va dentro
     */
    static <T> Contestacion<T> protegida(CortaCircuitos corta, Supplier<T> llamada) {
        return corta.ejecutarOFallar(() -> de(llamada));
    }

    static <T> Contestacion<T> de(Supplier<T> llamada) {
        try {
            return new Contestacion<>(llamada.get(), null);
        } catch (HttpClientErrorException rechazo) {
            return new Contestacion<>(null, rechazo);
        }
    }

    boolean rechazada() {
        return rechazo != null;
    }

    /** Codigo HTTP del rechazo. Solo tiene sentido si {@link #rechazada()}. */
    int estado() {
        return rechazo.getStatusCode().value();
    }
}
