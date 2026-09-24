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
    private long desviados;
    private long rechazados;
    private long omitidos;

    public RegistroDeEnvios(ConfiguracionDeCorreo configuracion) {
        this.capacidad = configuracion.enviosRecordados();
    }

    public synchronized void anotar(EnvioRegistrado envio) {
        recientes.addFirst(envio);
        while (recientes.size() > capacidad) {
            recientes.removeLast();
        }
        switch (envio.estado()) {
            case EnvioRegistrado.ACEPTADO -> {
                // Los que acepto el buzon de pruebas se cuentan aparte: sumados
                // a los del proveedor, "aceptados" diria que salieron correos
                // que en realidad no iban a ninguna bandeja.
                if (EnvioRegistrado.BUZON_DE_PRUEBAS.equals(envio.destino())) {
                    desviados++;
                } else {
                    aceptados++;
                }
            }
            case EnvioRegistrado.OMITIDO -> omitidos++;
            default -> rechazados++;
        }
    }

    /** Del mas reciente al mas antiguo, como maximo {@code cuantos}. */
    public synchronized List<EnvioRegistrado> ultimos(int cuantos) {
        List<EnvioRegistrado> copia = new ArrayList<>(recientes);
        return copia.subList(0, Math.min(Math.max(cuantos, 0), copia.size()));
    }

    /** Aceptados por el servidor principal (el proveedor, fuera de desarrollo local). */
    public synchronized long aceptados() {
        return aceptados;
    }

    /** Aceptados por el buzon de pruebas: direcciones reservadas, que no llegan a nadie. */
    public synchronized long desviados() {
        return desviados;
    }

    public synchronized long rechazados() {
        return rechazados;
    }

    /** Los que no se enviaron a proposito: direccion reservada sin buzon de pruebas. */
    public synchronized long omitidos() {
        return omitidos;
    }
}