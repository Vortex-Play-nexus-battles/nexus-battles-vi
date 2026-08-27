# Entorno de desarrollo — Empresa A

Guía única para los dieciocho integrantes. Si tu máquina cumple esto, puedes
clonar el repositorio y empezar a programar el mismo día.

El editor estándar de la empresa es **Visual Studio Code**. La configuración
está versionada en `.vscode/`, así que no hay que ajustar nada a mano: al abrir
el repositorio, VS Code aplica los mismos ajustes para todos.

## 1. Qué instalar

| Herramienta | Versión | Para qué | Dónde |
|---|---|---|---|
| **JDK 21 (Eclipse Temurin)** | 21 LTS | Compilar y ejecutar los servicios de Spring Boot | adoptium.net |
| **Node.js** | 20 LTS o superior | Solo las herramientas de calidad del frontend | nodejs.org |
| **Git** | 2.40 o superior | Control de versiones | git-scm.com |
| **Docker Desktop** | Última estable | Levantar PostgreSQL, MongoDB, Redis, RabbitMQ y Keycloak | docker.com |
| **Visual Studio Code** | Última estable | Editor de la empresa | code.visualstudio.com |

**Java 21 y no otra.** La pila fija Java 21 LTS. Si tienes Java 17 o Java 25
instalados de antes, no pasa nada, pero el proyecto debe compilar con la 21: es
la versión contra la que se evalúa. Comprueba con `java -version`.

**Node solo sirve para el linter.** La aplicación web es HTML, CSS y JavaScript
sin framework y sin paso de compilación: el navegador carga los archivos tal
cual. Node hace falta únicamente para ejecutar ESLint y Prettier.

## 2. Primer arranque

```bash
git clone https://github.com/Vortex-Play-nexus-battles/nexus-battles-vi.git
cd nexus-battles-vi
git switch develop
code .
```

**Se trabaja sobre `develop`.** Es la rama de integración diaria del equipo.
`main` es la rama de entregas y está protegida.

Al abrir, VS Code ofrece instalar las **extensiones recomendadas**. Acepta. Si
no aparece el aviso: `Ctrl+Shift+P` → *Extensions: Show Recommended Extensions*
→ instalar todas.

Después, una sola vez, instala las herramientas de calidad del frontend:

`Ctrl+Shift+P` → *Tasks: Run Task* → **Web: instalar herramientas**

## 3. Comprobar que todo está bien

`Ctrl+Shift+P` → *Tasks: Run Task* → **Comprobar entorno**

Debe imprimir las versiones de Java, Node, npm, Git y Docker. Si alguna falta o
no coincide con la tabla de arriba, resuélvelo antes de escribir código: los
problemas de versión se descubren tarde y siempre en el peor momento.

Para comprobar que el backend compila:

```bash
./gradlew build
```

## 4. Cómo se trabaja

Las reglas ya están activas en el servidor; esto es solo el recordatorio.

1. **Nunca se trabaja sobre `main`.** VS Code te avisa si lo intentas y GitHub lo
   rechaza. Crea una rama por historia: `feature/HU-XXX-000`.
2. **Un pull request por historia**, con `Closes #n` en el cuerpo.
3. **Revisión obligatoria** del responsable del área, que GitHub solicita solo
   por CODEOWNERS.
4. **Solo squash-merge** hacia `main`: un commit por historia en el historial.
5. **Mensajes de commit** en formato Conventional Commits:
   `feat(cuentas): permitir registro con avatar`. La extensión
   *Conventional Commits* te guía con un formulario.

## 5. La aplicación web

### Dónde vive y quién la sirve

El HTML, CSS y JavaScript viven en `frontend/app-web/src/`, repartidos por
dominio igual que los servicios:

| Carpeta | Equipo |
|---|---|
| `src/comun/` | Compartido — propiedad de los tres Scrum Masters |
| `src/cuentas/` | Santiago |
| `src/contenido/` | Thomas |
| `src/plataforma/` | Simón |

**Un único servicio de Spring Boot los sirve como recursos estáticos.** No hay
servidor web aparte ni despliegue independiente: el navegador pide las páginas y
las APIs al mismo origen, lo que elimina la configuración de CORS y simplifica
el TLS de las conexiones WebSocket.

Para trabajar en una vista sin levantar Spring, usa **Live Server**: clic
derecho sobre el `.html` → *Open with Live Server*. Se abre en el puerto 5500 y
recarga al guardar.

### Convenciones, porque aquí no hay framework que las imponga

JavaScript plano no impone ninguna estructura, así que estas convenciones son
obligatorias y se revisan en el pull request:

- **Un archivo `.html` por vista**, dentro de la carpeta de su dominio.
- **Un archivo `.js` por vista**, con el mismo nombre, cargado como módulo:
  `<script type="module" src="./vista.js"></script>`.
- **Lo que se repite en dos vistas se sube a `src/comun/`.** Cabecera, menú,
  paginación de dieciséis elementos, barra de vida, insignias de estado y la
  ventana del chatbot son compartidos: no se copian y pegan.
- **Nada de variables globales.** Todo se exporta e importa con módulos ES.
  ESLint rechaza lo contrario.
- **Ningún color ni tipografía escritos a mano.** Se usan las variables de tema
  definidas a partir de la propuesta de diseño.

### Verificación de tipos sin TypeScript

`jsconfig.json` activa la revisión de tipos sobre JavaScript plano. VS Code
detecta llamadas con argumentos de más, propiedades mal escritas y variables
inexistentes, **sin ningún paso de compilación**. Para que funcione bien,
documenta las funciones con JSDoc:

```js
/**
 * Pinta la barra de vida con el color que corresponde al porcentaje.
 * @param {HTMLElement} contenedor
 * @param {number} porcentaje  Entre 0 y 100.
 * @returns {void}
 */
export function pintarBarraDeVida(contenedor, porcentaje) { /* ... */ }
```

No es opcional en `src/comun/`: es código que consumen los tres equipos.

## 6. Qué NO trae todavía este entorno

Para que nadie lo busque:

- **No hay tareas de VS Code para Gradle.** El wrapper y el build multimódulo ya
  existen en `develop`, pero todavía se ejecutan desde la terminal con
  `./gradlew`. Las tareas llegan en el siguiente pull request.
- **No hay configuración de depuración de Java** en `launch.json`. Por ahora
  solo está la de la interfaz web en Chrome.
- **No hay compuerta automática de cobertura.** El objetivo del ochenta por
  ciento se revisa a mano hasta que existan las primeras pruebas.

## 7. Problemas frecuentes

**«El pull request muestra el archivo entero como modificado y yo solo cambié
una línea.»** Son los finales de línea. `.gitattributes` lo resuelve, pero si el
archivo se creó antes, normalízalo una vez:

```bash
git add --renormalize .
git commit -m "chore(repo): normalizar finales de linea"
```

**«VS Code no encuentra Java» o compila con la versión equivocada.** Paleta →
*Java: Configure Java Runtime* → asigna JavaSE-21 a tu instalación de Temurin.

**«`npm` no se reconoce como comando.»** Node no está instalado o no está en el
PATH. Reinstala Node.js y reinicia VS Code por completo.

**«ESLint no marca nada.»** Falta ejecutar la tarea *Web: instalar
herramientas*. ESLint necesita estar instalado en `frontend/app-web/`.

**«Me formatea distinto que a mi compañero.»** Comprueba que tienes instaladas
las extensiones *EditorConfig* y *Prettier*, y que Prettier es el formateador por
defecto. Están ambas en las recomendaciones del repositorio.
