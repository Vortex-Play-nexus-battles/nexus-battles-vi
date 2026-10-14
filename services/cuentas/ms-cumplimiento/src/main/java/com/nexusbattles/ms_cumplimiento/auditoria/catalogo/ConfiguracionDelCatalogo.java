package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * La importación de las operaciones del catálogo — SA-18.
 *
 * <p>Se enciende con la URL de productos ({@code CATALOGO_URL}) y la
 * credencial de esta bitácora ({@code DIRECTORIO_ACTIVO_CLIENT_ID} y su
 * secreto, que desplegar.sh genera en el host de plataforma). Si falta algo,
 * queda apagada y lo dice al arrancar: la bitácora nunca deja de arrancar por
 * esto, porque registrar y consultar lo demás no depende de ello.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ConfiguracionDelCatalogo {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionDelCatalogo.class);

    /** La ronda programada, si la importación está encendida. */
    public record ImportacionProgramada(Optional<ImportadorDelCatalogo> importador) {

        @Scheduled(fixedDelayString = "${cumplimiento.catalogo.intervalo:PT30S}",
            initialDelayString = "${cumplimiento.catalogo.retraso-inicial:PT45S}")
        public void ronda() {
            importador.ifPresent(ImportadorDelCatalogo::importarProgramado);
        }
    }

    @Bean
    ImportacionProgramada importacionDelCatalogo(
        AuditLogService bitacora,
        AuditLogRepository repositorio,
        @Value("${cumplimiento.catalogo.url:}") String url,
        @Value("${cumplimiento.credencial.url:}") String emisor,
        @Value("${cumplimiento.credencial.client-id:}") String clientId,
        @Value("${cumplimiento.credencial.client-secret:}") String clientSecret,
        @Value("${cumplimiento.catalogo.solape:PT2M}") Duration solape,
        @Value("${cumplimiento.catalogo.limite:100}") int limite,
        @Value("${cumplimiento.catalogo.lotes-por-ronda:10}") int lotesPorRonda,
        @Value("${cumplimiento.catalogo.timeout-conexion:PT2S}") Duration timeoutConexion,
        @Value("${cumplimiento.catalogo.timeout-lectura:PT10S}") Duration timeoutLectura) {
        if (vacio(url)) {
            log.warn("Bitácora: la importación de las operaciones del catálogo está apagada (falta CATALOGO_URL).");
            return new ImportacionProgramada(Optional.empty());
        }
        if (vacio(emisor) || vacio(clientId) || vacio(clientSecret)) {
            log.warn("Bitácora: la importación de las operaciones del catálogo está apagada:"
                + " falta DIRECTORIO_ACTIVO_URL, DIRECTORIO_ACTIVO_CLIENT_ID o su secreto.");
            return new ImportacionProgramada(Optional.empty());
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutConexion);
        fabrica.setReadTimeout(timeoutLectura);
        RestClient http = RestClient.builder().requestFactory(fabrica).build();
        CredencialDeServicio credencial = new CredencialDeServicio(http, emisor, clientId, clientSecret,
            Clock.systemUTC());
        ClienteDelCatalogo cliente = new ClienteDelCatalogo(http, url, credencial);
        log.info("Bitácora: importa las operaciones del catálogo de {} cada ronda", url);
        return new ImportacionProgramada(Optional.of(
            new ImportadorDelCatalogo(cliente, bitacora, repositorio, solape, limite, lotesPorRonda)));
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
