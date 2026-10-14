package com.nexusbattles.plataforma.moderacionsanciones.sanciones;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reglas de HU-USR-004/005/006/007, HU-NOT-005 y el levantamiento (1.1.0), sin base de datos. */
@ExtendWith(MockitoExtension.class)
@DisplayName("SancionesService · reglas de emision, consulta, apelacion y levantamiento")
class SancionesServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-21T10:00:00Z");
    private static final UUID JUGADOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Actor MODERADORA = new Actor(UUID.fromString("22222222-2222-2222-2222-222222222222"), "MODERADOR");
    private static final Actor ADMIN = new Actor(UUID.fromString("33333333-3333-3333-3333-333333333333"), "ADMINISTRADOR");
    private static final Actor SUPER = new Actor(UUID.fromString("55555555-5555-5555-5555-555555555555"), "SUPER_ADMINISTRADOR");
    private static final Actor OTRO_JUGADOR = new Actor(UUID.fromString("44444444-4444-4444-4444-444444444444"), "JUGADOR");
    private static final Actor SANCIONADO = new Actor(JUGADOR, "JUGADOR");

    @Mock SancionRepository sanciones;
    @Mock ApelacionRepository apelaciones;
    @Mock SalidaPendienteRepository salidasGuardadas;

    private SancionesService servicio;
    private final List<Sancion> guardadas = new ArrayList<>();
    /** El rol de cada cuenta segun ms-identidad; las que no estan son jugadores. */
    private final Map<UUID, String> rolesDeLasCuentas = new HashMap<>();
    /** Si no es nulo, ms-identidad "no responde" con este rechazo. */
    private SancionRechazada identidadCaida;

    @BeforeEach
    void preparar() {
        servicio = nuevo(Clock.fixed(AHORA, ZoneOffset.UTC), LimitesDeSancion.Fijos.de(1, 30, 30));
        lenient().when(sanciones.save(any())).thenAnswer(inv -> {
            guardadas.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(sanciones.findByUsuarioIdAndRevertidaEnIsNullAndTipoNot(eq(JUGADOR), any()))
                .thenAnswer(inv -> guardadas.stream()
                        .filter(s -> s.usuarioId().equals(JUGADOR) && s.revertidaEn() == null
                                && s.tipo() != Sancion.Tipo.ADVERTENCIA).toList());
        lenient().when(apelaciones.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SancionesService nuevo(Clock reloj, LimitesDeSancion limites) {
        RolesDeCuentas roles = cuenta -> {
            if (identidadCaida != null) {
                throw identidadCaida;
            }
            return rolesDeLasCuentas.getOrDefault(cuenta, "JUGADOR");
        };
        return new SancionesService(sanciones, apelaciones, new SalidasDeSancion(salidasGuardadas), reloj, limites,
                roles);
    }

    private static SancionesService.SolicitudDeSancion solicitud(Sancion.Tipo tipo, Long horas, boolean confirmacion) {
        return new SancionesService.SolicitudDeSancion(JUGADOR, tipo, "Lenguaje ofensivo en el chat",
                "Normas de convivencia", null, horas, confirmacion);
    }

    /** Todas las salidas que se guardaron, en orden. */
    private List<SalidaPendiente> salidas() {
        ArgumentCaptor<SalidaPendiente> captor = ArgumentCaptor.forClass(SalidaPendiente.class);
        verify(salidasGuardadas, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    /** Los avisos a la bandeja, en orden. */
    private List<SalidaPendiente> avisos() {
        return salidas().stream().filter(s -> s.canal() == CanalDeSalida.AVISO).toList();
    }

    @Nested
    @DisplayName("HU-USR-004 · advertencia")
    class Advertencia {

        @Test
        @DisplayName("queda en el historial con autor, rol y motivo, deja aviso y correo y no restringe (CA-01, CA-02)")
        void emitir() {
            Sancion s = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.ADVERTENCIA, null, false));

            assertThat(s.tipo()).isEqualTo(Sancion.Tipo.ADVERTENCIA);
            assertThat(s.emitidaPor()).isEqualTo(MODERADORA.id());
            assertThat(s.rolEmisor()).isEqualTo("MODERADOR");
            assertThat(s.vigenteHasta()).isNull();
            assertThat(s.restringeEn(OffsetDateTime.now(ZoneOffset.UTC))).isFalse();
            SalidaPendiente aviso = avisos().get(0);
            assertThat(aviso.usuarioId()).isEqualTo(JUGADOR);
            assertThat(aviso.tipo()).isEqualTo("SANCION_ADVERTENCIA");
            assertThat(aviso.cuerpo()).contains("Lenguaje ofensivo").contains("apelar");
            assertThat(salidas()).extracting(SalidaPendiente::canal)
                    .containsExactly(CanalDeSalida.AVISO, CanalDeSalida.CORREO, CanalDeSalida.HISTORIAL);
            assertThat(servicio.activaDe(JUGADOR)).isEmpty();
        }

        @Test
        @DisplayName("un jugador no sanciona (CA-04) y nadie se sanciona a si mismo")
        void permisos() {
            assertThatThrownBy(() -> servicio.emitir(OTRO_JUGADOR, solicitud(Sancion.Tipo.ADVERTENCIA, null, false)))
                    .isInstanceOf(SancionRechazada.class)
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            assertThatThrownBy(() -> servicio.emitir(MODERADORA, new SancionesService.SolicitudDeSancion(
                    MODERADORA.id(), Sancion.Tipo.ADVERTENCIA, "x", null, null, null, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            verify(sanciones, never()).save(any());
            verify(salidasGuardadas, never()).save(any());
        }

        @Test
        @DisplayName("sin motivo no hay sancion")
        void motivoObligatorio() {
            assertThatThrownBy(() -> servicio.emitir(MODERADORA, new SancionesService.SolicitudDeSancion(
                    JUGADOR, Sancion.Tipo.ADVERTENCIA, "  ", null, null, null, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
        }
    }

    @Nested
    @DisplayName("HU-ADM-001 CA-04 · el aviso y la validacion dicen el mismo plazo")
    class PlazoDeApelacionEnElAviso {

        /**
         * El defecto que esta prueba cierra: el aviso llevaba «30 dias»
         * escrito a mano mientras {@code apelar} validaba con
         * {@code limites.plazoDeApelacion()}. Con el plazo del catalogo en 7,
         * el sistema prometia treinta dias y rechazaba al octavo.
         */
        @Test
        @DisplayName("con el plazo en 7 dias, el aviso promete 7 y no 30")
        void elAvisoUsaElPlazoConfigurado() {
            SancionesService conPlazoCorto = nuevo(Clock.fixed(AHORA, ZoneOffset.UTC), LimitesDeSancion.Fijos.de(1, 30, 7));

            conPlazoCorto.emitir(MODERADORA, solicitud(Sancion.Tipo.ADVERTENCIA, null, false));

            OffsetDateTime emitida = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
            assertThat(avisos().get(0).cuerpo())
                    .contains("Puedes apelar hasta el " + TextosDeSancion.fecha(emitida.plusDays(7)))
                    .doesNotContain(TextosDeSancion.fecha(emitida.plusDays(30)));
        }

        @Test
        @DisplayName("el numero del aviso es el mismo que aplica el rechazo por fuera de plazo")
        void elAvisoYElRechazoCoinciden() {
            SancionesService conPlazoCorto = nuevo(Clock.fixed(AHORA, ZoneOffset.UTC), LimitesDeSancion.Fijos.de(1, 30, 7));
            Sancion baneo = conPlazoCorto.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, true));
            when(sanciones.findById(baneo.id())).thenReturn(Optional.of(baneo));

            assertThat(avisos().get(0).cuerpo()).contains("Puedes apelar hasta el "
                    + TextosDeSancion.fecha(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusDays(7)));

            SancionesService alOctavoDia = nuevo(Clock.fixed(AHORA.plus(java.time.Duration.ofDays(8)), ZoneOffset.UTC),
                    LimitesDeSancion.Fijos.de(1, 30, 7));
            assertThatThrownBy(() -> alOctavoDia.apelar(SANCIONADO, baneo.id(), "tarde"))
                    .hasMessageContaining("7 dias")
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.APELACION_NO_PROCEDE);
        }
    }

    @Nested
    @DisplayName("HU-USR-005 · suspension temporal")
    class Suspension {

        @Test
        @DisplayName("fecha fin calculada y restringe mientras dura; al vencer deja de restringir sola (CA-01, CA-04)")
        void suspender() {
            Sancion s = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 48L, false));

            OffsetDateTime ahora = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
            assertThat(s.vigenteHasta()).isEqualTo(ahora.plusHours(48));
            assertThat(s.restringeEn(ahora.plusHours(47))).isTrue();
            assertThat(s.restringeEn(ahora.plusHours(49))).isFalse();
            assertThat(servicio.activaDe(JUGADOR)).contains(s);
        }

        @Test
        @DisplayName("7.3.2: la suspension se proyecta sobre la cuenta y se avisa por correo, en la misma transaccion")
        void salidas() {
            Sancion s = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 48L, false));

            assertThat(SancionesServiceTest.this.salidas())
                    .extracting(SalidaPendiente::canal, SalidaPendiente::sancionId)
                    .containsExactly(org.assertj.core.api.Assertions.tuple(CanalDeSalida.AVISO, s.id()),
                            org.assertj.core.api.Assertions.tuple(CanalDeSalida.PROYECCION, s.id()),
                            org.assertj.core.api.Assertions.tuple(CanalDeSalida.CORREO, s.id()),
                            org.assertj.core.api.Assertions.tuple(CanalDeSalida.HISTORIAL, s.id()));
        }

        @Test
        @DisplayName("la duracion fuera de rango se rechaza con motivo (CA-06)")
        void duracion() {
            assertThatThrownBy(() -> servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, null, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 0L, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 31L * 24, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
        }

        @Test
        @DisplayName("la activa es la que mas dura; con baneo, el baneo")
        void laActivaEsLaMasLarga() {
            Sancion corta = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 2L, false));
            Sancion larga = servicio.emitir(ADMIN, solicitud(Sancion.Tipo.SUSPENSION, 72L, false));
            assertThat(servicio.activaDe(JUGADOR)).contains(larga).isNotEqualTo(Optional.of(corta));

            Sancion baneo = servicio.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, true));
            assertThat(servicio.activaDe(JUGADOR)).contains(baneo);
        }
    }

    @Nested
    @DisplayName("HU-USR-006 · baneo definitivo")
    class Baneo {

        @Test
        @DisplayName("el moderador no banea (solo temporales, Tabla 24)")
        void moderadorNoBanea() {
            assertThatThrownBy(() -> servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.BANEO, null, true)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
        }

        @Test
        @DisplayName("sin confirmacion explicita se rechaza (CA-01)")
        void confirmacion() {
            assertThatThrownBy(() -> servicio.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
        }

        @Test
        @DisplayName("banea, restringe sin fecha fin, y despues no procede ninguna sancion mas (CA-04 de HU-USR-004)")
        void banear() {
            Sancion baneo = servicio.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, true));

            assertThat(baneo.vigenteHasta()).isNull();
            assertThat(baneo.restringeEn(OffsetDateTime.now(ZoneOffset.UTC).plusYears(10))).isTrue();
            assertThatThrownBy(() -> servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.ADVERTENCIA, null, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.USUARIO_BANEADO);
        }
    }

    @Nested
    @DisplayName("HU-USR-007 · apelacion")
    class Apelaciones {

        private Sancion suspension;

        @BeforeEach
        void conSuspension() {
            suspension = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 72L, false));
            lenient().when(sanciones.findById(suspension.id())).thenReturn(Optional.of(suspension));
            lenient().when(apelaciones.findBySancionIdAndEstado(suspension.id(), Apelacion.Estado.PENDIENTE))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("el sancionado abre la apelacion dentro del plazo (CA-01)")
        void apelar() {
            Apelacion a = servicio.apelar(SANCIONADO, suspension.id(), "No fui yo, me robaron la sesion");

            assertThat(a.estado()).isEqualTo(Apelacion.Estado.PENDIENTE);
            assertThat(a.usuarioId()).isEqualTo(JUGADOR);
            verify(apelaciones).save(a);
        }

        /**
         * §7.3.2: el panel notifica su decision «en un plazo de 15 dias
         * habiles». El sancionado sabe desde el primer momento que su
         * apelacion llego y hasta cuando tiene el panel para responderle.
         */
        @Test
        @DisplayName("SA-4: al apelar llega el aviso de recibida con la fecha limite de los 15 dias habiles")
        void avisoDeRecibida() {
            Apelacion a = servicio.apelar(SANCIONADO, suspension.id(), "No fui yo");

            SalidaPendiente aviso = avisos().get(avisos().size() - 1);
            assertThat(aviso.tipo()).isEqualTo("APELACION_RECIBIDA");
            assertThat(aviso.clave()).isEqualTo("apelacion-" + a.id() + "-recibida-aviso");
            assertThat(aviso.usuarioId()).isEqualTo(JUGADOR);
            assertThat(aviso.titulo()).isEqualTo("Recibimos tu apelación");
            // Presentada el lunes 21 de septiembre: del 22 al 12 de octubre, quince dias habiles.
            assertThat(aviso.cuerpo()).isEqualTo("Un panel de moderadores y administradores revisará tu apelación "
                    + "sobre tu suspensión y te notificará su decisión a más tardar el 12 de octubre de 2026, "
                    + "11:59 p. m.");
            assertThat(salidas()).filteredOn(s -> s.clave().equals(aviso.clave()))
                    .extracting(SalidaPendiente::canal).containsExactly(CanalDeSalida.AVISO);
        }

        @Test
        @DisplayName("SA-4: con festivos configurados, la fecha limite del aviso se corre")
        void avisoConFestivos() {
            SancionesService conFestivo = new SancionesService(sanciones, apelaciones,
                    new SalidasDeSancion(salidasGuardadas), Clock.fixed(AHORA, ZoneOffset.UTC),
                    LimitesDeSancion.Fijos.de(1, 30, 30), cuenta -> "JUGADOR",
                    new PlazoDeDecision(java.util.Set.of(java.time.LocalDate.of(2026, 10, 12))));

            conFestivo.apelar(SANCIONADO, suspension.id(), "No fui yo");

            assertThat(avisos().get(avisos().size() - 1).cuerpo())
                    .endsWith("a más tardar el 13 de octubre de 2026, 11:59 p. m.");
        }

        @Test
        @DisplayName("SA-4: el panel elige pendientes (por omision), resueltas o todas; un jugador no lo ve")
        void panelPorEstado() {
            Apelacion pendiente = new Apelacion(UUID.randomUUID(), suspension.id(), JUGADOR, "x",
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            Apelacion resuelta = new Apelacion(UUID.randomUUID(), suspension.id(), JUGADOR, "y",
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            when(apelaciones.findByEstadoOrderByCreadaEnAsc(Apelacion.Estado.PENDIENTE)).thenReturn(List.of(pendiente));
            when(apelaciones.findByEstadoNotOrderByResueltaEnDesc(Apelacion.Estado.PENDIENTE))
                    .thenReturn(List.of(resuelta));
            when(apelaciones.findAllByOrderByCreadaEnDesc()).thenReturn(List.of(resuelta, pendiente));

            assertThat(servicio.apelacionesDelPanel(MODERADORA, null)).containsExactly(pendiente);
            assertThat(servicio.apelacionesDelPanel(MODERADORA, SancionesService.EstadoDeLaLista.PENDIENTES))
                    .containsExactly(pendiente);
            assertThat(servicio.apelacionesPendientes(ADMIN)).containsExactly(pendiente);
            assertThat(servicio.apelacionesDelPanel(ADMIN, SancionesService.EstadoDeLaLista.RESUELTAS))
                    .containsExactly(resuelta);
            assertThat(servicio.apelacionesDelPanel(SUPER, SancionesService.EstadoDeLaLista.TODAS))
                    .containsExactly(resuelta, pendiente);
            assertThatThrownBy(() -> servicio.apelacionesDelPanel(SANCIONADO, SancionesService.EstadoDeLaLista.TODAS))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
        }

        @Test
        @DisplayName("otro jugador no apela por el; fuera de plazo o ya abierta, no procede (CA-02)")
        void noProcede() {
            assertThatThrownBy(() -> servicio.apelar(OTRO_JUGADOR, suspension.id(), "x"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.APELACION_NO_PROCEDE);
            when(apelaciones.findBySancionIdAndEstado(suspension.id(), Apelacion.Estado.PENDIENTE))
                    .thenReturn(Optional.of(new Apelacion(UUID.randomUUID(), suspension.id(), JUGADOR, "x",
                            OffsetDateTime.now(ZoneOffset.UTC))));
            assertThatThrownBy(() -> servicio.apelar(SANCIONADO, suspension.id(), "otra vez"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.APELACION_NO_PROCEDE);

            SancionesService tarde = nuevo(Clock.fixed(AHORA.plus(java.time.Duration.ofDays(31)), ZoneOffset.UTC),
                    LimitesDeSancion.Fijos.de(1, 30, 30));
            Sancion baneo = servicio.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, true));
            when(sanciones.findById(baneo.id())).thenReturn(Optional.of(baneo));
            assertThatThrownBy(() -> tarde.apelar(SANCIONADO, baneo.id(), "tarde"))
                    .hasMessageContaining("30 dias");
        }

        @Test
        @DisplayName("REVERTIDA levanta la sancion y avisa; MANTENIDA la deja; REDUCIDA la acorta (CA-03, CA-04)")
        void resolver() {
            Apelacion a = servicio.apelar(SANCIONADO, suspension.id(), "argumento");
            when(apelaciones.findById(a.id())).thenReturn(Optional.of(a));

            assertThatThrownBy(() -> servicio.resolver(MODERADORA, a.id(), Apelacion.Estado.REVERTIDA, "ok", null))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            assertThatThrownBy(() -> servicio.resolver(ADMIN, a.id(), Apelacion.Estado.REDUCIDA, "sin fecha", null))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);

            OffsetDateTime ahora = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
            Apelacion reducida = servicio.resolver(ADMIN, a.id(), Apelacion.Estado.REDUCIDA,
                    "Primera falta, se reduce", ahora.plusHours(24));
            assertThat(reducida.estado()).isEqualTo(Apelacion.Estado.REDUCIDA);
            assertThat(suspension.vigenteHasta()).isEqualTo(ahora.plusHours(24));
            assertThat(suspension.revertidaEn()).isNull();

            assertThatThrownBy(() -> servicio.resolver(ADMIN, a.id(), Apelacion.Estado.MANTENIDA, "otra vez", null))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.APELACION_RESUELTA);

            Apelacion segunda = servicio.apelar(SANCIONADO, suspension.id(), "insisto");
            when(apelaciones.findById(segunda.id())).thenReturn(Optional.of(segunda));
            servicio.resolver(ADMIN, segunda.id(), Apelacion.Estado.REVERTIDA, "Tiene razon", null);
            assertThat(suspension.revertidaEn()).isNotNull();
            assertThat(suspension.revertidaPor()).isEqualTo(ADMIN.id());
            assertThat(servicio.activaDe(JUGADOR)).isEmpty();
            List<SalidaPendiente> avisos = avisos();
            assertThat(avisos.get(avisos.size() - 1).tipo()).isEqualTo("APELACION_REVERTIDA");
            assertThat(salidas()).filteredOn(s -> segunda.id().equals(s.apelacionId()))
                    .extracting(SalidaPendiente::canal)
                    .containsExactly(CanalDeSalida.PROYECCION, CanalDeSalida.CORREO);
        }
    }

    @Nested
    @DisplayName("1.1.0 · levantamiento")
    class Levantamiento {

        private Sancion suspension;

        @BeforeEach
        void conSuspension() {
            suspension = servicio.emitir(ADMIN, solicitud(Sancion.Tipo.SUSPENSION, 48L, false));
            lenient().when(sanciones.findById(suspension.id())).thenReturn(Optional.of(suspension));
        }

        @Test
        @DisplayName("un administrador levanta: queda revertida con motivo y autor, deja de restringir, avisa y proyecta")
        void levanta() {
            Sancion levantada = servicio.levantar(SUPER, suspension.id(), "  Error de moderacion  ");

            assertThat(levantada.revertidaEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            assertThat(levantada.revertidaPor()).isEqualTo(SUPER.id());
            assertThat(levantada.motivoReversion()).isEqualTo("Error de moderacion");
            assertThat(servicio.activaDe(JUGADOR)).isEmpty();
            assertThat(salidas()).filteredOn(s -> s.tipo().equals("SANCION_LEVANTADA")
                            || s.tipo().equals("LEVANTAMIENTO"))
                    .extracting(SalidaPendiente::canal)
                    .containsExactly(CanalDeSalida.AVISO, CanalDeSalida.PROYECCION);
            SalidaPendiente aviso = avisos().get(avisos().size() - 1);
            assertThat(aviso.titulo()).isEqualTo("Tu suspensión fue levantada");
            assertThat(aviso.cuerpo()).isEqualTo("Un administrador levantó tu suspensión. Motivo: Error de moderacion.");
        }

        /**
         * 7.3.2: el baneo es «inhabilitacion definitiva» y su unica via de
         * reversion es la apelacion ante el panel. Ni un Super Administrador
         * lo levanta a mano: el 409 lo dice, y nada cambia ni se avisa.
         */
        @Test
        @DisplayName("un baneo no se levanta a mano, ni por un Super Administrador: 409 y nada cambia")
        void elBaneoSoloPorApelacion() {
            Sancion baneo = servicio.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, true));
            when(sanciones.findById(baneo.id())).thenReturn(Optional.of(baneo));
            int salidasAntes = salidas().size();

            assertThatThrownBy(() -> servicio.levantar(SUPER, baneo.id(), "Error de moderacion"))
                    .isInstanceOf(SancionRechazada.class)
                    .hasMessageContaining("apelacion")
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.BANEO_SOLO_POR_APELACION);

            assertThat(baneo.revertidaEn()).isNull();
            assertThat(servicio.activaDe(JUGADOR)).isPresent();
            assertThat(salidas()).hasSize(salidasAntes);
        }

        @Test
        @DisplayName("un moderador o un jugador no levantan: 403")
        void permisos() {
            assertThatThrownBy(() -> servicio.levantar(MODERADORA, suspension.id(), "motivo"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            assertThatThrownBy(() -> servicio.levantar(SANCIONADO, suspension.id(), "motivo"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            assertThat(suspension.revertidaEn()).isNull();
        }

        @Test
        @DisplayName("sin motivo (menos de 3 caracteres) o con uno de mas de 1000: 400")
        void motivo() {
            assertThatThrownBy(() -> servicio.levantar(ADMIN, suspension.id(), " ok "))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.levantar(ADMIN, suspension.id(), null))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.levantar(ADMIN, suspension.id(), "x".repeat(1001)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
        }

        @Test
        @DisplayName("una sancion que no existe es 404; una ya levantada o vencida, 409")
        void noVigente() {
            UUID inexistente = UUID.randomUUID();
            when(sanciones.findById(inexistente)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.levantar(ADMIN, inexistente, "motivo"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.NO_ENCONTRADA);

            servicio.levantar(ADMIN, suspension.id(), "Primera vez");
            assertThatThrownBy(() -> servicio.levantar(ADMIN, suspension.id(), "Otra vez"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SANCION_NO_VIGENTE);

            Sancion corta = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 1L, false));
            when(sanciones.findById(corta.id())).thenReturn(Optional.of(corta));
            SancionesService dosHorasDespues = nuevo(
                    Clock.fixed(AHORA.plus(java.time.Duration.ofHours(2)), ZoneOffset.UTC),
                    LimitesDeSancion.Fijos.de(1, 30, 30));
            assertThatThrownBy(() -> dosHorasDespues.levantar(ADMIN, corta.id(), "Ya vencio"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SANCION_NO_VIGENTE);
        }
    }

    @Test
    @DisplayName("el historial lo ve el propio usuario o quien modera; otro jugador no")
    void historial() {
        when(sanciones.findByUsuarioIdOrderByEmitidaEnDesc(JUGADOR)).thenReturn(List.of());
        assertThat(servicio.historialDe(SANCIONADO, JUGADOR)).isEmpty();
        assertThat(servicio.historialDe(MODERADORA, JUGADOR)).isEmpty();
        assertThatThrownBy(() -> servicio.historialDe(OTRO_JUGADOR, JUGADOR))
                .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
    }

    @Nested
    @DisplayName("SA-4 · apelacion sin sesion (la cuenta suspendida o baneada no puede entrar)")
    class ApelacionSinSesion {

        private Sancion suspension;

        @BeforeEach
        void conSuspension() {
            suspension = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 72L, false));
            lenient().when(sanciones.findById(suspension.id())).thenReturn(Optional.of(suspension));
            lenient().when(apelaciones.findBySancionIdAndEstado(suspension.id(), Apelacion.Estado.PENDIENTE))
                    .thenReturn(Optional.empty());
            lenient().when(apelaciones.findFirstBySancionIdOrderByCreadaEnDesc(suspension.id()))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("con la suspension vigente y sin apelacion: se puede apelar hasta la emision + 30 dias")
        void apelable() {
            SancionesService.EstadoDeApelacion estado = servicio.estadoDeApelacionDe(JUGADOR);

            assertThat(estado.sancion()).isSameAs(suspension);
            assertThat(estado.apelableHasta()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusDays(30));
            assertThat(estado.puedeApelar()).isTrue();
            assertThat(estado.motivoNoApelable()).isNull();
            assertThat(estado.apelacion()).isNull();
        }

        @Test
        @DisplayName("con una apelacion abierta no se abre otra; fuera de plazo, tampoco; sin sancion, nada que apelar")
        void noApelable() {
            Apelacion abierta = new Apelacion(UUID.randomUUID(), suspension.id(), JUGADOR, "x",
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            when(apelaciones.findFirstBySancionIdOrderByCreadaEnDesc(suspension.id())).thenReturn(Optional.of(abierta));
            SancionesService.EstadoDeApelacion conAbierta = servicio.estadoDeApelacionDe(JUGADOR);
            assertThat(conAbierta.puedeApelar()).isFalse();
            assertThat(conAbierta.motivoNoApelable()).isEqualTo(SancionesService.MotivoNoApelable.APELACION_ABIERTA);
            assertThat(conAbierta.apelacion()).isSameAs(abierta);

            Apelacion resuelta = new Apelacion(UUID.randomUUID(), suspension.id(), JUGADOR, "y",
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            resuelta.resolver(Apelacion.Estado.MANTENIDA, "Se mantiene", ADMIN.id(),
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), null);
            SancionesService unMesDespues = nuevo(Clock.fixed(AHORA.plus(java.time.Duration.ofDays(31)), ZoneOffset.UTC),
                    LimitesDeSancion.Fijos.de(1, 60, 30));
            Sancion larga = servicio.emitir(ADMIN, new SancionesService.SolicitudDeSancion(JUGADOR,
                    Sancion.Tipo.BANEO, "Fraude", null, null, null, true));
            when(apelaciones.findFirstBySancionIdOrderByCreadaEnDesc(larga.id())).thenReturn(Optional.of(resuelta));
            SancionesService.EstadoDeApelacion tarde = unMesDespues.estadoDeApelacionDe(JUGADOR);
            assertThat(tarde.sancion()).isSameAs(larga);
            assertThat(tarde.motivoNoApelable()).isEqualTo(SancionesService.MotivoNoApelable.FUERA_DE_PLAZO);
            assertThat(tarde.apelacion()).as("la ultima, aunque este resuelta").isSameAs(resuelta);

            SancionesService.EstadoDeApelacion otro = servicio.estadoDeApelacionDe(UUID.randomUUID());
            assertThat(otro.sancion()).isNull();
            assertThat(otro.puedeApelar()).isFalse();
            assertThat(otro.motivoNoApelable()).isEqualTo(SancionesService.MotivoNoApelable.SIN_SANCION);
        }

        @Test
        @DisplayName("apela en su nombre la sancion que le impide entrar, con las reglas de siempre y su aviso")
        void apelaSinSesion() {
            Apelacion a = servicio.apelarSinSesion(JUGADOR, suspension.id(), "  No fui yo  ");

            assertThat(a.estado()).isEqualTo(Apelacion.Estado.PENDIENTE);
            assertThat(a.usuarioId()).isEqualTo(JUGADOR);
            assertThat(a.argumento()).isEqualTo("No fui yo");
            assertThat(avisos().get(avisos().size() - 1).tipo()).isEqualTo("APELACION_RECIBIDA");
        }

        @Test
        @DisplayName("otra sancion que no es la que impide entrar, o una cuenta sin sancion: 422 y nada guardado")
        void soloLaQueImpideEntrar() {
            Sancion advertencia = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.ADVERTENCIA, null, false));

            assertThatThrownBy(() -> servicio.apelarSinSesion(JUGADOR, advertencia.id(), "x"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.APELACION_NO_PROCEDE);
            assertThatThrownBy(() -> servicio.apelarSinSesion(UUID.randomUUID(), suspension.id(), "x"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.APELACION_NO_PROCEDE);
            verify(apelaciones, never()).save(any());
        }
    }

    @Nested
    @DisplayName("SA-4 · causal de §7.3.2")
    class CausalDeLaSancion {

        @Test
        @DisplayName("la causal se guarda con la sancion y abre el aviso, antes del motivo concreto")
        void conCausal() {
            Sancion s = servicio.emitir(MODERADORA, new SancionesService.SolicitudDeSancion(JUGADOR,
                    Sancion.Tipo.ADVERTENCIA, Causal.LENGUAJE_OFENSIVO, "Insulto en el chat de la sala 12",
                    "Normas de convivencia", null, null, false));

            assertThat(s.causal()).isEqualTo(Causal.LENGUAJE_OFENSIVO);
            assertThat(guardadas).extracting(Sancion::causal).containsExactly(Causal.LENGUAJE_OFENSIVO);
            assertThat(avisos().get(0).cuerpo()).startsWith(
                    "Causal: Uso de lenguaje ofensivo o abusivo en comentarios o chat. "
                            + "Motivo: Insulto en el chat de la sala 12. Política: Normas de convivencia.");
        }

        @Test
        @DisplayName("sin causal (quien emitia antes de SA-4) la sancion se emite igual")
        void sinCausal() {
            Sancion s = servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.ADVERTENCIA, null, false));

            assertThat(s.causal()).isNull();
            assertThat(avisos().get(0).cuerpo()).startsWith("Motivo: Lenguaje ofensivo en el chat.");
        }
    }

    @Nested
    @DisplayName("SA-4 · listado global de sanciones")
    class ListadoGlobal {

        private final FiltroDeSanciones porTipo = new FiltroDeSanciones(Sancion.Tipo.BANEO, null, null, null, null,
                null);

        @Test
        @DisplayName("de la mas reciente a la mas antigua, con la pagina pedida")
        void ordenYPagina() {
            when(sanciones.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Sancion>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(org.springframework.data.domain.Page.empty());
            ArgumentCaptor<org.springframework.data.domain.Pageable> pagina =
                    ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);

            servicio.buscar(MODERADORA, porTipo, 2, 16);

            verify(sanciones).findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Sancion>>any(),
                    pagina.capture());
            assertThat(pagina.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pagina.getValue().getPageSize()).isEqualTo(16);
            assertThat(pagina.getValue().getSort().toList()).containsExactly(
                    org.springframework.data.domain.Sort.Order.desc("emitidaEn"),
                    org.springframework.data.domain.Sort.Order.asc("id"));
        }

        @Test
        @DisplayName("sin filtro se listan todas")
        void sinFiltro() {
            when(sanciones.findAll(org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Sancion>>any(),
                    any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(org.springframework.data.domain.Page.empty());

            assertThat(servicio.buscar(ADMIN, null, 0, 16)).isEmpty();
        }

        @Test
        @DisplayName("un jugador no lista sanciones: 403, sin consultar la base")
        void soloModeracion() {
            assertThatThrownBy(() -> servicio.buscar(OTRO_JUGADOR, porTipo, 0, 16))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            verify(sanciones, never()).findAll(
                    org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Sancion>>any(),
                    any(org.springframework.data.domain.Pageable.class));
        }

        @Test
        @DisplayName("periodo al reves o vacio, pagina negativa, o de 0 o mas de 100 filas: 400")
        void solicitudInvalida() {
            OffsetDateTime ahora = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
            FiltroDeSanciones alReves = new FiltroDeSanciones(null, null, null, null, ahora, ahora.minusDays(1));
            FiltroDeSanciones vacio = new FiltroDeSanciones(null, null, null, null, ahora, ahora);

            assertThatThrownBy(() -> servicio.buscar(MODERADORA, alReves, 0, 16))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.buscar(MODERADORA, vacio, 0, 16))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.buscar(MODERADORA, porTipo, -1, 16))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.buscar(MODERADORA, porTipo, 0, 0))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.buscar(MODERADORA, porTipo, 0, 101))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
        }
    }

    @Nested
    @DisplayName("7.3.1 · jerarquia: solo sobre un rol inferior")
    class Jerarquia {

        private static final UUID CUENTA = UUID.fromString("66666666-6666-6666-6666-666666666666");

        private SancionesService.SolicitudDeSancion contra(String rol, Sancion.Tipo tipo, Long horas,
                                                          boolean confirmacion) {
            rolesDeLasCuentas.put(CUENTA, rol);
            return new SancionesService.SolicitudDeSancion(CUENTA, tipo, "Abuso de herramientas", null, null, horas,
                    confirmacion);
        }

        @Test
        @DisplayName("un moderador no advierte ni suspende a otro moderador, a un administrador ni a un super")
        void moderadorSobreIgualesOSuperiores() {
            for (String rol : List.of("MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR")) {
                assertThatThrownBy(() -> servicio.emitir(MODERADORA, contra(rol, Sancion.Tipo.SUSPENSION, 24L, false)))
                        .isInstanceOf(SancionRechazada.class)
                        .extracting("motivo").isEqualTo(SancionRechazada.Motivo.JERARQUIA_INSUFICIENTE);
                assertThatThrownBy(() -> servicio.emitir(MODERADORA, contra(rol, Sancion.Tipo.ADVERTENCIA, null, false)))
                        .extracting("motivo").isEqualTo(SancionRechazada.Motivo.JERARQUIA_INSUFICIENTE);
            }
            verify(sanciones, never()).save(any());
            verify(salidasGuardadas, never()).save(any());
        }

        @Test
        @DisplayName("un administrador no banea a otro administrador ni a un super; un super no banea a otro super")
        void baneoSobreIgualesOSuperiores() {
            assertThatThrownBy(() -> servicio.emitir(ADMIN, contra("ADMINISTRADOR", Sancion.Tipo.BANEO, null, true)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.JERARQUIA_INSUFICIENTE);
            assertThatThrownBy(() -> servicio.emitir(ADMIN,
                    contra("SUPER_ADMINISTRADOR", Sancion.Tipo.BANEO, null, true)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.JERARQUIA_INSUFICIENTE);
            assertThatThrownBy(() -> servicio.emitir(SUPER,
                    contra("SUPER_ADMINISTRADOR", Sancion.Tipo.BANEO, null, true)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.JERARQUIA_INSUFICIENTE);
            verify(sanciones, never()).save(any());
        }

        @Test
        @DisplayName("sobre un rol inferior si: el administrador suspende a un moderador, el super a un administrador")
        void sobreUnRolInferior() {
            Sancion aModeradora = servicio.emitir(ADMIN, contra("MODERADOR", Sancion.Tipo.SUSPENSION, 24L, false));
            Sancion aAdmin = servicio.emitir(SUPER, contra("ADMINISTRADOR", Sancion.Tipo.ADVERTENCIA, null, false));

            assertThat(aModeradora.usuarioId()).isEqualTo(CUENTA);
            assertThat(aAdmin.rolEmisor()).isEqualTo("SUPER_ADMINISTRADOR");
        }

        @Test
        @DisplayName("levantar la sancion de un administrador es del super, no de otro administrador")
        void levantarSobreUnIgual() {
            Sancion suspension = servicio.emitir(SUPER, contra("ADMINISTRADOR", Sancion.Tipo.SUSPENSION, 24L, false));
            when(sanciones.findById(suspension.id())).thenReturn(Optional.of(suspension));

            assertThatThrownBy(() -> servicio.levantar(ADMIN, suspension.id(), "Ya hablamos con el"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.JERARQUIA_INSUFICIENTE);
            assertThat(suspension.revertidaEn()).isNull();

            servicio.levantar(SUPER, suspension.id(), "Ya hablamos con el");
            assertThat(suspension.revertidaEn()).isNotNull();
        }

        @Test
        @DisplayName("con ms-identidad sin responder no se registra nada (fail-closed)")
        void identidadSinResponder() {
            identidadCaida = new SancionRechazada(SancionRechazada.Motivo.IDENTIDAD_NO_DISPONIBLE,
                    RolesDesdeIdentidad.NO_DISPONIBLE);

            assertThatThrownBy(() -> servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.ADVERTENCIA, null, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.IDENTIDAD_NO_DISPONIBLE);
            verify(sanciones, never()).save(any());
            verify(salidasGuardadas, never()).save(any());
        }

        @Test
        @DisplayName("el rol se comprueba despues de las reglas locales: una solicitud invalida ni pregunta")
        void primeroLasReglasLocales() {
            identidadCaida = new SancionRechazada(SancionRechazada.Motivo.IDENTIDAD_NO_DISPONIBLE, "caida");

            assertThatThrownBy(() -> servicio.emitir(MODERADORA, solicitud(Sancion.Tipo.BANEO, null, true)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            assertThatThrownBy(() -> servicio.emitir(OTRO_JUGADOR, solicitud(Sancion.Tipo.ADVERTENCIA, null, false)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
        }
    }

    @Test
    @DisplayName("Actor.superaA: estrictamente por encima; un rol desconocido no supera a nadie")
    void superaA() {
        assertThat(MODERADORA.superaA("JUGADOR")).isTrue();
        assertThat(MODERADORA.superaA("MODERADOR")).isFalse();
        assertThat(SUPER.superaA("ADMINISTRADOR")).isTrue();
        assertThat(SUPER.superaA("SUPER_ADMINISTRADOR")).isFalse();
        assertThat(OTRO_JUGADOR.superaA("JUGADOR")).isFalse();
        assertThat(new Actor(UUID.randomUUID(), "SERVICIO").superaA("JUGADOR")).isFalse();
        assertThat(ADMIN.superaA(null)).isFalse();
        assertThat(SUPER.superaA("ROOT")).isFalse();
    }

    // ---- D-45 (HU-USR-008): usuarios con 3 o mas sanciones no revertidas ----

    private static SancionRepository.Reincidente reincidente(UUID usuario, long cuantas, String ultima) {
        return new SancionRepository.Reincidente() {
            @Override public UUID getUsuarioId() { return usuario; }
            @Override public long getSanciones() { return cuantas; }
            @Override public OffsetDateTime getUltimaEn() { return OffsetDateTime.parse(ultima); }
        };
    }

    @Test
    @DisplayName("quien modera ve a los reincidentes; el total dice cuantos hay aunque la lista se corte")
    void reincidentesParaQuienModera() {
        UUID a = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID b = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(sanciones.reincidentes(eq(3L), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(
                reincidente(a, 5, "2026-09-20T10:00:00Z"), reincidente(b, 3, "2026-09-19T10:00:00Z")));
        when(sanciones.contarReincidentes(3L)).thenReturn(7L);

        SancionesService.Reincidentes reincidentes = servicio.reincidentes(MODERADORA, 3);

        assertThat(reincidentes.minimo()).isEqualTo(3);
        assertThat(reincidentes.total()).isEqualTo(7);
        assertThat(reincidentes.usuarios()).extracting(SancionesService.UsuarioReincidente::usuarioId)
                .containsExactly(a, b);
        assertThat(reincidentes.usuarios().get(0).sanciones()).isEqualTo(5);
        assertThat(reincidentes.usuarios().get(0).ultimaEn()).isEqualTo(OffsetDateTime.parse("2026-09-20T10:00:00Z"));
    }

    @Test
    @DisplayName("la lista de reincidentes es de quien modera: un jugador no la ve y ni se consulta")
    void unJugadorNoVeLosReincidentes() {
        assertThatThrownBy(() -> servicio.reincidentes(OTRO_JUGADOR, 3))
                .isInstanceOf(SancionRechazada.class)
                .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
        verify(sanciones, never()).reincidentes(org.mockito.ArgumentMatchers.anyLong(),
                any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    @DisplayName("un minimo menor que uno no es un criterio: se rechaza en vez de listar a todo el mundo")
    void minimoSinSentido() {
        assertThatThrownBy(() -> servicio.reincidentes(MODERADORA, 0))
                .isInstanceOf(SancionRechazada.class)
                .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
    }

    @Nested
    @DisplayName("SA-5 · bitacora central (§7.3.5)")
    class BitacoraCentral {

        private final com.nexusbattles.plataforma.moderacionsanciones.auditoria.BitacoraDeAuditoria bitacora =
                org.mockito.Mockito.mock(com.nexusbattles.plataforma.moderacionsanciones.auditoria.BitacoraDeAuditoria.class);
        private SancionesService conBitacora;

        @BeforeEach
        void conBitacora() {
            conBitacora = new SancionesService(sanciones, apelaciones, new SalidasDeSancion(salidasGuardadas),
                    Clock.fixed(AHORA, ZoneOffset.UTC), LimitesDeSancion.Fijos.de(1, 30, 30),
                    cuenta -> rolesDeLasCuentas.getOrDefault(cuenta, "JUGADOR"), PlazoDeDecision.deLunesAViernes(),
                    bitacora);
        }

        @Test
        @DisplayName("una advertencia queda como SANCION sobre el uid, con lo que se impuso y el motivo")
        void advertencia() {
            conBitacora.emitir(MODERADORA, solicitud(Sancion.Tipo.ADVERTENCIA, null, false));

            verify(bitacora).registrar(com.nexusbattles.plataforma.moderacionsanciones.auditoria.TipoDeAccion.SANCION,
                    JUGADOR.toString(), null, "Advertencia · Política: Normas de convivencia",
                    "Lenguaje ofensivo en el chat");
        }

        @Test
        @DisplayName("una suspension queda como SUSPENSION con su fin en la hora de Colombia")
        void suspension() {
            conBitacora.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 72L, false));

            ArgumentCaptor<String> nuevo = ArgumentCaptor.forClass(String.class);
            verify(bitacora).registrar(
                    eq(com.nexusbattles.plataforma.moderacionsanciones.auditoria.TipoDeAccion.SUSPENSION),
                    eq(JUGADOR.toString()), org.mockito.ArgumentMatchers.isNull(), nuevo.capture(),
                    eq("Lenguaje ofensivo en el chat"));
            assertThat(nuevo.getValue()).startsWith("Suspensión hasta el 24 de septiembre de 2026, 5:00")
                    .endsWith("· Política: Normas de convivencia");
        }

        @Test
        @DisplayName("levantar queda como ACTUALIZACION: como estaba, que se levanto y por que")
        void levantar() {
            Sancion suspension = conBitacora.emitir(ADMIN, solicitud(Sancion.Tipo.SUSPENSION, 48L, false));
            when(sanciones.findById(suspension.id())).thenReturn(Optional.of(suspension));

            conBitacora.levantar(SUPER, suspension.id(), "  Error de moderacion  ");

            ArgumentCaptor<String> antes = ArgumentCaptor.forClass(String.class);
            verify(bitacora).registrar(
                    eq(com.nexusbattles.plataforma.moderacionsanciones.auditoria.TipoDeAccion.ACTUALIZACION),
                    eq(JUGADOR.toString()), antes.capture(), eq("Suspensión levantada antes de su fin"),
                    eq("Error de moderacion"));
            assertThat(antes.getValue()).startsWith("Suspensión hasta el 23 de septiembre de 2026");
        }

        @Test
        @DisplayName("una apelacion estimada es APROBACION; desestimada, RECHAZO; las dos con la decision motivada")
        void apelaciones() {
            Sancion suspension = conBitacora.emitir(MODERADORA, solicitud(Sancion.Tipo.SUSPENSION, 72L, false));
            when(sanciones.findById(suspension.id())).thenReturn(Optional.of(suspension));
            when(apelaciones.findBySancionIdAndEstado(suspension.id(), Apelacion.Estado.PENDIENTE))
                    .thenReturn(Optional.empty());
            Apelacion apelacion = conBitacora.apelar(SANCIONADO, suspension.id(), "No fui yo");
            when(apelaciones.findById(apelacion.id())).thenReturn(Optional.of(apelacion));

            conBitacora.resolver(ADMIN, apelacion.id(), Apelacion.Estado.MANTENIDA, "Las pruebas son claras", null);

            ArgumentCaptor<String> antes = ArgumentCaptor.forClass(String.class);
            verify(bitacora).registrar(
                    eq(com.nexusbattles.plataforma.moderacionsanciones.auditoria.TipoDeAccion.RECHAZO),
                    eq(JUGADOR.toString()), antes.capture(), eq("Apelación desestimada: la sanción se mantiene"),
                    eq("Las pruebas son claras"));
            assertThat(antes.getValue()).startsWith("Apelación pendiente · Suspensión hasta el");
        }

        @Test
        @DisplayName("una accion rechazada (un moderador que intenta banear) no deja asiento")
        void rechazadaNoSeRegistra() {
            assertThatThrownBy(() -> conBitacora.emitir(MODERADORA, solicitud(Sancion.Tipo.BANEO, null, true)))
                    .isInstanceOf(SancionRechazada.class);

            org.mockito.Mockito.verifyNoInteractions(bitacora);
        }

        @Test
        @DisplayName("descripcionDe: baneo con causal")
        void descripcionDelBaneo() {
            Sancion baneo = new Sancion(UUID.randomUUID(), JUGADOR, Sancion.Tipo.BANEO, Causal.values()[0],
                    "Reincidencia grave", null, null, ADMIN.id(), ADMIN.rol(),
                    OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC), null);

            assertThat(SancionesService.descripcionDe(baneo))
                    .isEqualTo("Inhabilitación definitiva · Causal: " + Causal.values()[0].etiqueta());
        }
    }
}
