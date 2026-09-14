package com.nexusbattles.plataforma.resiliencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/**
 * HU-DIS-003, CA-02 — lo que llega al frontend cuando una seccion se limita.
 *
 * <p>Se comprueba contra {@code shared/ui-kit/MAPEO-ERRORES.md}: el documento
 * dice que la interfaz decide por {@code type} y por {@code status}, nunca por
 * el texto, asi que esos dos campos son el contrato de verdad.
 */
class ErroresDeDegradacionTest {

    private final DependenciaDegradada degradada =
            new DependenciaDegradada("inventario", "Inventario", new IllegalStateException("caido"));

    @Test
    void elTipoEsEstableYEsLoUnicoSobreLoQueElFrontendPuedeProgramar() {
        ProblemDetail problema = ErroresDeDegradacion.problema(degradada, 30);

        assertThat(problema.getType())
                .hasToString("https://nexusbattles.local/errores/seccion-no-disponible");
    }

    @Test
    void es503YNo500PorqueEsteServicioNoSeHaRoto() {
        // 500 significa «este servicio se rompio». Aqui el servicio esta
        // perfectamente: es una dependencia la que no responde, y el jugador
        // tiene que entender que lo demas si funciona.
        assertThat(ErroresDeDegradacion.problema(degradada, 30).getStatus()).isEqualTo(503);
    }

    @Test
    void llevaLaSeccionParaQueElJugadorSepaQueFuncionEstaLimitada() {
        ProblemDetail problema = ErroresDeDegradacion.problema(degradada, 30);

        assertThat(problema.getProperties()).containsEntry("seccion", "Inventario");
        assertThat(problema.getTitle()).contains("Inventario");
        assertThat(problema.getDetail()).contains("El resto del juego sigue funcionando");
    }

    @Test
    void diceCuandoVolverAIntentarlo() {
        assertThat(ErroresDeDegradacion.problema(degradada, 45).getProperties())
                .containsEntry("reintentarEnSegundos", 45L);
    }

    @Test
    void elNombreInternoDeLaDependenciaNoSeMeteEnElTextoDelJugador() {
        // MAPEO-ERRORES.md §9: no mostrar identificadores internos al usuario.
        // 'dependencia' viaja como propiedad aparte, para la bitacora.
        ProblemDetail problema = ErroresDeDegradacion.problema(degradada, 30);

        assertThat(problema.getProperties()).containsEntry("dependencia", "inventario");
        assertThat(problema.getDetail()).doesNotContain("inventario");
    }

    @Test
    void elRegistroDeDegradacionDiceQueSeccionesEstanLimitadasYDesdeCuando() {
        RegistroDeDegradacion registro = new RegistroDeDegradacion();
        Instant caida = Instant.parse("2026-09-11T10:00:00Z");

        registro.degradada("inventario", "Inventario", caida);
        registro.degradada("inventario", "Inventario", caida.plusSeconds(600));

        assertThat(registro.activas()).singleElement()
                .extracting(RegistroDeDegradacion.Degradacion::desde)
                .as("repetir la marca no mueve la fecha de inicio de la degradacion")
                .isEqualTo(caida);

        registro.recuperada("inventario");
        assertThat(registro.sinDegradaciones()).isTrue();
    }
}
