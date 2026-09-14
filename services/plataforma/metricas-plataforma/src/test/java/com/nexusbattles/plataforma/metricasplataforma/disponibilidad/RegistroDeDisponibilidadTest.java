package com.nexusbattles.plataforma.metricasplataforma.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * HU-DIS-001 — el calculo del que sale el informe.
 *
 * <p>Es dominio puro, asi que aqui se prueban las reglas que el requisito y
 * DEC-01 fijan, sin levantar Spring: cuando empieza y termina una caida, que
 * cuenta y que no, y como sale el porcentaje.
 */
class RegistroDeDisponibilidadTest {

    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant T_FIN = Instant.parse("2026-10-01T00:00:00Z");
    private static final double UMBRAL = 99.95;
    private static final List<String> BLOQUE = List.of("salas-partidas");

    private final RegistroDeDisponibilidad registro = new RegistroDeDisponibilidad();

    private static Instant enMinuto(long minutos) {
        return T0.plus(Duration.ofMinutes(minutos));
    }

    @Test
    void sinCaidasLaDisponibilidadEsDelCienPorCiento() {
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(1)));

        InformeDeDisponibilidad informe = registro.informe(BLOQUE, T0, T_FIN, UMBRAL);

        assertThat(informe.porcentajeDelBloque()).isEqualTo(100d);
        assertThat(informe.cumpleElUmbral()).isTrue();
        assertThat(informe.servicios().get(0).interrupciones()).isEmpty();
    }

    @Test
    void unaCaidaCuentaDesdeQueSeDetectaHastaQueElServicioVuelve() {
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(0)));
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(10), "connection refused"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(25)));

        assertThat(registro.indisponibilidadDe("salas-partidas", T0, T_FIN))
                .isEqualTo(Duration.ofMinutes(15));

        List<Interrupcion> interrupciones = registro.interrupcionesEn(T0, T_FIN);
        assertThat(interrupciones).hasSize(1);
        assertThat(interrupciones.get(0).detalle()).isEqualTo("connection refused");
        assertThat(interrupciones.get(0).abierta()).isFalse();
    }

    @Test
    void variasComprobacionesSeguidasEnRojoNoAbrenVariasInterrupciones() {
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(5), "503"));
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(6), "503"));
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(7), "503"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(8)));

        assertThat(registro.interrupcionesEn(T0, T_FIN)).hasSize(1);
        assertThat(registro.indisponibilidadDe("salas-partidas", T0, T_FIN))
                .isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void unaCaidaQueSigueAbiertaCuentaHastaElFinDelPeriodo() {
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(10), "sin respuesta"));

        Instant corte = enMinuto(40);

        assertThat(registro.indisponibilidadDe("salas-partidas", T0, corte))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(registro.interrupcionesEn(T0, corte).get(0).abierta()).isTrue();
    }

    @Test
    void unaCaidaAnteriorAlPeriodoNoLeRestaTiempoAEstePeriodo() {
        // La caida empieza antes del periodo pedido y termina dentro: solo el
        // tramo de dentro cuenta. Si no se recortara, un mes arrastraria las
        // caidas del anterior y el informe mentiria.
        registro.registrar(Comprobacion.caido("salas-partidas", T0.minus(Duration.ofHours(2)), "previa"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(30)));

        assertThat(registro.indisponibilidadDe("salas-partidas", T0, T_FIN))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void elMantenimientoProgramadoNoCuentaComoIndisponibilidad() {
        // DEC-01: se excluyen las ventanas de mantenimiento programado.
        registro.programarMantenimiento(
                new VentanaDeMantenimiento(enMinuto(10), enMinuto(20), "despliegue L1"));

        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(10), "en mantenimiento"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(30)));

        // 20 minutos caido, de los cuales 10 son mantenimiento programado.
        assertThat(registro.indisponibilidadDe("salas-partidas", T0, T_FIN))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void elMantenimientoNoDejaLaIndisponibilidadEnNegativo() {
        registro.programarMantenimiento(
                new VentanaDeMantenimiento(enMinuto(0), enMinuto(60), "ventana larga"));

        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(10), "en mantenimiento"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(20)));

        assertThat(registro.indisponibilidadDe("salas-partidas", T0, T_FIN)).isZero();
    }

    @Test
    void elInformeTraeElTiempoDisponibleYLasInterrupciones() {
        // CP-02: «incluye el tiempo disponible y las interrupciones registradas».
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(0), "caida"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(60)));

        Instant finDeSemana = T0.plus(Duration.ofHours(168));
        InformeDeDisponibilidad informe = registro.informe(BLOQUE, T0, finDeSemana, UMBRAL);
        InformeDeDisponibilidad.LineaDeServicio linea = informe.servicios().get(0);

        assertThat(informe.periodo()).isEqualTo(Duration.ofHours(168));
        assertThat(linea.indisponible()).isEqualTo(Duration.ofHours(1));
        assertThat(linea.disponible()).isEqualTo(Duration.ofHours(167));
        assertThat(linea.porcentaje()).isEqualTo(99.4d);
        assertThat(linea.interrupciones()).hasSize(1);
    }

    @Test
    void elPorcentajeSeRedondeaADosDecimalesComoElInformeDelCliente() {
        // El ejemplo de la historia: 167 h 52 min de 168 h = 99,92 %.
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(0), "caida"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(8)));

        InformeDeDisponibilidad informe =
                registro.informe(BLOQUE, T0, T0.plus(Duration.ofHours(168)), UMBRAL);

        assertThat(informe.servicios().get(0).porcentaje()).isEqualTo(99.92d);
    }

    @Test
    void laDisponibilidadDelBloqueEsLaDelPeorServicio() {
        // Promediar maquillaria la cifra: si salas-partidas esta caido, el
        // jugador no puede jugar aunque correo responda.
        registro.registrar(Comprobacion.disponible("correo", enMinuto(0)));
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(0), "caida"));
        registro.registrar(Comprobacion.disponible("salas-partidas", enMinuto(60)));

        InformeDeDisponibilidad informe = registro.informe(
                List.of("correo", "salas-partidas"), T0, T0.plus(Duration.ofHours(168)), UMBRAL);

        assertThat(informe.servicios().get(0).porcentaje()).isEqualTo(100d);
        assertThat(informe.porcentajeDelBloque()).isEqualTo(99.4d);
        assertThat(informe.cumpleElUmbral()).isFalse();
        assertThat(informe.porDebajoDelUmbral())
                .extracting(InformeDeDisponibilidad.LineaDeServicio::servicio)
                .containsExactly("salas-partidas");
    }

    @Test
    void elEstadoActualReportaTodosLosServiciosComprobados() {
        // CP-01: el 100 % de los servicios reporta su disponibilidad.
        registro.registrar(Comprobacion.disponible("correo", enMinuto(1)));
        registro.registrar(Comprobacion.caido("salas-partidas", enMinuto(1), "503"));

        assertThat(registro.estadoActual())
                .extracting(Comprobacion::servicio)
                .containsExactlyInAnyOrder("correo", "salas-partidas");
    }

    @Test
    void unPeriodoAlRevesEsUnError() {
        assertThatThrownBy(() -> registro.informe(BLOQUE, T_FIN, T0, UMBRAL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaVentanaDeMantenimientoAlRevesEsUnError() {
        assertThatThrownBy(() -> new VentanaDeMantenimiento(enMinuto(20), enMinuto(10), "mal"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
