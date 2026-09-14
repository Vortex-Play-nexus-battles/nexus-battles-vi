package com.nexusbattles.ms_subastas.subastas.service;

import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaRequest;
import com.nexusbattles.ms_subastas.subastas.dto.PublicarSubastaResponse;
import com.nexusbattles.ms_subastas.subastas.model.EstadoSubasta;
import com.nexusbattles.ms_subastas.subastas.port.*;
import com.nexusbattles.ms_subastas.subastas.repository.SubastaRepository;
import com.nexusbattles.ms_subastas.subastas.model.Subasta;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import static com.nexusbattles.ms_subastas.subastas.service.PublicacionSubastaException.Motivo.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


public class PublicarSubastaApplicationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublicarSubastaApplicationService.class);
    private final SubastaRepository subastas;
    private final InventarioClient inventario;
    private final CatalogoProductosClient catalogo;
    private final FinanzasPublicacionClient finanzas;
    private final IdentidadClient identidad;
    private final SancionesClient sanciones;
    private final IdempotenciaPublicacion idempotencia;
    private final CalculadorComisionPublicacion comisiones;
    private final Clock clock;
    private final BigDecimal incrementoMinimo;

    public PublicarSubastaApplicationService(SubastaRepository subastas, InventarioClient inventario,
            CatalogoProductosClient catalogo, FinanzasPublicacionClient finanzas, IdentidadClient identidad,
            SancionesClient sanciones, IdempotenciaPublicacion idempotencia,
            CalculadorComisionPublicacion comisiones, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.subastas.incremento-minimo:}") String incrementoMinimo) {
        this.subastas = subastas; this.inventario = inventario; this.catalogo = catalogo; this.finanzas = finanzas;
        this.identidad = identidad; this.sanciones = sanciones; this.idempotencia = idempotencia;
        this.comisiones = comisiones; this.clock = clock;
        this.incrementoMinimo = incrementoMinimo == null || incrementoMinimo.isBlank() ? null : new BigDecimal(incrementoMinimo);
    }

    @Transactional
    public PublicarSubastaResponse publicar(PublicarSubastaRequest solicitud, String idempotencyKey) {
        var quien = identidad.actual();
        if (quien == null || quien.usuarioId() == null) throw new PublicacionSubastaException(NO_AUTENTICADO, "Usuario no autenticado");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) throw new PublicacionSubastaException(SOLICITUD_INVALIDA, "Idempotency-Key es obligatorio");
        solicitud.validarPrecios();
        if (incrementoMinimo == null || incrementoMinimo.signum() <= 0) throw new PublicacionSubastaException(DEPENDENCIA_NO_DISPONIBLE, "app.subastas.incremento-minimo no está configurado");
        String huella = huella(solicitud);
        String claveUsuario = quien.usuarioId() + ":" + idempotencyKey;
        var adquisicion = idempotencia.adquirir(claveUsuario, huella);
        if (adquisicion.resultado().isPresent()) return adquisicion.resultado().get().respuesta();
        var ejecucion = new Ejecucion(claveUsuario, adquisicion.titular(), quien.usuarioId(), idempotencyKey);
        boolean registrada = false;
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(ejecucion);
                registrada = true;
            }
            ejecucion.respuesta = ejecutar(solicitud, idempotencyKey, quien, ejecucion);
            if (!registrada) ejecucion.afterCommit();
            return ejecucion.respuesta;
        } catch (RuntimeException | Error fallo) {
            if (!registrada) ejecucion.revertir();
            throw fallo;
        }
    }

    private PublicarSubastaResponse ejecutar(PublicarSubastaRequest solicitud, String idempotencyKey,
            IdentidadClient.Identidad quien, Ejecucion ejecucion) {
        if (sanciones.tieneSancionActiva(quien.usuarioId())) throw new PublicacionSubastaException(PROHIBIDO, "El usuario tiene una sanción activa");
        var elemento = inventario.buscar(solicitud.elementoInventarioId()).orElseThrow(() -> new PublicacionSubastaException(NO_ENCONTRADO, "Elemento de inventario inexistente"));
        if (!quien.usuarioId().equals(elemento.propietarioId())) throw new PublicacionSubastaException(PROHIBIDO, "El elemento no pertenece al usuario");
        if (!solicitud.productoId().equals(elemento.productoId())) throw new PublicacionSubastaException("El producto no coincide con el elemento");
        if (elemento.enUso()) throw new PublicacionSubastaException("El producto está en uso");
        var producto = catalogo.buscar(solicitud.productoId()).orElseThrow(() -> new PublicacionSubastaException(NO_ENCONTRADO, "Producto inexistente"));
        if (!producto.subastable()) throw new PublicacionSubastaException("El producto no es subastable");

        UUID subastaId = UUID.randomUUID();
        BigDecimal comision = comisiones.calcular(solicitud.duracion(), quien.esMaestroDeJuego());
        ejecucion.elemento = elemento.id();
        ejecucion.subasta = subastaId;
        ejecucion.comision = comision;
        try {
            inventario.reservar(elemento.id(), quien.usuarioId(), subastaId, idempotencyKey); ejecucion.reservado = true;
            if (comision.signum() > 0) { finanzas.debitarComision(quien.usuarioId(), comision, subastaId, idempotencyKey); ejecucion.debitado = true; }
            Instant publicada = Instant.now(clock);
            Subasta subasta = new Subasta();
            subasta.setId(subastaId); subasta.setProductoId(producto.id()); subasta.setElementoInventarioId(elemento.id());
            subasta.setVendedorId(quien.usuarioId()); subasta.setPrecioInicial(solicitud.precioInicial());
            subasta.setOfertaVigente(solicitud.precioInicial()); subasta.setIncrementoMinimo(incrementoMinimo);
            subasta.setPrecioCompraInmediata(solicitud.precioCompraInmediata()); subasta.setEstado(EstadoSubasta.ACTIVA);
            subasta.setFechaPublicacion(publicada); subasta.setFechaFin(publicada.plus(solicitud.duracion().duracion()));
            subasta.setNombreProducto(producto.nombre()); subasta.setTipoProducto(producto.tipo()); subasta.setRareza(producto.rareza());
            subasta.setMiniaturaUrl(producto.miniaturaUrl()); subasta.setDescripcionCorta(producto.descripcionCorta());
            subasta.setHabilidades(producto.habilidades()); subasta.setCantidadPujas(0); subasta.setEsMaestroDeJuego(quien.esMaestroDeJuego()); subasta.setVistas(0);
            PublicarSubastaResponse respuesta = PublicarSubastaResponse.desde(subastas.saveAndFlush(subasta), comision);
            return respuesta;
        } catch (DataIntegrityViolationException e) {
            if (esUnidadActivaDuplicada(e)) {
                throw new PublicacionSubastaException(CONFLICTO, "El elemento ya tiene una subasta activa", e);
            }
            throw e;
        }
    }

    private static boolean esUnidadActivaDuplicada(Throwable fallo) {
        var visitados = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable causa = fallo; causa != null && visitados.add(causa); causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException restriccion
                    && "uq_subastas_elemento_inventario_activa".equals(restriccion.getConstraintName())) return true;
        }
        return false;
    }

    private final class Ejecucion implements TransactionSynchronization {
        private final String clave;
        private final UUID titular;
        private final UUID jugador;
        private final String claveExterna;
        private String elemento;
        private UUID subasta;
        private BigDecimal comision;
        private boolean reservado;
        private boolean debitado;
        private PublicarSubastaResponse respuesta;

        private Ejecucion(String clave, UUID titular, UUID jugador, String claveExterna) {
            this.clave = clave;
            this.titular = titular;
            this.jugador = jugador;
            this.claveExterna = claveExterna;
        }

        @Override
        public void afterCommit() {
            idempotencia.confirmar(clave, titular, respuesta);
        }

        @Override
        public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) revertir();
            else if (status == STATUS_UNKNOWN) {
                idempotencia.marcarIncierta(clave, titular);
                log.error("Resultado de commit desconocido para subasta {}; requiere conciliacion", subasta);
            }
        }

        private void revertir() {
            try {
                compensar(jugador, elemento, subasta, claveExterna, comision, reservado, debitado);
            } finally {
                idempotencia.liberar(clave, titular);
            }
        }
    }

    private void compensar(UUID jugador, String elemento, UUID subasta, String clave, BigDecimal monto, boolean reservado, boolean debitado) {
        if (debitado) try { finanzas.compensarDebito(jugador, monto, subasta, clave); }
        catch (RuntimeException fallo) { log.error("No se pudo compensar comision de subasta {}; requiere conciliacion", subasta, fallo); }
        if (reservado) try { inventario.liberarReserva(elemento, subasta, clave); }
        catch (RuntimeException fallo) { log.error("No se pudo liberar reserva de subasta {}; requiere conciliacion", subasta, fallo); }
    }
    private static String huella(PublicarSubastaRequest s) {
        String texto = s.elementoInventarioId()+"|"+s.productoId()+"|"+s.duracion()+"|"+s.precioInicial()+"|"+s.precioCompraInmediata();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
