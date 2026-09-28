package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.AvisoDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ElementoInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoResumenDto;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// HU-CHA-008: consultas y acciones asistidas para usuarios autenticados.
// Este motor es INDEPENDIENTE de MotorRespuestas (HU-CHA-004): mientras ese
// busca en una base de conocimiento estatica (temas_conocimiento), este
// consulta datos EN VIVO (inventario, subastas, notificaciones), resuelve
// navegacion asistida hacia secciones del sitio, arma un informe de
// actividad combinando las consultas en vivo, y solo aplica cuando hay un
// usuario autenticado. ChatService lo intenta primero; si no encuentra una
// intencion de este tipo, sigue el flujo normal con MotorRespuestas.
//
// Deteccion de intencion: por coincidencia de frase (no por puntaje con
// tolerancia a errores como en MotorRespuestas). Son intenciones fijas del
// sistema, con frases multi-palabra bastante especificas, asi que una
// coincidencia simple es suficiente y evita el riesgo de falsos positivos
// que si tuvimos que resolver en MotorRespuestas.
//
// IMPORTANTE sobre el orden de deteccion: las frases de navegacion (ej.
// "llevame a mi inventario") contienen la misma palabra de dominio que las
// frases de consulta (ej. "inventario"), asi que la navegacion se revisa
// SIEMPRE antes que la consulta correspondiente; de lo contrario la consulta
// generica atraparia el mensaje antes de llegar a la deteccion de
// navegacion.
@Service
public class MotorConsultasAsistidas {

    private static final List<String> PALABRAS_INVENTARIO = List.of(
        "inventario", "mis armas", "mis items", "mis objetos", "mi equipo", "mi equipamiento",
        "my inventory", "my items", "my gear", "my equipment"
    );
    private static final List<String> PALABRAS_SUBASTAS = List.of(
        "mis subastas", "mis pujas", "mi puja", "voy ganando", "estoy ganando",
        "my auctions", "my bids", "am i winning"
    );
    private static final List<String> PALABRAS_NOTIFICACIONES = List.of(
        "mis notificaciones", "mis avisos", "tengo notificaciones", "tengo avisos",
        "my notifications", "my alerts"
    );
    private static final List<String> PALABRAS_MISIONES = List.of(
        "mis misiones", "mi mision", "mi progreso en misiones", "estado de mis misiones",
        "my missions", "my quests", "my mission progress"
    );
    private static final List<String> PALABRAS_TORNEOS = List.of(
        "mi torneo", "mis torneos", "estado de mi torneo", "en que torneo estoy",
        "torneo en curso", "torneos en curso", "hay torneos", "hay algun torneo",
        "my tournament", "my tournaments", "current tournament"
    );
    // B11 — 7.4.4 «historial de transacciones reciente».
    private static final List<String> PALABRAS_MOVIMIENTOS = List.of(
        "mis movimientos", "mis transacciones", "historial de transacciones", "historial de creditos",
        "movimientos de creditos", "ultimos movimientos",
        "my transactions", "my credit history", "my movements"
    );

    private static final List<String> PALABRAS_NAVEGACION_INVENTARIO = List.of(
        "llevame a mi inventario", "ir a mi inventario", "quiero ir a mi inventario",
        "como llego a mi inventario", "abre mi inventario", "abrir mi inventario",
        "muestrame la seccion de inventario", "ir a la seccion de inventario",
        "take me to my inventory", "go to my inventory", "open my inventory"
    );
    private static final List<String> PALABRAS_NAVEGACION_SUBASTAS = List.of(
        "llevame a mis subastas", "ir a mis subastas", "quiero ir a mis subastas",
        "como llego a mis subastas", "abre mis subastas", "abrir mis subastas",
        "llevame a mis pujas", "ir a mis pujas", "muestrame la seccion de subastas",
        "take me to my auctions", "go to my auctions", "open my bids"
    );
    private static final List<String> PALABRAS_NAVEGACION_NOTIFICACIONES = List.of(
        "llevame a mis notificaciones", "ir a mis notificaciones", "quiero ir a mis notificaciones",
        "como llego a mis notificaciones", "abre mis notificaciones", "abrir mis notificaciones",
        "muestrame la seccion de notificaciones",
        "take me to my notifications", "go to my notifications", "open my notifications"
    );
    private static final List<String> PALABRAS_NAVEGACION_PERFIL = List.of(
        "llevame a mi perfil", "ir a mi perfil", "quiero ir a mi perfil",
        "como llego a mi perfil", "abre mi perfil", "abrir mi perfil",
        "muestrame mi perfil", "ver mi perfil",
        "take me to my profile", "go to my profile", "open my profile"
    );
    private static final List<String> PALABRAS_NAVEGACION_MISIONES = List.of(
        "llevame a mis misiones", "ir a mis misiones", "abre mis misiones", "abrir mis misiones",
        "take me to my missions", "go to my missions"
    );
    private static final List<String> PALABRAS_NAVEGACION_TORNEOS = List.of(
        "llevame a mi torneo", "llevame a mis torneos", "ir a mis torneos",
        "abre mis torneos", "abrir mis torneos",
        "take me to my tournament", "go to my tournaments"
    );

    private static final List<String> PALABRAS_INFORME_ACTIVIDAD = List.of(
        "informe de actividad", "informe de mi actividad", "reporte de actividad", "reporte de mi actividad",
        "resumen de mi actividad", "resumen de actividad general",
        "activity report", "my activity report", "summary of my activity"
    );

    private static final String MENSAJE_MISIONES_EN_CONSTRUCCION =
        "La consulta de tu progreso en misiones todavia esta en construccion. "
            + "Pronto podras preguntarme por el estado de tus misiones directamente aqui.";
    private static final String MENSAJE_SERVICIO_NO_DISPONIBLE_TORNEOS =
        "No pude consultar los torneos en este momento porque el servicio no esta disponible. Intenta de nuevo en unos minutos.";
    private static final String MENSAJE_SERVICIO_NO_DISPONIBLE_MOVIMIENTOS =
        "No pude consultar tus movimientos de creditos en este momento porque el servicio no esta disponible. Intenta de nuevo en unos minutos.";
    private static final String MENSAJE_SERVICIO_NO_DISPONIBLE_INVENTARIO =
        "No pude consultar tu inventario en este momento porque el servicio no esta disponible. Intenta de nuevo en unos minutos.";
    private static final String MENSAJE_SERVICIO_NO_DISPONIBLE_SUBASTAS =
        "No pude consultar tus subastas en este momento porque el servicio no esta disponible. Intenta de nuevo en unos minutos.";
    private static final String MENSAJE_SERVICIO_NO_DISPONIBLE_NOTIFICACIONES =
        "No pude consultar tus notificaciones en este momento porque el servicio no esta disponible. Intenta de nuevo en unos minutos.";

    private static final String MENSAJE_NAVEGACION_INVENTARIO =
        "Puedes revisar tu inventario en la seccion de Inventario del menu principal.";
    private static final String MENSAJE_NAVEGACION_SUBASTAS =
        "Puedes revisar tus subastas y pujas en la seccion de Subastas del menu principal.";
    private static final String MENSAJE_NAVEGACION_NOTIFICACIONES =
        "Puedes revisar tus notificaciones en la seccion de Notificaciones del menu principal.";
    private static final String MENSAJE_NAVEGACION_PERFIL =
        "Puedes revisar y editar tu perfil en la seccion de Mi Perfil del menu principal.";
    // B11: las dos secciones ya existen en el menu principal (shell.js:
    // «Misiones» y «Torneo»); la navegacion asistida lleva a ellas.
    private static final String MENSAJE_NAVEGACION_MISIONES =
        "Puedes ver tus misiones en la seccion Misiones del menu principal.";
    private static final String MENSAJE_NAVEGACION_TORNEOS =
        "Puedes ver los torneos, inscribir a tu equipo y seguir el arbol en la seccion Torneo del menu principal.";

    // Cuantos torneos recientes se revisan buscando el equipo del jugador.
    private static final int TORNEOS_A_REVISAR = 3;
    private static final int MOVIMIENTOS_A_MOSTRAR = 5;

    private final InventarioClient inventarioClient;
    private final SubastasClient subastasClient;
    private final NotificacionesClient notificacionesClient;
    private final TorneosClient torneosClient;
    private final FinanzasClient finanzasClient;

    public MotorConsultasAsistidas(InventarioClient inventarioClient, SubastasClient subastasClient,
                                   NotificacionesClient notificacionesClient, TorneosClient torneosClient,
                                   FinanzasClient finanzasClient) {
        this.inventarioClient = inventarioClient;
        this.subastasClient = subastasClient;
        this.notificacionesClient = notificacionesClient;
        this.torneosClient = torneosClient;
        this.finanzasClient = finanzasClient;
    }

    public Optional<ResultadoMotor> generarRespuesta(String mensajeUsuario, String tokenBearer, String uid) {
        String mensajeNormalizado = NormalizadorTexto.normalizar(mensajeUsuario);

        if (contieneAlguna(mensajeNormalizado, PALABRAS_NAVEGACION_INVENTARIO)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_INVENTARIO, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_NAVEGACION_SUBASTAS)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_SUBASTAS, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_NAVEGACION_NOTIFICACIONES)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_NOTIFICACIONES, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_NAVEGACION_PERFIL)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_PERFIL, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_NAVEGACION_MISIONES)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_MISIONES, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_NAVEGACION_TORNEOS)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_TORNEOS, null, TipoRespuesta.DIRECTA));
        }

        if (contieneAlguna(mensajeNormalizado, PALABRAS_INFORME_ACTIVIDAD)) {
            return Optional.of(generarInformeActividad(tokenBearer, uid));
        }

        if (contieneAlguna(mensajeNormalizado, PALABRAS_INVENTARIO)) {
            return Optional.of(consultarInventario(tokenBearer));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_SUBASTAS)) {
            return Optional.of(consultarSubastas(tokenBearer));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_NOTIFICACIONES)) {
            return Optional.of(consultarNotificaciones(tokenBearer, uid));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_MOVIMIENTOS)) {
            return Optional.of(consultarMovimientos(tokenBearer, uid));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_MISIONES)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_MISIONES_EN_CONSTRUCCION, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_TORNEOS)) {
            return Optional.of(consultarTorneos(uid));
        }

        return Optional.empty();
    }

    // B11 — torneos en curso y el equipo del jugador. Solo datos publicos
    // (GET /torneos, sin credencial): el uid del token sirve para encontrar su
    // equipo en lo que cualquiera puede ver, no para pedir nada en su nombre.
    private ResultadoMotor consultarTorneos(String uid) {
        try {
            return ResultadoMotor.deTema(construirTextoTorneos(uid), null, TipoRespuesta.CONTEXTUAL);
        } catch (RestClientException excepcion) {
            return ResultadoMotor.deTema(MENSAJE_SERVICIO_NO_DISPONIBLE_TORNEOS, null, TipoRespuesta.DIRECTA);
        }
    }

    private String construirTextoTorneos(String uid) {
        List<TorneoResumenDto> activos = torneosClient.listar().stream()
            .filter(t -> "INSCRIPCIONES_ABIERTAS".equals(t.estado()) || "EN_CURSO".equals(t.estado()))
            .limit(TORNEOS_A_REVISAR)
            .toList();
        if (activos.isEmpty()) {
            return "Ahora mismo no hay ningun torneo con inscripciones abiertas ni en curso.";
        }
        UUID jugador = uidComoUuid(uid);
        for (TorneoResumenDto resumen : activos) {
            TorneoDetalleDto torneo = torneosClient.obtener(resumen.id());
            if (torneo == null || torneo.equipos() == null) {
                continue;
            }
            Optional<TorneoDetalleDto.Equipo> suyo = torneo.equipos().stream()
                .filter(e -> e.integrantes() != null && jugador != null && e.integrantes().contains(jugador))
                .findFirst();
            if (suyo.isPresent()) {
                return textoDelEquipo(torneo, suyo.get());
            }
        }
        TorneoResumenDto primero = activos.get(0);
        return "No estas en ningun torneo activo. El torneo «" + primero.nombre() + "» "
            + ("EN_CURSO".equals(primero.estado())
                ? "ya esta en curso."
                : "tiene inscripciones abiertas (" + primero.equiposInscritos() + " de " + primero.cupos()
                    + " equipos, inscripcion de " + primero.costoInscripcion() + " creditos).")
            + " " + MENSAJE_NAVEGACION_TORNEOS;
    }

    private static String textoDelEquipo(TorneoDetalleDto torneo, TorneoDetalleDto.Equipo equipo) {
        StringBuilder texto = new StringBuilder("Estas en el torneo «").append(torneo.nombre())
            .append("» con tu equipo «").append(equipo.nombre()).append("».");
        if ("INSCRIPCIONES_ABIERTAS".equals(torneo.estado())) {
            texto.append(equipo.inscrito()
                ? " Tu equipo ya esta inscrito, en la posicion " + equipo.posicion() + "."
                : " Tu equipo esta registrado pero todavia no se ha inscrito: la inscripcion se paga en la seccion Torneo.");
            return texto.toString();
        }
        if (equipo.eliminado()) {
            return texto.append(" Tu equipo ya quedo eliminado.").toString();
        }
        List<TorneoDetalleDto.Encuentro> encuentros = torneo.encuentros() == null ? List.of() : torneo.encuentros();
        encuentros.stream()
            .filter(e -> "LISTO".equals(e.estado()) && (equipo.id().equals(e.equipoA()) || equipo.id().equals(e.equipoB())))
            .findFirst()
            .ifPresentOrElse(
                e -> texto.append(" Tu proximo encuentro es el ").append(e.numero()).append(" y ya esta listo para jugarse."),
                () -> texto.append(" Tu equipo sigue en carrera; tu proximo encuentro espera a que se jueguen los anteriores."));
        return texto.toString();
    }

    // B11 — ultimos movimientos de creditos, con el token del propio jugador.
    private ResultadoMotor consultarMovimientos(String tokenBearer, String uid) {
        try {
            return ResultadoMotor.deTema(construirTextoMovimientos(tokenBearer, uid), null, TipoRespuesta.CONTEXTUAL);
        } catch (RestClientException excepcion) {
            return ResultadoMotor.deTema(MENSAJE_SERVICIO_NO_DISPONIBLE_MOVIMIENTOS, null, TipoRespuesta.DIRECTA);
        }
    }

    private String construirTextoMovimientos(String tokenBearer, String uid) {
        PaginaMovimientosDto pagina = finanzasClient.movimientos(tokenBearer, uid, MOVIMIENTOS_A_MOSTRAR);
        List<PaginaMovimientosDto.Movimiento> movimientos = pagina == null || pagina.content() == null
            ? List.of() : pagina.content();
        if (movimientos.isEmpty()) {
            return "Todavia no tienes movimientos de creditos.";
        }
        String lista = movimientos.stream()
            .limit(MOVIMIENTOS_A_MOSTRAR)
            .map(MotorConsultasAsistidas::textoDelMovimiento)
            .reduce((a, b) -> a + "; " + b)
            .orElse("");
        return "Tus ultimos movimientos de creditos: " + lista + ".";
    }

    private static String textoDelMovimiento(PaginaMovimientosDto.Movimiento movimiento) {
        String monto = movimiento.monto() == null ? "?" : movimiento.monto().stripTrailingZeros().toPlainString();
        String cifra = switch (movimiento.signo() == null ? "" : movimiento.signo()) {
            case "SUMA" -> "+" + monto;
            case "RESTA" -> "-" + monto;
            case "APARTA" -> monto + " apartados";
            default -> monto + " sin mover saldo";
        };
        return cifra + (movimiento.concepto() == null ? "" : " (" + movimiento.concepto() + ")");
    }

    private static UUID uidComoUuid(String uid) {
        try {
            return uid == null ? null : UUID.fromString(uid);
        } catch (IllegalArgumentException noEsUuid) {
            return null;
        }
    }

    private ResultadoMotor consultarInventario(String tokenBearer) {
        try {
            PaginaInventarioDto pagina = inventarioClient.consultarInventario(tokenBearer, 0);
            return ResultadoMotor.deTema(construirTextoInventario(pagina), null, TipoRespuesta.CONTEXTUAL);
        } catch (RestClientException excepcion) {
            return ResultadoMotor.deTema(MENSAJE_SERVICIO_NO_DISPONIBLE_INVENTARIO, null, TipoRespuesta.DIRECTA);
        }
    }

    private ResultadoMotor consultarSubastas(String tokenBearer) {
        try {
            MiResumenDto resumen = subastasClient.consultarMiResumen(tokenBearer);
            return ResultadoMotor.deTema(construirTextoSubastas(resumen), null, TipoRespuesta.CONTEXTUAL);
        } catch (RestClientException excepcion) {
            return ResultadoMotor.deTema(MENSAJE_SERVICIO_NO_DISPONIBLE_SUBASTAS, null, TipoRespuesta.DIRECTA);
        }
    }

    private ResultadoMotor consultarNotificaciones(String tokenBearer, String uid) {
        try {
            BandejaResponseDto bandeja = notificacionesClient.consultarBandeja(tokenBearer, uid);
            return ResultadoMotor.deTema(construirTextoNotificaciones(bandeja), null, TipoRespuesta.CONTEXTUAL);
        } catch (RestClientException excepcion) {
            return ResultadoMotor.deTema(MENSAJE_SERVICIO_NO_DISPONIBLE_NOTIFICACIONES, null, TipoRespuesta.DIRECTA);
        }
    }

    // Informe de actividad: compone en un solo mensaje las 3 consultas en
    // vivo que ya existian por separado. Si alguna falla, se muestra el
    // aviso de "no disponible" solo para esa parte y se sigue con las demas,
    // en vez de fallar el informe completo por un solo servicio caido.
    private ResultadoMotor generarInformeActividad(String tokenBearer, String uid) {
        String texto = "Este es un resumen de tu actividad reciente:" + System.lineSeparator() + System.lineSeparator()
            + "Inventario: " + obtenerTextoInventarioOFallo(tokenBearer) + System.lineSeparator() + System.lineSeparator()
            + "Subastas: " + obtenerTextoSubastasOFallo(tokenBearer) + System.lineSeparator() + System.lineSeparator()
            + "Notificaciones: " + obtenerTextoNotificacionesOFallo(tokenBearer, uid) + System.lineSeparator() + System.lineSeparator()
            + "Creditos: " + obtenerTextoMovimientosOFallo(tokenBearer, uid);

        return ResultadoMotor.deTema(texto, null, TipoRespuesta.CONTEXTUAL);
    }

    private String obtenerTextoMovimientosOFallo(String tokenBearer, String uid) {
        try {
            return construirTextoMovimientos(tokenBearer, uid);
        } catch (RestClientException excepcion) {
            return MENSAJE_SERVICIO_NO_DISPONIBLE_MOVIMIENTOS;
        }
    }

    private String obtenerTextoInventarioOFallo(String tokenBearer) {
        try {
            return construirTextoInventario(inventarioClient.consultarInventario(tokenBearer, 0));
        } catch (RestClientException excepcion) {
            return MENSAJE_SERVICIO_NO_DISPONIBLE_INVENTARIO;
        }
    }

    private String obtenerTextoSubastasOFallo(String tokenBearer) {
        try {
            return construirTextoSubastas(subastasClient.consultarMiResumen(tokenBearer));
        } catch (RestClientException excepcion) {
            return MENSAJE_SERVICIO_NO_DISPONIBLE_SUBASTAS;
        }
    }

    private String obtenerTextoNotificacionesOFallo(String tokenBearer, String uid) {
        try {
            return construirTextoNotificaciones(notificacionesClient.consultarBandeja(tokenBearer, uid));
        } catch (RestClientException excepcion) {
            return MENSAJE_SERVICIO_NO_DISPONIBLE_NOTIFICACIONES;
        }
    }

    private String construirTextoInventario(PaginaInventarioDto pagina) {
        if (pagina.totalElementos() == 0) {
            return "Tu inventario esta vacio por ahora.";
        }
        List<ElementoInventarioDto> elementos = pagina.elementos() == null ? List.of() : pagina.elementos();
        String nombres = elementos.stream()
            .limit(5)
            .map(ElementoInventarioDto::nombrePropio)
            .filter(nombre -> nombre != null && !nombre.isBlank())
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

        StringBuilder texto = new StringBuilder("Tienes ").append(pagina.totalElementos()).append(" elemento(s) en tu inventario.");
        if (!nombres.isBlank()) {
            texto.append(" Algunos de ellos: ").append(nombres).append(".");
        }
        return texto.toString();
    }

    private String construirTextoSubastas(MiResumenDto resumen) {
        StringBuilder texto = new StringBuilder();
        texto.append("Vas ganando ").append(resumen.subastasGanando()).append(" subasta(s) en este momento, ");
        texto.append("con ").append(resumen.creditosRetenidos()).append(" creditos retenidos en pujas activas.");
        if (resumen.saldoDisponible() != null) {
            texto.append(" Tu saldo disponible es de ").append(resumen.saldoDisponible()).append(" creditos.");
        }
        return texto.toString();
    }

    private String construirTextoNotificaciones(BandejaResponseDto bandeja) {
        if (bandeja.noLeidas() == 0) {
            return "No tienes notificaciones sin leer.";
        }
        StringBuilder texto = new StringBuilder("Tienes ").append(bandeja.noLeidas()).append(" notificacion(es) sin leer.");
        List<AvisoDto> noLeidos = bandeja.avisos() == null ? List.of() : bandeja.avisos().stream()
            .filter(aviso -> Boolean.FALSE.equals(aviso.leida()))
            .limit(3)
            .toList();
        if (!noLeidos.isEmpty()) {
            String titulos = noLeidos.stream()
                .map(AvisoDto::titulo)
                .filter(titulo -> titulo != null && !titulo.isBlank())
                .reduce((a, b) -> a + " | " + b)
                .orElse("");
            if (!titulos.isBlank()) {
                texto.append(" Las mas recientes: ").append(titulos).append(".");
            }
        }
        return texto.toString();
    }

    private boolean contieneAlguna(String mensajeNormalizado, List<String> frases) {
        for (String frase : frases) {
            if (mensajeNormalizado.contains(NormalizadorTexto.normalizar(frase))) {
                return true;
            }
        }
        return false;
    }
}
