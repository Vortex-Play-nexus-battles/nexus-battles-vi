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
//   * enlaces: la seccion del sitio de la que habla el tema (por su titulo) o
//     la que abre una consulta asistida;
//   * respuestas rapidas: otros temas de la misma categoria, o, al escalar,
//     los temas relacionados y los de la vista donde esta el jugador;
//   * soporte humano: se ofrece siempre que la consulta se escala.
//
// Sin estado: lo usan MotorRespuestas y MotorConsultasAsistidas.
public final class Enriquecedor {

    static final int MAXIMO_DE_RAPIDAS = 3;
    static final int MAXIMO_DE_PASOS = 20;
    static final int LARGO_MAXIMO_DE_PASO = 500;

    /** Temas de cortesia: no se ofrecen como pregunta (nadie necesita un boton para decir "hola"). */
    public static final Set<String> TITULOS_DE_CORTESIA = Set.of("saludo", "despedida");

    // "1) ...", "2) ..." dentro del texto de un tema paso a paso.
    private static final Pattern MARCA_DE_PASO = Pattern.compile("\\s*\\b\\d{1,2}\\)\\s+");

    // Nombre visible de cada vista a la que el chatbot puede enlazar. Los ids
    // son los de comun/matriz-acceso.js.
    private static final Map<String, String> NOMBRE_DE_DESTINO = Map.ofEntries(
        Map.entry("registro", "Crear una cuenta"),
        Map.entry("login", "Iniciar sesión"),
        Map.entry("perfil", "Mi cuenta"),
        Map.entry("inventario", "Mi inventario"),
        Map.entry("productos", "Catálogo"),
        Map.entry("tienda", "Tienda"),
        Map.entry("subastas", "Subastas"),
        Map.entry("misiones", "Misiones"),
        Map.entry("torneos", "Torneos"),
        Map.entry("batallas", "Batallas"),
        Map.entry("notificaciones", "Notificaciones")
    );

    // Palabra del titulo (normalizado) -> destino. El orden importa: la primera
    // que aparece decide ("crear una cuenta" va a registro antes que a perfil).
    private static final List<Map.Entry<String, String>> DESTINO_POR_PALABRA = List.of(
        Map.entry("crear una cuenta", "registro"),
        Map.entry("registr", "registro"),
        Map.entry("contrasena", "login"),
        Map.entry("subasta", "subastas"),
        Map.entry("pujar", "subastas"),
        Map.entry("mision", "misiones"),
        Map.entry("torneo", "torneos"),
        Map.entry("batalla", "batallas"),
        Map.entry("combate", "batallas"),
        Map.entry("credito", "tienda"),
        Map.entry("perfil", "perfil"),
        Map.entry("cuenta", "perfil"),
        Map.entry("heroe", "productos"),
        Map.entry("arma", "productos"),
        Map.entry("epica", "productos")
    );

    private Enriquecedor() {
        // Utilidades sin estado.
    }

    public static boolean esDeCortesia(TemaConocimiento tema) {
        return TITULOS_DE_CORTESIA.contains(NormalizadorTexto.normalizar(tema.getTitulo()));
    }

    /** Respuesta enriquecida de un tema de la base de conocimiento. */
    public static RespuestaEnriquecida paraTema(TemaConocimiento tema, String texto, List<TemaConocimiento> temas) {
        List<String> pasos = tema.getTipoRespuesta() == TipoRespuesta.PASO_A_PASO ? pasosDe(texto) : List.of();
        List<EnlaceInterno> enlaces = destinoDeTitulo(tema.getTitulo()).map(Enriquecedor::enlaceA).stream().toList();
        List<String> rapidas = titulosDe(temas, tema.getCategoria(), Set.of(tema.getClave()), MAXIMO_DE_RAPIDAS);
        return new RespuestaEnriquecida(pasos, enlaces, List.of(), rapidas, false);
    }

    /**
     * Respuesta enriquecida de una consulta escalada: los temas relacionados
     * y, si faltan, los de la vista donde esta el jugador; y la oferta de
     * soporte humano.
     */
    public static RespuestaEnriquecida paraEscalamiento(List<String> sugeridos, List<TemaConocimiento> temas,
                                                        VistaDelChat vista) {
        Set<String> rapidas = new LinkedHashSet<>(sugeridos == null ? List.of() : sugeridos);
        Categoria deLaVista = vista == null ? null : vista.categoria();
        if (deLaVista != null && rapidas.size() < MAXIMO_DE_RAPIDAS) {
            rapidas.addAll(titulosDe(temas, deLaVista, Set.of(), MAXIMO_DE_RAPIDAS));
        }
        List<String> limitadas = rapidas.stream().limit(MAXIMO_DE_RAPIDAS).toList();
        return new RespuestaEnriquecida(List.of(), List.of(), List.of(), limitadas, true);
    }

    /** Solo un enlace a una seccion (navegacion y consultas asistidas). */
    public static RespuestaEnriquecida conEnlace(String destino) {
        return new RespuestaEnriquecida(List.of(), List.of(enlaceA(destino)), List.of(), List.of(), false);
    }

    /** Tarjetas con un enlace a la seccion donde se ve el detalle. */
    public static RespuestaEnriquecida conTarjetas(List<TarjetaInformativa> tarjetas, String destino) {
        return new RespuestaEnriquecida(List.of(), List.of(enlaceA(destino)), tarjetas, List.of(), false);
    }

    public static EnlaceInterno enlaceA(String destino) {
        String nombre = NOMBRE_DE_DESTINO.getOrDefault(destino, destino);
        return new EnlaceInterno("Ir a " + nombre, destino);
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
    private static List<String> titulosDe(List<TemaConocimiento> temas, Categoria categoria, Set<String> excluidas,
                                          int cuantos) {
        if (temas == null || categoria == null) {
            return List.of();
        }
        List<TemaConocimiento> candidatos = new ArrayList<>();
        for (TemaConocimiento tema : temas) {
            if (tema.isActivo() && tema.getCategoria() == categoria && !excluidas.contains(tema.getClave())
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
