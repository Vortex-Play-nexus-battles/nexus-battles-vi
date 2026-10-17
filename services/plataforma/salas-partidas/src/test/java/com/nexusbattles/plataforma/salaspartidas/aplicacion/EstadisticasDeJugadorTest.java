package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.PaginaDePartidas;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Estadisticas de juego de una cuenta — salas-partidas.yaml 1.11.0 (§7.3.4).
 *
 * <p>La misma regla que el historial: el resultado se cuenta desde el punto de
 * vista de la cuenta. Lo que sigue en curso va aparte y una cuenta que nunca
 * jugo tiene ceros, no un error.
 */
@DisplayName("EstadisticasDeJugador · ficha administrativa (salas-partidas.yaml 1.11.0)")
class EstadisticasDeJugadorTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-10-10T10:00:00Z");

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final EstadisticasDeJugador estadisticas = new EstadisticasDeJugador(partidas, salas);

    private static HeroeDeCombate heroe(String nombre) {
        return new HeroeDeCombate("h-" + nombre, nombre, "Guerrero Tanque", null, 1, 44, 44, 11);
    }

    /** Una partida entre Ana y Bruno en esa modalidad, con su sala guardada. */
    private Partida entreAnaYBruno(Modalidad modalidad, Instant cuando) {
        int maximo = modalidad == Modalidad.HASTA_SEIS ? 6 : 2;
        Sala sala = Sala.crear(new ParametrosDeSala(maximo, modalidad, 0, false, false, null),
                ANA, new FichaDeParticipante("Ana", heroe("Aquiles")));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Brisa")), null);
        salas.guardar(sala);
        return Partida.iniciar(sala, cuando);
    }

    /** Termina la partida dejando en pie solo a {@code ganador} (o a nadie: empate). */
    private void terminar(Partida partida, UUID... caen) {
        for (UUID cae : caen) {
            partida.aplicarDano(cae, 100);
        }
        partida.terminarSiSoloQuedaUno(partida.iniciadaEn().plusSeconds(120));
        partidas.guardar(partida);
    }

    @Test
    @DisplayName("victorias, derrotas y empates desde el punto de vista de la cuenta, y por modalidad")
    void recuentoDesdeElPuntoDeVistaDeLaCuenta() {
        terminar(entreAnaYBruno(Modalidad.UNO_CONTRA_UNO, AHORA), BRUNO);
        terminar(entreAnaYBruno(Modalidad.UNO_CONTRA_UNO, AHORA.plusSeconds(600)), ANA);
        terminar(entreAnaYBruno(Modalidad.HASTA_SEIS, AHORA.plusSeconds(1200)), ANA, BRUNO);
        Partida enCurso = entreAnaYBruno(Modalidad.UNO_CONTRA_UNO, AHORA.plusSeconds(1800));
        partidas.guardar(enCurso);

        EstadisticasDeJugador.Estadisticas deAna = estadisticas.ejecutar(ANA);
        EstadisticasDeJugador.Estadisticas deBruno = estadisticas.ejecutar(BRUNO);

        assertAll(
                () -> assertEquals(ANA, deAna.idJugador()),
                () -> assertEquals(3, deAna.jugadas(), "las terminadas"),
                () -> assertEquals(1, deAna.victorias()),
                () -> assertEquals(1, deAna.derrotas()),
                () -> assertEquals(1, deAna.empates()),
                () -> assertEquals(1, deAna.enCurso()),
                () -> assertEquals(AHORA.plusSeconds(1800), deAna.ultimaPartidaEn(), "la mas reciente, aunque siga"),
                () -> assertFalse(deAna.parcial()),
                () -> assertEquals(new EstadisticasDeJugador.PorModalidad(2, 1, 1, 0),
                        deAna.porModalidad().get(Modalidad.UNO_CONTRA_UNO)),
                () -> assertEquals(new EstadisticasDeJugador.PorModalidad(1, 0, 0, 1),
                        deAna.porModalidad().get(Modalidad.HASTA_SEIS)),
                () -> assertNull(deAna.porModalidad().get(Modalidad.CONTRA_IA), "sin partidas no hay entrada"),
                // La misma partida, desde el otro lado.
                () -> assertEquals(1, deBruno.victorias()),
                () -> assertEquals(1, deBruno.derrotas()),
                () -> assertEquals(1, deBruno.empates()));
    }

    @Test
    @DisplayName("una cuenta que nunca jugo tiene ceros y sin fecha, no un error")
    void sinPartidas() {
        EstadisticasDeJugador.Estadisticas nada = estadisticas.ejecutar(UUID.randomUUID());

        assertAll(
                () -> assertEquals(0, nada.jugadas()),
                () -> assertEquals(0, nada.enCurso()),
                () -> assertTrue(nada.porModalidad().isEmpty()),
                () -> assertNull(nada.ultimaPartidaEn()),
                () -> assertFalse(nada.parcial()),
                () -> assertThrows(NullPointerException.class, () -> estadisticas.ejecutar(null)));
    }

    @Test
    @DisplayName("una partida cuya sala ya no esta cuenta en el total, no en una modalidad inventada")
    void salaDesconocida() {
        Sala ajena = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null),
                ANA, new FichaDeParticipante("Ana", heroe("Aquiles")));
        ajena.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Brisa")), null);
        // La sala no se guarda.
        Partida partida = Partida.iniciar(ajena, AHORA);
        terminar(partida, BRUNO);

        EstadisticasDeJugador.Estadisticas deAna = estadisticas.ejecutar(ANA);

        assertAll(
                () -> assertEquals(1, deAna.victorias()),
                () -> assertTrue(deAna.porModalidad().isEmpty()));
    }

    @Test
    @DisplayName("recorre el historial por paginas y, pasado el limite, dice que el recuento es parcial")
    void porPaginasYConLimite() {
        Partida modelo = entreAnaYBruno(Modalidad.UNO_CONTRA_UNO, AHORA);
        modelo.aplicarDano(BRUNO, 100);
        modelo.terminarSiSoloQuedaUno(AHORA.plusSeconds(60));
        int total = EstadisticasDeJugador.LIMITE_DE_PARTIDAS + 5;
        List<Integer> paginasPedidas = new ArrayList<>();
        // Un almacen que devuelve siempre la misma partida terminada, en
        // paginas de LOTE, hasta `total`.
        EstadisticasDeJugador muchas = new EstadisticasDeJugador(new RepositorioDePartidasEnMemoria() {
            @Override
            public PaginaDePartidas buscarPorJugador(UUID idJugador, int pagina, int tamano) {
                paginasPedidas.add(pagina);
                int desde = pagina * tamano;
                int cuantas = Math.max(0, Math.min(tamano, total - desde));
                int paginas = (total + tamano - 1) / tamano;
                return new PaginaDePartidas(java.util.Collections.nCopies(cuantas, modelo), pagina, tamano,
                        total, paginas);
            }
        }, salas);

        EstadisticasDeJugador.Estadisticas resultado = muchas.ejecutar(ANA);

        assertAll(
                () -> assertTrue(resultado.parcial()),
                () -> assertEquals(EstadisticasDeJugador.LIMITE_DE_PARTIDAS, resultado.victorias()),
                () -> assertEquals(EstadisticasDeJugador.LIMITE_DE_PARTIDAS / EstadisticasDeJugador.LOTE + 1,
                        paginasPedidas.size(), "para en cuanto llega al limite"));
    }
}
