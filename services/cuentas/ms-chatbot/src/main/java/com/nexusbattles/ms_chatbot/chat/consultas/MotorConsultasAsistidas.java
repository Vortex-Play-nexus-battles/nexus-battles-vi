package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.AvisoDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ElementoInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

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
        "my tournament", "my tournaments"
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
    private static final String MENSAJE_TORNEOS_EN_CONSTRUCCION =
        "La consulta del estado de tus torneos todavia esta en construccion. "
            + "Pronto podras preguntarme por tu progreso en torneos directamente aqui.";
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
    private static final String MENSAJE_NAVEGACION_MISIONES_EN_CONSTRUCCION =
        "La seccion de misiones todavia esta en construccion. Pronto podras navegar directamente a ella desde aqui.";
    private static final String MENSAJE_NAVEGACION_TORNEOS_EN_CONSTRUCCION =
        "La seccion de torneos todavia esta en construccion. Pronto podras navegar directamente a ella desde aqui.";

    private final InventarioClient inventarioClient;
    private final SubastasClient subastasClient;
    private final NotificacionesClient notificacionesClient;

    public MotorConsultasAsistidas(InventarioClient inventarioClient, SubastasClient subastasClient,
                                   NotificacionesClient notificacionesClient) {
        this.inventarioClient = inventarioClient;
        this.subastasClient = subastasClient;
        this.notificacionesClient = notificacionesClient;
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
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_MISIONES_EN_CONSTRUCCION, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_NAVEGACION_TORNEOS)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_NAVEGACION_TORNEOS_EN_CONSTRUCCION, null, TipoRespuesta.DIRECTA));
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
        if (contieneAlguna(mensajeNormalizado, PALABRAS_MISIONES)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_MISIONES_EN_CONSTRUCCION, null, TipoRespuesta.DIRECTA));
        }
        if (contieneAlguna(mensajeNormalizado, PALABRAS_TORNEOS)) {
            return Optional.of(ResultadoMotor.deTema(MENSAJE_TORNEOS_EN_CONSTRUCCION, null, TipoRespuesta.DIRECTA));
        }

        return Optional.empty();
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
            + "Notificaciones: " + obtenerTextoNotificacionesOFallo(tokenBearer, uid);

        return ResultadoMotor.deTema(texto, null, TipoRespuesta.CONTEXTUAL);
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
