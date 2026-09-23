package com.nexusbattles.plataforma.adminparametros.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Validacion por tipo/rango/opciones, inalterables y vigencia programada (HU-ADM-001 CA-02, CA-03, CA-05). */
@DisplayName("Parametro (HU-ADM-001)")
class ParametroTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID ADMIN = UUID.randomUUID();

    @Test
    @DisplayName("un entero se valida contra su rango; texto, decimal mal formado o fuera de rango se rechaza (CA-02)")
    void entero() {
        Parametro p = Parametro.editable("sanciones.suspension.maxima-dias", Parametro.Tipo.ENTERO, "30",
                BigDecimal.ONE, BigDecimal.valueOf(365), null, "D-20");
        assertThat(p.validar(" 45 ")).isEqualTo("45");
        assertThat(p.validar(null)).isNull();
        assertThatThrownBy(() -> p.validar("abc")).isInstanceOfSatisfying(ParametroRechazado.class,
                ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.VALOR_INVALIDO));
        assertThatThrownBy(() -> p.validar("0")).hasMessageContaining("menor que 1");
        assertThatThrownBy(() -> p.validar("366")).hasMessageContaining("mayor que 365");
        assertThatThrownBy(() -> p.validar("1.5")).isInstanceOf(ParametroRechazado.class);
    }

    @Test
    @DisplayName("decimal, booleano y texto con opciones cerradas")
    void otrosTipos() {
        Parametro decimal = Parametro.editable("subastas.incremento-minimo", Parametro.Tipo.DECIMAL, null,
                new BigDecimal("0.01"), BigDecimal.valueOf(1000000), null, "RF-SUB-002");
        assertThat(decimal.validar("2.50")).isEqualTo("2.5");
        assertThatThrownBy(() -> decimal.validar("0.001")).hasMessageContaining("menor que 0.01");
        assertThatThrownBy(() -> decimal.validar("x")).isInstanceOf(ParametroRechazado.class);

        Parametro booleano = Parametro.editable("x.activo", Parametro.Tipo.BOOLEANO, "true", null, null, null, "prueba");
        assertThat(booleano.validar("FALSE")).isEqualTo("false");
        assertThatThrownBy(() -> booleano.validar("si")).isInstanceOf(ParametroRechazado.class);

        Parametro texto = Parametro.editable("salas.apuestas.si-gana-la-maquina", Parametro.Tipo.TEXTO, "LIBERAR",
                null, null, "LIBERAR|CONSUMIR", "D-02");
        assertThat(texto.validar("CONSUMIR")).isEqualTo("CONSUMIR");
        assertThat(texto.opcionesComoLista()).containsExactly("LIBERAR", "CONSUMIR");
        assertThatThrownBy(() -> texto.validar("REGALAR")).hasMessageContaining("LIBERAR/CONSUMIR");
    }

    @Test
    @DisplayName("un inalterable no se edita (CA-05), aunque el valor sea valido")
    void inalterable() {
        Parametro p = Parametro.inalterable("torneos.dias-entre-torneos", Parametro.Tipo.ENTERO, "91", "Charter");
        assertThat(p.validar("90")).isEqualTo("90");
        assertThatThrownBy(() -> p.cambiar("90", "probando", ADMIN, AHORA, null))
                .isInstanceOfSatisfying(ParametroRechazado.class,
                        ex -> assertThat(ex.motivo()).isEqualTo(ParametroRechazado.Motivo.INALTERABLE));
        assertThat(p.valor()).isEqualTo("91");
        assertThat(p.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("cambio inmediato: nueva version con anterior y nuevo; cambio programado: entra en vigor a su hora (CA-01, CA-03)")
    void versionesYVigencia() {
        Parametro p = Parametro.editable("sanciones.suspension.maxima-dias", Parametro.Tipo.ENTERO, "30",
                BigDecimal.ONE, BigDecimal.valueOf(365), null, "D-20");
        Version v2 = p.cambiar("15", "Sprint Review", ADMIN, AHORA, null);
        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.valorAnterior()).isEqualTo("30");
        assertThat(v2.valorNuevo()).isEqualTo("15");
        assertThat(v2.vigenteDesde()).isEqualTo(AHORA);
        assertThat(p.valor()).isEqualTo("15");
        assertThat(p.valorVigenteEn(AHORA)).isEqualTo("15");

        OffsetDateTime manana = AHORA.plusDays(1);
        Version v3 = p.cambiar("7", "Baja a partir de manana", ADMIN, AHORA, manana);
        assertThat(v3.version()).isEqualTo(3);
        assertThat(v3.vigenteDesde()).isEqualTo(manana);
        assertThat(p.valor()).isEqualTo("15");
        assertThat(p.valorProgramado()).isEqualTo("7");
        assertThat(p.valorVigenteEn(AHORA.plusHours(1))).isEqualTo("15");
        assertThat(p.valorVigenteEn(manana)).isEqualTo("7");
        assertThat(p.consolidar(AHORA.plusHours(1))).isFalse();
        assertThat(p.consolidar(manana.plusMinutes(1))).isTrue();
        assertThat(p.valor()).isEqualTo("7");
        assertThat(p.valorProgramado()).isNull();
        assertThat(p.vigenteDesde()).isNull();

        // Una fecha ya pasada equivale a «ahora».
        p.cambiar("9", "otra", ADMIN, AHORA.plusDays(2), AHORA);
        assertThat(p.valor()).isEqualTo("9");
        assertThat(p.valorProgramado()).isNull();
    }
}
