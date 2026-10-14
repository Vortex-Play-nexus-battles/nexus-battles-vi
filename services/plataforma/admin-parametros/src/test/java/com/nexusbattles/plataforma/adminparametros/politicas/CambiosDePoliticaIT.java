package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.plataforma.adminparametros.parametros.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** V8 contra PostgreSQL real: el orden de lectura de la bandeja y la cola de auditoria (SA-11). */
@Testcontainers
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@DisplayName("SA-11 · cambios en las politicas de uso sobre PostgreSQL")
class CambiosDePoliticaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    private static final Actor ADMIN = new Actor(UUID.randomUUID(), "SUPER_ADMINISTRADOR");

    @Autowired
    private PoliticasService servicio;

    @Autowired
    private CambioDePoliticaRepository cambios;

    private CambioDePolitica publicar(String politica, String titulo) throws InterruptedException {
        // Marcas distintas aunque el reloj de la maquina sea grueso.
        Thread.sleep(5);
        return servicio.publicar(ADMIN, new PoliticasService.Publicacion(politica, titulo,
                "Lo que cambia, explicado para los jugadores en una frase clara.", null, "Acuerdo del comité"),
                "203.0.113.9");
    }

    @Test
    @DisplayName("historial de lo mas reciente a lo mas antiguo; posteriores en orden, estrictos y por lotes")
    void lectura() throws InterruptedException {
        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        CambioDePolitica primero = publicar("TERMINOS_Y_CONDICIONES", "Primero de la lectura");
        CambioDePolitica segundo = publicar("POLITICA_DE_PRIVACIDAD", "Segundo de la lectura");
        CambioDePolitica tercero = publicar("TERMINOS_Y_CONDICIONES", "Tercero de la lectura");

        assertThat(servicio.historial()).extracting(CambioDePolitica::titulo)
                .containsSubsequence("Tercero de la lectura", "Segundo de la lectura", "Primero de la lectura");

        assertThat(servicio.posteriores(antes, 100)).extracting(CambioDePolitica::id)
                .containsSubsequence(primero.id(), segundo.id(), tercero.id());
        assertThat(servicio.posteriores(primero.publicadoEn(), 100)).extracting(CambioDePolitica::id)
                .doesNotContain(primero.id())
                .containsSubsequence(segundo.id(), tercero.id());
        assertThat(servicio.posteriores(tercero.publicadoEn(), 100)).extracting(CambioDePolitica::id)
                .doesNotContain(primero.id(), segundo.id(), tercero.id());

        List<CambioDePolitica> lote = servicio.posteriores(antes, 2);
        assertThat(lote).hasSize(2);
        assertThat(lote.get(0).publicadoEn()).isBeforeOrEqualTo(lote.get(1).publicadoEn());

        CambioDePolitica leido = cambios.findById(segundo.id()).orElseThrow();
        assertThat(leido.politica()).isEqualTo(Politica.POLITICA_DE_PRIVACIDAD);
        assertThat(leido.ipOrigen()).isEqualTo("203.0.113.9");
        assertThat(leido.publicadoPor()).isEqualTo(ADMIN.id());
    }

    @Test
    @DisplayName("la cola de auditoria: sin auditar y sin espera pendiente, del mas antiguo al mas reciente")
    void colaDeAuditoria() throws InterruptedException {
        CambioDePolitica auditado = publicar("TERMINOS_Y_CONDICIONES", "Auditado en la cola");
        CambioDePolitica enEspera = publicar("POLITICA_DE_PRIVACIDAD", "En espera en la cola");
        CambioDePolitica pendiente = publicar("TERMINOS_Y_CONDICIONES", "Pendiente en la cola");
        OffsetDateTime ahora = OffsetDateTime.now(ZoneOffset.UTC);

        auditado.auditado(ahora);
        cambios.save(auditado);
        enEspera.falloDeAuditoria("caido", ahora, Duration.ofMinutes(5), Duration.ofMinutes(15));
        cambios.save(enEspera);

        List<UUID> porAuditar = cambios.porAuditar(ahora, PageRequest.of(0, 50)).stream()
                .map(CambioDePolitica::id).toList();
        assertThat(porAuditar).contains(pendiente.id()).doesNotContain(auditado.id(), enEspera.id());

        List<UUID> masTarde = cambios.porAuditar(ahora.plusMinutes(6), PageRequest.of(0, 50)).stream()
                .map(CambioDePolitica::id).toList();
        assertThat(masTarde).containsSubsequence(enEspera.id(), pendiente.id()).doesNotContain(auditado.id());
        assertThat(cambios.findById(enEspera.id()).orElseThrow().errorDeAuditoria()).isEqualTo("caido");
    }
}
