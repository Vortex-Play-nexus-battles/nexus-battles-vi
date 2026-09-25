package com.nexusbattles.plataforma.correo.envio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo unico que sale de este servicio sobre un destinatario.
 *
 * <p>La mitad de este archivo prueba lo que NO aparece: en cuanto la evidencia
 * de entrega o la bitacora guarden direcciones completas, dejan de ser un
 * diagnostico y pasan a ser una lista de correos de los jugadores.
 */
class EnmascararTest {

    @ParameterizedTest
    @CsvSource({
        "victima@gmail.com, v***a@gmail.com",
        "simon.perez@gmail.com, s***z@gmail.com",
        "ana@nexus.test, a***a@nexus.test",
        "ab@nexus.test, ***@nexus.test",
        "a@nexus.test, ***@nexus.test",
        "' jugador@upb.edu.co ', j***r@upb.edu.co",
    })
    void dejaPrimeraYUltimaLetraYElDominioEntero(String direccion, String esperado) {
        // El dominio entero se conserva porque casi todo lo que un proveedor
        // rechaza lo rechaza por dominio: sin el no se puede diagnosticar.
        assertThat(Enmascarar.direccion(direccion)).isEqualTo(esperado);
    }

    @Test
    void siempreTresAsteriscosParaNoDelatarElLargo() {
        assertThat(Enmascarar.direccion("ab1@x.co")).isEqualTo("a***1@x.co");
        assertThat(Enmascarar.direccion("abcdefghijklmnopqrstuvwxyz@x.co")).isEqualTo("a***z@x.co");
    }

    @Test
    void unaDireccionVaciaOSinArrobaNoRevienta() {
        assertThat(Enmascarar.direccion(null)).isEqualTo("(sin destinatario)");
        assertThat(Enmascarar.direccion("   ")).isEqualTo("(sin destinatario)");
        assertThat(Enmascarar.direccion("no-es-un-correo")).isEqualTo("***");
        assertThat(Enmascarar.direccion("@solo-dominio.com")).isEqualTo("***");
    }

    @Test
    void enmascaraCadaDireccionDentroDeUnTextoLibre() {
        String rechazo = "550 5.1.1 <victima@gmail.com>: Recipient address rejected (from no-reply@nexus.test)";

        assertThat(Enmascarar.direccionesEn(rechazo))
                .isEqualTo("550 5.1.1 <v***a@gmail.com>: Recipient address rejected (from n***y@nexus.test)")
                .doesNotContain("victima");
    }

    @Test
    void unTextoSinDireccionesQuedaIgual() {
        assertThat(Enmascarar.direccionesEn("Connection refused")).isEqualTo("Connection refused");
        assertThat(Enmascarar.direccionesEn("")).isEmpty();
        assertThat(Enmascarar.direccionesEn(null)).isNull();
    }
}
