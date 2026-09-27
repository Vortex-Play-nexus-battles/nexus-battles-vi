package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.envio.DestinoDeEntrega;

import java.time.Instant;
import java.util.Map;

/**
 * Lo que hay que escribir en una fila despues de un intento.
 *
 * @param estado         estado nuevo
 * @param proximoIntento cuando se puede volver a intentar (solo cuenta en
 *                       ERROR_REINTENTABLE)
 * @param error          resumen saneado del fallo o motivo de la omision;
 *                       nulo si salio bien
 * @param destino        a que servidor se entrego; nulo si no se entrego
 * @param identificador  Message-ID; nulo si no se entrego
 * @param datos          datos que quedan guardados; nulo = no se tocan (un
 *                       correo que espera reintento conserva su codigo)
 * @param enviadoEn      cuando lo acepto un servidor; nulo si no lo acepto
 */
public record CambioDeEstado(
        EstadoDeEnvio estado,
        Instant proximoIntento,
        String error,
        DestinoDeEntrega destino,
        String identificador,
        Map<String, Object> datos,
        Instant enviadoEn) {
}
