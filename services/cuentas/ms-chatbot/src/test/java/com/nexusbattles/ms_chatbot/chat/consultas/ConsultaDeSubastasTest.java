package com.nexusbattles.ms_chatbot.chat.consultas;

import com.nexusbattles.ms_chatbot.chat.consultas.dto.PaginaDeSubastasDto;
import com.nexusbattles.ms_chatbot.chat.consultas.dto.SubastaActivaDto;
import com.nexusbattles.ms_chatbot.chat.enriquecido.EnlaceInterno;
import com.nexusbattles.ms_chatbot.chat.enriquecido.TarjetaInformativa;
import com.nexusbattles.ms_chatbot.chat.model.ResultadoDeRespuesta;
import com.nexusbattles.ms_chatbot.chat.motor.ResultadoMotor;
import com.nexusbattles.ms_chatbot.chat.motor.model.Categoria;
import com.nexusbattles.ms_chatbot.chat.preferencias.IdiomaPreferido;
import com.nexusbattles.ms_chatbot.chat.texto.NormalizadorTexto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// ms-chatbot.yaml 1.16.0 (7.4.4 «estado de productos en la subasta»): lo que
// hay ahora en la subasta, para visitantes y jugadores, del listado publico.
class ConsultaDeSubastasTest {

    private static final Instant PRONTO = Instant.parse("2026-10-15T17:30:00Z");
    private static final Instant DESPUES = Instant.parse("2026-10-16T02:00:00Z");
    private static final String RECURSO = "/api/v1/productos/recursos/" + UUID.randomUUID();

    private final SubastasClient subastas = mock(SubastasClient.class);
    private final ConsultaDeSubastas consulta = new ConsultaDeSubastas(subastas);

    private static SubastaActivaDto subasta(String nombre, String tipo, String oferta, Instant fin) {
        return new SubastaActivaDto(nombre, tipo, new BigDecimal(oferta), null, 2, fin, null);
    }

    private static PaginaDeSubastasDto pagina(long total, SubastaActivaDto... subastas) {
        return new PaginaDeSubastasDto(List.of(subastas), total);
    }

    private static boolean pide(String pregunta) {
        return ConsultaDeSubastas.pideLoQueHay(NormalizadorTexto.normalizar(pregunta));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "¿Qué hay ahora en la subasta?", "subastas activas", "¿Cuántas subastas hay?", "¿hay espadas en subasta?",
        "¿Cómo va la subasta del hacha?", "¿Puedo ver las subastas abiertas?", "estado de las subastas",
        "¿Qué se está subastando?", "muéstrame las subastas que cierran pronto", "¿Hay alguna subasta abierta?",
        "what's on auction right now?", "is there any sword on auction?", "show me the open auctions",
        "¿qué hay en la subsata?"})
    void reconoceLasPreguntasPorLoQueHayAhora(String pregunta) {
        assertThat(pide(pregunta)).as(pregunta).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "¿Cómo funciona la subasta?", "¿Cómo publico una subasta?", "¿Cuánto cuesta publicar una subasta?",
        "¿Hay límite de subastas?", "¿Cuál es la comisión de la subasta?", "¿Qué es una subasta?",
        "¿Puedo cancelar una subasta?", "¿Hay que pagar para subastar?", "¿Cómo van mis subastas?",
        "¿Qué subastas tengo?", "¿Hay un tope de pujas en la subasta?", "how do auctions work?",
        "what is an auction?", "¿Qué tienda hay?", "", "subastas", "¿Cuánto cobran en la subasta?",
        "¿Cuál es la duración máxima de una subasta?", "¿Dónde recojo lo que gané en la subasta?"})
    void noTomaLasReglasNiLoSuyoNiLoQueNoEsDeLaSubasta(String pregunta) {
        assertThat(pide(pregunta)).as(pregunta).isFalse();
    }

    @Test
    void sinNadaQueBuscarDiceCuantasHayYLasQueCierranAntes() {
        when(subastas.activas(null, ConsultaDeSubastas.A_MOSTRAR)).thenReturn(pagina(7,
            new SubastaActivaDto("Hacha del Norte", "ARMA", new BigDecimal("120.00"), new BigDecimal("300"), 2,
                PRONTO, RECURSO),
            new SubastaActivaDto("Orbe", "ITEM", new BigDecimal("75.5"), null, 0, DESPUES, "espada.png")));

        ResultadoMotor resultado = consulta.responder("¿Qué hay ahora en la subasta?", IdiomaPreferido.AUTOMATICO)
            .orElseThrow();

        assertThat(resultado.texto()).isEqualTo("Hay 7 subasta(s) abierta(s) ahora. Las que cierran antes: "
            + "«Hacha del Norte» (oferta vigente 120, 2 puja(s), cierra el 15/10 a las 12:30); «Orbe» (oferta "
            + "vigente 75.5, 0 puja(s), cierra el 15/10 a las 21:00).");
        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);
        assertThat(resultado.categoria()).isEqualTo(Categoria.SUBASTA_Y_COMERCIO);
        List<TarjetaInformativa> tarjetas = resultado.enriquecido().tarjetas();
        assertThat(tarjetas).extracting(TarjetaInformativa::titulo).containsExactly("Hacha del Norte", "Orbe");
        assertThat(tarjetas.get(0).texto()).isEqualTo("Oferta vigente: 120 créditos · 2 puja(s) · cierra el 15/10 a "
            + "las 12:30 · compra inmediata: 300 créditos.");
        // La imagen solo si es del catalogo (ImagenDeProducto): un nombre suelto, no.
        assertThat(tarjetas).extracting(TarjetaInformativa::imagen).containsExactly(RECURSO, null);
        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::destino).containsExactly("subastas");
    }

    @Test
    void sinNingunaAbiertaLoDiceYEnInglesSiPreguntaEnIngles() {
        when(subastas.activas(null, ConsultaDeSubastas.A_MOSTRAR)).thenReturn(pagina(0));

        assertThat(consulta.responder("subastas activas", IdiomaPreferido.AUTOMATICO).orElseThrow().texto())
            .isEqualTo("Ahora no hay ninguna subasta abierta. Puedes publicar una en «Subasta», con «Publicar "
                + "subasta».");
        ResultadoMotor enIngles = consulta.responder("what's on auction right now?", IdiomaPreferido.AUTOMATICO)
            .orElseThrow();
        assertThat(enIngles.texto()).startsWith("There are no open auctions right now.");
        assertThat(enIngles.ingles()).isTrue();
        assertThat(enIngles.enriquecido().enlaces()).extracting(EnlaceInterno::destino).containsExactly("subastas");
    }

    @Test
    void unProductoSeBuscaSinTildesEnPluralYConUnErrorDeTecleoYTambienPorSuTipo() {
        when(subastas.activas(null, ConsultaDeSubastas.ACTIVAS_QUE_SE_MIRAN)).thenReturn(pagina(3,
            subasta("Poción de vida", "ITEM", "20", PRONTO),
            subasta("Espada de fuego", "ARMA", "90", PRONTO),
            subasta("Espada corta", "ARMA", "40", DESPUES)));

        ResultadoMotor espadas = consulta.responder("¿hay espadas en subasta?", IdiomaPreferido.AUTOMATICO)
            .orElseThrow();
        assertThat(espadas.texto()).startsWith("Hay 2 subasta(s) abierta(s) de «espadas»: «Espada de fuego»");
        assertThat(espadas.enriquecido().tarjetas()).extracting(TarjetaInformativa::titulo)
            .containsExactly("Espada de fuego", "Espada corta");

        assertThat(consulta.responder("¿cómo va la subasta de la pocion?", IdiomaPreferido.AUTOMATICO).orElseThrow()
            .enriquecido().tarjetas()).extracting(TarjetaInformativa::titulo).containsExactly("Poción de vida");
        assertThat(consulta.responder("¿hay pociones en subasta?", IdiomaPreferido.AUTOMATICO).orElseThrow()
            .enriquecido().tarjetas()).extracting(TarjetaInformativa::titulo).containsExactly("Poción de vida");
        // «espda»: un error de tecleo; gana la que comparte mas palabras.
        assertThat(consulta.responder("¿hay alguna espda de fuego en subasta?", IdiomaPreferido.AUTOMATICO)
            .orElseThrow().enriquecido().tarjetas()).extracting(TarjetaInformativa::titulo)
            .containsExactly("Espada de fuego");
        // «armas»: el tipo del producto.
        assertThat(consulta.responder("¿hay armas en subasta?", IdiomaPreferido.AUTOMATICO).orElseThrow()
            .enriquecido().tarjetas()).hasSize(2);
        verify(subastas, never()).activas(anyString(), anyInt());
    }

    @Test
    void siNoHayDeEseProductoLoDiceConCuantasHayDeOtros() {
        when(subastas.activas(null, ConsultaDeSubastas.ACTIVAS_QUE_SE_MIRAN)).thenReturn(pagina(2,
            subasta("Orbe", "ITEM", "20", PRONTO), subasta("Escudo", "ARMADURA", "30", DESPUES)));

        ResultadoMotor resultado = consulta.responder("¿hay espadas en subasta?", IdiomaPreferido.AUTOMATICO)
            .orElseThrow();

        assertThat(resultado.texto()).isEqualTo("Ahora no hay subastas abiertas de «espadas». Hay 2 abierta(s) de "
            + "otros productos.");
        assertThat(resultado.enriquecido().tarjetas()).isEmpty();
        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.CONSULTA_EN_VIVO);

        when(subastas.activas(null, ConsultaDeSubastas.ACTIVAS_QUE_SE_MIRAN)).thenReturn(pagina(0));
        assertThat(consulta.responder("is there any sword on auction?", IdiomaPreferido.AUTOMATICO).orElseThrow()
            .texto()).isEqualTo("There are no open auctions for «sword» right now; in fact, there are none open.");
    }

    // Con mas abiertas de las que se miran, la busqueda del propio listado,
    // con la palabra mas larga en singular.
    @Test
    void conMasAbiertasDeLasQueSeMiranBuscaEnElListado() {
        List<SubastaActivaDto> cien = new ArrayList<>();
        for (int i = 0; i < ConsultaDeSubastas.ACTIVAS_QUE_SE_MIRAN; i++) {
            cien.add(subasta("Orbe " + i, "ITEM", "10", PRONTO));
        }
        when(subastas.activas(null, ConsultaDeSubastas.ACTIVAS_QUE_SE_MIRAN))
            .thenReturn(new PaginaDeSubastasDto(cien, 140));
        when(subastas.activas("armadura", ConsultaDeSubastas.A_MOSTRAR))
            .thenReturn(pagina(9, subasta("Armadura de placas", "ARMADURA", "200", DESPUES)));

        ResultadoMotor resultado = consulta.responder("¿hay armaduras en subasta?", IdiomaPreferido.AUTOMATICO)
            .orElseThrow();

        assertThat(resultado.texto()).startsWith("Hay 9 subasta(s) abierta(s) de «armaduras»: «Armadura de placas»");
    }

    @Test
    void siLaSubastaNoRespondeLoDiceSinInventarNada() {
        when(subastas.activas(isNull(), anyInt())).thenThrow(new ResourceAccessException("caido"));

        ResultadoMotor resultado = consulta.responder("¿Qué hay en la subasta?", IdiomaPreferido.ES).orElseThrow();

        assertThat(resultado.texto()).isEqualTo("No pude consultar la subasta en este momento: el servicio no "
            + "respondió a tiempo. Inténtalo de nuevo en unos minutos.");
        assertThat(resultado.resultado()).isEqualTo(ResultadoDeRespuesta.NO_DISPONIBLE);
        assertThat(resultado.enriquecido().enlaces()).extracting(EnlaceInterno::destino).containsExactly("subastas");
    }

    @Test
    void loQueNoEsDeLaSubastaNiSiquieraConsulta() {
        assertThat(consulta.responder("¿Cómo funciona la subasta?", IdiomaPreferido.AUTOMATICO)).isEmpty();
        assertThat(consulta.responder(null, IdiomaPreferido.AUTOMATICO)).isEmpty();
        assertThat(consulta.responder("   ", null)).isEmpty();
        verifyNoInteractions(subastas);
    }

    @Test
    void laPreferenciaDeIdiomaMandaSobreElDeLaPregunta() {
        when(subastas.activas(null, ConsultaDeSubastas.A_MOSTRAR)).thenReturn(pagina(0));

        assertThat(consulta.responder("subastas activas", IdiomaPreferido.EN).orElseThrow().ingles()).isTrue();
        assertThat(consulta.responder("what's on auction now", IdiomaPreferido.ES).orElseThrow().ingles()).isFalse();
    }

    @Test
    void piezas() {
        assertThat(ConsultaDeSubastas.palabrasDelProducto("hay alguna espada de fuego en la subasta ahora"))
            .containsExactly("espada", "fuego");
        assertThat(ConsultaDeSubastas.formas("pociones")).containsExactly("pociones", "pocione", "pocion");
        assertThat(ConsultaDeSubastas.formas("orbe")).containsExactly("orbe");
        assertThat(ConsultaDeSubastas.paraElListado(List.of("hacha", "armaduras"))).isEqualTo("armadura");
        assertThat(ConsultaDeSubastas.paraElListado(List.of("orbe"))).isEqualTo("orbe");
        assertThat(ConsultaDeSubastas.pideLoQueHay(null)).isFalse();
        assertThat(TextosDeConsultas.creditos(null)).isEqualTo("—");
        assertThat(TextosDeConsultas.creditos("no es un numero")).isEqualTo("no es un numero");
        assertThat(TextosDeConsultas.creditos(new BigDecimal("1E+3"))).isEqualTo("1000");
        verify(subastas, never()).activas(eq("x"), anyInt());
    }
}
