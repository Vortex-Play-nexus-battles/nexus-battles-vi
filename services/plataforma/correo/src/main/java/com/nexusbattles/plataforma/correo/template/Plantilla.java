package com.nexusbattles.plataforma.correo.template;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Los correos que este servicio sabe componer: uno por ruta de envio de
 * {@code contracts/openapi/correo.yaml}.
 *
 * <p>El {@link #nombre()} es lo que se guarda en la cola, lo que se ve en la
 * evidencia de entrega ({@code GET /correos/envios}) y la etiqueta de las
 * metricas. La {@link #ruta()} es la plantilla Thymeleaf que lo pinta sobre
 * el layout corporativo (HU-COR-001).
 *
 * <p><b>Datos sensibles.</b> Dos plantillas llevan un codigo de un solo uso.
 * Mientras el correo espera en la cola hay que guardarlo: un reintento tiene
 * que poder volver a pintarlo. En cuanto el envio termina -entregado,
 * desviado, omitido o fallido- ya no hay motivo para conservarlo, y un codigo
 * guardado en una tabla es una cuenta activable, o una contrasena
 * restablecible, por quien lea la tabla. {@link #sinDatosSensibles} los quita.
 */
public enum Plantilla {

    BIENVENIDA("bienvenida"),
    AVISO_ACCESO("aviso-acceso"),
    CAMBIO_CLAVE("cambio-clave"),
    CONFIRMACION_CUENTA("confirmacion-cuenta", "codigo"),
    RECUPERACION_CLAVE("recuperacion-clave", "codigo"),
    MISION("mision"),
    SUBASTA("subasta"),
    CONFIRMACION_COMPRA("confirmacion-compra"),
    SANCION("sancion");

    private final String nombre;
    private final Set<String> datosSensibles;

    Plantilla(String nombre, String... datosSensibles) {
        this.nombre = nombre;
        this.datosSensibles = Set.of(datosSensibles);
    }

    /** Nombre corto, p. ej. {@code recuperacion-clave}. */
    public String nombre() {
        return nombre;
    }

    /** Plantilla Thymeleaf que la pinta, p. ej. {@code email/recuperacion-clave}. */
    public String ruta() {
        return "email/" + nombre;
    }

    /** Variables que no pueden sobrevivir al final del envio. */
    public Set<String> datosSensibles() {
        return datosSensibles;
    }

    /** Copia de {@code datos} sin las variables sensibles de esta plantilla. */
    public Map<String, Object> sinDatosSensibles(Map<String, Object> datos) {
        Map<String, Object> copia = datos == null ? new LinkedHashMap<>() : new LinkedHashMap<>(datos);
        copia.keySet().removeAll(datosSensibles);
        return copia;
    }

    /**
     * La plantilla de ese nombre, o vacio si no existe.
     *
     * <p>Una fila de la cola con un nombre desconocido solo puede venir de
     * otra version del servicio; se trata como fallo permanente en vez de
     * reventar al leerla.
     */
    public static Optional<Plantilla> deNombre(String nombre) {
        for (Plantilla plantilla : values()) {
            if (plantilla.nombre.equals(nombre)) {
                return Optional.of(plantilla);
            }
        }
        return Optional.empty();
    }
}
