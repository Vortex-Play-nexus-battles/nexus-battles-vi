package com.nexusbattles.ms_cumplimiento.auditoria.catalogo;

import com.nexusbattles.ms_cumplimiento.auditoria.repository.AuditLogRepository;
import com.nexusbattles.ms_cumplimiento.auditoria.service.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Bitácora: la importación del catálogo se enciende solo con todo lo que necesita (SA-18)")
class ConfiguracionDelCatalogoTest {

    private final AuditLogService bitacora = mock(AuditLogService.class);
    private final AuditLogRepository repositorio = mock(AuditLogRepository.class);
    private final ConfiguracionDelCatalogo configuracion = new ConfiguracionDelCatalogo();

    private ConfiguracionDelCatalogo.ImportacionProgramada con(String url, String emisor, String id, String secreto) {
        return configuracion.importacionDelCatalogo(bitacora, repositorio, url, emisor, id, secreto,
            Duration.ofMinutes(2), 100, 10, Duration.ofSeconds(2), Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("sin URL del catálogo o sin credencial queda apagada, y la ronda no hace nada")
    void apagada() {
        ConfiguracionDelCatalogo.ImportacionProgramada sinUrl = con("", "http://emisor", "ms-cumplimiento", "s");
        ConfiguracionDelCatalogo.ImportacionProgramada sinSecreto =
            con("http://productos:8080", "http://emisor", "ms-cumplimiento", " ");

        assertThat(sinUrl.importador()).isEmpty();
        assertThat(sinSecreto.importador()).isEmpty();
        sinUrl.ronda();
        verifyNoInteractions(bitacora, repositorio);
    }

    @Test
    @DisplayName("con URL y credencial queda encendida")
    void encendida() {
        assertThat(con("http://productos:8080", "http://emisor/token", "ms-cumplimiento", "secreto")
            .importador()).isPresent();
    }
}
