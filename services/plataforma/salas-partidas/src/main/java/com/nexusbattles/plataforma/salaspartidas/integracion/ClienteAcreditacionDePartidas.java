package com.nexusbattles.plataforma.salaspartidas.integracion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nexusbattles.plataforma.resiliencia.CortaCircuitos;
import com.nexusbattles.plataforma.salaspartidas.aplicacion.AcreditadorDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditoPorPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.CreditosNoDisponibles;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador de {@link AcreditadorDePartidas} contra ms-finanzas — HU-JUE-012.
 *
 * <p>Habla {@code POST /partidas/resultado} del contrato
 * {@code contracts/openapi/creditos.yaml} (1.2.0). Es la unica clase que
 * conoce esas formas: el puerto habla en terminos del dominio.
 *
 * <p><b>Politica de fallo</b>, la misma que {@link ClienteCreditos}: lo que no
 * responde (o responde 5xx) pasa por el corta circuitos y sale como
 * {@code DependenciaDegradada}; lo que el libro contesta con 4xx llega aqui.
 * De esos, el {@code 409 partida-ya-procesada} no es un fallo sino la
 * idempotencia del libro funcionando (CA-05): se devuelve
 * {@link AcreditadorDePartidas.Acreditacion#repetida()}. Cualquier otro 4xx es
 * un desacuerdo entre este servicio y el libro, y se propaga como
 * {@link CreditosNoDisponibles} para que quede anotado y se reintente, no
 * para taparlo.
 *
 * <p>El {@link RestClient} llega con la credencial de servicio (ADR-005): el
 * libro cierra {@code /partidas/**} a {@code ROLE_SERVICIO} (#455).
 */
public class ClienteAcreditacionDePartidas implements AcreditadorDePartidas {

    /** El libro ya tenia la partida: es la respuesta idempotente, no un error. */
    static final int YA_PROCESADA = 409;

    private final RestClient http;
    private final String base;
    private final CortaCircuitos corta;

    public ClienteAcreditacionDePartidas(RestClient http, String base, CortaCircuitos corta) {
        this.http = Objects.requireNonNull(http);
        this.base = Objects.requireNonNull(base).replaceAll("/+$", "");
        this.corta = Objects.requireNonNull(corta);
    }

    @Override
    public Acreditacion acreditar(InformeDePartida informe) {
        Objects.requireNonNull(informe);
        Contestacion<Resultado> contestacion = Contestacion.protegida(corta, () -> http.post()
                .uri(base + "/partidas/resultado")
                .body(Peticion.de(informe))
                .retrieve()
                .body(Resultado.class));

        if (contestacion.rechazada()) {
            if (contestacion.estado() == YA_PROCESADA) {
                return Acreditacion.repetida();
            }
            throw new CreditosNoDisponibles("el libro rechazo el resultado de la partida "
                    + informe.idPartida() + " con " + contestacion.estado());
        }

        Resultado respuesta = contestacion.cuerpo();
        if (respuesta == null || respuesta.acreditaciones() == null) {
            throw new CreditosNoDisponibles("el libro respondio un resultado que no se entiende");
        }
        List<CreditoPorPartida> creditos = respuesta.acreditaciones().stream()
                .map(a -> new CreditoPorPartida(UUID.fromString(a.uid()), a.monto(), a.esGanador(), a.cofreId()))
                .toList();
        List<UUID> excluidos = respuesta.sancionadosExcluidos() == null ? List.of()
                : respuesta.sancionadosExcluidos().stream().map(UUID::fromString).toList();
        return new Acreditacion(creditos, excluidos, false);
    }

    // -- Formas exactas del contrato creditos.yaml -----------------------------

    record Peticion(String partidaId, String tipoPartida, String ganadorUid, List<String> ganadoresUid,
                    List<Participante> participantes) {
        static Peticion de(InformeDePartida informe) {
            List<String> ganadores = informe.ganadores().stream().map(UUID::toString).toList();
            return new Peticion(informe.idPartida().toString(), informe.tipo().name(),
                    ganadores.size() == 1 ? ganadores.get(0) : null, ganadores,
                    informe.jugadores().stream()
                            .map(j -> new Participante(j.id().toString(), j.sancionado()))
                            .toList());
        }
    }

    record Participante(String uid, boolean sancionado) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resultado(String partidaId, List<Acreditada> acreditaciones, List<String> sancionadosExcluidos) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Acreditada(String uid, int monto, boolean esGanador, UUID cofreId) { }
}
