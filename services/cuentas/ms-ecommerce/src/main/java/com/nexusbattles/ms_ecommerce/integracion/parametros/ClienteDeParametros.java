package com.nexusbattles.ms_ecommerce.integracion.parametros;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.PropiedadesDeLaTienda;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Lectura de un parametro del sistema en admin-parametros
 * ({@code GET /parametros/{clave}/valor}, admin-parametros.yaml 1.0.0: la
 * lectura ligera y publica que el contrato ofrece a los servicios).
 *
 * <p>Tres respuestas distintas y ninguna se confunde con otra: el valor; que
 * el parametro no exista o no tenga valor (decision del PO pendiente), que es
 * un vacio; y que admin-parametros no responda, que es
 * {@link ServicioNoDisponibleException}.
 */
@Component
public class ClienteDeParametros {

    static final String RUTA = "/parametros/{clave}/valor";

    private final RestClient cliente;
    private final boolean configurado;

    public ClienteDeParametros(@Qualifier(ConfiguracionDeIntegraciones.PARAMETROS) RestClient cliente,
                               PropiedadesDeLaTienda propiedades) {
        this.cliente = cliente;
        String base = propiedades.servicios().parametros();
        this.configurado = base != null && !base.isBlank();
    }

    /**
     * @return el valor vigente como texto; vacio si el parametro no existe
     *         (404), existe sin valor ({@code valor: null}) o el despliegue no
     *         le dio a la tienda la direccion de admin-parametros
     *         ({@code PARAMETROS_URL} vacia, la misma convencion que el resto
     *         de servicios: vacia = no se consulta)
     * @throws ServicioNoDisponibleException si admin-parametros no respondio
     */
    public Optional<String> valor(String clave) {
        if (!configurado) {
            return Optional.empty();
        }
        try {
            return cliente.get()
                    .uri(RUTA, clave)
                    .exchange((peticion, respuesta) -> {
                        HttpStatusCode estado = respuesta.getStatusCode();
                        if (estado.value() == 404) {
                            return Optional.<String>empty();
                        }
                        if (!estado.is2xxSuccessful()) {
                            throw new ServicioNoDisponibleException("admin-parametros",
                                    "admin-parametros respondio " + estado.value() + " al leer " + clave);
                        }
                        ValorVigente cuerpo = respuesta.bodyTo(ValorVigente.class);
                        return Optional.ofNullable(cuerpo)
                                .map(ValorVigente::valor)
                                .filter(valor -> !valor.isBlank());
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException("admin-parametros",
                    "No se pudo leer " + clave + " en admin-parametros: " + fallo.getMessage(), fallo);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ValorVigente(String clave, String valor) {
    }
}
