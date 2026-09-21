package com.nexusbattles.ms_identidad.auth.servicio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registro de los servicios que pueden pedir una credencial de servicio
 * (ADR-001, ADR-005): el equivalente a los <i>clients</i> de un realm de
 * Keycloak, mientras no haya Keycloak.
 *
 * <h2>De donde sale</h2>
 *
 * <p>De {@code app.servicios.clientes} (variable {@code AUTH_CLIENTES_SERVICIO}),
 * con la forma {@code cliente=secreto;otro=secreto}. Regla 10: aqui no se
 * versiona ningun valor real; en local se define en {@code .env} y en el host
 * de despliegue lo genera y guarda el flujo de CD, que es quien reparte el
 * mismo secreto al servicio cliente ({@code DIRECTORIO_ACTIVO_CLIENT_SECRET}).
 *
 * <p>Sin la variable no hay clientes y el endpoint de token responde
 * {@code invalid_client} a todo el mundo: un servicio que no fue dado de alta
 * no obtiene credencial. Es lo mismo que hace un realm vacio.
 *
 * <p>Los secretos se comparan en tiempo constante. No se guardan en claro en
 * ninguna bitacora: al arrancar se anuncian solo los identificadores.
 */
@Component
public class ClientesDeServicio {

    private static final Logger BITACORA = LoggerFactory.getLogger(ClientesDeServicio.class);

    private final Map<String, byte[]> secretos;

    public ClientesDeServicio(@Value("${app.servicios.clientes:}") String configuracion) {
        this.secretos = Collections.unmodifiableMap(leer(configuracion));
        if (secretos.isEmpty()) {
            BITACORA.warn("app.servicios.clientes no define ningun cliente: ningun servicio podra "
                    + "obtener credencial de servicio (ADR-005). Define AUTH_CLIENTES_SERVICIO.");
        } else {
            BITACORA.info("Clientes de servicio registrados: {}", secretos.keySet());
        }
    }

    /** Identificadores de los servicios dados de alta. */
    public Set<String> clientes() {
        return secretos.keySet();
    }

    /** Si el par cliente/secreto corresponde a un servicio dado de alta. */
    public boolean autentica(String clientId, String secreto) {
        if (clientId == null || secreto == null) {
            return false;
        }
        byte[] esperado = secretos.get(clientId);
        if (esperado == null) {
            return false;
        }
        return MessageDigest.isEqual(esperado, secreto.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, byte[]> leer(String configuracion) {
        Map<String, byte[]> resultado = new LinkedHashMap<>();
        if (configuracion == null || configuracion.isBlank()) {
            return resultado;
        }
        for (String entrada : configuracion.split("[;,]")) {
            String par = entrada.strip();
            if (par.isEmpty()) {
                continue;
            }
            int igual = par.indexOf('=');
            if (igual <= 0 || igual == par.length() - 1) {
                throw new IllegalStateException("app.servicios.clientes: cada entrada debe ser cliente=secreto; "
                        + "entrada invalida (no se muestra por seguridad).");
            }
            String clientId = par.substring(0, igual).strip();
            String secreto = par.substring(igual + 1).strip();
            if (secreto.length() < 16) {
                throw new IllegalStateException("app.servicios.clientes: el secreto de '" + clientId
                        + "' es demasiado corto (minimo 16 caracteres).");
            }
            resultado.put(clientId, secreto.getBytes(StandardCharsets.UTF_8));
        }
        return resultado;
    }
}
