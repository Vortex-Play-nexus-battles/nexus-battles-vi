package com.nexusbattles.ms_chatbot.chat.enriquecido;

import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.motor.model.TemaConocimiento;
import com.nexusbattles.ms_chatbot.chat.motor.model.TipoRespuesta;
import org.junit.jupiter.api.Test;

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
        assertThat(enriquecida.enlaces()).containsExactly(new EnlaceInterno("Ir a Subastas", "subastas"));
        // El de mayor prioridad primero; ni el propio tema ni el de cortesia.
        assertThat(enriquecida.respuestasRapidas()).containsExactly("Cómo pujar o comprar en una subasta",
            "Créditos y moneda del juego");
        assertThat(enriquecida.ofrecerSoporteHumano()).isFalse();
    }

    @Test
    void unTemaDirectoNoTraePasos() {
        RespuestaEnriquecida enriquecida = Enriquecedor.paraTema(creditos, "1) uno 2) dos", temas);

        assertThat(enriquecida.pasos()).isEmpty();
        assertThat(enriquecida.enlaces()).extracting(EnlaceInterno::destino).containsExactly("tienda");
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
        assertThat(Enriquecedor.destinoDeTitulo("Recuperar mi contraseña")).contains("login");
        assertThat(Enriquecedor.destinoDeTitulo("Gestionar mi cuenta y mi perfil")).contains("perfil");
        assertThat(Enriquecedor.destinoDeTitulo("Cómo completar una misión (modo JcE)")).contains("misiones");
        assertThat(Enriquecedor.destinoDeTitulo("Cómo funciona el Torneo")).contains("torneos");
        assertThat(Enriquecedor.destinoDeTitulo("Héroes disponibles")).contains("productos");
        assertThat(Enriquecedor.destinoDeTitulo("Sistema de sanciones")).isEmpty();
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

    @Test
    void cadaVistaApuntaASuCategoria() {
        assertThat(VistaDelChat.INICIO.categoria()).isNull();
        assertThat(VistaDelChat.INVENTARIO.categoria()).isEqualTo(Categoria.PRODUCTO);
        assertThat(VistaDelChat.CUENTA.categoria()).isEqualTo(Categoria.CUENTA_Y_REGISTRO);
    }
}
