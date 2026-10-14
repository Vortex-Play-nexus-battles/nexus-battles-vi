package com.nexusbattles.ms_identidad.admin.titulares;

import com.nexusbattles.ms_identidad.admin.directorio.DirectorioDeCuentas;
import com.nexusbattles.ms_identidad.auth.model.Usuario;
import com.nexusbattles.ms_identidad.auth.service.ClavesDeFirma;
import com.nexusbattles.ms_identidad.auth.service.JwtService;
import com.nexusbattles.ms_identidad.rbac.model.RolEntity;
import com.nexusbattles.ms_identidad.rbac.repository.RbacMatrixRepository;
import com.nexusbattles.ms_identidad.rbac.security.SecurityInterceptor;
import com.nexusbattles.ms_identidad.rbac.service.RbacAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Quien es cada cuenta, para la consola de moderacion — SA-4
 * (ms-identidad-admin.yaml 1.9.0). Con el {@code SecurityInterceptor} de
 * verdad y tokens firmados por el mismo emisor: los tres roles de moderacion
 * lo leen, el jugador no, y lo que sale no lleva correo.
 */
@DisplayName("Titulares de cuentas por HTTP (SA-4)")
class AdminTitularesHttpTest {

    private static final String RUTA = "/api/v1/admin/titulares";
    private static final String ERRORES = "https://nexusbattles.upb.edu.co/errors/";
    private static final UUID ANA = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID BETO = UUID.fromString("66666666-7777-8888-9999-000000000000");

    private JwtService jwtService;
    private DirectorioDeCuentas directorio;
    private MockMvc mvc;

    @BeforeEach
    void montar() {
        jwtService = new JwtService(new ClavesDeFirma(""));
        ReflectionTestUtils.setField(jwtService, "horasExpiracion", 24);
        ReflectionTestUtils.setField(jwtService, "emisor", "ms-identidad");
        directorio = mock(DirectorioDeCuentas.class);
        SecurityInterceptor interceptor = new SecurityInterceptor(
                new RbacAuthorizationService(new RbacMatrixRepository()), null, jwtService, null, false);
        mvc = MockMvcBuilders.standaloneSetup(new AdminTitularesController(directorio))
                .addInterceptors(interceptor).build();
    }

    private String token(String rol) {
        return "Bearer " + jwtService.generarToken("operadora", rol, 0, UUID.randomUUID());
    }

    private static Usuario cuenta(UUID uid, String apodo, String estado, String rol) {
        Usuario usuario = new Usuario();
        usuario.setPublicId(uid);
        usuario.setApodo(apodo);
        usuario.setEmail(apodo.toLowerCase() + "@ejemplo.org");
        usuario.setEstado(estado);
        usuario.setPassword("hash-que-nunca-deberia-salir");
        usuario.setRol(new RolEntity(rol, "rol de prueba"));
        return usuario;
    }

    @SuppressWarnings("unchecked")
    private void devolviendo(Usuario... cuentas) {
        when(directorio.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cuentas)));
    }

    @SuppressWarnings("unchecked")
    private Pageable pedido() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(directorio).findAll(any(Specification.class), captor.capture());
        return captor.getValue();
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"MODERADOR", "ADMINISTRADOR", "SUPER_ADMINISTRADOR"})
    @DisplayName("por uids: los tres roles de moderacion reconocen a la cuenta, sin su correo ni su clave")
    void porUids(String rol) throws Exception {
        devolviendo(cuenta(ANA, "Ana", "ACTIVO", "JUGADOR"), cuenta(BETO, "Beto", "SUSPENDIDA", "JUGADOR"));

        mvc.perform(get(RUTA).param("uids", ANA + "," + BETO).header("Authorization", token(rol)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$[0].uid").value(ANA.toString()))
                .andExpect(jsonPath("$[0].apodo").value("Ana"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[0].rol").value("JUGADOR"))
                // Las filas anteriores a B2 se guardaron en femenino: salen como el contrato.
                .andExpect(jsonPath("$[1].estado").value("SUSPENDIDO"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("ejemplo.org"))));

        assertThat(pedido()).isEqualTo(PageRequest.of(0, 2, Sort.by("apodo")));
    }

    @Test
    @DisplayName("por apodo: las diez primeras por apodo")
    void porApodo() throws Exception {
        devolviendo(cuenta(ANA, "Ana", "ACTIVO", "JUGADOR"));

        mvc.perform(get(RUTA).param("buscar", "  an ").header("Authorization", token("MODERADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apodo").value("Ana"));

        assertThat(pedido()).isEqualTo(PageRequest.of(0, AdminTitularesController.RESULTADOS_DE_BUSQUEDA,
                Sort.by("apodo")));
    }

    @Test
    @DisplayName("el jugador no lee titulares: 403 del interceptor y no se consulta nada")
    void jugadorProhibido() throws Exception {
        mvc.perform(get(RUTA).param("uids", ANA.toString()).header("Authorization", token("JUGADOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(ERRORES + "forbidden"));
        verifyNoInteractions(directorio);
    }

    @Test
    @DisplayName("sin token: 403 y no se consulta nada")
    void sinToken() throws Exception {
        mvc.perform(get(RUTA).param("uids", ANA.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(directorio);
    }

    @Test
    @DisplayName("ni uids ni buscar: 400 con lo que falta")
    void sinNada() throws Exception {
        mvc.perform(get(RUTA).header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(ERRORES + "datos-invalidos"))
                .andExpect(jsonPath("$.detail").value("Indica las cuentas («uids») o lo que buscas («buscar»)."))
                .andExpect(jsonPath("$.instance").value(RUTA));
        verifyNoInteractions(directorio);
    }

    @Test
    @DisplayName("las dos a la vez: 400, no se elige una por quien pregunta")
    void lasDos() throws Exception {
        mvc.perform(get(RUTA).param("uids", ANA.toString()).param("buscar", "ana")
                        .header("Authorization", token("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Pide las cuentas por «uids» o búscalas con «buscar», no las dos cosas a la vez."));
        verifyNoInteractions(directorio);
    }

    @Test
    @DisplayName("una letra no es una busqueda: 400")
    void busquedaCorta() throws Exception {
        mvc.perform(get(RUTA).param("buscar", " a ").header("Authorization", token("MODERADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Escribe al menos 2 letras del apodo para buscar."));
        verifyNoInteractions(directorio);
    }

    @Test
    @DisplayName("un uid mal escrito: 400 con el valor")
    void uidMalEscrito() throws Exception {
        mvc.perform(get(RUTA).param("uids", "ana").header("Authorization", token("MODERADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("«ana» no es el uid de una cuenta")));
        verifyNoInteractions(directorio);
    }

    @Test
    @DisplayName("solo comas: ninguna cuenta pedida, lista vacia sin consultar")
    void soloComas() throws Exception {
        mvc.perform(get(RUTA).param("uids", " , ").header("Authorization", token("MODERADOR")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        verifyNoInteractions(directorio);
    }

    @Test
    @DisplayName("los comodines escritos se buscan tal cual")
    void comodines() {
        assertThat(AdminTitularesController.escaparComodines("a%b_c\\d")).isEqualTo("a\\%b\\_c\\\\d");
    }
}
