package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.nexusbattles.comun.error.ErrorDeNegocio;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.CreditosDelJugador;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.ReservaDeCreditos;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

/**
 * Adaptador del puerto de creditos mientras no exista el modulo proveedor.
 *
 * <p><b>Esto no es un doble ni un simulador.</b> No devuelve datos inventados:
 * falla, y dice por que. Existe porque el contexto de Spring necesita un bean
 * para el puerto, y porque un adaptador que fingiera tener saldo dejaria pasar
 * salas con recompensa que nadie ha pagado.
 *
 * <p>Consecuencia visible y buscada: hoy se pueden crear salas <b>sin
 * recompensa</b> de extremo a extremo, y una sala con recompensa devuelve 503
 * explicando que falta la integracion. Es la verdad del estado del sistema.
 *
 * <p>Se borra el dia que exista {@code contracts/openapi/creditos.yaml} y su
 * adaptador HTTP. Ese dia esta clase desaparece entera; no se adapta.
 */
@Component
public class CreditosSinIntegrar implements CreditosDelJugador {

    @Override
    public ReservaDeCreditos reservar(UUID idJugador, int creditos, UUID idSala) {
        throw new IntegracionDeCreditosPendiente();
    }

    @Override
    public void liberar(UUID idReserva) {
        throw new IntegracionDeCreditosPendiente();
    }

    /** 503: no es culpa de quien llama, es que falta una pieza del sistema. */
    public static class IntegracionDeCreditosPendiente extends ErrorDeNegocio {

        public static final URI TIPO =
                URI.create("https://nexusbattles.local/errores/creditos-sin-integrar");

        public IntegracionDeCreditosPendiente() {
            super(TIPO,
                  "Las apuestas todavia no estan disponibles",
                  503,
                  "Por ahora solo se pueden crear salas sin recompensa. "
                          + "La apuesta de creditos se habilita cuando este listo el modulo de creditos.");
        }
    }
}
