package com.nexusbattles.ms_ecommerce.integracion.identidad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.ms_ecommerce.integracion.ConfiguracionDeIntegraciones;
import com.nexusbattles.ms_ecommerce.integracion.ServicioNoDisponibleException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * El correo y el apodo de un jugador, para escribirle la confirmacion de
 * compra ({@code GET /api/v1/internal/usuarios/{uid}/contacto},
 * ms-identidad-admin.yaml, solo servicios).
 *
 * <p>La tienda no guarda el correo de nadie (regla 7 y minimizacion de datos
 * personales): lo pide en el momento de enviar y lo olvida.
 */
@Component
public class ClienteDeIdentidad {

    static final String RUTA = "/api/v1/internal/usuarios/{uid}/contacto";

    private final RestClient cliente;

    public ClienteDeIdentidad(@Qualifier(ConfiguracionDeIntegraciones.IDENTIDAD) RestClient cliente) {
        this.cliente = cliente;
    }

    /**
     * @return el contacto; vacio si ms-identidad no tiene esa cuenta (404)
     * @throws ServicioNoDisponibleException si ms-identidad no respondio o no
     *         acepto la credencial de la tienda
     */
    public Optional<Contacto> contacto(String uid) {
        try {
            return cliente.get()
                    .uri(RUTA, uid)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 404) {
                            return Optional.<Contacto>empty();
                        }
                        if (estado != 200) {
                            throw new ServicioNoDisponibleException("ms-identidad",
                                    "ms-identidad respondio " + estado + " al pedir un contacto");
                        }
                        Contacto contacto = respuesta.bodyTo(Contacto.class);
                        if (contacto == null || contacto.email() == null || contacto.email().isBlank()) {
                            return Optional.<Contacto>empty();
                        }
                        return Optional.of(contacto);
                    });
        } catch (RestClientException | IllegalArgumentException fallo) {
            throw new ServicioNoDisponibleException("ms-identidad",
                    "No se pudo pedir el contacto a ms-identidad: " + fallo.getMessage(), fallo);
        }
    }

    /**
     * Lo que devuelve ms-identidad; el correo nunca se escribe en la bitacora.
     *
     * @param apodo nombre visible, con el que se saluda en el correo
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contacto(String email, String apodo, String estado) {

        @Override
        public String toString() {
            return "Contacto[apodo=" + apodo + ", estado=" + estado + "]";
        }
    }
}
