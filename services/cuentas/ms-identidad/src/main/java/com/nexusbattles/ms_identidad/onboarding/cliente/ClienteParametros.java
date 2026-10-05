package com.nexusbattles.ms_identidad.onboarding.cliente;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_identidad.onboarding.traza.InterceptorDeTraza;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * Valor vigente de un parametro del sistema (admin-parametros, RF-ADM-001,
 * {@code GET /parametros/{clave}/valor}, ruta publica).
 *
 * <p>Tres respuestas distintas, que el alta trata distinto:
 * <ul>
 *   <li>valor presente: manda el parametro;</li>
 *   <li>vacio —clave sin valor (el PO no ha decidido), clave que el catalogo
 *       no tiene (404) o URL sin configurar—: se aplica el respaldo de
 *       desarrollo, si lo hay, y se dice;</li>
 *   <li>servicio caido: {@code PasoFallido} y se reintenta mas tarde. No se cae
 *       al respaldo, porque el administrador puede haber fijado otro valor y
 *       el jugador recibiria una cantidad que nadie decidio.</li>
 * </ul>
 */
@Component
public class ClienteParametros {

    private final RestClient http;
    private final String base;

    @Autowired
    public ClienteParametros(@Value("${app.onboarding.parametros-url:}") String base,
                             InterceptorDeTraza traza) {
        this.base = ClientesHttp.sinBarraFinal(base);
        this.http = ClientesHttp.construir(traza);
    }

    public Optional<String> valorDe(String clave) {
        if (base.isBlank()) {
            return Optional.empty();
        }
        try {
            Valor valor = http.get()
                    .uri(base + "/parametros/{clave}/valor", clave)
                    .retrieve()
                    .body(Valor.class);
            return Optional.ofNullable(valor)
                    .map(Valor::valor)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(texto -> !texto.isEmpty());
        } catch (RestClientResponseException respuesta) {
            if (respuesta.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw ClientesHttp.traducir("admin-parametros", "consultar " + clave, respuesta);
        } catch (RuntimeException fallo) {
            throw ClientesHttp.traducir("admin-parametros", "consultar " + clave, fallo);
        }
    }

    /** {@code valor} puede llegar como texto o como numero segun el tipo. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Valor(String clave, Object valor, String tipo, Integer version) {
    }
}
