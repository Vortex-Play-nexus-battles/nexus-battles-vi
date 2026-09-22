package com.nexusbattles.plataforma.adminparametros.parametros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/** Casos de uso de HU-ADM-001: consultar, cambiar (ahora o programado), historial. */
@Service
public class ParametrosService {

    private static final Logger BITACORA = LoggerFactory.getLogger(ParametrosService.class);

    private final ParametroRepository parametros;
    private final VersionRepository versiones;
    private final Auditoria auditoria;
    private final Clock reloj;

    public ParametrosService(ParametroRepository parametros, VersionRepository versiones, Auditoria auditoria, Clock reloj) {
        this.parametros = parametros;
        this.versiones = versiones;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    public record Cambio(String valor, String motivo, OffsetDateTime vigenteDesde) { }

    /** Un parametro con su valor vigente ya resuelto para {@code ahora}. */
    public record Vigente(Parametro parametro, String valor) { }

    @Transactional
    public List<Vigente> listar() {
        OffsetDateTime ahora = ahora();
        return parametros.findAllByOrderByOrdenAscClaveAsc().stream().map(p -> vigente(p, ahora)).toList();
    }

    @Transactional
    public Vigente obtener(String clave) {
        return vigente(parametroDe(clave), ahora());
    }

    @Transactional
    public Vigente cambiar(Actor actor, String clave, Cambio cambio) {
        if (!actor.puedeConfigurar()) {
            throw new ParametroRechazado(ParametroRechazado.Motivo.PERMISO_INSUFICIENTE,
                    "solo un administrador configura parametros");
        }
        if (cambio.motivo() == null || cambio.motivo().strip().length() < 3) {
            throw new ParametroRechazado(ParametroRechazado.Motivo.SOLICITUD_INVALIDA, "todo cambio lleva su motivo");
        }
        Parametro parametro = parametroDe(clave);
        String valor = parametro.validar(cambio.valor());
        OffsetDateTime ahora = ahora();
        Version version = parametro.cambiar(valor, cambio.motivo().strip(), actor.id(), ahora, cambio.vigenteDesde());
        parametros.save(parametro);
        versiones.save(version);
        BITACORA.info("Parametro {} v{}: {} -> {} por {} (vigente desde {}) motivo={}", clave, version.version(),
                version.valorAnterior(), version.valorNuevo(), actor.id(), version.vigenteDesde(), version.motivo());
        auditoria.registrar(version);
        return vigente(parametro, ahora);
    }

    @Transactional(readOnly = true)
    public List<Version> historial(Actor actor, String clave) {
        if (!actor.puedeConfigurar()) {
            throw new ParametroRechazado(ParametroRechazado.Motivo.PERMISO_INSUFICIENTE,
                    "solo un administrador ve el historial");
        }
        parametroDe(clave);
        return versiones.findByClaveOrderByVersionDesc(clave);
    }

    private Vigente vigente(Parametro p, OffsetDateTime ahora) {
        if (p.consolidar(ahora)) {
            parametros.save(p);
        }
        return new Vigente(p, p.valorVigenteEn(ahora));
    }

    private Parametro parametroDe(String clave) {
        return parametros.findById(clave).orElseThrow(() ->
                new ParametroRechazado(ParametroRechazado.Motivo.NO_ENCONTRADO, "no hay ningun parametro " + clave));
    }

    private OffsetDateTime ahora() {
        return OffsetDateTime.now(reloj);
    }
}
