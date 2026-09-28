package com.nexusbattles.ms_subastas.notificaciones;

import com.nexusbattles.comun.observabilidad.FiltroDeTraza;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * La salida por correo de los avisos (B8): contacto de ms-identidad en el
 * momento, {@code Idempotency-Key} estable por evento, reintentos con espera
 * creciente y final, y ningun correo a quien no existe o esta baneado.
 */
@DisplayName("Drenador de correos de subastas")
class DrenadorDeCorreosJobTest {

    private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");

    private final NotificacionPendienteRepository repositorio = mock(NotificacionPendienteRepository.class);
    private final CorreoSubastaClient correo = mock(CorreoSubastaClient.class);
    private final ContactoClient contactos = mock(ContactoClient.class);

    private DrenadorDeCorreosJob drenador;

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> proveedor(T valor) {
        ObjectProvider<T> proveedor = mock(ObjectProvider.class);
        when(proveedor.getIfAvailable()).thenReturn(valor);
        return proveedor;
    }

    @BeforeEach
    void preparar() {
        drenador = new DrenadorDeCorreosJob(repositorio, proveedor(correo), proveedor(contactos),
                Clock.fixed(AHORA, ZoneOffset.UTC), 50, 30, 3);
    }

    private NotificacionPendiente aviso(UUID destinatario) {
        NotificacionPendiente aviso = new NotificacionPendiente(UUID.randomUUID(), TipoNotificacion.PUJA_SUPERADA,
                destinatario, UUID.randomUUID(), "Otra puja supero tu oferta.", AHORA.minusSeconds(60), null);
        aviso.setConCorreo(true);
        aviso.setAsunto("Te superaron en una subasta · Espada");
        aviso.setCorreoEstado(EstadoCorreo.PENDIENTE);
        return aviso;
    }

    private void enCola(NotificacionPendiente... avisos) {
        when(repositorio.correosPorEnviar(eq(AHORA), any(Pageable.class))).thenReturn(List.of(avisos));
    }

    private void contacto(UUID uid, String email, String apodo, String estado) {
        when(contactos.contactoDe(uid)).thenReturn(Optional.of(new ContactoClient.Contacto(uid, email, apodo, estado)));
    }

    @Test
    @DisplayName("envia al correo de la cuenta, con el id del aviso como clave de idempotencia, y lo marca ENVIADO")
    void enviaYMarca() {
        UUID uid = UUID.randomUUID();
        NotificacionPendiente aviso = aviso(uid);
        enCola(aviso);
        contacto(uid, "lyra@example.com", "lyra", "ACTIVO");

        drenador.drenar();

        ArgumentCaptor<CorreoSubastaClient.CorreoSubasta> enviado = ArgumentCaptor.forClass(CorreoSubastaClient.CorreoSubasta.class);
        verify(correo).enviar(eq("ms-subastas-" + aviso.getId()), enviado.capture());
        assertEquals(new CorreoSubastaClient.CorreoSubasta("lyra@example.com", "lyra",
                "Te superaron en una subasta · Espada", "Otra puja supero tu oferta.", true), enviado.getValue());
        assertEquals(EstadoCorreo.ENVIADO, aviso.getCorreoEstado());
        assertEquals(AHORA, aviso.getCorreoEnviadoEn());
        verify(repositorio).save(aviso);
        assertNull(MDC.get(FiltroDeTraza.CLAVE_MDC), "la traza de la pasada se cierra al terminar");
    }

    @Test
    @DisplayName("sin asunto guardado usa el titulo; sin apodo, «jugador»; sin detalle, el titulo como mensaje")
    void valoresPorOmision() {
        UUID uid = UUID.randomUUID();
        NotificacionPendiente aviso = aviso(uid);
        aviso.setAsunto(null);
        aviso.setDetalle(null);
        enCola(aviso);
        contacto(uid, "a@example.com", " ", "ACTIVO");

        drenador.drenar();

        ArgumentCaptor<CorreoSubastaClient.CorreoSubasta> enviado = ArgumentCaptor.forClass(CorreoSubastaClient.CorreoSubasta.class);
        verify(correo).enviar(any(), enviado.capture());
        assertEquals("jugador", enviado.getValue().apodo());
        assertEquals(TipoNotificacion.PUJA_SUPERADA.getTitulo(), enviado.getValue().asunto());
        assertEquals(TipoNotificacion.PUJA_SUPERADA.getTitulo(), enviado.getValue().mensaje());
    }

    @Test
    @DisplayName("a quien no existe, no tiene correo o esta BANEADO no se le escribe: DESCARTADO")
    void descartaSinDestinatario() {
        UUID noExiste = UUID.randomUUID();
        UUID sinCorreo = UUID.randomUUID();
        UUID baneado = UUID.randomUUID();
        NotificacionPendiente a = aviso(noExiste);
        NotificacionPendiente b = aviso(sinCorreo);
        NotificacionPendiente c = aviso(baneado);
        enCola(a, b, c);
        when(contactos.contactoDe(noExiste)).thenReturn(Optional.empty());
        contacto(sinCorreo, " ", "x", "ACTIVO");
        contacto(baneado, "b@example.com", "b", "BANEADO");

        drenador.drenar();

        verifyNoInteractions(correo);
        assertEquals(EstadoCorreo.DESCARTADO, a.getCorreoEstado());
        assertEquals(EstadoCorreo.DESCARTADO, b.getCorreoEstado());
        assertEquals(EstadoCorreo.DESCARTADO, c.getCorreoEstado());
        verify(repositorio, times(3)).save(any());
    }

    @Test
    @DisplayName("un rechazo definitivo de correo (400) lo deja FALLIDO y sigue con el siguiente")
    void rechazoDefinitivo() {
        UUID uid = UUID.randomUUID();
        NotificacionPendiente mal = aviso(uid);
        NotificacionPendiente bien = aviso(uid);
        enCola(mal, bien);
        contacto(uid, "lyra@example.com", "lyra", "ACTIVO");
        doThrow(new CorreoRechazadoException("400")).when(correo).enviar(eq("ms-subastas-" + mal.getId()), any());

        drenador.drenar();

        assertEquals(EstadoCorreo.FALLIDO, mal.getCorreoEstado());
        assertEquals(EstadoCorreo.ENVIADO, bien.getCorreoEstado());
    }

    @Test
    @DisplayName("si correo no responde: cuenta el intento, programa el siguiente y corta el lote")
    void noDisponibleReintentaYCorta() {
        UUID uid = UUID.randomUUID();
        NotificacionPendiente primero = aviso(uid);
        NotificacionPendiente segundo = aviso(uid);
        enCola(primero, segundo);
        contacto(uid, "lyra@example.com", "lyra", "ACTIVO");
        doThrow(new CorreoNoDisponibleException("503")).when(correo).enviar(any(), any());

        drenador.drenar();

        assertEquals(EstadoCorreo.PENDIENTE, primero.getCorreoEstado());
        assertEquals(1, primero.getCorreoIntentos());
        assertEquals(AHORA.plusSeconds(30), primero.getCorreoProximoIntentoEn());
        verify(correo, times(1)).enviar(any(), any());
        assertEquals(0, segundo.getCorreoIntentos(), "el lote se corta: el segundo ni se intenta");
        verify(repositorio, times(1)).save(any());
    }

    @Test
    @DisplayName("si ms-identidad no responde pasa lo mismo: se reintenta, no se descarta")
    void identidadNoDisponible() {
        UUID uid = UUID.randomUUID();
        NotificacionPendiente aviso = aviso(uid);
        enCola(aviso);
        when(contactos.contactoDe(uid)).thenThrow(new CorreoNoDisponibleException("identidad caida"));

        drenador.drenar();

        assertEquals(EstadoCorreo.PENDIENTE, aviso.getCorreoEstado());
        assertEquals(1, aviso.getCorreoIntentos());
    }

    @Test
    @DisplayName("tras el maximo de intentos queda FALLIDO: nada se reintenta para siempre")
    void maximoDeIntentos() {
        UUID uid = UUID.randomUUID();
        NotificacionPendiente aviso = aviso(uid);
        aviso.setCorreoIntentos(2);
        enCola(aviso);
        contacto(uid, "lyra@example.com", "lyra", "ACTIVO");
        doThrow(new CorreoNoDisponibleException("503")).when(correo).enviar(any(), any());

        drenador.drenar();

        assertEquals(3, aviso.getCorreoIntentos());
        assertEquals(EstadoCorreo.FALLIDO, aviso.getCorreoEstado());
    }

    @Test
    @DisplayName("la espera se dobla con cada intento hasta una hora")
    void esperaExponencialConTope() {
        assertEquals(Duration.ofSeconds(30), drenador.espera(1));
        assertEquals(Duration.ofSeconds(60), drenador.espera(2));
        assertEquals(Duration.ofSeconds(120), drenador.espera(3));
        assertEquals(Duration.ofHours(1), drenador.espera(10));
        assertEquals(Duration.ofHours(1), drenador.espera(40));
    }

    @Test
    @DisplayName("sin correo configurado (CORREO_URL vacia) no hace nada")
    void sinCorreoConfigurado() {
        DrenadorDeCorreosJob sinCorreo = new DrenadorDeCorreosJob(repositorio, proveedor(null), proveedor(contactos),
                Clock.fixed(AHORA, ZoneOffset.UTC), 50, 30, 3);

        sinCorreo.drenar();

        verifyNoInteractions(repositorio, contactos);
    }

    @Test
    @DisplayName("sin nada en cola no llama a nadie")
    void colaVacia() {
        enCola();

        drenador.drenar();

        verifyNoInteractions(correo, contactos);
    }

    @Test
    @DisplayName("una traza que ya existia no la cierra el drenador")
    void respetaLaTrazaExistente() {
        UUID uid = UUID.randomUUID();
        enCola(aviso(uid));
        contacto(uid, "lyra@example.com", "lyra", "ACTIVO");
        MDC.put(FiltroDeTraza.CLAVE_MDC, "traza-previa");
        try {
            drenador.drenar();
            assertEquals("traza-previa", MDC.get(FiltroDeTraza.CLAVE_MDC));
        } finally {
            MDC.remove(FiltroDeTraza.CLAVE_MDC);
        }
    }
}
