package com.nexusbattles.plataforma.salaspartidas.aplicacion;

import com.nexusbattles.plataforma.salaspartidas.dominio.EstadoPartida;
import com.nexusbattles.plataforma.salaspartidas.dominio.FichaDeParticipante;
import com.nexusbattles.plataforma.salaspartidas.dominio.HeroeDeCombate;
import com.nexusbattles.plataforma.salaspartidas.dominio.Modalidad;
import com.nexusbattles.plataforma.salaspartidas.dominio.ParametrosDeSala;
import com.nexusbattles.plataforma.salaspartidas.dominio.Partida;
import com.nexusbattles.plataforma.salaspartidas.dominio.Sala;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Historial de partidas del jugador — {@code GET /partidas/mias} (1.7.0).
 *
 * <p>El resultado se cuenta desde el punto de vista de quien pregunta: la misma
 * partida es una VICTORIA para uno y una DERROTA para el otro.
 */
@DisplayName("MisPartidas · historial del jugador (salas-partidas.yaml 1.7.0)")
class MisPartidasTest {

    private static final UUID ANA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BRUNO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CARLA = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant AHORA = Instant.parse("2026-09-25T10:00:00Z");

    private final RepositorioDePartidasEnMemoria partidas = new RepositorioDePartidasEnMemoria();
    private final RepositorioDeSalasEnMemoria salas = new RepositorioDeSalasEnMemoria();
    private final MisPartidas misPartidas = new MisPartidas(partidas, salas);

    private static HeroeDeCombate heroe(String nombre) {
        return new HeroeDeCombate("h-" + nombre, nombre, "Guerrero Tanque", null, 1, 44, 44, 11);
    }

    /** Un uno contra uno entre Ana y Bruno, guardado con su sala. */
    private Partida unoContraUno(Instant cuando) {
        Sala sala = Sala.crear(new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null),
                ANA, new FichaDeParticipante("Ana", heroe("Aquiles")));
        sala.unirse(BRUNO, new FichaDeParticipante("Bruno", heroe("Brisa")), null);
        salas.guardar(sala);
        return Partida.iniciar(sala, cuando);
    }

    @Test
    @DisplayName("la misma partida es VICTORIA para quien gano y DERROTA para quien perdio")
    void resultadoDesdeCadaPuntoDeVista() {
        Partida partida = unoContraUno(AHORA);
        partida.aplicarDano(BRUNO, 100);
        partida.terminarSiSoloQuedaUno(AHORA.plusSeconds(240));
        partidas.guardar(partida);

        MisPartidas.Resumen deAna = misPartidas.ejecutar(ANA, 0, 16).contenido().get(0);
        MisPartidas.Resumen deBruno = misPartidas.ejecutar(BRUNO, 0, 16).contenido().get(0);

        assertAll(
                () -> assertEquals("VICTORIA", deAna.resultado()),
                () -> assertEquals("DERROTA", deBruno.resultado()),
                () -> assertEquals("Aquiles", deAna.heroe(), "el heroe con el que jugo quien pregunta"),
                () -> assertEquals("Brisa", deBruno.heroe()),
                () -> assertEquals(Modalidad.UNO_CONTRA_UNO, deAna.modalidad()),
                () -> assertEquals(EstadoPartida.FINALIZADA, deAna.estado()),
                () -> assertEquals(2, deAna.participantes()),
                () -> assertEquals(AHORA.plusSeconds(240), deAna.finalizadaEn()));
    }

    @Test
    @DisplayName("en curso no hay resultado; si caen los dos, EMPATE para ambos")
    void enCursoYEmpate() {
        Partida enCurso = unoContraUno(AHORA);
        partidas.guardar(enCurso);
        Partida empatada = unoContraUno(AHORA.plusSeconds(600));
        empatada.aplicarDano(ANA, 100);
        empatada.aplicarDano(BRUNO, 100);
        empatada.terminarSiSoloQuedaUno(AHORA.plusSeconds(700));
        partidas.guardar(empatada);

        List<MisPartidas.Resumen> deAna = misPartidas.ejecutar(ANA, 0, 16).contenido();

        assertAll(
                () -> assertEquals(2, deAna.size()),
                () -> assertEquals(empatada.id(), deAna.get(0).id(), "de la mas reciente a la mas antigua"),
                () -> assertEquals("EMPATE", deAna.get(0).resultado()),
                () -> assertNull(deAna.get(1).resultado(), "en curso no hay resultado"),
                () -> assertNull(deAna.get(1).finalizadaEn()));
    }

    @Test
    @DisplayName("solo aparecen las partidas de quien pregunta, y sin sala conocida la modalidad va nula")
    void soloLasPropias() {
        partidas.guardar(unoContraUno(AHORA));
        Sala ajena = Sala.crear(new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), CARLA);
        // La sala ajena no se guarda: su partida tampoco es de Ana ni de Bruno.
        partidas.guardar(Partida.iniciar(ajena, AHORA));

        assertAll(
                () -> assertEquals(1, misPartidas.ejecutar(ANA, 0, 16).totalElementos()),
                () -> assertEquals(1, misPartidas.ejecutar(CARLA, 0, 16).totalElementos()),
                () -> assertNull(misPartidas.ejecutar(CARLA, 0, 16).contenido().get(0).modalidad()),
                () -> assertNull(misPartidas.ejecutar(CARLA, 0, 16).contenido().get(0).heroe(),
                        "sin ficha no se inventa un heroe"),
                () -> assertEquals(0, misPartidas.ejecutar(UUID.randomUUID(), 0, 16).totalElementos()));
    }

    @Test
    @DisplayName("la pagina y el tamano se acotan: tamano 16 por omision, 50 como maximo, pagina no negativa")
    void paginaAcotada() {
        for (int i = 0; i < 3; i++) {
            partidas.guardar(unoContraUno(AHORA.plusSeconds(i)));
        }

        MisPartidas.Pagina porOmision = misPartidas.ejecutar(ANA, -3, 0);
        MisPartidas.Pagina grande = misPartidas.ejecutar(ANA, 0, 500);
        MisPartidas.Pagina deUna = misPartidas.ejecutar(ANA, 1, 1);

        assertAll(
                () -> assertEquals(0, porOmision.pagina()),
                () -> assertEquals(MisPartidas.TAMANO_POR_DEFECTO, porOmision.tamano()),
                () -> assertEquals(MisPartidas.TAMANO_MAXIMO, grande.tamano()),
                () -> assertEquals(3, grande.contenido().size()),
                () -> assertEquals(1, deUna.contenido().size()),
                () -> assertEquals(3, deUna.totalPaginas()),
                () -> assertThrows(NullPointerException.class, () -> misPartidas.ejecutar(null, 0, 16)));
    }
}
