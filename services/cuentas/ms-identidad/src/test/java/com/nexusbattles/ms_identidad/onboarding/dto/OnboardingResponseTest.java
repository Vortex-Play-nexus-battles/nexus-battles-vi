package com.nexusbattles.ms_identidad.onboarding.dto;

import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoPaso;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingJugador;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingPaso;
import com.nexusbattles.ms_identidad.onboarding.model.PasoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lo que ve la pantalla «Preparando tu cuenta» (R17)")
class OnboardingResponseTest {

    private static final UUID UID = UUID.fromString("0c0c0c0c-1d1d-4e4e-8f8f-202020202020");
    private static final LocalDateTime HOY = LocalDateTime.of(2026, 9, 24, 12, 0);

    private static OnboardingJugador alta(EstadoOnboarding estado, int intentos) {
        OnboardingJugador alta = new OnboardingJugador(UID, 1, "t", HOY);
        ReflectionTestUtils.setField(alta, "estado", estado);
        ReflectionTestUtils.setField(alta, "intentos", intentos);
        return alta;
    }

    private static OnboardingPaso paso(PasoOnboarding cual, EstadoPaso estado, String detalle, String error) {
        OnboardingPaso paso = new OnboardingPaso(UID, cual, estado, detalle, HOY);
        ReflectionTestUtils.setField(paso, "ultimoError", error);
        return paso;
    }

    @Test
    @DisplayName("completa: lista, con los creditos acreditados y el heroe inicial")
    void completa() {
        OnboardingResponse respuesta = OnboardingResponse.de(alta(EstadoOnboarding.COMPLETO, 1), List.of(
                paso(PasoOnboarding.EQUIPO, EstadoPaso.HECHO, "heroe=h1;equipados=a1", null),
                paso(PasoOnboarding.HEROE, EstadoPaso.HECHO, "heroe=h1;producto=p", null),
                paso(PasoOnboarding.CREDITOS, EstadoPaso.HECHO, "transaccion=TX;monto=500;fuente=PARAMETRO", null),
                paso(PasoOnboarding.PERFIL, EstadoPaso.HECHO, "perfil", null)));

        assertThat(respuesta.estado()).isEqualTo("COMPLETO");
        assertThat(respuesta.listo()).isTrue();
        assertThat(respuesta.creditosIniciales()).isEqualTo(500L);
        assertThat(respuesta.heroeInicial()).isEqualTo("h1");
        assertThat(respuesta.siguienteIntento()).isNull();
        assertThat(respuesta.pasos()).extracting(OnboardingResponse.Paso::titulo).containsExactly(
                "Perfil de jugador", "Créditos de bienvenida", "Héroe inicial", "Equipo del héroe");
        assertThat(respuesta.pasos()).allSatisfy(p -> assertThat(p.motivo()).isNull());
    }

    @Test
    @DisplayName("en error: motivo legible por paso, nunca el error tecnico, y cuando se reintenta")
    void enError() {
        OnboardingJugador enError = alta(EstadoOnboarding.ERROR_REINTENTABLE, 2);
        ReflectionTestUtils.setField(enError, "siguienteIntento", HOY.plusSeconds(30));

        OnboardingResponse respuesta = OnboardingResponse.de(enError, List.of(
                paso(PasoOnboarding.CREDITOS, EstadoPaso.ERROR, null, "SERVICIO_NO_DISPONIBLE: ms-finanzas 503 en 10.0.0.5"),
                paso(PasoOnboarding.HEROE, EstadoPaso.ERROR, null, "CONFIGURACION_INCOMPLETA: sin kit"),
                paso(PasoOnboarding.EQUIPO, EstadoPaso.ERROR, null, "DEPENDE_DE_OTRO_PASO: espera"),
                paso(PasoOnboarding.PERFIL, EstadoPaso.ERROR, null, "texto sin causa")));

        assertThat(respuesta.listo()).isFalse();
        assertThat(respuesta.intentos()).isEqualTo(2);
        assertThat(respuesta.siguienteIntento()).isEqualTo("2026-09-24T12:00:30Z");
        assertThat(respuesta.creditosIniciales()).isNull();
        assertThat(respuesta.pasos()).extracting(OnboardingResponse.Paso::motivo).containsExactly(
                "No se pudo completar. Lo reintentamos automáticamente.",
                "El servicio no respondió a tiempo. Lo reintentamos automáticamente.",
                "Este paso todavía no está configurado en la plataforma. Se completará solo cuando lo esté.",
                "Espera a que el héroe inicial esté listo.");
        assertThat(respuesta.pasos()).noneSatisfy(p -> assertThat(String.valueOf(p.motivo())).contains("10.0.0.5"));
    }

    @Test
    @DisplayName("un rechazo del servicio tambien se explica; un monto ilegible no se inventa")
    void rechazoYMontoIlegible() {
        OnboardingResponse respuesta = OnboardingResponse.de(alta(EstadoOnboarding.EN_PROCESO, 1), List.of(
                paso(PasoOnboarding.CREDITOS, EstadoPaso.HECHO, "monto=muchos", null),
                paso(PasoOnboarding.HEROE, EstadoPaso.ERROR, null, "RECHAZADO: 422")));

        assertThat(respuesta.creditosIniciales()).isNull();
        assertThat(respuesta.heroeInicial()).isNull();
        assertThat(respuesta.pasos().get(1).motivo()).startsWith("El servicio rechazó la operación");
    }

    @Test
    @DisplayName("NO_APLICA: nada que preparar, lista")
    void noAplica() {
        OnboardingResponse respuesta = OnboardingResponse.noAplica();
        assertThat(respuesta.listo()).isTrue();
        assertThat(respuesta.pasos()).isEmpty();
    }

    @Test
    @DisplayName("la causa se recupera del texto guardado; un texto sin causa conocida no la tiene")
    void causaGuardada() {
        assertThat(PasoFallido.causaDe("RECHAZADO: x")).isEqualTo(PasoFallido.Causa.RECHAZADO);
        assertThat(PasoFallido.causaDe("INVENTADA: x")).isNull();
        assertThat(PasoFallido.causaDe("sin dos puntos")).isNull();
        assertThat(PasoFallido.causaDe(":vacia")).isNull();
        assertThat(PasoFallido.causaDe(null)).isNull();
        assertThat(new PasoFallido(PasoFallido.Causa.RECHAZADO, "m").paraGuardar()).isEqualTo("RECHAZADO: m");
    }
}
