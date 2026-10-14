package com.nexusbattles.ms_chatbot.chat.motor.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// ms-chatbot.yaml 1.6.0 (7.4.10 «asociar respuestas con multiples intenciones»).
class TemaConocimientoTest {

    private static TemaConocimiento tema(Categoria categoria) {
        return new TemaConocimiento(null, "clave-creditos", categoria, TipoRespuesta.DIRECTA, "Créditos",
            "creditos", null, "Respuesta", null, 0, true);
    }

    @Test
    void atiendeSuCategoriaYSusIntencionesAdicionales() {
        TemaConocimiento creditos = tema(Categoria.CUENTA_Y_REGISTRO);
        creditos.asignarIntencionesAdicionales(List.of(Categoria.SUBASTA_Y_COMERCIO));

        assertThat(creditos.atiende(Categoria.CUENTA_Y_REGISTRO)).isTrue();
        assertThat(creditos.atiende(Categoria.SUBASTA_Y_COMERCIO)).isTrue();
        assertThat(creditos.atiende(Categoria.PRODUCTO)).isFalse();
        assertThat(creditos.atiende(null)).isFalse();
    }

    // Se devuelven en el orden del enum (el del contrato), sin poder tocarlas.
    @Test
    void lasIntencionesSalenEnElOrdenDelEnumYNoSeModificanDesdeFuera() {
        TemaConocimiento creditos = tema(Categoria.CUENTA_Y_REGISTRO);
        creditos.asignarIntencionesAdicionales(List.of(Categoria.FAQ_GENERAL, Categoria.PRODUCTO));

        List<Categoria> intenciones = creditos.getIntencionesAdicionales();

        assertThat(intenciones).containsExactly(Categoria.PRODUCTO, Categoria.FAQ_GENERAL);
        assertThatThrownBy(() -> intenciones.add(Categoria.SOPORTE_TECNICO))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void asignarReemplazaLasAnterioresYNullLasQuitaTodas() {
        TemaConocimiento creditos = tema(Categoria.CUENTA_Y_REGISTRO);
        creditos.asignarIntencionesAdicionales(List.of(Categoria.PRODUCTO, Categoria.FAQ_GENERAL));

        creditos.asignarIntencionesAdicionales(List.of(Categoria.FAQ_GENERAL, Categoria.SOPORTE_TECNICO));
        assertThat(creditos.getIntencionesAdicionales()).containsExactly(Categoria.SOPORTE_TECNICO,
            Categoria.FAQ_GENERAL);

        creditos.asignarIntencionesAdicionales(null);
        assertThat(creditos.getIntencionesAdicionales()).isEmpty();
    }

    // El servicio ya responde 400 antes; la entidad no se deja dejar mal.
    @Test
    void noAceptaLaPrincipalNiMasDeCinco() {
        TemaConocimiento creditos = tema(Categoria.CUENTA_Y_REGISTRO);

        assertThatThrownBy(() -> creditos.asignarIntencionesAdicionales(List.of(Categoria.CUENTA_Y_REGISTRO)))
            .isInstanceOf(IllegalArgumentException.class);
        List<Categoria> seis = new ArrayList<>(List.of(Categoria.values()));
        seis.remove(Categoria.CUENTA_Y_REGISTRO);
        seis.remove(Categoria.FAQ_GENERAL);
        assertThat(seis).hasSize(6);
        assertThatThrownBy(() -> creditos.asignarIntencionesAdicionales(seis))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(creditos.getIntencionesAdicionales()).isEmpty();
    }

    // Crear la candidata copia cada tema: la copia atiende lo mismo y es
    // independiente (cambiar una no cambia la otra).
    @Test
    void laCopiaParaOtraVersionLlevaSusIntenciones() {
        TemaConocimiento original = tema(Categoria.CUENTA_Y_REGISTRO);
        original.asignarIntencionesAdicionales(List.of(Categoria.SUBASTA_Y_COMERCIO));
        VersionBaseConocimiento candidata = VersionBaseConocimiento.nuevaCandidata(3, null);

        TemaConocimiento copia = original.copiarA(candidata);

        assertThat(copia.getVersion()).isSameAs(candidata);
        assertThat(copia.getClave()).isEqualTo(original.getClave());
        assertThat(copia.getIntencionesAdicionales()).containsExactly(Categoria.SUBASTA_Y_COMERCIO);
        copia.asignarIntencionesAdicionales(List.of(Categoria.PRODUCTO));
        assertThat(original.getIntencionesAdicionales()).containsExactly(Categoria.SUBASTA_Y_COMERCIO);
    }

    // Editar cambia la categoria primero: la nueva principal no puede quedar
    // tambien como adicional.
    @Test
    void actualizarValidaLasIntencionesContraLaNuevaCategoria() {
        TemaConocimiento creditos = tema(Categoria.CUENTA_Y_REGISTRO);
        creditos.asignarIntencionesAdicionales(List.of(Categoria.SUBASTA_Y_COMERCIO));

        creditos.actualizar(Categoria.SUBASTA_Y_COMERCIO, TipoRespuesta.DIRECTA, "Créditos", "creditos", null,
            "Respuesta", null, 0, true, List.of(Categoria.CUENTA_Y_REGISTRO));

        assertThat(creditos.getCategoria()).isEqualTo(Categoria.SUBASTA_Y_COMERCIO);
        assertThat(creditos.getIntencionesAdicionales()).containsExactly(Categoria.CUENTA_Y_REGISTRO);
        assertThatThrownBy(() -> creditos.actualizar(Categoria.PRODUCTO, TipoRespuesta.DIRECTA, "Créditos",
            "creditos", null, "Respuesta", null, 0, true, List.of(Categoria.PRODUCTO)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // 1.17.0 (7.4.2): el titulo en ingles, opcional.
    @Test
    void elTituloEnInglesEsOpcionalYLaCopiaLoConserva() {
        TemaConocimiento creditos = tema(Categoria.SUBASTA_Y_COMERCIO);
        assertThat(creditos.titulo(true)).isEqualTo("Créditos");
        assertThat(creditos.titulo(false)).isEqualTo("Créditos");

        creditos.asignarTituloEn("  Credits  ");
        assertThat(creditos.getTituloEn()).isEqualTo("Credits");
        assertThat(creditos.titulo(true)).isEqualTo("Credits");
        assertThat(creditos.titulo(false)).isEqualTo("Créditos");
        assertThat(creditos.copiarA(null).getTituloEn()).isEqualTo("Credits");

        creditos.asignarTituloEn("   ");
        assertThat(creditos.getTituloEn()).isNull();
        creditos.asignarTituloEn(null);
        assertThat(creditos.titulo(true)).isEqualTo("Créditos");
    }
}
