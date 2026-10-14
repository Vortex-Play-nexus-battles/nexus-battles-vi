package com.nexusbattles.ms_identidad.sanciones.apelacion;

import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafiosDeAcceso;
import com.nexusbattles.ms_identidad.auth.segundofactor.SegundoFactorRechazadoException;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.ApelacionDeLaCuenta;
import com.nexusbattles.ms_identidad.sanciones.ModeracionSancionesClient.EstadoDeApelacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * La apelacion de una cuenta que no puede iniciar sesion — SA-4,
 * ms-identidad-auth.yaml 2.4.0 (§7.3.2: «Los usuarios sancionados deben tener
 * derecho a apelar»; RF-USR-007 deja sin decir si el baneado conserva el
 * acceso al portal, y no lo conserva).
 *
 * <p>El login, con la contrasena ya comprobada, responde el 403 de la cuenta
 * suspendida o baneada con un codigo opaco ({@link Proposito#APELAR}). Aqui
 * ese codigo sirve para dos cosas y para nada mas: ver la sancion que impide
 * entrar y presentar UNA apelacion. Ni abre sesion ni es un token: un JWT
 * «solo para apelar» lo aceptaria cualquier servicio que pida estar
 * autenticado, porque la biblioteca comun no mira la audiencia.
 *
 * <p>La sancion y la apelacion viven en moderacion-sanciones; este servicio
 * pregunta y apela alli EN NOMBRE de la cuenta con su credencial de servicio
 * (moderacion-sanciones-consulta.yaml 1.5.0, rutas internas solo para
 * ms-identidad). Consultar no gasta el codigo; apelar con exito, si.
 */
@Service
public class ApelacionSinSesionService {

    private static final Logger BITACORA = LoggerFactory.getLogger(ApelacionSinSesionService.class);

    private final DesafiosDeAcceso desafios;
    private final UsuarioRepository usuarios;
    private final ModeracionSancionesClient moderacion;

    public ApelacionSinSesionService(DesafiosDeAcceso desafios, UsuarioRepository usuarios,
                                     ModeracionSancionesClient moderacion) {
        this.desafios = desafios;
        this.usuarios = usuarios;
        this.moderacion = moderacion;
    }

    /** La sancion que impide entrar y como va su apelacion. No gasta el codigo. */
    @Transactional
    public EstadoDeApelacion consultar(String codigo) {
        Usuario titular = titularDe(vigente(codigo));
        return moderacion.estadoDeApelacion(titular.getPublicId());
    }

    /**
     * Presenta la apelacion y gasta el codigo. Si moderacion dice que no
     * (fuera de plazo, ya hay una abierta, la sancion cambio), el codigo sigue
     * sirviendo para consultar por que.
     */
    @Transactional
    public ApelacionDeLaCuenta apelar(String codigo, UUID sancionId, String argumento) {
        DesafioDeAcceso desafio = vigente(codigo);
        Usuario titular = titularDe(desafio);
        ApelacionDeLaCuenta apelacion = moderacion.apelarSinSesion(titular.getPublicId(), sancionId,
                argumento.strip());
        desafios.consumir(desafio);
        BITACORA.info("APELACION_SIN_SESION usuarioId={} sancion={} apelacion={}", titular.getId(), sancionId,
                apelacion.id());
        return apelacion;
    }

    private DesafioDeAcceso vigente(String codigo) {
        try {
            return desafios.vigente(codigo, Proposito.APELAR);
        } catch (SegundoFactorRechazadoException invalido) {
            throw new CodigoDeApelacionInvalidoException();
        }
    }

    /** La cuenta del codigo, si sigue siendo la misma version (contrasena y rol). */
    private Usuario titularDe(DesafioDeAcceso desafio) {
        Usuario usuario = usuarios.findById(desafio.getUsuarioId())
                .orElseThrow(CodigoDeApelacionInvalidoException::new);
        if (usuario.getVersionToken() != desafio.getVersionToken() || usuario.getPublicId() == null) {
            throw new CodigoDeApelacionInvalidoException();
        }
        return usuario;
    }
}
