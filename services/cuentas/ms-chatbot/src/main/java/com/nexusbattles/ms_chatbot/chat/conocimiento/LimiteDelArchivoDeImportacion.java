package com.nexusbattles.ms_chatbot.chat.conocimiento;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

// ms-chatbot.yaml 1.6.0: el archivo de importacion de la base de conocimiento
// no pasa de ImportacionDemasiadoGrandeException.MAXIMO_DE_BYTES (8 MiB).
//
// Spring no pone techo a un cuerpo JSON: Jackson lo lee entero en memoria, y
// la JVM del servicio tiene 256 MiB. El borde corta en 10 MB, pero solo para
// lo que pasa por el. Aqui:
//
//   * con Content-Length mayor que el maximo, 413 sin leer una sola linea;
//   * sin Content-Length (envio por trozos), se lee como mucho el maximo y un
//     byte: si hay mas, 413; si no, la peticion sigue con ese cuerpo.
//
// Mismo cuerpo que ManejadorErroresDeLaBase (problem details con `motivo`
// IMPORTACION_DEMASIADO_GRANDE), escrito aqui porque un filtro va antes del
// DispatcherServlet y de los @ControllerAdvice.
//
// No es un @Component: SecurityConfig lo pone en la cadena de seguridad,
// despues de la autorizacion. Quien no es administrador recibe su 401/403
// sin que se lea el cuerpo, y el orden no depende de como se registren los
// filtros del servidor (en MockMvc un filtro suelto corria antes que la
// seguridad).
public class LimiteDelArchivoDeImportacion extends OncePerRequestFilter {

    static final String RUTA = "/chatbot/admin/base-conocimiento/borrador/importacion";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final UrlPathHelper rutas = new UrlPathHelper();
    private final long maximoDeBytes;

    public LimiteDelArchivoDeImportacion() {
        this(ImportacionDemasiadoGrandeException.MAXIMO_DE_BYTES);
    }

    LimiteDelArchivoDeImportacion(long maximoDeBytes) {
        this.maximoDeBytes = maximoDeBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"PUT".equalsIgnoreCase(request.getMethod())
            || !RUTA.equals(rutas.getPathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
        throws ServletException, IOException {
        long declarado = request.getContentLengthLong();
        if (declarado > maximoDeBytes) {
            rechazar(request, response);
            return;
        }
        if (declarado >= 0) {
            cadena.doFilter(request, response);
            return;
        }
        byte[] cuerpo = request.getInputStream().readNBytes(Math.toIntExact(maximoDeBytes + 1));
        if (cuerpo.length > maximoDeBytes) {
            rechazar(request, response);
            return;
        }
        cadena.doFilter(new CuerpoYaLeido(request, cuerpo), response);
    }

    private static void rechazar(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpStatus estado = HttpStatus.CONTENT_TOO_LARGE;
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "about:blank");
        cuerpo.put("title", ManejadorErroresDeLaBase.TITULO_ARCHIVO_GRANDE);
        cuerpo.put("status", estado.value());
        cuerpo.put("detail", ImportacionDemasiadoGrandeException.porTamano().getMessage());
        cuerpo.put("instance", request.getRequestURI());
        cuerpo.put("motivo", ManejadorErroresDeLaBase.IMPORTACION_DEMASIADO_GRANDE);

        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(JSON.writeValueAsBytes(cuerpo));
    }

    // La peticion con su cuerpo ya leido en memoria (envio por trozos).
    private static final class CuerpoYaLeido extends HttpServletRequestWrapper {

        private final byte[] cuerpo;

        CuerpoYaLeido(HttpServletRequest request, byte[] cuerpo) {
            super(request);
            this.cuerpo = cuerpo;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream entrada = new ByteArrayInputStream(cuerpo);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return entrada.read();
                }

                @Override
                public int read(byte[] destino, int desde, int cuantos) {
                    return entrada.read(destino, desde, cuantos);
                }

                @Override
                public boolean isFinished() {
                    return entrada.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Lectura asincrona no disponible para este cuerpo.");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            Charset juego = getCharacterEncoding() == null
                ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), juego));
        }

        @Override
        public int getContentLength() {
            return cuerpo.length;
        }

        @Override
        public long getContentLengthLong() {
            return cuerpo.length;
        }
    }
}
