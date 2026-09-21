package com.nexusbattles.plataforma.adminparametros.parametros;

import com.nexusbattles.comun.seguridad.IdentidadDelToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** {@code /api/v1/parametros} — contracts/openapi/admin-parametros.yaml 1.0.0. */
@RestController
@RequestMapping("/api/v1/parametros")
public class ParametrosController {

    private final ParametrosService servicio;

    public ParametrosController(ParametrosService servicio) {
        this.servicio = servicio;
    }

    static Actor actorDe(Jwt token) {
        String rol = token.getClaimAsString("rol");
        UUID id;
        try {
            id = IdentidadDelToken.idDe(token);
        } catch (IllegalArgumentException sinUid) {
            id = null;
        }
        return new Actor(id, rol == null ? "" : rol);
    }

    @GetMapping
    public List<ParametroResponse> listar() {
        return servicio.listar().stream().map(ParametroResponse::desde).toList();
    }

    @GetMapping("/{clave}")
    public ParametroResponse obtener(@PathVariable String clave) {
        return ParametroResponse.desde(servicio.obtener(clave));
    }

    @GetMapping("/{clave}/valor")
    public ValorResponse valor(@PathVariable String clave) {
        ParametrosService.Vigente v = servicio.obtener(clave);
        return new ValorResponse(v.parametro().clave(), v.valor(), v.parametro().tipo(), v.parametro().version());
    }

    @PutMapping("/{clave}")
    public ParametroResponse cambiar(@AuthenticationPrincipal Jwt actor, @PathVariable String clave,
                                     @RequestBody CambioRequest request) {
        return ParametroResponse.desde(servicio.cambiar(actorDe(actor), clave,
                new ParametrosService.Cambio(request.valor(), request.motivo(), request.vigenteDesde())));
    }

    @GetMapping("/{clave}/historial")
    public List<VersionResponse> historial(@AuthenticationPrincipal Jwt actor, @PathVariable String clave) {
        return servicio.historial(actorDe(actor), clave).stream().map(VersionResponse::desde).toList();
    }

    public record CambioRequest(String valor, String motivo, OffsetDateTime vigenteDesde) { }

    public record ValorResponse(String clave, String valor, Parametro.Tipo tipo, int version) { }

    public record ParametroResponse(String clave, String descripcion, Parametro.Tipo tipo, String valor, String unidad,
                                    BigDecimal minimo, BigDecimal maximo, List<String> opciones, boolean inalterable,
                                    String origen, int version, UUID actualizadoPor, OffsetDateTime actualizadoEn,
                                    String valorProgramado, OffsetDateTime vigenteDesde) {
        static ParametroResponse desde(ParametrosService.Vigente v) {
            Parametro p = v.parametro();
            return new ParametroResponse(p.clave(), p.descripcion(), p.tipo(), v.valor(), p.unidad(), p.minimo(),
                    p.maximo(), p.opciones() == null ? null : p.opcionesComoLista(), p.inalterable(), p.origen(),
                    p.version(), p.actualizadoPor(), p.actualizadoEn(), p.valorProgramado(), p.vigenteDesde());
        }
    }

    public record VersionResponse(int version, String valorAnterior, String valorNuevo, String motivo, UUID cambiadoPor,
                                  OffsetDateTime cambiadoEn, OffsetDateTime vigenteDesde) {
        static VersionResponse desde(Version v) {
            return new VersionResponse(v.version(), v.valorAnterior(), v.valorNuevo(), v.motivo(), v.cambiadoPor(),
                    v.cambiadoEn(), v.vigenteDesde());
        }
    }
}
