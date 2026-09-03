# Barra superior de navegacion

Vista de `HU-INV-004`. Fuente: *Proyecto Integrador II*, secciones 7.1-7.1.1,
pp. 34-35. Depende de `HU-INV-001`.

## Alcance

- las **seis secciones** que enumera el criterio 1 —Jugar online, Misiones,
  Torneo, Mi inventario, Subasta y Mi Cuenta— en su orden;
- el campo de busqueda de productos;
- marca de la seccion activa, con `aria-current="page"`;
- Mi Cuenta despliega sus opciones en sitio, sin sacar al visitante de la
  pantalla en la que esta;
- un visitante no autenticado recibe **unicamente** la opcion de registro.

El **comportamiento** de la busqueda es `HU-INV-002`: aqui solo esta el campo.

## Dos decisiones

**La sesion entra por parametro y por omision es visitante.** El contrato de
identidad es `HU-INF-009` y todavia no existe. Asumir visitante es la opcion
segura: un jugador mal reconocido veria opciones que no le tocan, mientras que
un jugador tratado como visitante solo ve de menos.

**Vive en `contenido/` y no en `shared/ui-kit`.** El criterio 1 la acota a
"cualquier pantalla **del alcance del grupo**", asi que hoy es de grupo-alpha.
Si pasa a servir a los tres grupos, se muda sin cambiar su interfaz.

## Pruebas

```bash
npm test                  # 10 unitarias, sobre jsdom
npm run test:aceptacion   # 4 de aceptacion, sobre Chromium real
```

Una de las de aceptacion comprueba que la barra **no introduce desplazamiento
horizontal** a 375 px: no puede ser ella la que rompa el criterio 2 de
`HU-INV-001`.
