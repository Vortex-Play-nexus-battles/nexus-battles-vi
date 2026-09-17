package com.nexusbattles.plataforma.salaspartidas.tiemporeal;

import com.nexusbattles.plataforma.salaspartidas.dominio.MotivoDeCancelacion;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;

import java.util.UUID;

/**
 * Mensaje {@code sala.cancelada} del AsyncAPI, tal cual viaja.
 *
 * <p>Calcado de {@code SalaCancelada} en
 * {@code contracts/websocket/salas-partidas.yaml}: tres campos obligatorios
 * —{@code tipo}, {@code idSala}, {@code motivo}— y {@code creditosDevueltos}
 * opcional.
 *
 * <p>El motivo viaja como enumerado y no como frase: la interfaz lo traduce al
 * idioma de quien mira. Una frase escrita aqui obligaria a este servicio a
 * decidir en que idioma se muestra, que no es asunto suyo.
 *
 * <p>Los creditos devueltos viajan porque la descripcion del mensaje lo dice
 * —«los creditos comprometidos se devuelven»— y quien estaba dentro tiene
 * derecho a ver el numero, no solo a que se lo prometan.
 */
public record AvisoDeCancelacion(String tipo, UUID idSala, MotivoDeCancelacion motivo,
                                 Integer creditosDevueltos) {

    /** Valor constante del discriminador, fijado por el contrato. */
    public static final String TIPO = "sala.cancelada";

    static AvisoDeCancelacion de(Sala sala, MotivoDeCancelacion motivo, int creditosDevueltos) {
        return new AvisoDeCancelacion(TIPO, sala.id(), motivo, creditosDevueltos);
    }
}
