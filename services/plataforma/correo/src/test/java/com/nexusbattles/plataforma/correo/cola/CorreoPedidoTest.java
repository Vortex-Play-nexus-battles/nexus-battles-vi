package com.nexusbattles.plataforma.correo.cola;

import com.nexusbattles.plataforma.correo.template.Plantilla;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorreoPedidoTest {

    @Test
    void losDatosNulosSeDescartanYElMapaNoSePuedeModificar() {
        Map<String, Object> datos = new HashMap<>();
        datos.put("apodo", "ElGuerrero");
        datos.put("hasta", null);

        CorreoPedido pedido = CorreoPedido.paraEnviar(Plantilla.SANCION, "j@ejemplo.com", "Aviso", datos);

        assertThat(pedido.datos()).containsOnlyKeys("apodo");
        assertThatThrownBy(() -> pedido.datos().put("otro", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(pedido.debeEnviarse()).isTrue();
    }

    @Test
    void unPedidoSuprimidoNoLlevaDatosYSiSuMotivo() {
        CorreoPedido pedido = CorreoPedido.suprimido(Plantilla.SUBASTA, "j@ejemplo.com", "Aviso", "no quiere");

        assertThat(pedido.debeEnviarse()).isFalse();
        assertThat(pedido.motivoDeOmision()).isEqualTo("no quiere");
        assertThat(pedido.datos()).isEmpty();
    }

    @Test
    void sinPlantillaDestinatarioOAsuntoNoHayPedido() {
        assertThatThrownBy(() -> CorreoPedido.paraEnviar(null, "j@ejemplo.com", "a", Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CorreoPedido.paraEnviar(Plantilla.MISION, null, "a", Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CorreoPedido.paraEnviar(Plantilla.MISION, "j@ejemplo.com", null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThat(CorreoPedido.paraEnviar(Plantilla.MISION, "j@ejemplo.com", "a", null).datos()).isEmpty();
    }
}
