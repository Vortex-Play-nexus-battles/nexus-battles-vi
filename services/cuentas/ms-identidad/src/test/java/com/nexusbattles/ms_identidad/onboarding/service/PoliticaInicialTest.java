package com.nexusbattles.ms_identidad.onboarding.service;

import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteParametros;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteProductos;
import com.nexusbattles.ms_identidad.onboarding.cliente.ClienteProductos.Producto;
import com.nexusbattles.ms_identidad.onboarding.service.PasoFallido.Causa;
import com.nexusbattles.ms_identidad.onboarding.service.PoliticaInicial.Fuente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Politica inicial: de donde salen los creditos y el kit (R17)")
class PoliticaInicialTest {

    private ClienteParametros parametros;
    private ClienteProductos productos;

    @BeforeEach
    void preparar() {
        parametros = mock(ClienteParametros.class);
        productos = mock(ClienteProductos.class);
        when(productos.consultar("heroe")).thenReturn(new Producto("heroe", "Guerrero Tanque", "HEROE", null, "PUBLICADO"));
        when(productos.consultar("espada")).thenReturn(new Producto("espada", "Espada", "ARMA", null, "PUBLICADO"));
        when(productos.consultar("casco")).thenReturn(new Producto("casco", "Casco", "ARMADURA", "CASCO", "PUBLICADO"));
        when(productos.consultar("pocion")).thenReturn(new Producto("pocion", "", "ITEM", null, "PUBLICADO"));
    }

    private PoliticaInicial politica(String creditosRespaldo, String kitRespaldo) {
        return new PoliticaInicial(parametros, productos, creditosRespaldo, kitRespaldo);
    }

    private static void causa(Runnable accion, Causa esperada, String texto) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(PasoFallido.class, fallo -> {
            assertThat(fallo.causa()).isEqualTo(esperada);
            assertThat(fallo.getMessage()).contains(texto);
        });
    }

    @Test
    @DisplayName("manda el parametro del sistema cuando tiene valor, aunque haya respaldo")
    void mandaElParametro() {
        when(parametros.valorDe(PoliticaInicial.CLAVE_CREDITOS)).thenReturn(Optional.of("750"));

        PoliticaInicial.CreditosIniciales creditos = politica("500", "").creditos();

        assertThat(creditos.monto()).isEqualTo(750);
        assertThat(creditos.fuente()).isEqualTo(Fuente.PARAMETRO);
    }

    @Test
    @DisplayName("sin decision del PO, el respaldo de DEV, marcado como tal")
    void respaldoMarcado() {
        when(parametros.valorDe(anyString())).thenReturn(Optional.empty());

        PoliticaInicial.CreditosIniciales creditos = politica(" 500 ", "").creditos();

        assertThat(creditos.monto()).isEqualTo(500);
        assertThat(creditos.fuente()).isEqualTo(Fuente.RESPALDO_DEV);
    }

    @Test
    @DisplayName("sin parametro ni respaldo no se inventa una cifra: falta configuracion y lo dice")
    void sinNadaNoSeInventa() {
        when(parametros.valorDe(anyString())).thenReturn(Optional.empty());

        causa(() -> politica(null, null).creditos(), Causa.CONFIGURACION_INCOMPLETA, "JUGADOR_CREDITOS_INICIALES");
        causa(() -> politica("", "").kit(), Causa.CONFIGURACION_INCOMPLETA, "JUGADOR_KIT_INICIAL");
    }

    @Test
    @DisplayName("todo jugador nuevo empieza con creditos: cero, negativos o decimales no valen")
    void montoValido() {
        when(parametros.valorDe(anyString())).thenReturn(Optional.empty());

        causa(() -> politica("0", "").creditos(), Causa.CONFIGURACION_INCOMPLETA, "mayor que cero");
        causa(() -> politica("-5", "").creditos(), Causa.CONFIGURACION_INCOMPLETA, "mayor que cero");
        causa(() -> politica("10.5", "").creditos(), Causa.CONFIGURACION_INCOMPLETA, "no es un entero");
        causa(() -> politica("mil", "").creditos(), Causa.CONFIGURACION_INCOMPLETA, "no es un entero");
        assertThat(politica("500.00", "").creditos().monto()).isEqualTo(500);
    }

    @Test
    @DisplayName("admin-parametros caido: el fallo sube tal cual (se reintenta), no se cae al respaldo")
    void parametrosCaido() {
        when(parametros.valorDe(anyString()))
                .thenThrow(new PasoFallido(Causa.SERVICIO_NO_DISPONIBLE, "admin-parametros sin respuesta"));

        causa(() -> politica("500", "heroe,espada").creditos(), Causa.SERVICIO_NO_DISPONIBLE, "sin respuesta");
    }

    @Test
    @DisplayName("el kit: un heroe y su equipo; tipo, nombre y parte los dice el catalogo")
    void kitValido() {
        when(parametros.valorDe(PoliticaInicial.CLAVE_KIT)).thenReturn(Optional.of("espada; heroe  casco,pocion"));

        PoliticaInicial.KitInicial kit = politica("", "").kit();

        assertThat(kit.fuente()).isEqualTo(Fuente.PARAMETRO);
        assertThat(kit.heroe().id()).isEqualTo("heroe");
        assertThat(kit.heroe().nombre()).isEqualTo("Guerrero Tanque");
        assertThat(kit.equipo()).extracting(PoliticaInicial.ProductoDelKit::id).containsExactly("espada", "casco", "pocion");
        assertThat(kit.equipo().get(1).parte()).isEqualTo("CASCO");
        assertThat(kit.equipo().get(2).nombre()).as("sin nombre en el catalogo se usa el id").isEqualTo("pocion");
    }

    @Test
    @DisplayName("kit de respaldo cuando el parametro no esta decidido")
    void kitDeRespaldo() {
        when(parametros.valorDe(anyString())).thenReturn(Optional.empty());

        PoliticaInicial.KitInicial kit = politica("", "heroe,espada").kit();

        assertThat(kit.fuente()).isEqualTo(Fuente.RESPALDO_DEV);
        verify(productos).consultar("heroe");
    }

    @Test
    @DisplayName("kits imposibles: sin heroe, dos heroes, sin equipo, algo no equipable, armadura sin parte, vacio")
    void kitsInvalidos() {
        when(parametros.valorDe(anyString())).thenReturn(Optional.empty());
        when(productos.consultar("heroe2")).thenReturn(new Producto("heroe2", "Otro", "HEROE", null, "PUBLICADO"));
        when(productos.consultar("habilidad")).thenReturn(new Producto("habilidad", "H", "HABILIDAD", null, "PUBLICADO"));
        when(productos.consultar("coraza")).thenReturn(new Producto("coraza", "C", "ARMADURA", " ", "PUBLICADO"));

        causa(() -> politica("", "espada").kit(), Causa.CONFIGURACION_INCOMPLETA, "exactamente un heroe y tiene 0");
        causa(() -> politica("", "heroe,heroe2,espada").kit(), Causa.CONFIGURACION_INCOMPLETA, "tiene 2");
        causa(() -> politica("", "heroe").kit(), Causa.CONFIGURACION_INCOMPLETA, "HU-SAL-003");
        causa(() -> politica("", "heroe,habilidad").kit(), Causa.CONFIGURACION_INCOMPLETA, "HABILIDAD");
        causa(() -> politica("", "heroe,coraza").kit(), Causa.CONFIGURACION_INCOMPLETA, "que parte");
        causa(() -> politica("", " , ; ").kit(), Causa.CONFIGURACION_INCOMPLETA, "no tiene productos");
    }

    @Test
    @DisplayName("un producto del kit que no existe sube como configuracion incompleta del catalogo")
    void productoInexistente() {
        when(parametros.valorDe(anyString())).thenReturn(Optional.empty());
        when(productos.consultar("fantasma"))
                .thenThrow(new PasoFallido(Causa.CONFIGURACION_INCOMPLETA, "el producto fantasma no existe"));

        causa(() -> politica("", "heroe,fantasma").kit(), Causa.CONFIGURACION_INCOMPLETA, "fantasma");
    }

    @Test
    @DisplayName("los creditos no consultan el catalogo")
    void creditosSinCatalogo() {
        when(parametros.valorDe(anyString())).thenReturn(Optional.of("100"));
        ClienteProductos sinUso = mock(ClienteProductos.class);

        new PoliticaInicial(parametros, sinUso, "", "").creditos();

        verifyNoInteractions(sinUso);
    }
}
