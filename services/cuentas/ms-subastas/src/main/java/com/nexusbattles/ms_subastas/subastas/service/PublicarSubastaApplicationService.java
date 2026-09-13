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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

@Service
@ConditionalOnBean({InventarioClient.class, CatalogoProductosClient.class, FinanzasPublicacionClient.class,
        IdentidadClient.class, SancionesClient.class})
public class PublicarSubastaApplicationService {
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
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new PublicacionSubastaException("Idempotency-Key es obligatorio");
        solicitud.validarPrecios();
        if (incrementoMinimo == null || incrementoMinimo.signum() <= 0) throw new PublicacionSubastaException("app.subastas.incremento-minimo no está configurado");
        String huella = huella(solicitud);
        var previo = idempotencia.buscar(idempotencyKey);
        if (previo.isPresent()) {
            if (!previo.get().huella().equals(huella)) throw new PublicacionSubastaException("La clave de idempotencia fue usada con otra solicitud");
            return previo.get().respuesta();
        }
        var quien = identidad.actual();
        if (quien == null || quien.usuarioId() == null) throw new PublicacionSubastaException("Usuario no autenticado");
        if (sanciones.tieneSancionActiva(quien.usuarioId())) throw new PublicacionSubastaException("El usuario tiene una sanción activa");
        var elemento = inventario.buscar(solicitud.elementoInventarioId()).orElseThrow(() -> new PublicacionSubastaException("Elemento de inventario inexistente"));
        if (!quien.usuarioId().equals(elemento.propietarioId())) throw new PublicacionSubastaException("El elemento no pertenece al usuario");
        if (!solicitud.productoId().equals(elemento.productoId())) throw new PublicacionSubastaException("El producto no coincide con el elemento");
        if (elemento.enUso()) throw new PublicacionSubastaException("El producto está en uso");
        var producto = catalogo.buscar(solicitud.productoId()).orElseThrow(() -> new PublicacionSubastaException("Producto inexistente"));
        if (!producto.subastable()) throw new PublicacionSubastaException("El producto no es subastable");

        UUID subastaId = UUID.randomUUID();
        BigDecimal comision = comisiones.calcular(solicitud.duracion(), quien.esMaestroDeJuego());
        boolean reservado = false, debitado = false;
        try {
            inventario.reservar(elemento.id(), quien.usuarioId(), subastaId, idempotencyKey); reservado = true;
            if (comision.signum() > 0) { finanzas.debitarComision(quien.usuarioId(), comision, subastaId, idempotencyKey); debitado = true; }
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
            idempotencia.guardar(idempotencyKey, huella, subastaId, respuesta);
            return respuesta;
        } catch (DataIntegrityViolationException e) {
            compensar(quien.usuarioId(), elemento.id(), subastaId, idempotencyKey, comision, reservado, debitado);
            throw new PublicacionSubastaException("El elemento ya tiene una subasta activa", e);
        } catch (RuntimeException e) {
            compensar(quien.usuarioId(), elemento.id(), subastaId, idempotencyKey, comision, reservado, debitado);
            throw e;
        }
    }

    private void compensar(UUID jugador, String elemento, UUID subasta, String clave, BigDecimal monto, boolean reservado, boolean debitado) {
        if (debitado) try { finanzas.compensarDebito(jugador, monto, subasta, clave); } catch (RuntimeException ignored) { }
        if (reservado) try { inventario.liberarReserva(elemento, subasta, clave); } catch (RuntimeException ignored) { }
    }
    private static String huella(PublicarSubastaRequest s) {
        String texto = s.elementoInventarioId()+"|"+s.productoId()+"|"+s.duracion()+"|"+s.precioInicial()+"|"+s.precioCompraInmediata();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
