package com.nexusbattles.comun.seguridad.servicio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CredencialesDeServicioAutoConfiguration · solo cuando el servicio tiene credenciales")
class CredencialesDeServicioAutoConfigurationTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CredencialesDeServicioAutoConfiguration.class));

    @Test
    @DisplayName("un servicio que solo es servidor de recursos no recibe ninguna credencial")
    void sinClientIdNoHayBeans() {
        contexto.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(TokenDeServicio.class);
            assertThat(ctx).doesNotHaveBean(InterceptorDePortadorDeServicio.class);
        });
    }

    @Test
    @DisplayName("con las variables DIRECTORIO_ACTIVO_* (o sus propiedades) se crean el token y el interceptor")
    void conCredencialesHayBeans() {
        contexto.withPropertyValues(
                        "DIRECTORIO_ACTIVO_URL=http://kc/realms/nexus-battles",
                        "DIRECTORIO_ACTIVO_CLIENT_ID=ms-subastas",
                        "DIRECTORIO_ACTIVO_CLIENT_SECRET=secreto")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TokenDeServicio.class);
                    assertThat(ctx).hasSingleBean(InterceptorDePortadorDeServicio.class);
                });
    }

    @Test
    @DisplayName("las propiedades seguridad.servicio.* mandan sobre las variables de entorno")
    void lasPropiedadesMandan() {
        contexto.withPropertyValues(
                        "seguridad.servicio.url=http://kc/realms/prueba",
                        "seguridad.servicio.client-id=otro",
                        "seguridad.servicio.client-secret=s")
                .run(ctx -> assertThat(ctx).hasSingleBean(TokenDeServicioOAuth2.class));
    }

    @Test
    @DisplayName("un TokenDeServicio propio (un doble, por ejemplo) sustituye al de la biblioteca")
    void unBeanPropioSustituye() {
        contexto.withPropertyValues("seguridad.servicio.client-id=x")
                .withBean(TokenDeServicio.class, () -> () -> "doble")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TokenDeServicio.class);
                    assertThat(ctx.getBean(TokenDeServicio.class).portador()).isEqualTo("doble");
                    assertThat(ctx).doesNotHaveBean(TokenDeServicioOAuth2.class);
                });
    }
}
