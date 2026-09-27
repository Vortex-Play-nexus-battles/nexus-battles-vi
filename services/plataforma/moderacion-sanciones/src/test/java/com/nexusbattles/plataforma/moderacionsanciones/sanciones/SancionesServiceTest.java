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
import java.util.List;
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
        return new SancionesService(sanciones, apelaciones, new SalidasDeSancion(salidasGuardadas), reloj, limites);
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
                    .containsExactly(CanalDeSalida.AVISO, CanalDeSalida.CORREO);
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

            assertThat(avisos().get(0).cuerpo()).contains("dentro de los 7 dias").doesNotContain("30 dias");
        }

        @Test
        @DisplayName("el numero del aviso es el mismo que aplica el rechazo por fuera de plazo")
        void elAvisoYElRechazoCoinciden() {
            SancionesService conPlazoCorto = nuevo(Clock.fixed(AHORA, ZoneOffset.UTC), LimitesDeSancion.Fijos.de(1, 30, 7));
            Sancion baneo = conPlazoCorto.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, true));
            when(sanciones.findById(baneo.id())).thenReturn(Optional.of(baneo));

            assertThat(avisos().get(0).cuerpo()).contains("dentro de los 7 dias");

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
                            org.assertj.core.api.Assertions.tuple(CanalDeSalida.CORREO, s.id()));
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

        private Sancion baneo;

        @BeforeEach
        void conBaneo() {
            baneo = servicio.emitir(ADMIN, solicitud(Sancion.Tipo.BANEO, null, true));
            lenient().when(sanciones.findById(baneo.id())).thenReturn(Optional.of(baneo));
        }

        @Test
        @DisplayName("un administrador levanta: queda revertida con motivo y autor, deja de restringir, avisa y proyecta")
        void levanta() {
            Sancion levantada = servicio.levantar(SUPER, baneo.id(), "  Error de moderacion  ");

            assertThat(levantada.revertidaEn()).isEqualTo(OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC));
            assertThat(levantada.revertidaPor()).isEqualTo(SUPER.id());
            assertThat(levantada.motivoReversion()).isEqualTo("Error de moderacion");
            assertThat(servicio.activaDe(JUGADOR)).isEmpty();
            assertThat(salidas()).filteredOn(s -> s.tipo().equals("SANCION_LEVANTADA")
                            || s.tipo().equals("LEVANTAMIENTO"))
                    .extracting(SalidaPendiente::canal)
                    .containsExactly(CanalDeSalida.AVISO, CanalDeSalida.PROYECCION);
            assertThat(avisos().get(avisos().size() - 1).titulo()).isEqualTo("Tu inhabilitacion fue levantada");
        }

        @Test
        @DisplayName("un moderador o un jugador no levantan: 403")
        void permisos() {
            assertThatThrownBy(() -> servicio.levantar(MODERADORA, baneo.id(), "motivo"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            assertThatThrownBy(() -> servicio.levantar(SANCIONADO, baneo.id(), "motivo"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.PERMISO_INSUFICIENTE);
            assertThat(baneo.revertidaEn()).isNull();
        }

        @Test
        @DisplayName("sin motivo (menos de 3 caracteres) o con uno de mas de 1000: 400")
        void motivo() {
            assertThatThrownBy(() -> servicio.levantar(ADMIN, baneo.id(), " ok "))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.levantar(ADMIN, baneo.id(), null))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
            assertThatThrownBy(() -> servicio.levantar(ADMIN, baneo.id(), "x".repeat(1001)))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.SOLICITUD_INVALIDA);
        }

        @Test
        @DisplayName("una sancion que no existe es 404; una ya levantada o vencida, 409")
        void noVigente() {
            UUID inexistente = UUID.randomUUID();
            when(sanciones.findById(inexistente)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> servicio.levantar(ADMIN, inexistente, "motivo"))
                    .extracting("motivo").isEqualTo(SancionRechazada.Motivo.NO_ENCONTRADA);

            servicio.levantar(ADMIN, baneo.id(), "Primera vez");
            assertThatThrownBy(() -> servicio.levantar(ADMIN, baneo.id(), "Otra vez"))
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
}
