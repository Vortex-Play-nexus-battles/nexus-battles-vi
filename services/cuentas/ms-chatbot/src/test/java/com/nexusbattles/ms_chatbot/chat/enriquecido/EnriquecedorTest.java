package com.nexusbattles.ms_chatbot.chat.enriquecido;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// ms-chatbot.yaml 1.3.4: pasos, enlaces y respuestas rapidas de una respuesta.
class EnriquecedorTest {

    private static TemaConocimiento tema(String clave, Categoria categoria, TipoRespuesta tipo, String titulo,
                                         int prioridad) {
        return new TemaConocimiento(null, clave, categoria, tipo, titulo, "x", null, "respuesta", null, prioridad,
            true);
    }

    private final TemaConocimiento publicar = tema("k-publicar", Categoria.SUBASTA_Y_COMERCIO,
        TipoRespuesta.PASO_A_PASO, "Cómo publicar un producto en subasta", 0);
    private final TemaConocimiento pujar = tema("k-pujar", Categoria.SUBASTA_Y_COMERCIO,
        TipoRespuesta.PASO_A_PASO, "Cómo pujar o comprar en una subasta", 5);
    private final TemaConocimiento creditos = tema("k-creditos", Categoria.SUBASTA_Y_COMERCIO,
        TipoRespuesta.DIRECTA, "Créditos y moneda del juego", 0);
    private final TemaConocimiento saludo = tema("k-hola", Categoria.SUBASTA_Y_COMERCIO,
        TipoRespuesta.DIRECTA, "Saludo", 9);
    private final List<TemaConocimiento> temas = List.of(publicar, pujar, creditos, saludo);

    @Test
    void unTemaPasoAPasoTraeSusPasosSuEnlaceYOtrasPreguntasDeSuCategoria() {
        String texto = "Para publicar: 1) Elige el ítem. 2) Configura la duración (24 horas). 3) Confirma.";

        RespuestaEnriquecida enriquecida = Enriquecedor.paraTema(publicar, texto, temas);

        assertThat(enriquecida.pasos()).containsExactly("Elige el ítem.", "Configura la duración (24 horas).",
            "Confirma.");
        assertThat(enriquecida.enlaces()).containsExactly(new EnlaceInterno("Ir a Subasta", "subastas"));
        // El de mayor prioridad primero; ni el propio tema ni el de cortesia.
        assertThat(enriquecida.respuestasRapidas()).containsExactly("Cómo pujar o comprar en una subasta",
            "Créditos y moneda del juego");
        assertThat(enriquecida.ofrecerSoporteHumano()).isFalse();
    }

    // 1.15.0 (D-CHA-13): «Más detalle» delante; detras, las que quepan, sin repetirlo.
    @Test
    void conMasDetalleLoPoneDelanteYConservaLoDemas() {
        TarjetaInformativa tarjeta = new TarjetaInformativa("Torneo", "Se juega en equipos.", null);
        RespuestaEnriquecida original = new RespuestaEnriquecida(List.of("Paso."),
            List.of(new EnlaceInterno("Ir a Torneo", "torneos")), List.of(tarjeta),
            List.of("Uno", "Más detalle", "Dos", "Tres"), true);

        RespuestaEnriquecida conBoton = Enriquecedor.conMasDetalle(original, false);

        assertThat(conBoton.respuestasRapidas()).containsExactly("Más detalle", "Uno", "Dos");
        assertThat(conBoton.pasos()).isEqualTo(original.pasos());
        assertThat(conBoton.enlaces()).isEqualTo(original.enlaces());
        assertThat(conBoton.tarjetas()).containsExactly(tarjeta);
        assertThat(conBoton.ofrecerSoporteHumano()).isTrue();
        assertThat(Enriquecedor.conMasDetalle(new RespuestaEnriquecida(List.of(), List.of(), List.of(), List.of(),
            false), true).respuestasRapidas()).containsExactly(Enriquecedor.MAS_DETALLE_EN);
    }

    @Test
    void unTemaDirectoNoTraePasos() {
        RespuestaEnriquecida enriquecida = Enriquecedor.paraTema(creditos, "1) uno 2) dos", temas);

        assertThat(enriquecida.pasos()).isEmpty();
        assertThat(enriquecida.enlaces()).extracting(EnlaceInterno::destino).containsExactly("historial-transacciones");
    }

    @Test
    void pasosSoloSiHayAlMenosDos() {
        assertThat(Enriquecedor.pasosDe("Texto sin pasos.")).isEmpty();
        assertThat(Enriquecedor.pasosDe("Solo 1) uno.")).isEmpty();
        assertThat(Enriquecedor.pasosDe(null)).isEmpty();
        assertThat(Enriquecedor.pasosDe("1) uno 2) dos")).containsExactly("uno", "dos");
    }

    @Test
    void elDestinoSaleDeLasPalabrasDelTitulo() {
        assertThat(Enriquecedor.destinoDeTitulo("Cómo crear una cuenta")).contains("registro");
        assertThat(Enriquecedor.destinoDeTitulo("Recuperar mi contraseña")).contains("restablecer-solicitar");
        assertThat(Enriquecedor.destinoDeTitulo("Gestionar mi cuenta y mi perfil")).contains("perfil");
        assertThat(Enriquecedor.destinoDeTitulo("Cómo completar una misión (modo JcE)")).contains("misiones");
        assertThat(Enriquecedor.destinoDeTitulo("Cómo funciona el Torneo")).contains("torneos");
        assertThat(Enriquecedor.destinoDeTitulo("Héroes disponibles")).contains("inventario");
        assertThat(Enriquecedor.destinoDeTitulo("Sistema de sanciones")).contains("mis-sanciones");
        assertThat(Enriquecedor.destinoDeTitulo("Normas de conducta")).isEmpty();
    }

    @Test
    void alEscalarOfreceSoporteYCompletaConLosTemasDeLaVista() {
        RespuestaEnriquecida enriquecida = Enriquecedor.paraEscalamiento(
            List.of("Cómo publicar un producto en subasta"), temas, VistaDelChat.SUBASTAS);

        assertThat(enriquecida.ofrecerSoporteHumano()).isTrue();
        assertThat(enriquecida.respuestasRapidas()).containsExactly("Cómo publicar un producto en subasta",
            "Cómo pujar o comprar en una subasta", "Créditos y moneda del juego");
    }

    @Test
    void alEscalarSinVistaSoloLosRelacionados() {
        RespuestaEnriquecida enriquecida = Enriquecedor.paraEscalamiento(List.of("A", "B"), temas, null);

        assertThat(enriquecida.respuestasRapidas()).containsExactly("A", "B");
        assertThat(Enriquecedor.paraEscalamiento(null, temas, VistaDelChat.INICIO).respuestasRapidas()).isEmpty();
    }

    @Test
    void conEnlaceYConTarjetas() {
        assertThat(Enriquecedor.conEnlace("inventario").enlaces())
            .containsExactly(new EnlaceInterno("Ir a Mi inventario", "inventario"));
        TarjetaInformativa tarjeta = new TarjetaInformativa("El Templo", "40 %", null);
        RespuestaEnriquecida conTarjetas = Enriquecedor.conTarjetas(List.of(tarjeta), "misiones");
        assertThat(conTarjetas.tarjetas()).containsExactly(tarjeta);
        assertThat(conTarjetas.enlaces()).extracting(EnlaceInterno::destino).containsExactly("misiones");
        assertThat(Enriquecedor.enlaceA("otra").texto()).isEqualTo("Ir a otra");
    }

    @Test
    void unaRespuestaSinNadaEstaVaciaYLasListasNuncaSonNull() {
        RespuestaEnriquecida vacia = new RespuestaEnriquecida(null, null, null, null, false);

        assertThat(vacia.estaVacia()).isTrue();
        assertThat(vacia.pasos()).isEmpty();
        assertThat(new RespuestaEnriquecida(null, null, null, null, true).estaVacia()).isFalse();
    }

    // 1.5.0: el destino sale primero de la clave del tema (V11), y el enlace
    // va en el idioma de la respuesta con la etiqueta que ve el jugador.
    @Test
    void elDestinoSaleDeLaClaveAntesQueDelTitulo() {
        TemaConocimiento recuperar = tema("recuperar-contrasena", Categoria.CUENTA_Y_REGISTRO,
            TipoRespuesta.PASO_A_PASO, "Olvidé mi clave de acceso", 0);
        TemaConocimiento cofres = tema("cofres", Categoria.PRODUCTO, TipoRespuesta.DIRECTA, "Premios", 0);

        assertThat(Enriquecedor.destinoDe(recuperar)).contains("restablecer-solicitar");
        assertThat(Enriquecedor.destinoDe(cofres)).contains("mis-cofres");
        assertThat(Enriquecedor.paraTema(cofres, "x", List.of(cofres), true).enlaces())
            .containsExactly(new EnlaceInterno("Go to Mis cofres", "mis-cofres"));
    }

    @Test
    void elEnlaceEnInglesDejaLaEtiquetaDeLaInterfaz() {
        assertThat(Enriquecedor.enlaceA("subastas", true)).isEqualTo(new EnlaceInterno("Go to Subasta", "subastas"));
        assertThat(Enriquecedor.enlaceA("torneos", false)).isEqualTo(new EnlaceInterno("Ir a Torneo", "torneos"));
        assertThat(Enriquecedor.conEnlace("perfil", true).enlaces()).extracting(EnlaceInterno::texto)
            .containsExactly("Go to Mi Cuenta");
    }

    // 7.4.3 «enlaces a documentación relevante»: al escalar, hasta dos enlaces
    // a las secciones de las preguntas relacionadas, sin repetir.
    @Test
    void alEscalarEnlazaLasSeccionesDeLasPreguntasRelacionadas() {
        RespuestaEnriquecida enriquecida = Enriquecedor.paraEscalamiento(List.of("Cómo publicar un producto en subasta",
            "Cómo pujar o comprar en una subasta", "Créditos y moneda del juego"), temas, null, true);

        // 1.8.0: y al final el centro de ayuda (sin vista, sin categoria).
        assertThat(enriquecida.enlaces()).containsExactly(new EnlaceInterno("Go to Subasta", "subastas"),
            new EnlaceInterno("Go to Historial de transacciones", "historial-transacciones"),
            new EnlaceInterno("Go to Centro de ayuda", "ayuda"));
        assertThat(Enriquecedor.paraEscalamiento(List.of("Un título que no es de ningún tema"), temas, null, false)
            .enlaces()).containsExactly(new EnlaceInterno("Ver el centro de ayuda", "ayuda"));
    }

    @Test
    void siNoLeSirvioOfreceSoporteOtrasPreguntasYElEnlace() {
        RespuestaEnriquecida enriquecida = Enriquecedor.paraNoSirvio(publicar, temas, false);

        assertThat(enriquecida.ofrecerSoporteHumano()).isTrue();
        assertThat(enriquecida.respuestasRapidas()).containsExactly("Cómo pujar o comprar en una subasta",
            "Créditos y moneda del juego");
        assertThat(enriquecida.enlaces()).containsExactly(new EnlaceInterno("Ir a Subasta", "subastas"),
            new EnlaceInterno("Ver el centro de ayuda", "ayuda", "categoria-subasta-y-comercio"));

        RespuestaEnriquecida soloSoporte = Enriquecedor.soloSoporte();
        assertThat(soloSoporte.ofrecerSoporteHumano()).isTrue();
        assertThat(soloSoporte.enlaces()).containsExactly(new EnlaceInterno("Ver el centro de ayuda", "ayuda"));
        assertThat(soloSoporte.respuestasRapidas()).isEmpty();
        assertThat(Enriquecedor.soloSoporte(true).enlaces())
            .containsExactly(new EnlaceInterno("Go to Centro de ayuda", "ayuda"));
    }

    // 1.8.0 (7.4.3): al escalar, el centro de ayuda abierto en la categoria de
    // la vista donde esta el jugador; sin categoria, al principio.
    @Test
    void alEscalarElCentroDeAyudaVaALaCategoriaDeLaVista() {
        // Las secciones de los temas de la vista y, al final, la ayuda en su categoria.
        assertThat(Enriquecedor.paraEscalamiento(List.of(), temas, VistaDelChat.SUBASTAS).enlaces())
            .containsExactly(new EnlaceInterno("Ir a Subasta", "subastas"),
                new EnlaceInterno("Ir a Historial de transacciones", "historial-transacciones"),
                new EnlaceInterno("Ver el centro de ayuda", "ayuda", "categoria-subasta-y-comercio"));
        assertThat(Enriquecedor.paraEscalamiento(List.of(), temas, VistaDelChat.INICIO).enlaces())
            .containsExactly(new EnlaceInterno("Ver el centro de ayuda", "ayuda"));
        assertThat(Enriquecedor.NOMBRE_DE_DESTINO).containsEntry("ayuda", "Centro de ayuda");
    }

    @Test
    void elAnclaDeCadaCategoriaTieneLaFormaDelContrato() {
        for (Categoria categoria : Categoria.values()) {
            assertThat(Enriquecedor.anclaDe(categoria)).as(categoria.name()).matches("^categoria-[a-z0-9-]{1,49}$");
        }
        assertThat(Enriquecedor.anclaDe(Categoria.CUENTA_Y_REGISTRO)).isEqualTo("categoria-cuenta-y-registro");
        assertThat(Enriquecedor.enlaceAlCentroDeAyuda(Categoria.FAQ_GENERAL, true))
            .isEqualTo(new EnlaceInterno("Go to Centro de ayuda", "ayuda", "categoria-faq-general"));
    }

    // Cada tema de la base que se publica en V11 enlaza, si enlaza, a una vista
    // de jugador o publica con nombre visible: nunca un id suelto ni una vista
    // de administracion.
    @Test
    void cadaTemaDeLaBaseEnlazaAUnaVistaConNombre() throws Exception {
        JsonNode base = JsonMapper.builder().build().readTree(Path.of("contenido/base-de-conocimiento.json").toFile());
        int conEnlace = 0;
        for (JsonNode nodo : base.get("temas")) {
            TemaConocimiento tema = tema(nodo.get("clave").asText(), Categoria.valueOf(nodo.get("categoria").asText()),
                TipoRespuesta.valueOf(nodo.get("tipoRespuesta").asText()), nodo.get("titulo").asText(), 0);
            var destino = Enriquecedor.destinoDe(tema);
            if (destino.isPresent()) {
                conEnlace++;
                assertThat(Enriquecedor.NOMBRE_DE_DESTINO).as(tema.getClave()).containsKey(destino.get());
            }
        }
        assertThat(conEnlace).isGreaterThanOrEqualTo(45);
        assertThat(Enriquecedor.NOMBRE_DE_DESTINO.keySet())
            .noneMatch(id -> id.startsWith("panel") || id.startsWith("admin") || id.startsWith("consola"));
    }

    // 1.6.0 (7.4.10): un tema aparece entre los relacionados de cada una de
    // sus intenciones, no solo de su categoria principal.
    @Test
    void unTemaConIntencionAdicionalApareceEntreLosRelacionadosDeEsaIntencion() {
        TemaConocimiento recargar = tema("k-recargar", Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.DIRECTA,
            "Cómo conseguir más créditos", 9);
        recargar.asignarIntencionesAdicionales(List.of(Categoria.SUBASTA_Y_COMERCIO));
        List<TemaConocimiento> conRecargar = List.of(publicar, pujar, creditos, saludo, recargar);

        assertThat(Enriquecedor.paraTema(publicar, "x", conRecargar).respuestasRapidas())
            .containsExactly("Cómo conseguir más créditos", "Cómo pujar o comprar en una subasta",
                "Créditos y moneda del juego");
        assertThat(Enriquecedor.paraEscalamiento(List.of(), conRecargar, VistaDelChat.SUBASTAS).respuestasRapidas())
            .contains("Cómo conseguir más créditos");
        assertThat(Enriquecedor.paraEscalamiento(List.of(), conRecargar, VistaDelChat.INVENTARIO).respuestasRapidas())
            .isEmpty();
    }

    // 1.6.0: los relacionados de un tema salen de todas sus intenciones:
    // primero los de su categoria y, si quedan huecos, los de las adicionales.
    @Test
    void losRelacionadosDeUnTemaSalenDeTodasSusIntencionesPrimeroSuCategoria() {
        TemaConocimiento verificar = tema("k-verificar", Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.DIRECTA,
            "Confirmar el correo", 0);
        verificar.asignarIntencionesAdicionales(List.of(Categoria.SUBASTA_Y_COMERCIO, Categoria.SOPORTE_TECNICO));
        TemaConocimiento recuperar = tema("k-recuperar", Categoria.CUENTA_Y_REGISTRO, TipoRespuesta.DIRECTA,
            "Recuperar la contraseña", 0);
        TemaConocimiento requisitos = tema("k-requisitos", Categoria.SOPORTE_TECNICO, TipoRespuesta.DIRECTA,
            "Requisitos del sistema", 0);
        List<TemaConocimiento> todos = List.of(verificar, recuperar, publicar, pujar, requisitos, saludo);

        assertThat(Enriquecedor.relacionadosCon(verificar, todos)).containsExactly("Recuperar la contraseña",
            "Cómo pujar o comprar en una subasta", "Cómo publicar un producto en subasta");
        assertThat(Enriquecedor.paraNoSirvio(verificar, todos, false).respuestasRapidas())
            .containsExactly("Recuperar la contraseña", "Cómo pujar o comprar en una subasta",
                "Cómo publicar un producto en subasta");
        // Sin intenciones adicionales, exactamente lo de antes de 1.6.0.
        assertThat(Enriquecedor.relacionadosCon(recuperar, todos)).containsExactly("Confirmar el correo");
        // Con huecos, llega hasta la ultima intencion adicional.
        assertThat(Enriquecedor.relacionadosCon(verificar, List.of(verificar, requisitos)))
            .containsExactly("Requisitos del sistema");
    }

    @Test
    void cadaVistaApuntaASuCategoria() {
        assertThat(VistaDelChat.INICIO.categoria()).isNull();
        assertThat(VistaDelChat.INVENTARIO.categoria()).isEqualTo(Categoria.PRODUCTO);
        assertThat(VistaDelChat.CUENTA.categoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
    }
}
