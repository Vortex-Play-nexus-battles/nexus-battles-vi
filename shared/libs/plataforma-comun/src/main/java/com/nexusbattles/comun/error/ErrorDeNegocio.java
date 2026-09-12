package com.nexusbattles.comun.error;

import java.net.URI;
import java.util.List;

/**
 * Raiz de todos los errores de negocio del sistema.
 *
 * <p><b>Regla 4 de plataforma:</b> formato de error estandar, identico en los 20
 * modulos. Esta clase existe para que ese formato se defina una sola vez y no
 * veinte. Cada servicio extiende esta clase con sus errores propios; la capa REST
 * la traduce a problem details (RFC 7807) sin conocer el dominio.
 *
 * <p>El campo decisivo es {@link #tipo()}: es un identificador estable sobre el
 * que la interfaz puede programar. {@code titulo} y {@code detalle} son texto para
 * la persona y pueden cambiar de redaccion sin previo aviso, asi que nadie debe
 * comparar cadenas para decidir que pintar. Ver
 * {@code shared/ui-kit/MAPEO-ERRORES.md}.
 *
 * <p>No hereda de {@code RuntimeException} por comodidad: hereda porque un error
 * de negocio interrumpe el caso de uso y no tiene sentido obligar a declararlo en
 * cada firma intermedia.
 */
public abstract class ErrorDeNegocio extends RuntimeException {

    private final URI tipo;
    private final String titulo;
    private final int estado;
    private final List<ErrorDeCampo> errores;

    protected ErrorDeNegocio(URI tipo, String titulo, int estado, String detalle) {
        this(tipo, titulo, estado, detalle, List.of());
    }

    protected ErrorDeNegocio(URI tipo, String titulo, int estado, String detalle,
                             List<ErrorDeCampo> errores) {
        super(detalle);
        if (tipo == null) {
            throw new IllegalArgumentException("Un error de negocio necesita un tipo estable.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Un error de negocio necesita un titulo.");
        }
        if (detalle == null || detalle.isBlank()) {
            throw new IllegalArgumentException(
                    "Un error de negocio necesita explicar el motivo, no solo que fallo.");
        }
        this.tipo = tipo;
        this.titulo = titulo;
        this.estado = estado;
        this.errores = List.copyOf(errores == null ? List.of() : errores);
    }

    /** Identificador estable del tipo de error. Es lo unico sobre lo que se programa. */
    public URI tipo() {
        return tipo;
    }

    /** Titulo corto, sin punto final. Va al titulo del componente Aviso. */
    public String titulo() {
        return titulo;
    }

    /** Codigo HTTP. Decide el tono del Aviso segun la tabla del mapeo de errores. */
    public int estado() {
        return estado;
    }

    /** Explicacion para la persona. Va al cuerpo del Aviso. */
    public String detalle() {
        return getMessage();
    }

    /** Errores por campo. Vacio salvo en validaciones de formulario. */
    public List<ErrorDeCampo> errores() {
        return errores;
    }
}
