package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.segundofactor.CambioDeSegundoFactor.Cambio;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;
import com.nexusbattles.ms_identidad.onboarding.auditoria.AuditoriaDeCuenta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Lo que queda en la auditoria de cada cambio del segundo factor (sin secreto
 * ni codigos), y como se presenta el 403 del primer paso.
 */
@DisplayName("Auditoria y mensajes del segundo factor")
class AuditoriaDelSegundoFactorTest {

    @Test
    @DisplayName("activar, desactivar y usar un codigo de recuperacion quedan con su motivo y sin secretos")
    void auditaCadaCambio() {
        AuditoriaDeCuenta auditoria = mock(AuditoriaDeCuenta.class);
        AuditoriaDelSegundoFactor oyente = new AuditoriaDelSegundoFactor(auditoria);

        oyente.alCambiar(new CambioDeSegundoFactor("uid-1", Cambio.ACTIVADO, null, "10.0.0.1"));
        oyente.alCambiar(new CambioDeSegundoFactor("uid-1", Cambio.DESACTIVADO, null, "10.0.0.2"));
        oyente.alCambiar(new CambioDeSegundoFactor("uid-1", Cambio.RECUPERACION_USADA, 4L, "10.0.0.3"));
        oyente.alCambiar(new CambioDeSegundoFactor("uid-1", Cambio.RECUPERACION_USADA, null, null));

        verify(auditoria).segundoFactor("uid-1", "SEGUNDO_FACTOR_ACTIVADO", "totp", "10.0.0.1");
        verify(auditoria).segundoFactor("uid-1", "SEGUNDO_FACTOR_DESACTIVADO", "sin-segundo-factor", "10.0.0.2");
        verify(auditoria).segundoFactor("uid-1", "CODIGO_RECUPERACION_USADO", "codigos-restantes=4", "10.0.0.3");
        verify(auditoria).segundoFactor("uid-1", "CODIGO_RECUPERACION_USADO", "codigos-restantes=0", null);
    }

    @Test
    @DisplayName("el 403 del login dice que falta: el codigo, o activar el segundo factor; nunca imprime el desafio")
    void mensajesDelPrimerPaso() {
        DesafioEmitido codigo = new DesafioEmitido("secreto-opaco", Instant.EPOCH, Proposito.VERIFICAR);
        DesafioEmitido enrolar = new DesafioEmitido("secreto-opaco", Instant.EPOCH, Proposito.ENROLAR);

        SegundoFactorRequeridoException conCodigo = new SegundoFactorRequeridoException(codigo);
        SegundoFactorRequeridoException conEnrolamiento = new SegundoFactorRequeridoException(enrolar);

        assertThat(conCodigo.tipo()).isEqualTo("segundo-factor-requerido");
        assertThat(conCodigo.titulo()).isEqualTo("Falta el segundo factor");
        assertThat(conCodigo.getMessage()).contains("código de tu aplicación");
        assertThat(conEnrolamiento.tipo()).isEqualTo("segundo-factor-enrolamiento-requerido");
        assertThat(conEnrolamiento.titulo()).isEqualTo("Falta activar el segundo factor");
        assertThat(conEnrolamiento.getMessage()).contains("Actívala");
        assertThat(codigo.toString()).doesNotContain("secreto-opaco");
    }
}
