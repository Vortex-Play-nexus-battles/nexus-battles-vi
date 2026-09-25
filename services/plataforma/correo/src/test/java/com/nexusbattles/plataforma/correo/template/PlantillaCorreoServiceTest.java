package com.nexusbattles.plataforma.correo.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantillaCorreoServiceTest {

    private final PlantillaCorreoService service = new PlantillaCorreoService();

    @Test
    void incluyeElEncabezadoCorporativo() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).contains("THE NEXUS BATTLES VI");
    }

    @Test
    void incluyeElPieDePaginaCorporativo() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).contains("The Nexus Battles VI");
        assertThat(html).contains("instagram.com/thenexusbattles");
    }

    @Test
    void inyectaElContenidoDinamicoRecibido() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).contains("Hola Mundo");
    }

    @Test
    void noDejaMarcadoresDePlantillaSinReemplazar() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html).doesNotContain("CONTENIDO_DINAMICO");
    }

    @Test
    void referenciaElLogoCorporativoComoImagenIncrustada() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html)
                .as("el logo debe ir incrustado (cid:), no como URL ni en base64")
                .contains("src=\"cid:logo-nexus\"");
    }

    @Test
    void conservaElTextoDeMarcaAunqueElClienteBloqueeImagenes() {
        String html = service.renderizar("email/plantilla-prueba", Map.of("mensaje", "Hola Mundo"));

        assertThat(html)
                .as("muchos clientes bloquean imágenes; sin el texto el encabezado queda vacío")
                .contains("THE NEXUS BATTLES VI");
        assertThat(html)
                .as("el texto alternativo es lo único que se ve si la imagen no carga")
                .contains("alt=\"The Nexus Battles VI\"");
    }

    @Test
    void elArchivoDelLogoExisteEnElClasspath() {
        assertThat(getClass().getResourceAsStream("/imagenes/logo-nexus.png"))
                .as("EnviadorCorreoService lo adjunta desde ahí; si falta, todo correo falla")
                .isNotNull();
    }

    @Test
    void rechazaUnaPlantillaQueNoEstaEnElRegistro() {
        // El nombre de la plantilla nunca debe poder venir del usuario: cargar
        // una arbitraria del classpath permitiria leer plantillas ajenas o
        // inyectar contenido. Solo se sirven las declaradas.
        assertThatThrownBy(() -> service.renderizar("email/inventada", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email/inventada");
    }

    @Test
    void rechazaIntentosDeSalirseDeLaCarpetaDePlantillas() {
        assertThatThrownBy(() -> service.renderizar("../../application", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conservaLosAcentosYLaEneAlRenderizar() {
        String html = service.renderizar("email/recuperacion-clave",
                Map.of("apodo", "ElGuerrero", "codigo", "482915", "minutosVigencia", 15));

        assertThat(html)
                .as("si la plantilla se lee con la codificacion equivocada, los correos salen con simbolos raros")
                .contains("contraseña")
                .doesNotContain("�");
    }

    // ----- HU-COR-002: plantilla de confirmacion de cuenta -----

    @Test
    void laConfirmacionDeCuentaVaSobreLaPlantillaCorporativa() {
        // CP-01 de #24: "el correo llega con el codigo legible y aplicando el
        // diseno de la plantilla corporativa oficial".
        String html = service.renderizar("email/confirmacion-cuenta",
                Map.of("apodo", "ElGuerrero", "codigo", "734201", "minutosVigencia", 15));

        assertThat(html)
                .contains("THE NEXUS BATTLES VI")
                .contains("src=\"cid:logo-nexus\"")
                .contains("instagram.com/thenexusbattles");
    }

    @Test
    void laConfirmacionDeCuentaMuestraElCodigoLaVigenciaYElApodo() {
        String html = service.renderizar("email/confirmacion-cuenta",
                Map.of("apodo", "ElGuerrero", "codigo", "734201", "minutosVigencia", 15,
                        "proposito", "VERIFICACION"));

        assertThat(html)
                .contains("Confirma tu cuenta")
                .contains("ElGuerrero")
                .contains("734201")
                .contains("15</strong> minutos")
                .as("debe decir que se puede pedir uno nuevo y que el anterior deja de servir (CA-03)")
                .contains("pide uno nuevo")
                // El valor de muestra de la celda del codigo (">000000</td>") debe
                // quedar sustituido. No se busca "000000" a secas: el layout
                // corporativo lleva "background:#000000" en su CSS.
                .doesNotContain(">000000</td>")
                .doesNotContain("�");
    }

    @Test
    void laConfirmacionDeCuentaNoDejaValoresDeEjemploSiFaltaUnaVariable() {
        // Si ms-identidad mandara el codigo vacio, la plantilla no debe rellenar
        // con el "000000" de muestra: quedaria un correo que parece valido.
        // (El "#000000" del CSS del layout corporativo no cuenta: es un color.)
        String html = service.renderizar("email/confirmacion-cuenta",
                Map.of("apodo", "ElGuerrero", "codigo", "", "minutosVigencia", 15));

        assertThat(html)
                .doesNotContain(">000000</td>")
                .as("la celda del codigo queda vacia, no con el valor de muestra")
                .contains("padding:20px 16px;\"></td>");
    }

    // ----- HU-COR-005: plantillas de mision y subasta -----

    @Test
    void laPlantillaDeMisionVaSobreLaPlantillaCorporativaYMuestraElContenido() {
        String html = service.renderizar("email/mision",
                Map.of("apodo", "ElGuerrero", "asunto", "Nueva misión disponible", "mensaje", "Derrota al dragón"));

        assertThat(html)
                .contains("THE NEXUS BATTLES VI")
                .contains("ElGuerrero")
                .contains("Nueva misión disponible")
                .contains("Derrota al dragón");
    }

    // ----- HU-AUT-006: aviso de cambio de contraseña -----

    @Test
    void laPlantillaDeCambioDeClaveVaSobreLaCorporativaYNuncaMuestraContrasenas() {
        String html = service.renderizar("email/cambio-clave",
                Map.of("apodo", "ElGuerrero", "ip", "190.85.12.44", "fechaHora", "21/09/2026 a las 15:00 (GMT-05:00)"));

        assertThat(html)
                .contains("THE NEXUS BATTLES VI")
                .contains("Tu contraseña cambió")
                .contains("ElGuerrero")
                .contains("190.85.12.44")
                .contains("21/09/2026 a las 15:00")
                .contains("Olvidé mi contraseña")
                .doesNotContain("${");
    }

    @Test
    void laPlantillaDeSubastaVaSobreLaPlantillaCorporativaYMuestraElContenido() {
        String html = service.renderizar("email/subasta",
                Map.of("apodo", "ElGuerrero", "asunto", "Ganaste la subasta", "mensaje", "Espada Legendaria"));

        assertThat(html)
                .contains("THE NEXUS BATTLES VI")
                .contains("ElGuerrero")
                .contains("Ganaste la subasta")
                .contains("Espada Legendaria");
    }

    // ----- 1.4.0: confirmacion de cuenta segun su proposito, con enlace -----

    private static final String ENLACE_VERIFICACION =
            "http://localhost/verificar#codigo=734201&correo=nuevo%40nexusbattles.test";

    @Test
    void laVerificacionDeUnJugadorLlevaElBotonAVerificarYElEnlaceEnClaro() {
        String html = service.renderizar("email/confirmacion-cuenta", Map.of(
                "apodo", "ElGuerrero", "codigo", "734201", "minutosVigencia", 15,
                "proposito", "VERIFICACION", "enlace", ENLACE_VERIFICACION));

        assertThat(html)
                .contains("Confirma tu cuenta")
                .contains("Gracias por registrarte")
                .contains("pantalla de verificación")
                .contains("Confirmar mi correo")
                .as("el enlace va en el boton y, por si el boton no funciona, a la vista")
                .contains("href=\"http://localhost/verificar#codigo=734201&amp;correo=nuevo%40nexusbattles.test\"")
                .contains("Si el botón no funciona")
                .contains("Si no creaste esta cuenta")
                .doesNotContain("Super Administrador")
                .doesNotContain("�");
    }

    @Test
    void laActivacionDeUnaCuentaAdministrativaPideElegirContrasena() {
        String html = service.renderizar("email/confirmacion-cuenta", Map.of(
                "apodo", "Moderadora", "codigo", "K7QX2M9P", "minutosVigencia", 1440,
                "proposito", "ACTIVACION",
                "enlace", "http://localhost/restablecer#codigo=K7QX2M9P&correo=mod%40upb.edu.co"));

        assertThat(html)
                .contains("Activa tu cuenta")
                .contains("Un Super Administrador creó tu cuenta")
                .contains("Elegir mi contraseña")
                .contains("href=\"http://localhost/restablecer#codigo=K7QX2M9P&amp;correo=mod%40upb.edu.co\"")
                .contains("1440</strong> minutos")
                .contains("pide a quien creó tu cuenta")
                .doesNotContain("Gracias por registrarte")
                .doesNotContain("Confirmar mi correo");
    }

    @Test
    void sinPropositoLaConfirmacionEsUnaActivacion() {
        String html = service.renderizar("email/confirmacion-cuenta",
                Map.of("apodo", "ElGuerrero", "codigo", "734201", "minutosVigencia", 15));

        assertThat(html).contains("Activa tu cuenta").doesNotContain("Confirma tu cuenta");
    }

    @Test
    void sinEnlaceNoSePintaUnBotonQueNoLlevaANingunSitio() {
        String html = service.renderizar("email/confirmacion-cuenta", Map.of(
                "apodo", "ElGuerrero", "codigo", "734201", "minutosVigencia", 15, "proposito", "VERIFICACION"));

        assertThat(html)
                .contains("734201")
                .doesNotContain("Confirmar mi correo")
                .doesNotContain("Si el botón no funciona")
                .doesNotContain("href=\"#\"");
    }

    @Test
    void laRecuperacionLlevaElBotonARestablecerYMencionaLasPreguntasDeSeguridad() {
        String html = service.renderizar("email/recuperacion-clave", Map.of(
                "apodo", "ElGuerrero", "codigo", "482915", "minutosVigencia", 30,
                "enlace", "http://localhost/restablecer#codigo=482915&correo=j%40gmail.com"));

        assertThat(html)
                .contains("Recupera tu contraseña")
                .contains("482915")
                .contains("Restablecer mi contraseña")
                .contains("href=\"http://localhost/restablecer#codigo=482915&amp;correo=j%40gmail.com\"")
                .contains("30</strong> minutos")
                .contains("preguntas de seguridad")
                .contains("Si no pediste este cambio");
    }

    // ----- 1.4.1: aviso de sancion (7.3.2 y 7.3.7) -----

    @Test
    void laSuspensionDiceQueNoSePuedeEntrarHastaCuandoPorQueYComoApelar() {
        String html = service.renderizar("email/sancion", Map.of(
                "apodo", "ElGuerrero", "tipo", "SUSPENSION", "motivo", "Acoso a otros jugadores",
                "hasta", "01/10/2026 a las 18:00 (GMT-05:00)",
                "apelableHasta", "25/10/2026 a las 23:59 (GMT-05:00)"));

        assertThat(html)
                .contains("THE NEXUS BATTLES VI")
                .contains("Tu cuenta está suspendida")
                .contains("no podrás acceder al sistema")
                .contains("tu inventario")
                .contains("Acoso a otros jugadores")
                .contains("Fin de la suspensión: <strong>01/10/2026 a las 18:00 (GMT-05:00)</strong>")
                .contains("puedes apelar esta decisión hasta el")
                .contains("25/10/2026 a las 23:59 (GMT-05:00)")
                .contains("15 días hábiles")
                .doesNotContain("Resultado:")
                .doesNotContain("�");
    }

    @Test
    void elBaneoEsDefinitivoYCongelaElInventario() {
        // Las frases de la plantilla estan partidas en varias lineas del
        // fuente; en HTML esos saltos son espacios.
        String html = service.renderizar("email/sancion", Map.of(
                        "apodo", "ElGuerrero", "tipo", "BANEO", "motivo", "Suplantación de identidad"))
                .replaceAll("\\s+", " ");

        assertThat(html)
                .contains("Tu cuenta fue baneada de forma definitiva")
                .contains("inhabilitada de forma permanente")
                .contains("inventario queda congelado")
                .contains("Suplantación de identidad")
                .doesNotContain("Fin de la suspensión")
                .as("sin plazo de apelacion no se promete ninguno")
                .doesNotContain("puedes apelar");
    }

    @Test
    void laAdvertenciaNoRestringeElAcceso() {
        String html = service.renderizar("email/sancion", Map.of(
                "apodo", "ElGuerrero", "tipo", "ADVERTENCIA", "motivo", "Lenguaje ofensivo en el chat"));

        assertThat(html)
                .contains("Recibiste una advertencia")
                .contains("no restringe tu")
                .contains("Lenguaje ofensivo en el chat");
    }

    @Test
    void laResolucionDeUnaApelacionDiceElResultado() {
        String html = service.renderizar("email/sancion", Map.of(
                "apodo", "ElGuerrero", "tipo", "APELACION_RESUELTA", "motivo", "Acoso a otros jugadores",
                "resultadoApelacion", "REVERTIDA"));

        assertThat(html)
                .contains("Tu apelación fue resuelta")
                .contains("Resultado:")
                .contains("la sanción se revirtió")
                .doesNotContain("la sanción se mantiene");
    }

    @Test
    void elMotivoSeEscapaComoTextoYNuncaComoHtml() {
        String html = service.renderizar("email/sancion", Map.of(
                "apodo", "ElGuerrero", "tipo", "ADVERTENCIA", "motivo", "<script>alert(1)</script>"));

        assertThat(html).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
    }

    // ----- HU-PAG-003: plantilla de confirmacion de compra -----

    @Test
    void laConfirmacionDeCompraDetallaLosProductosYLaOrden() {
        String html = service.renderizar("email/confirmacion-compra", Map.of(
                "apodo", "ElGuerrero", "monto", "250.00 COP", "concepto", "Compra en la tienda",
                "fechaHora", "23/09/2026 a las 10:15 (GMT-05:00)", "orden", "ORD-2026-0042",
                "lineas", java.util.List.of(
                        Map.of("nombre", "Espada Legendaria", "cantidad", 2,
                                "precioUnitario", "100.00 COP", "subtotal", "200.00 COP"),
                        Map.of("nombre", "Poción", "cantidad", 1, "subtotal", "50.00 COP"))));

        assertThat(html)
                .contains("Producto").contains("Precio unitario").contains("Subtotal")
                .contains("Espada Legendaria").contains(">2<").contains("100.00 COP").contains("200.00 COP")
                .as("sin precio unitario se pinta una raya, no un hueco")
                .contains("Poción").contains(">—<").contains("50.00 COP")
                .contains("Orden: <strong>ORD-2026-0042</strong>")
                .contains("Total pagado: <strong>250.00 COP</strong>");
    }

    @Test
    void laConfirmacionDeCompraSinDetalleNoPintaUnaTablaVacia() {
        String html = service.renderizar("email/confirmacion-compra", Map.of(
                "apodo", "ElGuerrero", "monto", "50000.00 COP", "concepto", "Paquete de créditos x500",
                "fechaHora", "23/09/2026 a las 10:15 (GMT-05:00)"));

        assertThat(html)
                .doesNotContain("Precio unitario")
                .doesNotContain("Orden:")
                .contains("Total pagado: <strong>50000.00 COP</strong>");
    }

    @Test
    void laPlantillaDeConfirmacionDeCompraVaSobreLaPlantillaCorporativaYMuestraElContenido() {
        String html = service.renderizar("email/confirmacion-compra",
                Map.of("apodo", "ElGuerrero", "monto", "50000.00 COP",
                        "concepto", "Paquete de créditos x500",
                        "fechaHora", "23/09/2026 a las 10:15 (GMT-05:00)"));

        assertThat(html)
                .contains("THE NEXUS BATTLES VI")
                .contains("src=\"cid:logo-nexus\"")
                .contains("ElGuerrero")
                .contains("50000.00 COP")
                .contains("Paquete de créditos x500")
                .contains("23/09/2026 a las 10:15")
                .doesNotContain("�");
    }
}
