package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.AvisoDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.BandejaResponseDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.ElementoInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MiResumenDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.MisionActivaDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaInventarioDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaMovimientosDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoDetalleDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.TorneoResumenDto;
import com.nexusbattles.ms_chatbot.chat.enriquecido.Enriquecedor;
import com.nexusbattles.ms_chatbot.chat.enriquecido.TarjetaInformativa;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.texto.DistanciaDeEdicion;
import com.nexusbattles.ms_chatbot.chat.texto.IdiomaDelTexto;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// HU-CHA-008: consultas y acciones asistidas para usuarios autenticados.
// Este motor es INDEPENDIENTE de MotorRespuestas (HU-CHA-004): mientras ese
// busca en una base de conocimiento estatica (temas_conocimiento), este
// consulta datos EN VIVO (inventario, subastas, notificaciones, creditos,
// torneos y misiones), resuelve navegacion asistida hacia secciones del sitio,
// busca en el inventario del jugador, arma un informe de actividad y solo
// aplica cuando hay un usuario autenticado. ChatService lo intenta primero;
// si no encuentra una intencion de este tipo, sigue con MotorRespuestas.
//
// Siempre con el token del PROPIO jugador (el dueno lo deduce cada servicio
// del claim uid) y solo para leer: el documento no habilita al chatbot para
// pujar, comprar ni cambiar nada (observacion de RF-CHA-009).
//
// Revision final (ms-chatbot.yaml 1.5.0):
//   * responde en el idioma de la pregunta (o el de la preferencia), con las
//     secciones nombradas como en la barra;
//   * tolera un error de tecleo por palabra en las frases (DistanciaDeEdicion);
//   * «busca X en mi inventario» usa la busqueda real del inventario (7.4.4,
//     «buscar productos en el inventario»);
//   * el informe de actividad pide sus cinco partes a la vez y espera como
//     mucho PLAZO_DEL_INFORME: la que no llega se dice «no disponible» (7.4.11,
//     menos de 2 s).
//
// IMPORTANTE sobre el orden de deteccion: las frases de navegacion (ej.
// "llevame a mi inventario") contienen la misma palabra de dominio que las
// frases de consulta (ej. "inventario"), asi que la navegacion se revisa
// SIEMPRE antes que la consulta correspondiente; y la busqueda antes que la
// consulta general del inventario.
@Service
public class MotorConsultasAsistidas {

    private static final List<String> PALABRAS_SUBASTAS = List.of(
        "mis subastas", "mis pujas", "mi puja", "voy ganando", "estoy ganando",
        "my auctions", "my bids", "am i winning"
    );
    private static final List<String> PALABRAS_NOTIFICACIONES = List.of(
        "mis notificaciones", "mis avisos", "tengo notificaciones", "tengo avisos",
        "my notifications", "my alerts", "any notifications"
    );
    private static final List<String> PALABRAS_MISIONES = List.of(
        "mis misiones", "mi mision", "mi progreso en misiones", "estado de mis misiones",
        "my missions", "my quests", "my mission progress"
    );
    private static final List<String> PALABRAS_TORNEOS = List.of(
        "mi torneo", "mis torneos", "estado de mi torneo", "en que torneo estoy",
        "torneo en curso", "torneos en curso", "hay torneos", "hay algun torneo",
        "my tournament", "my tournaments", "current tournament", "tournaments in progress"
    );
    // B11 — 7.4.4 «historial de transacciones reciente».
    private static final List<String> PALABRAS_MOVIMIENTOS = List.of(
        "mis movimientos", "mis transacciones", "historial de transacciones", "historial de creditos",
        "movimientos de creditos", "ultimos movimientos",
        "my transactions", "my credit history", "my movements", "my credits history"
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
        "llevame a la subasta", "ir a la subasta",
        "take me to my auctions", "go to my auctions", "open my bids", "take me to the auction"
    );
    private static final List<String> PALABRAS_NAVEGACION_NOTIFICACIONES = List.of(
        "llevame a mis notificaciones", "ir a mis notificaciones", "quiero ir a mis notificaciones",
        "como llego a mis notificaciones", "abre mis notificaciones", "abrir mis notificaciones",
        "muestrame la seccion de notificaciones", "donde estan mis notificaciones",
        "take me to my notifications", "go to my notifications", "open my notifications"
    );
    private static final List<String> PALABRAS_NAVEGACION_PERFIL = List.of(
        "llevame a mi perfil", "ir a mi perfil", "quiero ir a mi perfil",
        "como llego a mi perfil", "abre mi perfil", "abrir mi perfil",
        "muestrame mi perfil", "ver mi perfil", "llevame a mi cuenta", "ir a mi cuenta", "abre mi cuenta",
        "configuracion de mi cuenta", "ajustes de mi cuenta",
        "take me to my profile", "go to my profile", "open my profile", "my account settings"
    );
    private static final List<String> PALABRAS_NAVEGACION_MISIONES = List.of(
        "llevame a mis misiones", "ir a mis misiones", "abre mis misiones", "abrir mis misiones",
        "take me to my missions", "go to my missions"
    );
    private static final List<String> PALABRAS_NAVEGACION_TORNEOS = List.of(
        "llevame a mi torneo", "llevame a mis torneos", "ir a mis torneos",
        "abre mis torneos", "abrir mis torneos", "llevame al torneo", "ir al torneo",
        "take me to my tournament", "go to my tournaments", "take me to the tournament"
    );

    private static final List<String> PALABRAS_INFORME_ACTIVIDAD = List.of(
        "informe de actividad", "informe de mi actividad", "reporte de actividad", "reporte de mi actividad",
        "resumen de mi actividad", "resumen de actividad general",
        "activity report", "my activity report", "summary of my activity"
    );

    // 1.5.0 — «busca X en mi inventario» / «search my inventory for X». Sobre el
    // texto normalizado (sin tildes ni signos), con «inventario» ya corregido.
    private static final String ARTICULO = "(?:(?:el|la|los|las|un|una|unos|unas|mi|mis|algun|alguna|a|an|the|any|my) )?";
    private static final List<Pattern> BUSQUEDAS = List.of(
        Pattern.compile("(?:busca|buscar|buscame|encuentra|encontrar|encuentrame) " + ARTICULO
            + "(.+?) en mi inventario$"),
        Pattern.compile("(?:busca|buscar|buscame|encuentra|encontrar) en mi inventario " + ARTICULO + "(.+)$"),
        Pattern.compile("tengo " + ARTICULO + "(.+?) en mi inventario$"),
        Pattern.compile("(?:search|look|find) (?:in )?my inventory for " + ARTICULO + "(.+)$"),
        Pattern.compile("(?:search|look) for " + ARTICULO + "(.+?) in my inventory$"),
        Pattern.compile("find " + ARTICULO + "(.+?) in my inventory$"),
        Pattern.compile("do i have " + ARTICULO + "(.+?) in my inventory$")
    );
    private static final List<String> PALABRAS_INVENTARIO = List.of("inventario", "inventory");
    static final int LARGO_MINIMO_DE_BUSQUEDA = 4;

    // Cuantos torneos recientes se revisan buscando el equipo del jugador.
    private static final int TORNEOS_A_REVISAR = 3;
    private static final int MOVIMIENTOS_A_MOSTRAR = 5;
    private static final int MISIONES_A_MOSTRAR = 3;
    private static final int ELEMENTOS_A_NOMBRAR = 5;

    // 7.4.11 «latencia < 2 segundos»: lo que el informe de actividad espera, en
    // total, a sus cinco consultas (que corren a la vez).
    static final Duration PLAZO_DEL_INFORME = Duration.ofMillis(1700);

    private final InventarioClient inventarioClient;
    private final SubastasClient subastasClient;
    private final NotificacionesClient notificacionesClient;
    private final TorneosClient torneosClient;
    private final FinanzasClient finanzasClient;
    private final MisionesClient misionesClient;

    public MotorConsultasAsistidas(InventarioClient inventarioClient, SubastasClient subastasClient,
                                   NotificacionesClient notificacionesClient, TorneosClient torneosClient,
                                   FinanzasClient finanzasClient, MisionesClient misionesClient) {
        this.inventarioClient = inventarioClient;
        this.subastasClient = subastasClient;
        this.notificacionesClient = notificacionesClient;
        this.torneosClient = torneosClient;
        this.finanzasClient = finanzasClient;
        this.misionesClient = misionesClient;
    }

    public Optional<ResultadoMotor> generarRespuesta(String mensajeUsuario, String tokenBearer, String uid) {
        return generarRespuesta(mensajeUsuario, tokenBearer, uid, IdiomaPreferido.AUTOMATICO);
    }

    /**
     * 1.5.0: con el idioma que prefiere el jugador (AUTOMÁTICO: el de su
     * pregunta).
     */
    public Optional<ResultadoMotor> generarRespuesta(String mensajeUsuario, String tokenBearer, String uid,
                                                     IdiomaPreferido idioma) {
        String mensaje = corregirPalabras(NormalizadorTexto.normalizar(mensajeUsuario), PALABRAS_INVENTARIO);
        TextosDeConsultas t = TextosDeConsultas.en(enIngles(mensaje, idioma));

        Optional<ResultadoMotor> navegacion = navegacion(mensaje, t);
        if (navegacion.isPresent()) {
            return navegacion;
        }
        if (contieneAlguna(mensaje, PALABRAS_INFORME_ACTIVIDAD)) {
            return Optional.of(generarInformeActividad(tokenBearer, uid, t));
        }
        Optional<String> criterio = criterioDeBusqueda(mensaje);
        if (criterio.isPresent()) {
            return Optional.of(buscarEnInventario(tokenBearer, criterio.get(), t));
        }
        // El inventario no va por frase: IntencionDeConsulta separa "que tengo
        // en mi inventario" (datos en vivo) de "dame consejos para organizar mi
        // inventario" (base de conocimiento).
        if (IntencionDeConsulta.consultaSuInventario(mensaje)) {
            return Optional.of(consultarInventario(tokenBearer, t));
        }
        if (contieneAlguna(mensaje, PALABRAS_SUBASTAS)) {
            return Optional.of(consultarSubastas(tokenBearer, t));
        }
        if (contieneAlguna(mensaje, PALABRAS_NOTIFICACIONES)) {
            return Optional.of(consultarNotificaciones(tokenBearer, uid, t));
        }
        if (contieneAlguna(mensaje, PALABRAS_MOVIMIENTOS)) {
            return Optional.of(consultarMovimientos(tokenBearer, uid, t));
        }
        if (contieneAlguna(mensaje, PALABRAS_MISIONES)) {
            return Optional.of(consultarMisiones(tokenBearer, t));
        }
        if (contieneAlguna(mensaje, PALABRAS_TORNEOS)) {
            return Optional.of(consultarTorneos(uid, t));
        }
        return Optional.empty();
    }

    private static boolean enIngles(String mensaje, IdiomaPreferido idioma) {
        return switch (idioma == null ? IdiomaPreferido.AUTOMATICO : idioma) {
            case ES -> false;
            case EN -> true;
            case AUTOMATICO -> IdiomaDelTexto.esIngles(mensaje);
        };
    }

    private Optional<ResultadoMotor> navegacion(String mensaje, TextosDeConsultas t) {
        if (contieneAlguna(mensaje, PALABRAS_NAVEGACION_INVENTARIO)) {
            return Optional.of(navegar(t.navegarInventario(), "inventario", t));
        }
        if (contieneAlguna(mensaje, PALABRAS_NAVEGACION_SUBASTAS)) {
            return Optional.of(navegar(t.navegarSubastas(), "subastas", t));
        }
        if (contieneAlguna(mensaje, PALABRAS_NAVEGACION_NOTIFICACIONES)) {
            return Optional.of(navegar(t.navegarNotificaciones(), "notificaciones", t));
        }
        if (contieneAlguna(mensaje, PALABRAS_NAVEGACION_PERFIL)) {
            return Optional.of(navegar(t.navegarPerfil(), "perfil", t));
        }
        if (contieneAlguna(mensaje, PALABRAS_NAVEGACION_MISIONES)) {
            return Optional.of(navegar(t.navegarMisiones(), "misiones", t));
        }
        if (contieneAlguna(mensaje, PALABRAS_NAVEGACION_TORNEOS)) {
            return Optional.of(navegar(t.navegarTorneos(), "torneos", t));
        }
        return Optional.empty();
    }

    // ---- Busqueda en el inventario (7.4.4) ----------------------------------

    static Optional<String> criterioDeBusqueda(String mensajeNormalizado) {
        for (Pattern forma : BUSQUEDAS) {
            Matcher coincidencia = forma.matcher(mensajeNormalizado);
            if (coincidencia.find()) {
                return Optional.of(coincidencia.group(1).strip());
            }
        }
        return Optional.empty();
    }

    private ResultadoMotor buscarEnInventario(String tokenBearer, String criterio, TextosDeConsultas t) {
        if (criterio.length() < LARGO_MINIMO_DE_BUSQUEDA) {
            return ResultadoMotor.deTema(t.busquedaMuyCorta(), null, TipoRespuesta.DIRECTA).enIngles(t.ingles());
        }
        try {
            PaginaInventarioDto pagina = inventarioClient.buscar(tokenBearer, criterio);
            if (pagina == null || pagina.totalElementos() == 0) {
                return consultado(t.busquedaSinResultados(criterio), "inventario", t);
            }
            return consultado(t.busqueda(pagina.totalElementos(), criterio, nombresDe(pagina)), "inventario", t);
        } catch (RestClientException excepcion) {
            return noDisponible(t.inventarioNoDisponible(), t);
        }
    }

    // ---- Consultas en vivo ------------------------------------------------------

    private ResultadoMotor consultarInventario(String tokenBearer, TextosDeConsultas t) {
        try {
            return consultado(textoInventario(inventarioClient.consultarInventario(tokenBearer, 0), t), "inventario", t);
        } catch (RestClientException excepcion) {
            return noDisponible(t.inventarioNoDisponible(), t);
        }
    }

    private ResultadoMotor consultarSubastas(String tokenBearer, TextosDeConsultas t) {
        try {
            return consultado(textoSubastas(subastasClient.consultarMiResumen(tokenBearer), t), "subastas", t);
        } catch (RestClientException excepcion) {
            return noDisponible(t.subastasNoDisponibles(), t);
        }
    }

    private ResultadoMotor consultarNotificaciones(String tokenBearer, String uid, TextosDeConsultas t) {
        try {
            return consultado(textoNotificaciones(notificacionesClient.consultarBandeja(tokenBearer, uid), t),
                "notificaciones", t);
        } catch (RestClientException excepcion) {
            return noDisponible(t.notificacionesNoDisponibles(), t);
        }
    }

    // B11 — ultimos movimientos de creditos, con el token del propio jugador.
    private ResultadoMotor consultarMovimientos(String tokenBearer, String uid, TextosDeConsultas t) {
        try {
            return consultado(textoMovimientos(tokenBearer, uid, t), "historial-transacciones", t);
        } catch (RestClientException excepcion) {
            return noDisponible(t.movimientosNoDisponibles(), t);
        }
    }

    // 7.4.4 (ms-chatbot.yaml 1.3.0) — misiones en curso, con el token del propio jugador.
    private ResultadoMotor consultarMisiones(String tokenBearer, TextosDeConsultas t) {
        try {
            List<MisionActivaDto> misiones = misionesClient.enCurso(tokenBearer);
            List<TarjetaInformativa> tarjetas = misiones == null ? List.of() : misiones.stream()
                .limit(MISIONES_A_MOSTRAR)
                .map(m -> new TarjetaInformativa(m.nombre(), textoDeLaMision(m, t), null))
                .toList();
            return ResultadoMotor.deTema(textoMisiones(misiones, t), null, TipoRespuesta.CONTEXTUAL)
                .conEnriquecido(Enriquecedor.conTarjetas(tarjetas, "misiones", t.ingles()))
                .enIngles(t.ingles());
        } catch (RestClientException excepcion) {
            return noDisponible(t.misionesNoDisponibles(), t);
        }
    }

    // B11 — torneos en curso y el equipo del jugador. Solo datos publicos
    // (GET /torneos, sin credencial): el uid del token sirve para encontrar su
    // equipo en lo que cualquiera puede ver, no para pedir nada en su nombre.
    private ResultadoMotor consultarTorneos(String uid, TextosDeConsultas t) {
        try {
            return consultado(textoTorneos(uid, t), "torneos", t);
        } catch (RestClientException excepcion) {
            return noDisponible(t.torneosNoDisponibles(), t);
        }
    }

    // ---- Informe de actividad ---------------------------------------------------

    // Las cinco partes se piden a la vez (hilos virtuales) y se espera como
    // mucho PLAZO_DEL_INFORME en total: la parte que no llega o falla se dice
    // «no disponible» y el resto se entrega igual. No se espera a las tareas
    // que se quedaron colgadas (shutdownNow, sin close()).
    private ResultadoMotor generarInformeActividad(String tokenBearer, String uid, TextosDeConsultas t) {
        ExecutorService hilos = Executors.newVirtualThreadPerTaskExecutor();
        try {
            Future<String> inventario = hilos.submit(
                () -> textoInventario(inventarioClient.consultarInventario(tokenBearer, 0), t));
            Future<String> subastas = hilos.submit(() -> textoSubastas(subastasClient.consultarMiResumen(tokenBearer), t));
            Future<String> notificaciones = hilos.submit(
                () -> textoNotificaciones(notificacionesClient.consultarBandeja(tokenBearer, uid), t));
            Future<String> creditos = hilos.submit(() -> textoMovimientos(tokenBearer, uid, t));
            Future<String> misiones = hilos.submit(() -> textoMisiones(misionesClient.enCurso(tokenBearer), t));
            long limite = System.nanoTime() + PLAZO_DEL_INFORME.toNanos();
            String texto = t.informe(
                esperar(inventario, limite, t.inventarioNoDisponible()),
                esperar(subastas, limite, t.subastasNoDisponibles()),
                esperar(notificaciones, limite, t.notificacionesNoDisponibles()),
                esperar(creditos, limite, t.movimientosNoDisponibles()),
                esperar(misiones, limite, t.misionesNoDisponibles()));
            return ResultadoMotor.deTema(texto, null, TipoRespuesta.CONTEXTUAL).enIngles(t.ingles());
        } finally {
            hilos.shutdownNow();
        }
    }

    private static String esperar(Future<String> parte, long limiteNanos, String siNoLlega) {
        try {
            long queda = Math.max(0, limiteNanos - System.nanoTime());
            return parte.get(queda, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            return siNoLlega;
        } catch (ExecutionException | TimeoutException fallo) {
            parte.cancel(true);
            return siNoLlega;
        }
    }

    // ---- Textos ---------------------------------------------------------------------

    private static String nombresDe(PaginaInventarioDto pagina) {
        List<ElementoInventarioDto> elementos = pagina.elementos() == null ? List.of() : pagina.elementos();
        return elementos.stream()
            .map(ElementoInventarioDto::nombrePropio)
            .filter(nombre -> nombre != null && !nombre.isBlank())
            .limit(ELEMENTOS_A_NOMBRAR)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    private static String textoInventario(PaginaInventarioDto pagina, TextosDeConsultas t) {
        if (pagina == null || pagina.totalElementos() == 0) {
            return t.inventarioVacio();
        }
        return t.inventario(pagina.totalElementos(), nombresDe(pagina));
    }

    private static String textoSubastas(MiResumenDto resumen, TextosDeConsultas t) {
        return t.subastas(resumen.subastasGanando(), resumen.creditosRetenidos(), resumen.saldoDisponible());
    }

    private static String textoNotificaciones(BandejaResponseDto bandeja, TextosDeConsultas t) {
        if (bandeja.noLeidas() == 0) {
            return t.sinNotificaciones();
        }
        List<AvisoDto> noLeidos = bandeja.avisos() == null ? List.of() : bandeja.avisos().stream()
            .filter(aviso -> Boolean.FALSE.equals(aviso.leida()))
            .limit(3)
            .toList();
        String titulos = noLeidos.stream()
            .map(AvisoDto::titulo)
            .filter(titulo -> titulo != null && !titulo.isBlank())
            .reduce((a, b) -> a + " | " + b)
            .orElse("");
        return t.notificaciones(bandeja.noLeidas(), titulos);
    }

    private String textoMovimientos(String tokenBearer, String uid, TextosDeConsultas t) {
        PaginaMovimientosDto pagina = finanzasClient.movimientos(tokenBearer, uid, MOVIMIENTOS_A_MOSTRAR);
        List<PaginaMovimientosDto.Movimiento> movimientos = pagina == null || pagina.content() == null
            ? List.of() : pagina.content();
        if (movimientos.isEmpty()) {
            return t.sinMovimientos();
        }
        String lista = movimientos.stream()
            .limit(MOVIMIENTOS_A_MOSTRAR)
            .map(m -> textoDelMovimiento(m, t))
            .reduce((a, b) -> a + "; " + b)
            .orElse("");
        return t.movimientos(lista);
    }

    private static String textoDelMovimiento(PaginaMovimientosDto.Movimiento movimiento, TextosDeConsultas t) {
        String monto = movimiento.monto() == null ? "?" : movimiento.monto().stripTrailingZeros().toPlainString();
        String cifra = switch (movimiento.signo() == null ? "" : movimiento.signo()) {
            case "SUMA" -> "+" + monto;
            case "RESTA" -> "-" + monto;
            case "APARTA" -> t.apartados(monto);
            default -> t.sinMoverSaldo(monto);
        };
        return cifra + (movimiento.concepto() == null ? "" : " (" + movimiento.concepto() + ")");
    }

    private static String textoMisiones(List<MisionActivaDto> misiones, TextosDeConsultas t) {
        if (misiones == null || misiones.isEmpty()) {
            return t.sinMisiones();
        }
        String lista = misiones.stream()
            .limit(MISIONES_A_MOSTRAR)
            .map(m -> textoDeLaMision(m, t))
            .reduce((a, b) -> a + "; " + b)
            .orElse("");
        return t.misiones(misiones.size(), lista);
    }

    private static String textoDeLaMision(MisionActivaDto mision, TextosDeConsultas t) {
        StringBuilder texto = new StringBuilder("«").append(mision.nombre()).append("»");
        MisionActivaDto.Heroe heroe = mision.heroe();
        if (heroe != null && heroe.nombre() != null && !heroe.nombre().isBlank()) {
            texto.append(t.conHeroe(heroe.nombre()));
            if (heroe.nivel() != null) {
                texto.append(t.nivel(heroe.nivel()));
            }
        }
        if (mision.progreso() != null) {
            texto.append(t.completada(Math.round(mision.progreso() * 100)));
        }
        if (mision.terminaEn() != null) {
            texto.append(t.terminaEl(mision.terminaEn()));
        }
        return texto.toString();
    }

    private String textoTorneos(String uid, TextosDeConsultas t) {
        List<TorneoResumenDto> activos = torneosClient.listar().stream()
            .filter(r -> "INSCRIPCIONES_ABIERTAS".equals(r.estado()) || "EN_CURSO".equals(r.estado()))
            .limit(TORNEOS_A_REVISAR)
            .toList();
        if (activos.isEmpty()) {
            return t.sinTorneosActivos();
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
                return textoDelEquipo(torneo, suyo.get(), t);
            }
        }
        TorneoResumenDto primero = activos.get(0);
        return t.noEstasEnTorneo(primero.nombre(), "EN_CURSO".equals(primero.estado()), primero.equiposInscritos(),
            primero.cupos(), primero.costoInscripcion());
    }

    private static String textoDelEquipo(TorneoDetalleDto torneo, TorneoDetalleDto.Equipo equipo, TextosDeConsultas t) {
        StringBuilder texto = new StringBuilder(t.enTorneo(torneo.nombre(), equipo.nombre()));
        if ("INSCRIPCIONES_ABIERTAS".equals(torneo.estado())) {
            return texto.append(equipo.inscrito() ? t.inscrito(equipo.posicion()) : t.sinInscribir()).toString();
        }
        if (equipo.eliminado()) {
            return texto.append(t.eliminado()).toString();
        }
        List<TorneoDetalleDto.Encuentro> encuentros = torneo.encuentros() == null ? List.of() : torneo.encuentros();
        encuentros.stream()
            .filter(e -> "LISTO".equals(e.estado()) && (equipo.id().equals(e.equipoA()) || equipo.id().equals(e.equipoB())))
            .findFirst()
            .ifPresentOrElse(e -> texto.append(t.encuentroListo(e.numero())), () -> texto.append(t.sigueEnCarrera()));
        return texto.toString();
    }

    private static UUID uidComoUuid(String uid) {
        try {
            return uid == null ? null : UUID.fromString(uid);
        } catch (IllegalArgumentException noEsUuid) {
            return null;
        }
    }

    // ---- Resultados --------------------------------------------------------------------

    // 1.3.4: la navegacion y las consultas llevan un enlace a su seccion.
    private static ResultadoMotor navegar(String texto, String destino, TextosDeConsultas t) {
        return ResultadoMotor.deTema(texto, null, TipoRespuesta.DIRECTA)
            .conEnriquecido(Enriquecedor.conEnlace(destino, t.ingles()))
            .enIngles(t.ingles());
    }

    private static ResultadoMotor consultado(String texto, String destino, TextosDeConsultas t) {
        return ResultadoMotor.deTema(texto, null, TipoRespuesta.CONTEXTUAL)
            .conEnriquecido(Enriquecedor.conEnlace(destino, t.ingles()))
            .enIngles(t.ingles());
    }

    private static ResultadoMotor noDisponible(String texto, TextosDeConsultas t) {
        return ResultadoMotor.deTema(texto, null, TipoRespuesta.DIRECTA).enIngles(t.ingles());
    }

    // ---- Frases con errores de tecleo ------------------------------------------------

    private static boolean contieneAlguna(String mensajeNormalizado, List<String> frases) {
        for (String frase : frases) {
            if (DistanciaDeEdicion.contieneFrase(mensajeNormalizado, NormalizadorTexto.normalizar(frase))) {
                return true;
            }
        }
        return false;
    }

    // Cambia por su forma correcta las palabras que estan a un error de tecleo
    // de una de referencia («inventraio» -> «inventario»), para que las
    // expresiones de busqueda las reconozcan.
    static String corregirPalabras(String mensajeNormalizado, List<String> referencias) {
        if (mensajeNormalizado.isBlank()) {
            return mensajeNormalizado;
        }
        StringBuilder corregido = new StringBuilder();
        for (String palabra : mensajeNormalizado.split(" ")) {
            String elegida = referencias.stream()
                .filter(referencia -> DistanciaDeEdicion.coinciden(referencia, palabra))
                .findFirst()
                .orElse(palabra);
            if (!corregido.isEmpty()) {
                corregido.append(' ');
            }
            corregido.append(elegida);
        }
        return corregido.toString();
    }
}
