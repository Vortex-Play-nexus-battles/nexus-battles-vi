package com.nexusbattles.plataforma.comentarios.publicacion;

import java.util.Objects;

import com.nexusbattles.plataforma.comentarios.DeteccionAutomatica;
import com.nexusbattles.plataforma.comentarios.HiloDeComentarios;

/**
 * Puerta al filtro automatico de contenido que exigen RF-COM-001 y RF-COM-007.
 *
 * <p>El dominio no sabe como se verifica el texto, solo recibe el veredicto.
 * La implementacion real llama por REST al servicio de moderacion y sanciones
 * usando el contrato publicado en contracts/openapi/moderacion-lista-negra.yaml,
 * respetando la regla de plataforma de integrarse siempre por interfaz y nunca
 * por consulta directa a un esquema ajeno.
 */
public interface FiltroDeContenido {

    VeredictoDelFiltro verificar(String texto);

    /**
     * El veredicto y, si retiene, por que — HU-COM-007 CA-01.
     *
     * <p>Lo limpio no tiene nada que explicar; lo senalado siempre lo explica,
     * aunque sea para decir que la lista negra no respondio.
     *
     * @param resultado si el comentario se publica o queda en revision
     * @param deteccion lo que explica la retencion; nula cuando es LIMPIO
     */
    record VeredictoDelFiltro(HiloDeComentarios.ResultadoDelFiltro resultado, DeteccionAutomatica deteccion) {

        public VeredictoDelFiltro {
            Objects.requireNonNull(resultado, "el filtro tiene que dar un veredicto");
            if (resultado == HiloDeComentarios.ResultadoDelFiltro.LIMPIO && deteccion != null) {
                throw new IllegalArgumentException("un veredicto limpio no lleva deteccion");
            }
            if (resultado == HiloDeComentarios.ResultadoDelFiltro.SENALADO && deteccion == null) {
                throw new IllegalArgumentException("un veredicto senalado tiene que decir por que");
            }
        }

        /** El texto sale al hilo. */
        public static VeredictoDelFiltro limpio() {
            return new VeredictoDelFiltro(HiloDeComentarios.ResultadoDelFiltro.LIMPIO, null);
        }

        /** El comentario queda en revision, con lo que explica la retencion. */
        public static VeredictoDelFiltro senalado(DeteccionAutomatica deteccion) {
            return new VeredictoDelFiltro(HiloDeComentarios.ResultadoDelFiltro.SENALADO, deteccion);
        }
    }
}
