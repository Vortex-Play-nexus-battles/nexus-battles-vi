package com.nexusbattles.ms_ecommerce.integracion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Configuracion de la compra ({@code tienda.*} en application.properties,
 * regla 10: todo por variable de entorno, con un valor por omision seguro).
 *
 * @param http        tiempos de espera de toda llamada saliente de la compra
 * @param servicios   donde vive cada servicio con el que habla la compra
 * @param credencial  la credencial de servicio de la tienda (ADR-005)
 * @param correo      el correo de confirmacion
 * @param ordenes     la tarea programada que termina las ordenes a medias
 * @param zonaHoraria la de la fecha que lleva el correo de confirmacion
 */
@ConfigurationProperties(prefix = "tienda")
public record PropiedadesDeLaTienda(
        @DefaultValue Http http,
        @DefaultValue Servicios servicios,
        @DefaultValue Credencial credencial,
        @DefaultValue Correo correo,
        @DefaultValue Ordenes ordenes,
        @DefaultValue("America/Bogota") ZoneId zonaHoraria) {

    /**
     * Cortos a proposito, como los del catalogo: el borde corta a los 60 s y
     * una compra hace varias llamadas seguidas; cada una tiene que rendirse
     * antes para que la orden quede en su estado y la respuesta llegue.
     */
    public record Http(
            @DefaultValue("2s") Duration timeoutConexion,
            @DefaultValue("5s") Duration timeoutLectura) {
    }

    /**
     * Bases de cada servicio. Vacias = no configurado: la compra no empieza
     * (503 {@code compra-no-disponible}) en vez de cobrar y quedarse sin poder
     * entregar. {@code parametros} vacio solo apaga USD y EUR.
     *
     * @param inventario {@code INVENTARIO_BASE_URL}, sin {@code /api/v1}
     * @param finanzas   {@code FINANZAS_BASE_URL}, con {@code /api/v1} (su context-path)
     * @param correo     {@code CORREO_URL}, sin {@code /api/v1}
     * @param identidad  {@code IDENTIDAD_URL}, sin {@code /api/v1}
     * @param parametros {@code PARAMETROS_URL}, con {@code /api/v1}
     */
    public record Servicios(
            @DefaultValue("http://localhost:8102") String inventario,
            @DefaultValue("http://localhost:8093/api/v1") String finanzas,
            @DefaultValue("http://localhost:8082") String correo,
            @DefaultValue("http://localhost:8089") String identidad,
            @DefaultValue("http://localhost:8088/api/v1") String parametros) {
    }

    /**
     * @param url          endpoint de token del emisor ({@code DIRECTORIO_ACTIVO_URL})
     * @param clientId     {@code DIRECTORIO_ACTIVO_CLIENT_ID}; vacio = sin credencial
     * @param clientSecret {@code DIRECTORIO_ACTIVO_CLIENT_SECRET}
     */
    public record Credencial(
            @DefaultValue("") String url,
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret) {
    }

    /**
     * @param habilitado false solo en un entorno sin servicio de correo (el
     *                   banco E2E de esta rama): la orden se completa y dice
     *                   {@code correoConfirmacion: OMITIDO}. En cualquier otro
     *                   entorno, true: un correo que no sale es un paso que
     *                   falta, y la orden espera en ENTREGADA a que salga.
     */
    public record Correo(@DefaultValue("true") boolean habilitado) {
    }

    /**
     * @param concesion           cuanto tiempo una orden es de quien la procesa
     *                            (la peticion o la tarea programada) sin renovar
     * @param pendienteCaducaTras una orden que se quedo PENDIENTE (la pasarela no
     *                            respondio y nadie reintento) pasa a RECHAZADA
     *                            pasado este tiempo
     * @param reintentoInicial    espera antes del primer reintento de un paso
     *                            que fallo por una averia; se duplica en cada uno
     * @param reintentoMaximo     tope de esa espera
     * @param lote                ordenes que la tarea programada retoma por vuelta
     */
    public record Ordenes(
            @DefaultValue("2m") Duration concesion,
            @DefaultValue("30m") Duration pendienteCaducaTras,
            @DefaultValue("15s") Duration reintentoInicial,
            @DefaultValue("15m") Duration reintentoMaximo,
            @DefaultValue("20") int lote) {
    }
}
