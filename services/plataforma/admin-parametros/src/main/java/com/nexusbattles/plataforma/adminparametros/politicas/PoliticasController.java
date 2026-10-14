package com.nexusbattles.plataforma.adminparametros.politicas;

import com.nexusbattles.plataforma.adminparametros.parametros.Actor;
import com.nexusbattles.plataforma.adminparametros.parametros.ParametrosController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * {@code /api/v1/parametros/politicas/cambios} — admin-parametros.yaml 1.1.0
 * (SA-11, §7.3.7). Publicar exige token de administracion; leer es publico,
 * como el catalogo de parametros: es un anuncio para todos los jugadores, y
 * por eso la respuesta no lleva quien publico, ni el motivo ni la IP (eso va
 * a la bitacora central).
 */
@RestController
@RequestMapping("/api/v1/parametros/politicas/cambios")
public class PoliticasController {

    private final PoliticasService servicio;

    public PoliticasController(PoliticasService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CambioDePoliticaResponse publicar(@AuthenticationPrincipal Jwt token,
                                             @RequestBody PublicacionRequest pedido,
                                             HttpServletRequest peticion) {
        Actor actor = token == null ? null : ParametrosController.actorDe(token);
        return CambioDePoliticaResponse.desde(servicio.publicar(actor,
                new PoliticasService.Publicacion(pedido.politica(), pedido.titulo(), pedido.descripcion(),
                        pedido.vigenteDesde(), pedido.motivo()),
                ParametrosController.ipDe(peticion)));
    }

    @GetMapping
    public List<CambioDePoliticaResponse> historial() {
        return servicio.historial().stream().map(CambioDePoliticaResponse::desde).toList();
    }

    @GetMapping("/posteriores")
    public LoteDeCambiosResponse posteriores(
            @RequestParam("desde") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(value = "limite", defaultValue = "100") int limite) {
        List<CambioDePolitica> lote = servicio.posteriores(desde, limite);
        OffsetDateTime hasta = lote.isEmpty() ? desde : lote.get(lote.size() - 1).publicadoEn();
        return new LoteDeCambiosResponse(lote.stream().map(CambioDePoliticaResponse::desde).toList(), hasta,
                lote.size() < Math.max(1, Math.min(limite, PoliticasService.LOTE_MAXIMO)));
    }

    public record PublicacionRequest(String politica, String titulo, String descripcion,
                                     OffsetDateTime vigenteDesde, String motivo) {
    }

    public record CambioDePoliticaResponse(UUID id, Politica politica, String nombreDeLaPolitica, String titulo,
                                           String descripcion, OffsetDateTime vigenteDesde,
                                           OffsetDateTime publicadoEn) {
        static CambioDePoliticaResponse desde(CambioDePolitica c) {
            return new CambioDePoliticaResponse(c.id(), c.politica(), c.politica().nombre(), c.titulo(),
                    c.descripcion(), c.vigenteDesde(), c.publicadoEn());
        }
    }

    /** {@code completo} falso: hay mas despues de {@code hasta}; pedir otra vez desde ahi. */
    public record LoteDeCambiosResponse(List<CambioDePoliticaResponse> cambios, OffsetDateTime hasta,
                                        boolean completo) {
    }
}
