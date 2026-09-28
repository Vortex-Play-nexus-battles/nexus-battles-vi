package com.nexusbattles.ms_identidad.auth.recuperacion;

import com.nexusbattles.ms_identidad.auth.codigos.PreguntasConfiguradas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.repository.UsuarioRepository;
import com.nexusbattles.ms_identidad.auth.service.IntentosFallidosService;
import com.nexusbattles.ms_identidad.auth.recuperacion.ConfigurarPreguntasRequest.NuevaPregunta;
import com.nexusbattles.ms_identidad.auth.recuperacion.RecuperacionRechazadaException.Motivo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Preguntas de seguridad de la propia cuenta (7.1.1: «recuperar su cuenta
 * contestando preguntas con respuestas previamente configuradas y el codigo
 * enviado a su correo»).
 *
 * <p>La PERSONA escribe sus preguntas y sus respuestas; el sistema no trae
 * un catalogo (el documento no lo define y no se inventa). Cantidad entre
 * {@code identidad.recuperacion.preguntas-minimas} y {@code -maximas} (2 y 3,
 * valores PROVISIONALES hasta que decida el PO).
 *
 * <p>Cambiarlas exige la contrasena actual: son el segundo factor de la
 * recuperacion y un token robado no debe bastar para reemplazarlo. Una
 * contrasena incorrecta cuenta como intento fallido de la cuenta
 * (RF-AUT-009), igual que en el cambio de contrasena, y con la cuenta
 * bloqueada ni se compara (423).
 */
@Service
public class PreguntasDeSeguridadService {

    static final int TEXTO_MINIMO = 5;
    static final int TEXTO_MAXIMO = 200;
    static final int RESPUESTA_MINIMA = 2;
    static final int RESPUESTA_MAXIMA = 100;

    private final PreguntaSeguridadRepository preguntas;
    private final UsuarioRepository usuarios;
    private final IntentosFallidosService intentosFallidos;
    private final ApplicationEventPublisher eventos;
    private final PasswordEncoder resumidor;
    private final Clock reloj;
    private final int minimas;
    private final int maximas;

    @Autowired
    public PreguntasDeSeguridadService(PreguntaSeguridadRepository preguntas,
                                       UsuarioRepository usuarios,
                                       IntentosFallidosService intentosFallidos,
                                       ApplicationEventPublisher eventos,
                                       @Value("${identidad.recuperacion.preguntas-minimas:2}") int minimas,
                                       @Value("${identidad.recuperacion.preguntas-maximas:3}") int maximas) {
        this(preguntas, usuarios, intentosFallidos, eventos, minimas, maximas,
                new BCryptPasswordEncoder(), Clock.systemDefaultZone());
    }

    /** Con cifrador y reloj explicitos: pruebas. */
    public PreguntasDeSeguridadService(PreguntaSeguridadRepository preguntas,
                                       UsuarioRepository usuarios,
                                       IntentosFallidosService intentosFallidos,
                                       ApplicationEventPublisher eventos,
                                       int minimas,
                                       int maximas,
                                       PasswordEncoder resumidor,
                                       Clock reloj) {
        if (minimas < 1 || maximas < minimas) {
            throw new IllegalArgumentException("identidad.recuperacion.preguntas-minimas/-maximas incoherentes: "
                    + minimas + ".." + maximas);
        }
        this.preguntas = preguntas;
        this.usuarios = usuarios;
        this.intentosFallidos = intentosFallidos;
        this.eventos = eventos;
        this.minimas = minimas;
        this.maximas = maximas;
        this.resumidor = resumidor;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public PreguntasDeRecuperacion deLaCuenta(Long usuarioId) {
        return PreguntasDeRecuperacion.de(preguntas.findByUsuarioIdOrderByOrdenAsc(usuarioId));
    }

    public boolean tieneConfiguradas(Long usuarioId) {
        return preguntas.countByUsuarioId(usuarioId) > 0;
    }

    /**
     * Reemplaza TODAS las preguntas de la cuenta.
     *
     * @throws RecuperacionRechazadaException 423 cuenta-bloqueada, 422
     *         contrasena-actual-incorrecta o 422 preguntas-invalidas
     */
    @Transactional
    public PreguntasDeRecuperacion configurar(Long usuarioId, ConfigurarPreguntasRequest datos, String ip) {
        // Sin bloquear todavia: el intento fallido se anota en su propia
        // transaccion sobre ESTA fila, y si aqui la tuvieramos bloqueada se
        // quedaria esperandonos a nosotros.
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new IllegalStateException("No existe el usuario " + usuarioId));

        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (usuario.getBloqueadoHasta() != null && ahora.isBefore(usuario.getBloqueadoHasta())) {
            throw new RecuperacionRechazadaException(Motivo.CUENTA_BLOQUEADA,
                    "Cuenta bloqueada temporalmente por intentos fallidos. Intenta de nuevo más tarde.");
        }
        if (!resumidor.matches(datos.passwordActual(), usuario.getPassword())) {
            // Se confirma aparte (REQUIRES_NEW): sobrevive a la excepcion que sigue.
            intentosFallidos.registrarIntentoFallido(usuario.getId());
            throw new RecuperacionRechazadaException(Motivo.ACTUAL_INCORRECTA, "La contraseña actual es incorrecta.");
        }

        List<NuevaPregunta> validas = validar(datos.preguntas());

        // Ahora si: dos reemplazos a la vez no mezclan sus preguntas.
        usuarios.bloquear(usuarioId);
        preguntas.borrarDe(usuarioId);
        List<PreguntaSeguridad> guardadas = new ArrayList<>();
        for (int i = 0; i < validas.size(); i++) {
            NuevaPregunta nueva = validas.get(i);
            guardadas.add(preguntas.save(new PreguntaSeguridad(usuarioId, nueva.texto().strip(),
                    resumidor.encode(NormalizadorDeRespuestas.paraResumir(nueva.respuesta())), i + 1, ahora)));
        }

        String afectado = usuario.getPublicId() != null ? usuario.getPublicId().toString() : "usuario-" + usuarioId;
        eventos.publishEvent(new PreguntasConfiguradas(afectado, guardadas.size(), ip));
        return PreguntasDeRecuperacion.de(guardadas);
    }

    /**
     * Si las respuestas contestan bien TODAS las preguntas configuradas. Cada
     * pregunta se compara con BCrypt contra su resumen; una sin contestar es
     * incorrecta. Sin preguntas configuradas no hay nada que acertar: falso.
     */
    public boolean respuestasCorrectas(Long usuarioId, List<RespuestaDeSeguridad> respuestas) {
        List<PreguntaSeguridad> configuradas = preguntas.findByUsuarioIdOrderByOrdenAsc(usuarioId);
        if (configuradas.isEmpty()) {
            return false;
        }
        Map<UUID, String> porPregunta = new HashMap<>();
        if (respuestas != null) {
            for (RespuestaDeSeguridad respuesta : respuestas) {
                if (respuesta != null && respuesta.preguntaId() != null && respuesta.respuesta() != null) {
                    porPregunta.putIfAbsent(respuesta.preguntaId(), respuesta.respuesta());
                }
            }
        }
        boolean todas = true;
        for (PreguntaSeguridad pregunta : configuradas) {
            String dada = porPregunta.get(pregunta.getId());
            // Se comparan todas aunque una ya haya fallado: el tiempo no dice cual.
            boolean acierta = dada != null && dada.length() <= RESPUESTA_MAXIMA
                    && resumidor.matches(NormalizadorDeRespuestas.paraResumir(dada), pregunta.getRespuestaHash());
            todas &= acierta;
        }
        return todas;
    }

    private List<NuevaPregunta> validar(List<NuevaPregunta> propuestas) {
        if (propuestas == null || propuestas.size() < minimas || propuestas.size() > maximas) {
            throw invalidas(minimas == maximas
                    ? "Configura exactamente " + minimas + " preguntas."
                    : "Configura entre " + minimas + " y " + maximas + " preguntas.");
        }
        Set<String> vistas = new HashSet<>();
        for (NuevaPregunta propuesta : propuestas) {
            if (propuesta == null || propuesta.texto() == null || propuesta.respuesta() == null) {
                throw invalidas("Cada pregunta necesita su texto y su respuesta.");
            }
            String texto = propuesta.texto().strip();
            if (texto.length() < TEXTO_MINIMO || texto.length() > TEXTO_MAXIMO) {
                throw invalidas("Cada pregunta debe tener entre " + TEXTO_MINIMO + " y " + TEXTO_MAXIMO + " caracteres.");
            }
            String respuesta = NormalizadorDeRespuestas.normalizar(propuesta.respuesta());
            if (respuesta.length() < RESPUESTA_MINIMA || propuesta.respuesta().length() > RESPUESTA_MAXIMA) {
                throw invalidas("Cada respuesta debe tener entre " + RESPUESTA_MINIMA + " y " + RESPUESTA_MAXIMA
                        + " caracteres.");
            }
            if (!vistas.add(NormalizadorDeRespuestas.normalizar(texto))) {
                throw invalidas("No repitas preguntas: cada una debe ser distinta.");
            }
        }
        return propuestas;
    }

    private static RecuperacionRechazadaException invalidas(String detalle) {
        return new RecuperacionRechazadaException(Motivo.PREGUNTAS_INVALIDAS, detalle);
    }
}
