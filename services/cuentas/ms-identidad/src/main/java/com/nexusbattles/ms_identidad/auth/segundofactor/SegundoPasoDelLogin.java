package com.nexusbattles.ms_identidad.auth.segundofactor;

import com.nexusbattles.ms_identidad.auth.dto.LoginResponse;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.segundofactor.DesafioDeAcceso.Proposito;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.AccesoConSegundoFactorResponse;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.ActivacionConDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.CanjeDeDesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.DesafioRequest;
import com.nexusbattles.ms_identidad.auth.segundofactor.dto.EnrolamientoResponse;
import com.nexusbattles.ms_identidad.auth.service.LoginService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * El segundo paso del login — HU-AUT-007 CA-01 a CA-04.
 *
 * <p>El desafio de {@code POST /auth/login} prueba que ya se dio la
 * contrasena; aqui se prueba el segundo factor y solo entonces
 * {@link LoginService#completarAccesoConSegundoFactor} abre la sesion (con
 * {@code amr: ["pwd","otp"]}). Todo en una transaccion con el desafio
 * bloqueado: dos canjes simultaneos del mismo desafio no abren dos sesiones,
 * y si algo falla despues de comprobar el codigo (una sancion que llego entre
 * los dos pasos) no queda nada a medias.
 *
 * <p>Un codigo incorrecto NO gasta el desafio: se puede volver a intentar
 * mientras viva, y cada intento cuenta como intento fallido de la cuenta
 * ({@link SegundoFactorService#comprobarEnElAcceso}). Al bloquearse la cuenta
 * ya no se compara nada (423), y cuando el bloqueo termina el desafio ya
 * caduco: hay que volver a escribir la contrasena.
 */
@Service
public class SegundoPasoDelLogin {

    private final DesafiosDeAcceso desafios;
    private final SegundoFactorService segundoFactor;
    private final UsuarioRepository usuarios;
    private final LoginService login;

    public SegundoPasoDelLogin(DesafiosDeAcceso desafios, SegundoFactorService segundoFactor,
                               UsuarioRepository usuarios, LoginService login) {
        this.desafios = desafios;
        this.segundoFactor = segundoFactor;
        this.usuarios = usuarios;
        this.login = login;
    }

    /** {@code POST /auth/login/segundo-factor}: desafio + codigo (o de recuperacion) -> sesion. */
    @Transactional
    public AccesoConSegundoFactorResponse canjear(CanjeDeDesafioRequest datos, String ip, String userAgent) {
        DesafioDeAcceso desafio = desafios.vigente(datos.desafio(), Proposito.VERIFICAR);
        Usuario usuario = titularDe(desafio);
        ComprobacionDelSegundoFactor comprobacion = segundoFactor.comprobarEnElAcceso(usuario, datos.codigo(),
                datos.codigoRecuperacion(), ip);
        desafios.consumir(desafio);
        LoginResponse sesion = login.completarAccesoConSegundoFactor(usuario, ip, userAgent);
        return comprobacion.conRecuperacion()
                ? AccesoConSegundoFactorResponse.conRecuperacion(sesion, comprobacion.restantes())
                : AccesoConSegundoFactorResponse.con(sesion);
    }

    /** {@code POST /auth/login/segundo-factor/enrolamiento}: el secreto, sin sesion todavia. */
    @Transactional
    public EnrolamientoResponse enrolarConDesafio(DesafioRequest datos) {
        DesafioDeAcceso desafio = desafios.vigente(datos.desafio(), Proposito.ENROLAR);
        return segundoFactor.iniciarEnrolamiento(titularDe(desafio));
    }

    /**
     * {@code POST /auth/login/segundo-factor/activacion}: confirma el
     * enrolamiento obligatorio y abre la sesion, con los codigos de
     * recuperacion (una sola vez).
     */
    @Transactional
    public AccesoConSegundoFactorResponse activarConDesafio(ActivacionConDesafioRequest datos, String ip,
                                                            String userAgent) {
        DesafioDeAcceso desafio = desafios.vigente(datos.desafio(), Proposito.ENROLAR);
        Usuario usuario = titularDe(desafio);
        List<String> codigos = segundoFactor.activar(usuario, datos.codigo(), ip);
        desafios.consumir(desafio);
        LoginResponse sesion = login.completarAccesoConSegundoFactor(usuario, ip, userAgent);
        return AccesoConSegundoFactorResponse.recienActivado(sesion, codigos);
    }

    /**
     * La cuenta del desafio, si sigue siendo la misma: una version de token
     * distinta (cambio de contrasena o de rol entre los dos pasos) lo invalida.
     */
    private Usuario titularDe(DesafioDeAcceso desafio) {
        Usuario usuario = usuarios.findById(desafio.getUsuarioId())
                .orElseThrow(SegundoFactorRechazadoException::desafioInvalido);
        if (usuario.getVersionToken() != desafio.getVersionToken()) {
            throw SegundoFactorRechazadoException.desafioInvalido();
        }
        return usuario;
    }
}
