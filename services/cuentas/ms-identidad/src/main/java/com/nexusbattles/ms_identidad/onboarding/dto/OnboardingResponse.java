package com.nexusbattles.ms_identidad.onboarding.dto;

import com.nexusbattles.ms_identidad.onboarding.model.EstadoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.model.EstadoPaso;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingJugador;
import com.nexusbattles.ms_identidad.onboarding.model.OnboardingPaso;
import com.nexusbattles.ms_identidad.onboarding.model.PasoOnboarding;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido;
import com.nexusbattles.ms_identidad.onboarding.service.ProcesadorOnboarding;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

/**
 * Estado del alta del jugador que ha iniciado sesion ({@code GET
 * /api/v1/auth/onboarding}), para la pantalla «Preparando tu cuenta».
 *
 * <p>Los pasos llevan un titulo y, si fallaron, un motivo legible. El error
 * tecnico nunca sale de aqui: se queda en la bitacora y en la base de datos.
 *
 * @param estado            PENDIENTE, EN_PROCESO, COMPLETO, ERROR_REINTENTABLE
 *                          o NO_APLICA (cuenta anterior al alta automatica o
 *                          de administracion: no hay nada que preparar)
 * @param listo             si el jugador ya puede entrar al juego
 * @param siguienteIntento  ISO-8601 en UTC, solo en ERROR_REINTENTABLE
 * @param creditosIniciales los que acredito ms-finanzas, cuando ya estan
 * @param heroeInicial      id del elemento de inventario del heroe inicial
 */
public record OnboardingResponse(String estado,
                                 boolean listo,
                                 Integer version,
                                 int intentos,
                                 String siguienteIntento,
                                 List<Paso> pasos,
                                 Long creditosIniciales,
                                 String heroeInicial) {

    public static final String NO_APLICA = "NO_APLICA";

    public record Paso(String paso, String titulo, String estado, String motivo) {
    }

    public static OnboardingResponse noAplica() {
        return new OnboardingResponse(NO_APLICA, true, null, 0, null, List.of(), null, null);
    }

    public static OnboardingResponse de(OnboardingJugador alta, List<OnboardingPaso> pasos) {
        List<OnboardingPaso> ordenados = pasos.stream()
                .sorted(Comparator.comparing(OnboardingPaso::getPaso))
                .toList();
        boolean completo = alta.getEstado() == EstadoOnboarding.COMPLETO;
        String siguiente = alta.getEstado() == EstadoOnboarding.ERROR_REINTENTABLE && alta.getSiguienteIntento() != null
                ? alta.getSiguienteIntento().atOffset(ZoneOffset.UTC).toString()
                : null;
        return new OnboardingResponse(
                alta.getEstado().name(),
                completo,
                alta.getVersionBootstrap(),
                alta.getIntentos(),
                siguiente,
                ordenados.stream().map(OnboardingResponse::paso).toList(),
                creditos(ordenados),
                detalle(ordenados, PasoOnboarding.HEROE, "heroe"));
    }

    private static Paso paso(OnboardingPaso paso) {
        String motivo = paso.getEstado() == EstadoPaso.ERROR ? motivoDe(paso.getUltimoError()) : null;
        return new Paso(paso.getPaso().name(), tituloDe(paso.getPaso()), paso.getEstado().name(), motivo);
    }

    static String tituloDe(PasoOnboarding paso) {
        return switch (paso) {
            case PERFIL -> "Perfil de jugador";
            case CREDITOS -> "Créditos de bienvenida";
            case HEROE -> "Héroe inicial";
            case EQUIPO -> "Equipo del héroe";
        };
    }

    static String motivoDe(String ultimoError) {
        PasoFallido.Causa causa = PasoFallido.causaDe(ultimoError);
        if (causa == null) {
            return "No se pudo completar. Lo reintentamos automáticamente.";
        }
        return switch (causa) {
            case SERVICIO_NO_DISPONIBLE -> "El servicio no respondió a tiempo. Lo reintentamos automáticamente.";
            case CONFIGURACION_INCOMPLETA -> "Este paso todavía no está configurado en la plataforma."
                    + " Se completará solo cuando lo esté.";
            case RECHAZADO -> "El servicio rechazó la operación. Lo reintentamos automáticamente.";
            case DEPENDE_DE_OTRO_PASO -> "Espera a que el héroe inicial esté listo.";
        };
    }

    private static Long creditos(List<OnboardingPaso> pasos) {
        String monto = detalle(pasos, PasoOnboarding.CREDITOS, "monto");
        if (monto == null) {
            return null;
        }
        try {
            return Long.parseLong(monto);
        } catch (NumberFormatException ilegible) {
            return null;
        }
    }

    private static String detalle(List<OnboardingPaso> pasos, PasoOnboarding cual, String clave) {
        return pasos.stream()
                .filter(paso -> paso.getPaso() == cual && paso.hecho())
                .findFirst()
                .map(paso -> ProcesadorOnboarding.valorDe(paso.getDetalle(), clave))
                .orElse(null);
    }
}
