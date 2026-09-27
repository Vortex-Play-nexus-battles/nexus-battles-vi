package com.nexusbattles.ms_finanzas.partidas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La tabla del contenido del cofre y su sorteo (cofres.yaml 1.1.0, D-B7-18).
 *
 * <p>Las pruebas estadísticas usan semillas fijas: son deterministas, y sus
 * tolerancias están muy por encima de la desviación esperada, así que fallan
 * solo si el sorteo está sesgado de verdad.
 */
@DisplayName("TablaDeCofre · contenido configurable y sorteo reproducible")
class TablaDeCofreTest {

    @Test
    @DisplayName("se lee de texto: productoId=peso con ; , o saltos de línea, y # para comentarios")
    void seLeeDeTexto() {
        TablaDeCofre tabla = TablaDeCofre.desde("PO-2026-10", """
                # comentario entero
                a=1   # con comentario al final
                b=2; c=3,d=4
                """);

        assertThat(tabla.entradas()).extracting(TablaDeCofre.Entrada::productoId).containsExactly("a", "b", "c", "d");
        assertThat(tabla.pesoTotal()).isEqualTo(10);
        assertThat(tabla.esProvisional()).isFalse();
        assertThat(TablaDeCofre.desde("PROVISIONAL-DEV-7", "a=1").esProvisional()).isTrue();
    }

    @Test
    @DisplayName("una tabla mal escrita se rechaza al leerla, no al sortear")
    void tablasInvalidas() {
        assertThatThrownBy(() -> TablaDeCofre.desde("v", "a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablaDeCofre.desde("v", "a=x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablaDeCofre.desde("v", "a=0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablaDeCofre.desde("v", "=3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablaDeCofre.desde("v", "# solo comentarios"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablaDeCofre.desde(" ", "a=1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablaDeCofre.desde("v", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("misma semilla, mismo premio: el sorteo se puede repetir con lo que guarda el cofre")
    void reproducible() {
        TablaDeCofre tabla = TablaDeCofre.desde("v", "a=1;b=1;c=1;d=1;e=1");
        Random semillas = new Random(1);

        for (int i = 0; i < 200; i++) {
            long semilla = semillas.nextLong();
            assertThat(tabla.sortear(semilla)).isEqualTo(tabla.sortear(semilla));
        }
    }

    @Test
    @DisplayName("los pesos mandan: 1 contra 3 sale ~25 % / ~75 % (20.000 sorteos, ±2 puntos)")
    void pesos() {
        TablaDeCofre tabla = TablaDeCofre.desde("v", "raro=1;comun=3");
        Random semillas = new Random(20260927L);
        int sorteos = 20_000;
        int raros = 0;

        for (int i = 0; i < sorteos; i++) {
            if (tabla.sortear(semillas.nextLong()).productoId().equals("raro")) {
                raros++;
            }
        }

        // Binomial(20000, 0,25): desviacion tipica ~0,3 puntos.
        assertThat(raros / (double) sorteos).isBetween(0.23, 0.27);
    }

    /**
     * La tabla provisional reparte por igual entre sus 40 piezas: con 40.000
     * sorteos, el estadístico chi-cuadrado (39 grados de libertad) tiene que
     * quedar por debajo de 72,1, su valor crítico al 0,1 %.
     */
    @Test
    @DisplayName("la tabla provisional de desarrollo: 40 piezas de equipo distintas y equiprobables (chi-cuadrado)")
    void tablaProvisional() {
        TablaDeCofre tabla = ConfiguracionDeCofres.tablaDe("", ConfiguracionDeCofres.VERSION_PROVISIONAL);
        Random semillas = new Random(7L);
        Map<String, Integer> veces = new HashMap<>();
        int sorteos = 40_000;

        for (int i = 0; i < sorteos; i++) {
            veces.merge(tabla.sortear(semillas.nextLong()).productoId(), 1, Integer::sum);
        }

        double esperado = sorteos / 40.0;
        double chiCuadrado = veces.values().stream()
                .mapToDouble(observado -> Math.pow(observado - esperado, 2) / esperado).sum();
        assertThat(tabla.version()).isEqualTo("PROVISIONAL-DEV-1");
        assertThat(tabla.esProvisional()).isTrue();
        assertThat(tabla.entradas()).hasSize(40);
        assertThat(new HashSet<>(tabla.entradas().stream().map(TablaDeCofre.Entrada::productoId).toList()))
                .hasSize(40);
        assertThat(veces).hasSize(40);
        assertThat(chiCuadrado).isLessThan(72.1);
    }

    @Test
    @DisplayName("una tabla propia exige su propia versión; con ella, se usa tal cual")
    void tablaPropia() {
        assertThatThrownBy(() -> ConfiguracionDeCofres.tablaDe("x=1", ConfiguracionDeCofres.VERSION_PROVISIONAL))
                .isInstanceOf(IllegalStateException.class);

        TablaDeCofre propia = ConfiguracionDeCofres.tablaDe("x=1;y=2", "PO-2026-10");

        assertThat(propia.version()).isEqualTo("PO-2026-10");
        assertThat(propia.entradas()).extracting(TablaDeCofre.Entrada::productoId).containsExactly("x", "y");
    }

    @Test
    @DisplayName("no hay tabla sin entradas ni entrada sin producto")
    void entradasObligatorias() {
        assertThatThrownBy(() -> new TablaDeCofre("v", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TablaDeCofre("v", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TablaDeCofre.Entrada(" ", 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
