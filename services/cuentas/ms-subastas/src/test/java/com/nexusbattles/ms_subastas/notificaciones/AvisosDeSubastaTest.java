package com.nexusbattles.ms_subastas.notificaciones;

import com.nexusbattles.ms_subastas.panel.model.PendienteDeRecoger;
import com.nexusbattles.ms_subastas.panel.repository.SeguimientoRepository;
import com.nexusbattles.ms_subastas.pujas.model.EstadoPuja;
import com.nexusbattles.ms_subastas.pujas.model.Puja;
import com.nexusbattles.ms_subastas.pujas.model.TipoPuja;
import com.nexusbattles.ms_subastas.pujas.repository.PujaAutomaticaRepository;
import com.nexusbattles.ms_subastas.pujas.repository.PujaRepository;
import com.nexusbattles.ms_subastas.reglas.PoliticaAlVencer;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Quien se entera de que en cada hecho de una subasta (7.7.8), sin avisos
 * duplicados: cada destinatario recibe uno por hecho.
 */
@DisplayName("Avisos de subasta (7.7.8)")
class AvisosDeSubastaTest {

    private static final Instant AHORA = Instant.parse("2026-09-20T12:00:00Z");

    private final UUID vendedor = UUID.randomUUID();
    private final UUID postor = UUID.randomUUID();
    private final UUID superado = UUID.randomUUID();
    private final UUID seguidor = UUID.randomUUID();
    private final UUID conAutomatica = UUID.randomUUID();

    private final NotificacionOutbox outbox = mock(NotificacionOutbox.class);
    private final SeguimientoRepository seguimientos = mock(SeguimientoRepository.class);
    private final PujaRepository pujas = mock(PujaRepository.class);
    private final PujaAutomaticaRepository automaticas = mock(PujaAutomaticaRepository.class);
    private final List<Aviso> encolados = new ArrayList<>();

    private AvisosDeSubasta avisos;
    private Subasta subasta;

    /** Lo que se encolo, para comprobar destinatarios y textos. */
    private record Aviso(TipoNotificacion tipo, UUID destinatario, String discriminante, String titulo, String cuerpo) {
    }

    @BeforeEach
    void preparar() {
        avisos = new AvisosDeSubasta(outbox, seguimientos, pujas, automaticas);
        when(outbox.encolar(any(), any(), any(), anyString(), anyString(), anyString())).thenAnswer(invocacion -> {
            encolados.add(new Aviso(invocacion.getArgument(0), invocacion.getArgument(1), invocacion.getArgument(3),
                    invocacion.getArgument(4), invocacion.getArgument(5)));
            return true;
        });
        subasta = new Subasta(UUID.randomUUID(), UUID.randomUUID(), vendedor, new BigDecimal("120"),
                BigDecimal.TEN, new BigDecimal("500"), postor, EstadoSubasta.ACTIVA, AHORA.plusSeconds(3000), 0L);
        subasta.setNombreProducto("Espada del Alba");
        subasta.setPrecioInicial(new BigDecimal("100"));
        subasta.setComisionCobrada(new BigDecimal("3"));
        subasta.setCantidadPujas(2);
        when(seguimientos.seguidoresDe(subasta.getId())).thenReturn(List.of(seguidor, vendedor));
        when(pujas.findDistinctJugadorIdBySubastaId(subasta.getId())).thenReturn(List.of(postor, superado));
        when(automaticas.jugadoresConAutomatica(subasta.getId())).thenReturn(List.of(conAutomatica, postor));
    }

    private Puja puja(UUID jugador, String monto, String apodo) {
        Puja puja = new Puja(UUID.randomUUID(), subasta.getId(), jugador, new BigDecimal(monto), TipoPuja.MANUAL,
                EstadoPuja.ACTIVA, AHORA, UUID.randomUUID().toString());
        puja.setApodoPostor(apodo);
        return puja;
    }

    private List<UUID> destinatariosDe(TipoNotificacion tipo) {
        return encolados.stream().filter(a -> a.tipo() == tipo).map(Aviso::destinatario).toList();
    }

    private Aviso unico(TipoNotificacion tipo) {
        List<Aviso> deEseTipo = encolados.stream().filter(a -> a.tipo() == tipo).toList();
        assertEquals(1, deEseTipo.size(), "un solo aviso de " + tipo + ": " + encolados);
        return deEseTipo.getFirst();
    }

    private void cadaDestinatarioUnaVez() {
        List<UUID> todos = encolados.stream().map(Aviso::destinatario).toList();
        assertEquals(todos.size(), todos.stream().distinct().count(), "nadie recibe dos avisos del mismo hecho: "
                + encolados);
    }

    @Test
    @DisplayName("publicar confirma al vendedor con el precio minimo y la comision")
    void publicada() {
        avisos.publicada(subasta);

        Aviso aviso = unico(TipoNotificacion.SUBASTA_PUBLICADA);
        assertEquals(vendedor, aviso.destinatario());
        assertEquals("Tu subasta quedó publicada · Espada del Alba", aviso.titulo());
        assertTrue(aviso.cuerpo().contains("Precio mínimo: 100 créditos"), aviso.cuerpo());
        assertTrue(aviso.cuerpo().contains("Comisión cobrada: 3 créditos"), aviso.cuerpo());
    }

    @Test
    @DisplayName("una puja: al postor, al vendedor (con el postor anonimizado), al superado y a quien la sigue")
    void pujaRegistrada() {
        Puja anterior = puja(superado, "110", "gerardo");
        Puja nueva = puja(postor, "120", "valkyria");

        avisos.pujaRegistrada(subasta, nueva, anterior);

        assertEquals(List.of(postor), destinatariosDe(TipoNotificacion.PUJA_REGISTRADA));
        Aviso alVendedor = unico(TipoNotificacion.NUEVA_PUJA);
        assertEquals(vendedor, alVendedor.destinatario());
        assertTrue(alVendedor.cuerpo().startsWith("v***a pujó 120 créditos"), alVendedor.cuerpo());
        assertFalse(alVendedor.cuerpo().contains("valkyria"), "el apodo va anonimizado");
        Aviso alSuperado = unico(TipoNotificacion.PUJA_SUPERADA);
        assertEquals(superado, alSuperado.destinatario());
        assertTrue(alSuperado.cuerpo().contains("desde 130"), "le dice desde cuanto puede volver: " + alSuperado.cuerpo());
        assertEquals("superada:" + anterior.getId(), alSuperado.discriminante());
        // El vendedor tambien sigue su subasta, pero ya recibio NUEVA_PUJA.
        assertEquals(List.of(seguidor), destinatariosDe(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA));
        cadaDestinatarioUnaVez();
    }

    @Test
    @DisplayName("subir la propia puja no avisa al propio jugador de que lo superaron")
    void subirLaPropiaPuja() {
        avisos.pujaRegistrada(subasta, puja(postor, "130", null), puja(postor, "120", null));

        assertTrue(destinatariosDe(TipoNotificacion.PUJA_SUPERADA).isEmpty());
        assertTrue(unico(TipoNotificacion.NUEVA_PUJA).cuerpo().startsWith("Un jugador pujó"));
    }

    @Test
    @DisplayName("la primera puja no tiene a quien superar")
    void primeraPuja() {
        avisos.pujaRegistrada(subasta, puja(postor, "100", "ab"), null);

        assertTrue(destinatariosDe(TipoNotificacion.PUJA_SUPERADA).isEmpty());
        assertTrue(unico(TipoNotificacion.NUEVA_PUJA).cuerpo().startsWith("a*** pujó"));
    }

    @Test
    @DisplayName("una puja automatica lo dice")
    void pujaAutomatica() {
        Puja automatica = puja(postor, "130", "lyra");
        automatica.setTipo(TipoPuja.AUTOMATICA);

        avisos.pujaRegistrada(subasta, automatica, null);

        assertTrue(unico(TipoNotificacion.PUJA_REGISTRADA).cuerpo().startsWith("Tu puja automática ofreció 130"));
    }

    @Test
    @DisplayName("compra inmediata: comprador, vendedor, cada postor y quien tenia automatica, una vez cada uno")
    void compraInmediata() {
        UUID comprador = UUID.randomUUID();
        Puja compra = puja(comprador, "500", "comprador");

        avisos.compraInmediata(subasta, compra, List.of(postor, superado, comprador));

        assertEquals(List.of(comprador), destinatariosDe(TipoNotificacion.COMPRA_INMEDIATA_EXITOSA));
        assertEquals(List.of(vendedor), destinatariosDe(TipoNotificacion.COMPRA_INMEDIATA_EJECUTADA));
        assertEquals(List.of(postor, superado, conAutomatica),
                destinatariosDe(TipoNotificacion.SUBASTA_CERRADA_POR_COMPRA_INMEDIATA));
        assertEquals(List.of(seguidor), destinatariosDe(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA));
        cadaDestinatarioUnaVez();
    }

    @Test
    @DisplayName("cierre con ganador: victoria, venta, resultado a los demas y a quien la sigue")
    void cerradaConGanador() {
        Puja ganadora = puja(postor, "120", "valkyria");
        PendienteDeRecoger pendiente = new PendienteDeRecoger(subasta.getId(), postor, "elemento", ganadora.getMonto(),
                AHORA, AHORA.plusSeconds(7 * 86400));

        avisos.cerradaConGanador(subasta, ganadora, pendiente);

        Aviso victoria = unico(TipoNotificacion.SUBASTA_GANADA);
        assertEquals(postor, victoria.destinatario());
        assertTrue(victoria.cuerpo().contains("antes de 7 días"), victoria.cuerpo());
        assertEquals("¡Ganaste la subasta! · Espada del Alba", victoria.titulo());
        assertEquals(vendedor, unico(TipoNotificacion.SUBASTA_VENDIDA).destinatario());
        assertEquals(List.of(superado, conAutomatica), destinatariosDe(TipoNotificacion.SUBASTA_FINALIZADA));
        assertEquals(List.of(seguidor), destinatariosDe(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA));
        cadaDestinatarioUnaVez();
    }

    @Test
    @DisplayName("cierre sin ofertas: el vendedor, quien tenia automatica y quien la seguia")
    void cerradaSinOfertas() {
        when(pujas.findDistinctJugadorIdBySubastaId(subasta.getId())).thenReturn(List.of());

        avisos.cerradaSinOfertas(subasta);

        Aviso alVendedor = unico(TipoNotificacion.SUBASTA_SIN_OFERTAS);
        assertEquals(vendedor, alVendedor.destinatario());
        assertTrue(alVendedor.cuerpo().contains("la comisión no se reembolsa"), alVendedor.cuerpo());
        assertEquals(List.of(conAutomatica, postor), destinatariosDe(TipoNotificacion.SUBASTA_FINALIZADA));
        assertEquals(List.of(seguidor), destinatariosDe(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA));
        cadaDestinatarioUnaVez();
    }

    @Test
    @DisplayName("cancelacion: el vendedor con la penalizacion; los demas, que se cancelo")
    void cancelada() {
        when(pujas.findDistinctJugadorIdBySubastaId(subasta.getId())).thenReturn(List.of());
        subasta.setPenalizacionCobrada(new BigDecimal("1.50"));

        avisos.cancelada(subasta);

        Aviso alVendedor = unico(TipoNotificacion.SUBASTA_CANCELADA);
        assertEquals(vendedor, alVendedor.destinatario());
        assertTrue(alVendedor.cuerpo().contains("Penalización cobrada: 1.50 créditos (50 % de la comisión)"),
                alVendedor.cuerpo());
        assertEquals(List.of(conAutomatica, postor, seguidor),
                destinatariosDe(TipoNotificacion.CAMBIO_EN_SUBASTA_SEGUIDA));
        cadaDestinatarioUnaVez();
    }

    @Test
    @DisplayName("recordatorio de 1 hora: participantes y seguidores, nunca el vendedor")
    void recordatorio() {
        avisos.recordatorio(subasta);

        List<UUID> destinatarios = destinatariosDe(TipoNotificacion.RECORDATORIO_CIERRE);
        assertEquals(List.of(postor, superado, conAutomatica, seguidor), destinatarios);
        assertFalse(destinatarios.contains(vendedor));
        assertTrue(encolados.getFirst().cuerpo().contains("la próxima puja válida es de 130"),
                encolados.getFirst().cuerpo());
        assertTrue(encolados.stream().allMatch(a -> "recordatorio".equals(a.discriminante())));
    }

    @Test
    @DisplayName("recoger confirma el producto en el inventario del ganador")
    void recogido() {
        PendienteDeRecoger pendiente = new PendienteDeRecoger(subasta.getId(), postor, "elemento", BigDecimal.TEN,
                AHORA, AHORA.plusSeconds(60));

        avisos.recogido(subasta, pendiente);

        assertEquals(postor, unico(TipoNotificacion.PRODUCTO_RECOGIDO).destinatario());
    }

    @Test
    @DisplayName("al vencer el plazo: con ENTREGAR solo al ganador; con DEVOLVER_AL_VENDEDOR, a los dos")
    void pendienteVencido() {
        PendienteDeRecoger pendiente = new PendienteDeRecoger(subasta.getId(), postor, "elemento", BigDecimal.TEN,
                AHORA, AHORA.plusSeconds(60));

        avisos.pendienteVencido(subasta, pendiente, PoliticaAlVencer.ENTREGAR);
        assertEquals(List.of(postor), destinatariosDe(TipoNotificacion.PENDIENTE_VENCIDO));
        assertTrue(encolados.getFirst().cuerpo().contains("lo pasamos a tu inventario"));
        assertTrue(destinatariosDe(TipoNotificacion.PRODUCTO_DEVUELTO).isEmpty());

        encolados.clear();
        avisos.pendienteVencido(subasta, pendiente, PoliticaAlVencer.DEVOLVER_AL_VENDEDOR);
        assertEquals(List.of(postor), destinatariosDe(TipoNotificacion.PENDIENTE_VENCIDO));
        assertEquals(List.of(vendedor), destinatariosDe(TipoNotificacion.PRODUCTO_DEVUELTO));
    }

    @Test
    @DisplayName("sin nombre de producto el titulo es el del tipo y el texto dice «el producto»")
    void sinNombreDeProducto() {
        subasta.setNombreProducto(null);

        avisos.publicada(subasta);

        Aviso aviso = unico(TipoNotificacion.SUBASTA_PUBLICADA);
        assertEquals(TipoNotificacion.SUBASTA_PUBLICADA.getTitulo(), aviso.titulo());
        assertTrue(aviso.cuerpo().contains("de el producto"), aviso.cuerpo());
    }

    // --- Textos ---------------------------------------------------------------------

    @Test
    @DisplayName("los creditos se escriben sin ceros de mas, salvo los centimos")
    void creditos() {
        assertEquals("150", Textos.creditos(new BigDecimal("150.00")));
        assertEquals("1.50", Textos.creditos(new BigDecimal("1.5")));
        assertEquals("0.50", Textos.creditos(new BigDecimal("0.50")));
        assertEquals("1000", Textos.creditos(new BigDecimal("1E+3")));
        assertEquals("0", Textos.creditos(null));
    }

    @Test
    @DisplayName("el apodo va anonimizado parcialmente: primera y ultima letra")
    void anonimizar() {
        assertEquals("v***a", Textos.anonimizar("valkyria"));
        assertEquals("v***a", Textos.anonimizar("  valkyria "));
        assertEquals("a***", Textos.anonimizar("ab"));
        assertEquals("a***", Textos.anonimizar("a"));
        assertEquals("Un jugador", Textos.anonimizar(null));
        assertEquals("Un jugador", Textos.anonimizar(" "));
    }

    @Test
    @DisplayName("el producto sin nombre es «el producto»")
    void producto() {
        assertEquals("Espada", Textos.producto("Espada"));
        assertEquals("el producto", Textos.producto(null));
        assertEquals("el producto", Textos.producto(""));
    }
}
