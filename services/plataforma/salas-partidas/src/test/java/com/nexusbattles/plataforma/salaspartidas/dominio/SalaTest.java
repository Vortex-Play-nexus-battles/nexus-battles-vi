package com.nexusbattles.plataforma.salaspartidas.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de creacion de sala — HU-SAL-001.
 *
 * <p>Cada prueba cita el requisito que la obliga. Si una prueba no puede citar un
 * requisito, sobra: seria alcance inventado. Aqui vivio un tiempo la validacion
 * del nombre de sala, hasta que se comprobo que RF-JUE-001 no lo pide.
 *
 * <p>Tampoco se prueba el saldo: con reserva atomica eso no lo decide el dominio
 * sino el modulo de creditos, y se prueba en {@code CrearSalaTest}.
 */
@DisplayName("Sala · creacion (HU-SAL-001)")
class SalaTest {

    private static final UUID ANFITRION = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /**
     * Jugadores compartidos por los bloques de invitacion, salida y cancelacion.
     * El bloque {@code Ingreso} tiene los suyos propios, de antes de que hubiera
     * mas de un bloque que los necesitara.
     */
    private static final UUID VISITANTE = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID TERCERO = UUID.fromString("77777777-7777-7777-7777-777777777777");

    private static ParametrosDeSala validos() {
        return new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 400, false, false, null);
    }

    @Nested
    @DisplayName("RF-JUE-001 · parametros de creacion")
    class Creacion {

        @Test
        @DisplayName("una sala valida nace abierta, con el anfitrion dentro")
        void salaValida() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertAll(
                    () -> assertEquals(EstadoSala.ABIERTA, sala.estado()),
                    () -> assertEquals(4, sala.maximoParticipantes()),
                    () -> assertEquals(400, sala.recompensaCreditos()),
                    () -> assertEquals(ANFITRION, sala.idAnfitrion()),
                    () -> assertEquals(1, sala.ocupacion(), "solo el anfitrion al crearla"),
                    () -> assertNotNull(sala.id(), "toda sala nace con identificador"),
                    () -> assertNotNull(sala.creadaEn(), "toda sala nace con fecha"));
        }

        @Test
        @DisplayName("guarda si se incluye un heroe controlado por la IA")
        void heroeDeIa() {
            ParametrosDeSala con = new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, true, false, null);

            assertEquals(true, Sala.crear(con, ANFITRION).incluirHeroeIA());
        }

        @Test
        @DisplayName("una sala privada nace en estado PRIVADA, fuera del listado publico")
        void salaPrivada() {
            ParametrosDeSala privada = new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, true, null);

            assertEquals(EstadoSala.PRIVADA, Sala.crear(privada, ANFITRION).estado());
        }

        @Test
        @DisplayName("sin parametros no hay sala que crear")
        void exigeParametros() {
            assertThrows(NullPointerException.class, () -> Sala.crear(null, ANFITRION));
        }

        @Test
        @DisplayName("sin anfitrion la sala no tendria dueno")
        void exigeAnfitrion() {
            assertThrows(NullPointerException.class, () -> Sala.crear(validos(), null));
        }

        @Test
        @DisplayName("sin modalidad no se sabe cuantos jugadores caben")
        void exigeModalidad() {
            ParametrosDeSala sinModalidad = new ParametrosDeSala(4, null, 0, false, false, null);

            assertThrows(NullPointerException.class, () -> Sala.crear(sinModalidad, ANFITRION));
        }
    }

    @Nested
    @DisplayName("RF-JUE-004 · modalidades y limites de participantes")
    class Modalidades {

        @Test
        @DisplayName("menos de dos participantes no es una partida")
        void menosDeDos() {
            ParametrosDeSala uno = new ParametrosDeSala(1, Modalidad.HASTA_SEIS, 0, false, false, null);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(uno, ANFITRION));
            assertEquals("maximoParticipantes", error.errores().get(0).campo());
        }

        @Test
        @DisplayName("el maximo del juego son seis participantes")
        void masDeSeis() {
            ParametrosDeSala siete = new ParametrosDeSala(7, Modalidad.HASTA_SEIS, 0, false, false, null);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(siete, ANFITRION));
        }

        @Test
        @DisplayName("uno contra uno son exactamente dos")
        void duelo() {
            ParametrosDeSala cuatroEnDuelo =
                    new ParametrosDeSala(4, Modalidad.UNO_CONTRA_UNO, 0, false, false, null);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(cuatroEnDuelo, ANFITRION));
        }

        @Test
        @DisplayName("contra la IA tambien son exactamente dos")
        void contraIa() {
            ParametrosDeSala seisContraIa =
                    new ParametrosDeSala(6, Modalidad.CONTRA_IA, 0, false, false, null);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(seisContraIa, ANFITRION));
        }

        @Test
        @DisplayName("los equipos no pasan de tres integrantes")
        void equipoDemasiadoGrande() {
            ParametrosDeSala equipoDeCuatro =
                    new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, false, false, 4);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(equipoDeCuatro, ANFITRION));
            assertEquals("tamanoEquipo", error.errores().get(0).campo());
        }

        @Test
        @DisplayName("solo la modalidad de hasta seis admite equipos")
        void equipoEnDuelo() {
            ParametrosDeSala dueloConEquipo =
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, 2);

            assertThrows(ParametrosInvalidos.class, () -> Sala.crear(dueloConEquipo, ANFITRION));
        }

        @Test
        @DisplayName("un duelo bien formado se crea sin problema")
        void dueloValido() {
            ParametrosDeSala duelo =
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null);

            assertEquals(Modalidad.UNO_CONTRA_UNO, Sala.crear(duelo, ANFITRION).modalidad());
        }
    }

    /**
     * RF-JUE-004: «uno contra la inteligencia artificial» y «hasta seis en las
     * que cualquiera puede ser controlado por la IA». La maquina es un
     * participante mas: ocupa cupo, y la modalidad decide cuantas caben.
     */
    @Nested
    @DisplayName("RF-JUE-004 · heroes de la IA y cupos (HU-SAL-004)")
    class HeroesDeLaIA {

        @Test
        @DisplayName("contra la IA la maquina va siempre, aunque el formulario no la pida")
        void contraIaFuerzaLaMaquina() {
            ParametrosDeSala sinPedirla =
                    new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, false, false, null);

            Sala sala = Sala.crear(sinPedirla, ANFITRION);

            assertAll(
                    () -> assertEquals(1, sala.heroesIA()),
                    () -> assertTrue(sala.incluirHeroeIA()));
        }

        @Test
        @DisplayName("contra la IA la sala nace llena: el segundo cupo es de la maquina")
        void contraIaNaceLlena() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, true, false, null), ANFITRION);

            assertAll(
                    () -> assertEquals(EstadoSala.LLENA, sala.estado()),
                    () -> assertEquals(2, sala.ocupacion(), "anfitrion + maquina"),
                    () -> assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE),
                            "no cabe un segundo humano: seria 2 contra la IA"));
        }

        @Test
        @DisplayName("contra la IA es un solo rival de la maquina; pedir mas se rechaza diciendo el limite")
        void contraIaConDosMaquinas() {
            ParametrosDeSala dos =
                    new ParametrosDeSala(2, Modalidad.CONTRA_IA, 0, 2, false, null);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(dos, ANFITRION));
            assertAll(
                    () -> assertEquals("heroesIA", error.errores().get(0).campo()),
                    () -> assertTrue(error.errores().get(0).mensaje().contains("1"),
                            "dice el limite: " + error.errores().get(0).mensaje()));
        }

        @Test
        @DisplayName("uno contra uno es entre dos personas: con maquina seria contra la IA")
        void dueloSinMaquina() {
            ParametrosDeSala dueloConIa =
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, true, false, null);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(dueloConIa, ANFITRION));
            assertEquals("heroesIA", error.errores().get(0).campo());
        }

        @Test
        @DisplayName("hasta seis: cualquiera de los cupos puede ser de la IA, menos el del anfitrion")
        void hastaSeisConVariasMaquinas() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, 5, false, null), ANFITRION);

            assertAll(
                    () -> assertEquals(5, sala.heroesIA()),
                    () -> assertEquals(EstadoSala.LLENA, sala.estado(), "1 humano + 5 maquinas = 6"),
                    () -> assertEquals(6, sala.ocupacion()));
        }

        @Test
        @DisplayName("hasta seis: mas maquinas que cupos libres se rechaza diciendo cuantas caben")
        void masMaquinasQueCupos() {
            ParametrosDeSala seisMaquinasEnSeis =
                    new ParametrosDeSala(6, Modalidad.HASTA_SEIS, 0, 6, false, null);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(seisMaquinasEnSeis, ANFITRION));
            assertAll(
                    () -> assertEquals("heroesIA", error.errores().get(0).campo()),
                    () -> assertTrue(error.errores().get(0).mensaje().contains("5"),
                            "dice el limite: " + error.errores().get(0).mensaje()));
        }

        @Test
        @DisplayName("las maquinas ocupan cupo: con dos de cuatro, solo entra un humano mas")
        void lasMaquinasOcupanCupo() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, 2, false, null), ANFITRION);

            sala.unirse(VISITANTE);

            assertAll(
                    () -> assertEquals(EstadoSala.LLENA, sala.estado()),
                    () -> assertEquals(4, sala.ocupacion()),
                    () -> assertThrows(IngresoNoPermitido.class, () -> sala.unirse(TERCERO)));
        }

        @Test
        @DisplayName("si alguien se va, el cupo que se libera es el suyo, no el de la maquina")
        void alSalirSigueLaMaquina() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(3, Modalidad.HASTA_SEIS, 0, 1, false, null), ANFITRION);
            sala.unirse(VISITANTE);
            assertEquals(EstadoSala.LLENA, sala.estado());

            sala.abandonar(VISITANTE);

            assertAll(
                    () -> assertEquals(EstadoSala.ABIERTA, sala.estado()),
                    () -> assertEquals(1, sala.heroesIA()),
                    () -> assertEquals(2, sala.ocupacion()));
        }

        @Test
        @DisplayName("un anfitrion solo con una maquina ya tiene rival: la partida arranca")
        void conMaquinaArranca() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, 1, false, null), ANFITRION);

            sala.iniciarPartida(ANFITRION);

            assertEquals(EstadoSala.EN_JUEGO, sala.estado());
        }

        @Test
        @DisplayName("sin maquina y sin nadie mas, no hay a quien enfrentar")
        void sinRivalNoArranca() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, 0, false, null), ANFITRION);

            assertThrows(IngresoNoPermitido.class, () -> sala.iniciarPartida(ANFITRION));
        }

        @Test
        @DisplayName("el booleano de RF-JUE-001 sigue valiendo: true es una maquina, false ninguna")
        void elBooleanoSigueValiendo() {
            assertAll(
                    () -> assertEquals(1, Sala.crear(
                            new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, true, false, null),
                            ANFITRION).heroesIA()),
                    () -> assertEquals(0, Sala.crear(
                            new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null),
                            ANFITRION).heroesIA()));
        }
    }

    @Nested
    @DisplayName("RF-JUE-014 · recompensa como parametro")
    class Recompensa {

        @Test
        @DisplayName("la recompensa no puede ser negativa")
        void recompensaNegativa() {
            ParametrosDeSala negativa =
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, -1, false, false, null);

            ParametrosInvalidos error = assertThrows(ParametrosInvalidos.class,
                    () -> Sala.crear(negativa, ANFITRION));
            assertEquals("recompensaCreditos", error.errores().get(0).campo());
        }

        @Test
        @DisplayName("una sala sin recompensa es valida: apostar es libre")
        void sinRecompensa() {
            ParametrosDeSala gratis =
                    new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, false, null);

            assertEquals(0, Sala.crear(gratis, ANFITRION).recompensaCreditos());
        }
    }

    /**
     * HU-SAL-002 · RF-JUE-002 — ingreso a una sala existente.
     *
     * <p>Los tres criterios de aceptacion del issue #30:
     * <ol>
     *   <li>«El sistema verifica cupos y condiciones antes de admitir.»</li>
     *   <li>«El estado de la sala se actualiza para todos los participantes.»
     *       — la difusion es del canal; aqui se prueba que el ESTADO cambie.</li>
     *   <li>«Sala llena, cerrada o iniciada rechaza el ingreso.»</li>
     * </ol>
     *
     * <p>Se modelan participantes y no solo un contador porque el esquema
     * {@code Sala} del contrato exige el arreglo {@code participantes}, y
     * porque sin identidad no se puede impedir que el mismo jugador ocupe dos
     * cupos.
     *
     * <p>NO se prueba aqui el heroe equipado (HU-SAL-003, depende del modulo de
     * Thomas) ni los creditos de la apuesta (depende del modulo de Santiago).
     */
    @Nested
    @DisplayName("RF-JUE-002 · ingreso a una sala existente")
    class Ingreso {

        private static final UUID VISITANTE =
                UUID.fromString("55555555-5555-5555-5555-555555555555");
        private static final UUID OTRO =
                UUID.fromString("66666666-6666-6666-6666-666666666666");

        /**
         * Sala de dos cupos en el estado que pida la prueba.
         *
         * <p>El anfitrion lo anade el propio constructor, asi que aqui solo se
         * rellenan los cupos que falten hasta la ocupacion pedida.
         */
        private Sala salaEn(EstadoSala estado, int ocupacion) {
            Set<UUID> dentro = new LinkedHashSet<>();
            for (int i = 1; i < ocupacion; i++) {
                dentro.add(UUID.randomUUID());
            }
            return Sala.rehidratar(UUID.randomUUID(), estado, Modalidad.UNO_CONTRA_UNO,
                    2, 0, false, false, null, ANFITRION, dentro, Instant.now());
        }

        @Test
        @DisplayName("el anfitrion cuenta como participante desde que se crea la sala")
        void elAnfitrionYaEstaDentro() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertTrue(sala.participantes().contains(ANFITRION));
        }

        @Test
        @DisplayName("un jugador entra y la sala pasa a tener dos ocupantes")
        void entraUnJugador() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            sala.unirse(VISITANTE);

            assertAll(
                    () -> assertEquals(2, sala.ocupacion()),
                    () -> assertTrue(sala.participantes().contains(VISITANTE)),
                    () -> assertEquals(EstadoSala.ABIERTA, sala.estado(), "aun quedan cupos"));
        }

        @Test
        @DisplayName("al ocuparse el ultimo cupo la sala pasa a LLENA")
        void seLlena() {
            ParametrosDeSala duelo =
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null);
            Sala sala = Sala.crear(duelo, ANFITRION);

            sala.unirse(VISITANTE);

            assertEquals(EstadoSala.LLENA, sala.estado());
        }

        @Test
        @DisplayName("una sala llena rechaza el ingreso")
        void salaLlena() {
            Sala sala = salaEn(EstadoSala.LLENA, 2);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("una partida ya iniciada rechaza el ingreso")
        void partidaIniciada() {
            Sala sala = salaEn(EstadoSala.EN_JUEGO, 2);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("una sala cancelada rechaza el ingreso")
        void salaCancelada() {
            Sala sala = salaEn(EstadoSala.CANCELADA, 1);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("una sala finalizada rechaza el ingreso")
        void salaFinalizada() {
            Sala sala = salaEn(EstadoSala.FINALIZADA, 2);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("el mismo jugador no puede ocupar dos cupos")
        void ingresoRepetido() {
            Sala sala = Sala.crear(validos(), ANFITRION);
            sala.unirse(VISITANTE);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("el anfitrion no puede volver a entrar en su propia sala")
        void elAnfitrionNoSeUneDosVeces() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertThrows(IngresoNoPermitido.class, () -> sala.unirse(ANFITRION));
        }

        @Test
        @DisplayName("el rechazo es un conflicto: 409, como fija el contrato")
        void elRechazoEs409() {
            Sala sala = salaEn(EstadoSala.LLENA, 2);

            IngresoNoPermitido error =
                    assertThrows(IngresoNoPermitido.class, () -> sala.unirse(VISITANTE));
            assertEquals(409, error.estado());
        }

        @Test
        @DisplayName("una sala privada rechaza con 403, no con el 409 de los conflictos")
        void salaPrivada() {
            Sala sala = salaEn(EstadoSala.PRIVADA, 1);

            SalaPrivadaSinInvitacion error =
                    assertThrows(SalaPrivadaSinInvitacion.class, () -> sala.unirse(OTRO));

            assertAll(
                    () -> assertEquals(403, error.estado(), "lo fija el contrato"),
                    () -> assertTrue(error.detalle().toLowerCase().contains("invitacion"),
                            "el motivo tiene que nombrar la invitacion, no dejar adivinar"));
        }

        @Test
        @DisplayName("sin jugador identificado no hay ingreso")
        void exigeJugador() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertThrows(NullPointerException.class, () -> sala.unirse(null));
        }
    }

    @Nested
    @DisplayName("RF-JUE-002 · codigo de invitacion de sala privada")
    class Invitacion {

        private static ParametrosDeSala privados() {
            return new ParametrosDeSala(4, Modalidad.HASTA_SEIS, 0, false, true, null);
        }

        @Test
        @DisplayName("una sala privada nace con codigo y una publica sin el")
        void soloLasPrivadasTienenCodigo() {
            Sala privada = Sala.crear(privados(), ANFITRION);
            Sala publica = Sala.crear(validos(), ANFITRION);

            assertAll(
                    () -> assertNotNull(privada.codigoInvitacion()),
                    () -> assertNull(publica.codigoInvitacion(),
                            "un codigo que no protege nada solo es un secreto que filtrar"));
        }

        @Test
        @DisplayName("el codigo no usa caracteres que se confundan al dictarlo")
        void codigoLegible() {
            String codigo = Sala.crear(privados(), ANFITRION).codigoInvitacion();

            assertAll(
                    () -> assertEquals(9, codigo.length(), "ocho caracteres y un guion"),
                    () -> assertEquals('-', codigo.charAt(4)),
                    () -> assertTrue(codigo.chars()
                                    .filter(c -> c != '-')
                                    .noneMatch(c -> "IO01".indexOf(c) >= 0),
                            "I, O, 0 y 1 se confunden entre si: " + codigo),
                    () -> assertEquals(codigo.toUpperCase(java.util.Locale.ROOT), codigo));
        }

        @Test
        @DisplayName("dos salas privadas no comparten codigo")
        void codigosDistintos() {
            Set<String> codigos = new LinkedHashSet<>();
            for (int i = 0; i < 50; i++) {
                codigos.add(Sala.crear(privados(), ANFITRION).codigoInvitacion());
            }

            assertEquals(50, codigos.size(), "un codigo repetido abre la sala de otro");
        }

        @Test
        @DisplayName("con el codigo correcto se entra a una sala privada")
        void conCodigoEntra() {
            Sala sala = Sala.crear(privados(), ANFITRION);

            sala.unirse(VISITANTE, sala.codigoInvitacion());

            assertAll(
                    () -> assertEquals(2, sala.ocupacion()),
                    () -> assertTrue(sala.participantes().contains(VISITANTE)),
                    () -> assertEquals(EstadoSala.PRIVADA, sala.estado(),
                            "sigue siendo privada: no se abre porque haya entrado alguien"));
        }

        @Test
        @DisplayName("el codigo se acepta en minusculas y sin guion: se recibe copiado del chat")
        void codigoNormalizado() {
            Sala sala = Sala.crear(privados(), ANFITRION);
            String comoLoPego = sala.codigoInvitacion().replace("-", "")
                    .toLowerCase(java.util.Locale.ROOT);

            sala.unirse(VISITANTE, comoLoPego);

            assertTrue(sala.participantes().contains(VISITANTE));
        }

        @Test
        @DisplayName("con un codigo equivocado se rechaza con 403")
        void codigoEquivocado() {
            Sala sala = Sala.crear(privados(), ANFITRION);

            SalaPrivadaSinInvitacion error = assertThrows(SalaPrivadaSinInvitacion.class,
                    () -> sala.unirse(VISITANTE, "ZZZZ-9999"));

            assertAll(
                    () -> assertEquals(403, error.estado()),
                    () -> assertEquals(1, sala.ocupacion(), "un rechazo no deja rastro"));
        }

        @Test
        @DisplayName("sin codigo se rechaza con 403, no con 409")
        void sinCodigo() {
            Sala sala = Sala.crear(privados(), ANFITRION);

            assertThrows(SalaPrivadaSinInvitacion.class, () -> sala.unirse(VISITANTE));
        }

        @Test
        @DisplayName("una sala privada llena rechaza con 409 aunque el codigo sea bueno")
        void privadaLlenaEsConflicto() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, true, null),
                    ANFITRION);
            sala.unirse(VISITANTE, sala.codigoInvitacion());

            // El aforo se agoto: el estado paso a LLENA y el rechazo ya no es
            // «esta sala no es para ti» sino «esta sala cambio de estado».
            assertThrows(IngresoNoPermitido.class,
                    () -> sala.unirse(TERCERO, sala.codigoInvitacion()));
        }
    }

    @Nested
    @DisplayName("abandonarSala · salida de un participante")
    class Salida {

        @Test
        @DisplayName("quien estaba dentro sale y libera su cupo")
        void saleYLiberaCupo() {
            Sala sala = Sala.crear(validos(), ANFITRION);
            sala.unirse(VISITANTE);

            sala.abandonar(VISITANTE);

            assertAll(
                    () -> assertEquals(1, sala.ocupacion()),
                    () -> assertTrue(!sala.participantes().contains(VISITANTE)));
        }

        @Test
        @DisplayName("una sala llena vuelve a admitir cuando alguien se va")
        void llenaVuelveAAbrir() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, false, null),
                    ANFITRION);
            sala.unirse(VISITANTE);
            assertEquals(EstadoSala.LLENA, sala.estado());

            sala.abandonar(VISITANTE);

            assertAll(
                    () -> assertEquals(EstadoSala.ABIERTA, sala.estado()),
                    () -> assertEquals(1, sala.ocupacion()));
        }

        @Test
        @DisplayName("una privada que se vacia vuelve a PRIVADA, no a ABIERTA")
        void privadaLlenaVuelveAPrivada() {
            Sala sala = Sala.crear(
                    new ParametrosDeSala(2, Modalidad.UNO_CONTRA_UNO, 0, false, true, null),
                    ANFITRION);
            sala.unirse(VISITANTE, sala.codigoInvitacion());

            sala.abandonar(VISITANTE);

            assertEquals(EstadoSala.PRIVADA, sala.estado(),
                    "una sala no se vuelve publica porque alguien se haya ido");
        }

        @Test
        @DisplayName("el anfitrion no abandona: se le remite a cancelar")
        void elAnfitrionNoAbandona() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            SalidaNoPermitida error = assertThrows(SalidaNoPermitida.class,
                    () -> sala.abandonar(ANFITRION));

            assertAll(
                    () -> assertEquals(409, error.estado()),
                    () -> assertTrue(error.detalle().toLowerCase().contains("cancela"),
                            "el error tiene que decir cual es el camino correcto"),
                    () -> assertEquals(1, sala.ocupacion(), "sigue dentro"));
        }

        @Test
        @DisplayName("quien nunca entro no puede salir")
        void ajenoNoSale() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertThrows(SalidaNoPermitida.class, () -> sala.abandonar(VISITANTE));
        }

        @Test
        @DisplayName("con la partida en juego no se abandona la sala")
        void enJuegoNoSeAbandona() {
            Sala sala = enEstado(EstadoSala.EN_JUEGO);

            SalidaNoPermitida error = assertThrows(SalidaNoPermitida.class,
                    () -> sala.abandonar(VISITANTE));

            assertTrue(error.detalle().toLowerCase().contains("comenzo"));
        }

        @Test
        @DisplayName("sin jugador identificado no hay salida")
        void exigeJugador() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            assertThrows(NullPointerException.class, () -> sala.abandonar(null));
        }
    }

    @Nested
    @DisplayName("cancelarSala · solo el anfitrion, y solo antes de empezar")
    class Cancelacion {

        @Test
        @DisplayName("el anfitrion cancela y la sala queda CANCELADA")
        void elAnfitrionCancela() {
            Sala sala = Sala.crear(validos(), ANFITRION);

            sala.cancelar(ANFITRION);

            assertAll(
                    () -> assertEquals(EstadoSala.CANCELADA, sala.estado()),
                    () -> assertTrue(!sala.estado().apareceEnElListado(),
                            "una sala cancelada desaparece del listado"));
        }

        @Test
        @DisplayName("un participante que no es el anfitrion recibe 403")
        void otroNoCancela() {
            Sala sala = Sala.crear(validos(), ANFITRION);
            sala.unirse(VISITANTE);

            NoEsElAnfitrion error = assertThrows(NoEsElAnfitrion.class,
                    () -> sala.cancelar(VISITANTE));

            assertAll(
                    () -> assertEquals(403, error.estado()),
                    () -> assertEquals(EstadoSala.ABIERTA, sala.estado(), "la sala sigue en pie"),
                    () -> assertTrue(!error.detalle().contains(ANFITRION.toString()),
                            "un error no es el sitio para revelar quien es el anfitrion"));
        }

        @Test
        @DisplayName("con la partida en juego ya no se cancela: 409")
        void enJuegoNoSeCancela() {
            Sala sala = enEstado(EstadoSala.EN_JUEGO);

            SalidaNoPermitida error = assertThrows(SalidaNoPermitida.class,
                    () -> sala.cancelar(ANFITRION));

            assertEquals(409, error.estado());
        }

        @Test
        @DisplayName("cancelar dos veces la misma sala es un conflicto, no un exito silencioso")
        void noSeCancelaDosVeces() {
            Sala sala = Sala.crear(validos(), ANFITRION);
            sala.cancelar(ANFITRION);

            assertThrows(SalidaNoPermitida.class, () -> sala.cancelar(ANFITRION));
        }

        @Test
        @DisplayName("la reserva de creditos se anota una sola vez")
        void laReservaSeAnotaUnaVez() {
            Sala sala = Sala.crear(validos(), ANFITRION);
            UUID reserva = UUID.randomUUID();

            Sala conReserva = sala.conReserva(reserva);

            assertAll(
                    () -> assertEquals(reserva, conReserva.idReservaCreditos()),
                    () -> assertNull(sala.idReservaCreditos(), "la original no se toca"),
                    () -> assertThrows(IllegalStateException.class,
                            () -> conReserva.conReserva(UUID.randomUUID())));
        }
    }

    /** Sala en un estado que {@code crear} no produce, para probar los rechazos. */
    private static Sala enEstado(EstadoSala estado) {
        return Sala.rehidratar(UUID.randomUUID(), estado, Modalidad.HASTA_SEIS, 4, 0,
                false, false, null, ANFITRION, Set.of(VISITANTE), Instant.now());
    }
}
