package com.nexusbattles.plataforma.metricasplataforma.sistema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Los servicios que la consola administrativa muestra en «Sistema».
 *
 * ## Por que no reutiliza `disponibilidad.servicios`
 *
 * Esa lista alimenta la cifra de HU-DIS-001, y DEC-01 excluye de ella a los
 * servicios de los equipos socios a proposito: sus caidas no cuentan contra
 * el porcentaje del bloque. Meter aqui los diecisiete cambiaria ese numero
 * sin que nadie lo hubiera decidido.
 *
 * Son dos preguntas distintas y merecen dos listas: «¿cumplimos el acuerdo de
 * disponibilidad?» y «¿que esta encendido ahora mismo?». Esta responde la
 * segunda, no produce ninguna metrica y no toca el registro historico.
 *
 * ## Que significa cada entrada
 *
 * Clave: el nombre del servicio tal y como lo llama el catalogo de despliegue
 * ({@code infrastructure/despliegue/servicios.json}).
 *
 * Valor: la URL de salud, o la palabra {@code NO_DESPLEGADO} para los que el
 * catalogo marca fuera del host por capacidad. Eso no es un fallo y no debe
 * pintarse en rojo: es una decision medida, y la consola lo dice con esas
 * palabras en vez de dejar un hueco o, peor, inventar un verde.
 */
@ConfigurationProperties(prefix = "sistema")
public record ConfiguracionDelSistema(Map<String, String> servicios) {

    /** Valor reservado: el servicio existe y esta fuera del host a proposito. */
    public static final String NO_DESPLEGADO = "NO_DESPLEGADO";

    /** Valor reservado: esta desplegado en otro host y esta sonda no lo alcanza. */
    public static final String NO_OBSERVABLE = "NO_OBSERVABLE";

    public ConfiguracionDelSistema {
        // LinkedHashMap por la misma razon que en disponibilidad: el orden de
        // la configuracion es el que ve quien lee la pantalla.
        servicios = servicios == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(servicios));
    }
}
