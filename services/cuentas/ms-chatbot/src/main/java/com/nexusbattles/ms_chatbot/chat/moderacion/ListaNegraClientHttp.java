package com.nexusbattles.ms_chatbot.chat.moderacion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

// B11: POST /lista-negra/verificar (moderacion-lista-negra.yaml 2.0.0) con el
// contexto CHAT_GENERAL, que es donde la politica de moderacion aplica
// BLOQUEAR (el mensaje no se entrega). Es publico dentro de la red: no hace
// falta credencial, y sin token la respuesta no dice que termino coincidio.
//
// La lista negra acepta hasta 2000 caracteres y un mensaje del chat hasta
// 4000: se verifica por tramos que se solapan en 120 caracteres (lo que mide
// el termino mas largo que admite la lista), para que un termino partido
// entre dos tramos no se escape.
//
// Si la lista negra no responde, la politica por omision es RECHAZAR
// (fail-closed, D-14); CHATBOT_MODERACION_SI_NO_RESPONDE=PERMITIR la cambia
// para quien prefiera disponibilidad a filtrado mientras moderacion cae.
@Component
public class ListaNegraClientHttp implements ModeracionDeContenido {

    private static final Logger log = LoggerFactory.getLogger(ListaNegraClientHttp.class);

    static final String CONTEXTO = "CHAT_GENERAL";
    static final int TRAMO = 2000;
    static final int SOLAPE = 120;

    private final RestClient listaNegraRestClient;
    private final String url;
    private final boolean permitirSiNoResponde;

    public ListaNegraClientHttp(@Qualifier("listaNegraRestClient") RestClient listaNegraRestClient,
                                @Value("${app.lista-negra.url}") String url,
                                @Value("${chatbot.moderacion.si-no-responde:RECHAZAR}") String siNoResponde) {
        this.listaNegraRestClient = listaNegraRestClient;
        this.url = url;
        this.permitirSiNoResponde = "PERMITIR".equalsIgnoreCase(siNoResponde);
    }

    @Override
    public void verificar(String texto) {
        if (texto == null || texto.isBlank()) {
            return;
        }
        for (String tramo : tramos(texto)) {
            Verificacion resultado;
            try {
                resultado = listaNegraRestClient.post()
                    .uri(url)
                    .body(new Solicitud(tramo, CONTEXTO))
                    .retrieve()
                    .body(Verificacion.class);
            } catch (RestClientException noResponde) {
                noDisponible(noResponde.getClass().getSimpleName());
                return;
            }
            if (resultado == null) {
                noDisponible("respuesta vacia");
                return;
            }
            if (!resultado.aprobado()) {
                throw new ContenidoBloqueado();
            }
        }
    }

    private void noDisponible(String causa) {
        if (permitirSiNoResponde) {
            log.warn("La lista negra no respondio ({}); el mensaje pasa sin verificar por politica PERMITIR", causa);
            return;
        }
        throw new ModeracionNoDisponible();
    }

    static List<String> tramos(String texto) {
        List<String> tramos = new ArrayList<>();
        int inicio = 0;
        while (true) {
            int fin = Math.min(texto.length(), inicio + TRAMO);
            tramos.add(texto.substring(inicio, fin));
            if (fin == texto.length()) {
                return tramos;
            }
            inicio = fin - SOLAPE;
        }
    }

    record Solicitud(String texto, String contexto) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Verificacion(boolean aprobado, String accion) {
    }
}
