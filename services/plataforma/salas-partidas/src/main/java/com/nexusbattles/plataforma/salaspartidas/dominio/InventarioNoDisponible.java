package com.nexusbattles.plataforma.salaspartidas.dominio;

import com.nexusbattles.comun.error.ErrorDeNegocio;

import java.net.URI;

/**
 * El inventario no contesta y la verificacion de heroe no se puede resolver.
 *
 * <p>503, no 200 con un resultado inventado. El precedente es el chat: cuando
 * la lista negra no responde, {@code EnviarMensaje} bloquea el mensaje en vez
 * de publicarlo sin verificar. Aqui la misma idea con el signo contrario —no
 * se deja pasar ni se rechaza— porque la verificacion previa no tiene efectos:
 * su unico trabajo es informar, y una respuesta falsa informaria mal. Un
 * «disponible» de mentira manda al jugador a pulsar Entrar para que lo rechacen
 * despues, que es exactamente lo que RF-JUE-003 quiere evitar.
 *
 * <p>Que la interfaz sepa distinguirlo importa: {@code validacion-heroe.js}
 * pinta un estado de error con reintento, no una de las cuatro variantes del
 * dialogo (RNF-USA-003).
 */
public class InventarioNoDisponible extends ErrorDeNegocio {

    public static final URI TIPO =
            URI.create("https://nexusbattles.local/errores/inventario-no-disponible");

    public InventarioNoDisponible(Throwable causa) {
        super(TIPO,
              "No se pudo comprobar tu heroe",
              503,
              "El servicio de inventario no respondio. Vuelve a intentarlo en unos segundos.");
        initCause(causa);
    }

    public InventarioNoDisponible(String motivo) {
        super(TIPO,
              "No se pudo comprobar tu heroe",
              503,
              "El servicio de inventario no respondio (" + motivo + "). "
                      + "Vuelve a intentarlo en unos segundos.");
    }
}
