package com.nexusbattles.ms_ecommerce.catalogo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Donde esta el catalogo maestro y cuanto se le espera
 * ({@code catalogo.productos.*} en application.properties).
 *
 * <p>Los tiempos son cortos a proposito: el borde (nginx) corta a los 60 s, y
 * este servicio tiene que rendirse antes para poder contestar un 503 con
 * sentido en lugar de un 504 del borde. Mismo criterio que los clientes
 * salientes de salas-partidas (HU-DIS-003).
 *
 * @param url             base del servicio productos ({@code PRODUCTOS_URL})
 * @param timeoutConexion cuanto se espera a que acepte la conexion
 * @param timeoutLectura  cuanto se espera la respuesta una vez conectado
 */
@ConfigurationProperties(prefix = "catalogo.productos")
public record PropiedadesDelCatalogo(
        @DefaultValue("http://localhost:8103") String url,
        @DefaultValue("2s") Duration timeoutConexion,
        @DefaultValue("3s") Duration timeoutLectura) {
}
