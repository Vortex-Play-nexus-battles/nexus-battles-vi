package com.nexusbattles.plataforma.correo.envio;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Los ultimos envios, para poder demostrar la entrega.
 *
 * <p>Existe porque hasta R18 no habia forma de responder "se envio el correo
 * de recuperacion" sin entrar por SSH a leer la bitacora del contenedor. El
 * servicio respondia 202, el remitente se quedaba tranquilo y nadie recibia
 * nada: la peticion se habia aceptado, el mensaje habia salido, y el destino
 * era un buzon de pruebas que no reenvia a ninguna parte.
 *
 * <p><b>Es memoria acotada, no un historial.</b> Se pierde al reiniciar, a
 * proposito: guardar destinatarios de forma duradera convierte un servicio de
 * envio en una base de datos de correos, y este servicio no tiene por que
 * tener una. Para el diagnostico basta con lo reciente.
 */
@Component
public class RegistroDeEnvios {

    private final int capacidad;
    private final Deque<EnvioRegistrado> recientes = new ArrayDeque<>();
    private long aceptados;
    private long rechazados;

    public RegistroDeEnvios(ConfiguracionDeCorreo configuracion) {
        this.capacidad = configuracion.enviosRecordados();
    }

    public synchronized void anotar(EnvioRegistrado envio) {
        recientes.addFirst(envio);
        while (recientes.size() > capacidad) {
            recientes.removeLast();
        }
        if (EnvioRegistrado.ACEPTADO.equals(envio.estado())) {
            aceptados++;
        } else {
            rechazados++;
        }
    }

    /** Del mas reciente al mas antiguo, como maximo {@code cuantos}. */
    public synchronized List<EnvioRegistrado> ultimos(int cuantos) {
        List<EnvioRegistrado> copia = new ArrayList<>(recientes);
        return copia.subList(0, Math.min(Math.max(cuantos, 0), copia.size()));
    }

    public synchronized long aceptados() {
        return aceptados;
    }

    public synchronized long rechazados() {
        return rechazados;
    }
}