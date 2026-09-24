package com.nexusbattles.plataforma.correo.envio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Entrega el correo fuera de la peticion que lo pidio.
 *
 * <p><b>Por que.</b> Hasta R18 el envio ocurria dentro de la llamada HTTP:
 * quien pedia el correo se quedaba esperando a que el servidor SMTP
 * contestara. Con Mailpit eso son milisegundos y no se notaba. Con un
 * proveedor real son uno a tres segundos, y el cliente de ms-identidad corta
 * a los dos: habria dado por fallido un correo que si salio y, con su
 * reintento, habria mandado el mismo mensaje dos veces. Pagando cuota dos
 * veces, y con el jugador recibiendo dos copias del mismo codigo.
 *
 * <p>RF-COR-001 describe justo esto: el sistema "compone el mensaje sobre la
 * plantilla, <b>lo encola</b> y lo entrega al proveedor, registrando el
 * resultado del envio". El 202 que ya devolvia el controlador pasa a ser
 * cierto del todo: la peticion se acepto, la entrega va aparte y su resultado
 * queda en {@link RegistroDeEnvios}.
 *
 * <p>Un fallo aqui no rompe al que pidio el correo -- ya se fue -- pero no se
 * pierde: queda anotado como RECHAZADO con su motivo, que es lo que se
 * consulta despues para saber por que no llego.
 */
@Component
public class EntregaEnSegundoPlano {

    private static final Logger BITACORA = LoggerFactory.getLogger(EntregaEnSegundoPlano.class);

    private final Executor ejecutor;
    private final EnviadorCorreoService enviador;

    public EntregaEnSegundoPlano(Executor ejecutorDeCorreo, EnviadorCorreoService enviador) {
        this.ejecutor = ejecutorDeCorreo;
        this.enviador = enviador;
    }

    public void entregar(
            String destinatario, String asunto, String plantilla, Map<String, Object> variables) {
        ejecutor.execute(() -> {
            try {
                enviador.enviar(destinatario, asunto, plantilla, variables);
            } catch (RuntimeException e) {
                // Ya quedo anotado como RECHAZADO con su motivo dentro de
                // enviar(). Aqui solo se impide que la excepcion muera en un
                // hilo suelto sin dejar rastro.
                BITACORA.warn(
                        "No se pudo entregar {} a {}: {}",
                        plantilla,
                        EnvioRegistrado.enmascarar(destinatario),
                        e.getMessage());
            }
        });
    }
}