package com.nexusbattles.plataforma.metricasplataforma.sistema;

import java.time.Instant;

/**
 * Como esta un servicio ahora mismo, en las palabras que usa la consola.
 *
 * @param servicio  nombre del catalogo de despliegue
 * @param estado    OPERATIVO | CAIDO | LENTO | NO_DESPLEGADO | NO_OBSERVABLE
 * @param detalle   por que; en un fallo, el motivo recortado
 * @param instante  cuando se comprobo
 */
public record EstadoDeServicio(String servicio, String estado, String detalle, Instant instante) {

    public static final String OPERATIVO = "OPERATIVO";
    public static final String CAIDO = "CAIDO";
    public static final String NO_DESPLEGADO = "NO_DESPLEGADO";

    /**
     * Conecto, pero no contesto dentro del plazo de la sonda (RFINAL-08).
     *
     * Ni OPERATIVO —nadie confirmo que este sano— ni CAIDO —acepto la
     * conexion; lo mas probable es que este sobrecargado o colgado—. La
     * consola lo pinta en ambar y dice cuanto se espero. No poder conectar
     * (host apagado, regla de red que descarta) sigue siendo CAIDO.
     */
    public static final String LENTO = "LENTO";

    /**
     * Esta desplegado en otro host y en este entorno no tiene sonda configurada.
     *
     * Decir CAIDO seria mentir y decir OPERATIVO seria inventar una
     * comprobacion que nadie hizo. Se dice lo que es. En DEV ya no se usa: el
     * despliegue (docker-compose.deploy.yml) le da sonda a cada servicio del
     * host de contenido, cuyo grupo de seguridad admite a este host.
     */
    public static final String NO_OBSERVABLE = "NO_OBSERVABLE";
}
