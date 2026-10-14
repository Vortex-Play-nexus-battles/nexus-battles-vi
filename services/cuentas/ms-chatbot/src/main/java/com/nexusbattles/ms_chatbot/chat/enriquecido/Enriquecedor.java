package com.nexusbattles.ms_chatbot.chat.enriquecido;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

// ms-chatbot.yaml 1.3.4 (7.4.3): arma la respuesta enriquecida a partir de lo
// que ya sabe la base de conocimiento, sin columnas nuevas en los temas:
//
//   * pasos: los "1) ... 2) ..." de un tema PASO_A_PASO;
//   * enlaces: la seccion del sitio de la que habla el tema (desde 1.5.0 por
//     su clave, y si no la tiene en el mapa, por las palabras de su titulo) o
//     la que abre una consulta asistida. Nunca una vista de administracion:
//     el tema de un jugador no puede mandarlo a una pantalla de «sin acceso»;
//   * respuestas rapidas: otros temas de la misma categoria, o, al escalar,
//     los temas relacionados y los de la vista donde esta el jugador. Desde
//     1.6.0 (7.4.10) un tema cuenta en cada una de sus intenciones (su
//     categoria y las adicionales), y los relacionados de un tema salen de
//     todas las suyas, primero los de su categoria;
//   * soporte humano: se ofrece siempre que la consulta se escala.
//
// Sin estado: lo usan MotorRespuestas y MotorConsultasAsistidas.
public final class Enriquecedor {

    static final int MAXIMO_DE_RAPIDAS = 3;
    static final int MAXIMO_DE_PASOS = 20;
    static final int LARGO_MAXIMO_DE_PASO = 500;
    static final int MAXIMO_DE_ENLACES_AL_ESCALAR = 2;

    /** Temas de cortesia: no se ofrecen como pregunta (nadie necesita un boton para decir "hola"). */
    public static final Set<String> TITULOS_DE_CORTESIA = Set.of("saludo", "despedida");

    // "1) ...", "2) ..." dentro del texto de un tema paso a paso.
    private static final Pattern MARCA_DE_PASO = Pattern.compile("\\s*\\b\\d{1,2}\\)\\s+");

    // Nombre visible de cada vista a la que el chatbot puede enlazar, con la
    // etiqueta que tiene en la barra, en el menu del apodo o en su pantalla
    // (1.5.0). Los ids son los de comun/matriz-acceso.js. Solo vistas de
    // jugador o publicas: ninguna de administracion.
    static final Map<String, String> NOMBRE_DE_DESTINO = Map.ofEntries(
        Map.entry("registro", "Crear cuenta"),
        Map.entry("login", "Entrar"),
        Map.entry("restablecer-solicitar", "Recuperar contraseña"),
        Map.entry("verificar-cuenta", "Confirmar tu correo"),
        Map.entry("perfil", "Mi Cuenta"),
        Map.entry("historial-transacciones", "Historial de transacciones"),
        Map.entry("mis-cofres", "Mis cofres"),
        Map.entry("mis-sanciones", "Mis sanciones"),
        Map.entry("inventario", "Mi inventario"),
        Map.entry("tienda", "Tienda"),
        Map.entry("subastas", "Subasta"),
        Map.entry("publicar-subasta", "Publicar subasta"),
        Map.entry("misiones", "Misiones"),
        Map.entry("torneos", "Torneo"),
        Map.entry("batallas", "Jugar online"),
        Map.entry("crear-sala", "Crear sala"),
        Map.entry("chat", "Chat"),
        Map.entry("notificaciones", "Notificaciones")
    );

    // 1.5.0 — la seccion de cada tema de la base, por su clave (V11).
    private static final Map<String, String> DESTINO_POR_CLAVE = Map.ofEntries(
        Map.entry("heroes-disponibles", "inventario"),
        Map.entry("estadisticas-y-nivel", "inventario"),
        Map.entry("armas-armaduras-items", "inventario"),
        Map.entry("habilidades-epicas", "inventario"),
        Map.entry("completar-mazo", "inventario"),
        Map.entry("consejos-objetos", "inventario"),
        Map.entry("acciones-y-poder", "inventario"),
        Map.entry("combate-por-turnos", "batallas"),
        Map.entry("efectos-aleatorios", "batallas"),
        Map.entry("modo-batalla", "batallas"),
        Map.entry("estrategias-basicas", "batallas"),
        Map.entry("sinergias-equipo", "batallas"),
        Map.entry("derrota-del-heroe", "batallas"),
        Map.entry("recompensas-de-batalla", "batallas"),
        Map.entry("unirse-a-una-sala", "batallas"),
        Map.entry("crear-sala-de-batalla", "crear-sala"),
        Map.entry("completar-mision", "misiones"),
        Map.entry("tipos-de-misiones", "misiones"),
        Map.entry("recompensas-de-misiones", "misiones"),
        Map.entry("dificultades-de-misiones", "misiones"),
        Map.entry("torneo", "torneos"),
        Map.entry("inscribirse-en-torneo", "torneos"),
        Map.entry("crear-cuenta", "registro"),
        Map.entry("verificar-correo", "verificar-cuenta"),
        Map.entry("recuperar-contrasena", "restablecer-solicitar"),
        Map.entry("cuenta-bloqueada", "restablecer-solicitar"),
        Map.entry("sesion-terminada", "login"),
        Map.entry("mi-cuenta", "perfil"),
        Map.entry("verificacion-en-dos-pasos", "perfil"),
        Map.entry("preguntas-de-seguridad", "perfil"),
        Map.entry("cambiar-contrasena", "perfil"),
        Map.entry("cambiar-apodo", "perfil"),
        Map.entry("privacidad-y-datos", "perfil"),
        Map.entry("logros-titulos-insignias", "perfil"),
        Map.entry("creditos", "historial-transacciones"),
        Map.entry("cofres", "mis-cofres"),
        Map.entry("sanciones", "mis-sanciones"),
        Map.entry("apelar-sancion", "mis-sanciones"),
        Map.entry("publicar-subasta", "publicar-subasta"),
        Map.entry("pujar-o-comprar", "subastas"),
        Map.entry("cancelar-subasta", "subastas"),
        Map.entry("puja-automatica", "subastas"),
        Map.entry("recoger-subasta-ganada", "subastas"),
        Map.entry("limites-de-subastas", "subastas"),
        Map.entry("precios-y-disponibilidad", "tienda"),
        Map.entry("comprar-en-la-tienda", "tienda"),
        Map.entry("comentarios-y-calificaciones", "tienda"),
        Map.entry("mensajes-privados", "chat"),
        Map.entry("notificaciones", "notificaciones")
    );

    // Palabra del titulo (normalizado) -> destino, para los temas que crea un
    // administrador y no estan en DESTINO_POR_CLAVE. El orden importa: la
    // primera que aparece decide ("crear una cuenta" va a registro antes que a
    // perfil).
    private static final List<Map.Entry<String, String>> DESTINO_POR_PALABRA = List.of(
        Map.entry("crear una cuenta", "registro"),
        Map.entry("registr", "registro"),
        Map.entry("contrasena", "restablecer-solicitar"),
        Map.entry("subasta", "subastas"),
        Map.entry("pujar", "subastas"),
        Map.entry("mision", "misiones"),
        Map.entry("torneo", "torneos"),
        Map.entry("sala", "batallas"),
        Map.entry("batalla", "batallas"),
        Map.entry("combate", "batallas"),
        Map.entry("tienda", "tienda"),
        Map.entry("credito", "historial-transacciones"),
        Map.entry("cofre", "mis-cofres"),
        Map.entry("sancion", "mis-sanciones"),
        Map.entry("perfil", "perfil"),
        Map.entry("cuenta", "perfil"),
        Map.entry("heroe", "inventario"),
        Map.entry("arma", "inventario"),
        Map.entry("epica", "inventario"),
        Map.entry("inventario", "inventario")
    );

    private Enriquecedor() {
        // Utilidades sin estado.
    }

    public static boolean esDeCortesia(TemaConocimiento tema) {
        return TITULOS_DE_CORTESIA.contains(NormalizadorTexto.normalizar(tema.getTitulo()));
    }

    /** Respuesta enriquecida de un tema de la base de conocimiento. */
    public static RespuestaEnriquecida paraTema(TemaConocimiento tema, String texto, List<TemaConocimiento> temas) {
        return paraTema(tema, texto, temas, false);
    }

    /** Como {@link #paraTema(TemaConocimiento, String, List)}, con el enlace en el idioma de la respuesta (1.5.0). */
    public static RespuestaEnriquecida paraTema(TemaConocimiento tema, String texto, List<TemaConocimiento> temas,
                                                boolean ingles) {
        List<String> pasos = tema.getTipoRespuesta() == TipoRespuesta.PASO_A_PASO ? pasosDe(texto) : List.of();
        List<EnlaceInterno> enlaces = destinoDe(tema).map(d -> enlaceA(d, ingles)).stream().toList();
        return new RespuestaEnriquecida(pasos, enlaces, List.of(), relacionadosCon(tema, temas), false);
    }

    /**
     * Respuesta enriquecida de una consulta escalada: los temas relacionados
     * y, si faltan, los de la vista donde esta el jugador; y la oferta de
     * soporte humano.
     */
    public static RespuestaEnriquecida paraEscalamiento(List<String> sugeridos, List<TemaConocimiento> temas,
                                                        VistaDelChat vista) {
        return paraEscalamiento(sugeridos, temas, vista, false);
    }

    /**
     * 1.5.0 (7.4.3 «proporcionar enlaces a documentación relevante»): al
     * escalar, además de las preguntas relacionadas y la oferta de soporte,
     * los enlaces a las secciones de las que hablan esas preguntas (o a la de
     * la vista), donde está la información.
     */
    public static RespuestaEnriquecida paraEscalamiento(List<String> sugeridos, List<TemaConocimiento> temas,
                                                        VistaDelChat vista, boolean ingles) {
        Set<String> rapidas = new LinkedHashSet<>(sugeridos == null ? List.of() : sugeridos);
        Categoria deLaVista = vista == null ? null : vista.categoria();
        if (deLaVista != null && rapidas.size() < MAXIMO_DE_RAPIDAS) {
            rapidas.addAll(titulosDe(temas, deLaVista, Set.of(), MAXIMO_DE_RAPIDAS));
        }
        List<String> limitadas = rapidas.stream().limit(MAXIMO_DE_RAPIDAS).toList();
        Set<String> destinos = new LinkedHashSet<>();
        for (String titulo : limitadas) {
            temaConTitulo(temas, titulo).flatMap(Enriquecedor::destinoDe).ifPresent(destinos::add);
        }
        List<EnlaceInterno> enlaces = destinos.stream().limit(MAXIMO_DE_ENLACES_AL_ESCALAR)
            .map(destino -> enlaceA(destino, ingles))
            .toList();
        return new RespuestaEnriquecida(List.of(), enlaces, List.of(), limitadas, true);
    }

    /**
     * 1.5.0 — «no me sirvió»: otras preguntas del mismo tema, el enlace a su
     * sección y la oferta de soporte humano.
     */
    public static RespuestaEnriquecida paraNoSirvio(TemaConocimiento tema, List<TemaConocimiento> temas,
                                                    boolean ingles) {
        List<EnlaceInterno> enlaces = destinoDe(tema).map(d -> enlaceA(d, ingles)).stream().toList();
        return new RespuestaEnriquecida(List.of(), enlaces, List.of(), relacionadosCon(tema, temas), true);
    }

    /**
     * 1.6.0 (7.4.10): las preguntas relacionadas con un tema son los otros
     * temas que atienden alguna de sus intenciones: primero los de su
     * categoría y, si quedan huecos, los de cada intención adicional, en el
     * orden del enum.
     */
    static List<String> relacionadosCon(TemaConocimiento tema, List<TemaConocimiento> temas) {
        Set<String> excluidas = Set.of(tema.getClave());
        Set<String> titulos = new LinkedHashSet<>(titulosDe(temas, tema.getCategoria(), excluidas, MAXIMO_DE_RAPIDAS));
        for (Categoria intencion : tema.getIntencionesAdicionales()) {
            if (titulos.size() >= MAXIMO_DE_RAPIDAS) {
                break;
            }
            titulos.addAll(titulosDe(temas, intencion, excluidas, MAXIMO_DE_RAPIDAS));
        }
        return titulos.stream().limit(MAXIMO_DE_RAPIDAS).toList();
    }

    /** Solo la oferta de soporte humano (1.5.0, «no me sirvió» sin tema anterior). */
    public static RespuestaEnriquecida soloSoporte() {
        return new RespuestaEnriquecida(List.of(), List.of(), List.of(), List.of(), true);
    }

    private static Optional<TemaConocimiento> temaConTitulo(List<TemaConocimiento> temas, String titulo) {
        return temas == null ? Optional.empty()
            : temas.stream().filter(t -> titulo.equals(t.getTitulo())).findFirst();
    }

    /** Solo un enlace a una seccion (navegacion y consultas asistidas). */
    public static RespuestaEnriquecida conEnlace(String destino) {
        return conEnlace(destino, false);
    }

    public static RespuestaEnriquecida conEnlace(String destino, boolean ingles) {
        return new RespuestaEnriquecida(List.of(), List.of(enlaceA(destino, ingles)), List.of(), List.of(), false);
    }

    /** Tarjetas con un enlace a la seccion donde se ve el detalle. */
    public static RespuestaEnriquecida conTarjetas(List<TarjetaInformativa> tarjetas, String destino) {
        return conTarjetas(tarjetas, destino, false);
    }

    public static RespuestaEnriquecida conTarjetas(List<TarjetaInformativa> tarjetas, String destino, boolean ingles) {
        return new RespuestaEnriquecida(List.of(), List.of(enlaceA(destino, ingles)), tarjetas, List.of(), false);
    }

    public static EnlaceInterno enlaceA(String destino) {
        return enlaceA(destino, false);
    }

    // La interfaz esta en espanol: en ingles se dice «Go to» y se deja la
    // etiqueta tal cual la vera el jugador en la barra.
    public static EnlaceInterno enlaceA(String destino, boolean ingles) {
        String nombre = NOMBRE_DE_DESTINO.getOrDefault(destino, destino);
        return new EnlaceInterno((ingles ? "Go to " : "Ir a ") + nombre, destino);
    }

    /** Los pasos "1) ... 2) ..." de un texto; vacia si no hay al menos dos. */
    static List<String> pasosDe(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String[] partes = MARCA_DE_PASO.split(texto);
        if (partes.length < 3) {
            return List.of();
        }
        return Arrays.stream(partes, 1, partes.length)
            .map(String::strip)
            .filter(paso -> !paso.isEmpty())
            .map(paso -> paso.length() > LARGO_MAXIMO_DE_PASO ? paso.substring(0, LARGO_MAXIMO_DE_PASO) : paso)
            .limit(MAXIMO_DE_PASOS)
            .toList();
    }

    /** La seccion del sitio de la que habla un tema: por su clave y, si no, por su titulo (1.5.0). */
    static Optional<String> destinoDe(TemaConocimiento tema) {
        String porClave = tema.getClave() == null ? null : DESTINO_POR_CLAVE.get(tema.getClave());
        return porClave != null ? Optional.of(porClave) : destinoDeTitulo(tema.getTitulo());
    }

    /** La seccion del sitio de la que habla un tema, por las palabras de su titulo. */
    static Optional<String> destinoDeTitulo(String titulo) {
        String normalizado = NormalizadorTexto.normalizar(titulo);
        return DESTINO_POR_PALABRA.stream()
            .filter(par -> normalizado.contains(par.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    // Titulos de los temas activos de una categoria (sin los de cortesia ni
    // los excluidos), el de mayor prioridad primero.
    // 1.6.0: un tema es de la categoria si es la suya o una de sus
    // intenciones adicionales.
    private static List<String> titulosDe(List<TemaConocimiento> temas, Categoria categoria, Set<String> excluidas,
                                          int cuantos) {
        if (temas == null || categoria == null) {
            return List.of();
        }
        List<TemaConocimiento> candidatos = new ArrayList<>();
        for (TemaConocimiento tema : temas) {
            if (tema.isActivo() && tema.atiende(categoria) && !excluidas.contains(tema.getClave())
                && !esDeCortesia(tema)) {
                candidatos.add(tema);
            }
        }
        return candidatos.stream()
            .sorted(Comparator.comparingInt(TemaConocimiento::getPrioridad).reversed()
                .thenComparing(TemaConocimiento::getTitulo))
            .map(TemaConocimiento::getTitulo)
            .limit(cuantos)
            .toList();
    }
}
