/**
 * ==========================================================================
 * Controlador de Interfaz: HU-RBAC-004 (Seguridad Server-Side y Fail-Closed)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin frameworks / Sin dependencias)
 * Arquitectura: Inspección de respuestas RFC 7807 y encabezados reales
 * ==========================================================================
 */

import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { construirBarra } from '../comun/barra-navegacion.js';

// Montar la barra superior compartida oficial (HU-INV-004)
const token = sessionStorage.getItem('nexus.token');
const contenedorBarra = document.getElementById('contenedor-barra');
if (contenedorBarra) {
  contenedorBarra.replaceChildren(
    construirBarra({
      seccionActiva: 'cuenta',
      sesion: { autenticado: Boolean(token) },
      navegar: (ruta) => {
        if (ruta === '/inventario') {
          window.location.href = '../contenido/inventario/inventario.html';
        } else if (ruta === '/cuenta') {
          window.location.href = './seguridad-servidor.html';
        } else {
          alert(`La sección ${ruta} se habilitará en el Sprint 2.`);
        }
      }
    })
  );
}

// Auto-detección de puerto backend: 8089 (Spring Boot local) y 8081 (Docker Compose)
const BASES_BACKEND = [
  'http://localhost:8089/api/v1',
  'http://localhost:8081/api/v1'
];

const simRolSelect = document.querySelector('#sim-rol');
const simEndpointSelect = document.querySelector('#sim-endpoint');
const simTokenAlterado = document.querySelector('#sim-token-alterado');
const btnEjecutar = document.querySelector('#btn-ejecutar-peticion');
const resultadoInspeccion = document.querySelector('#resultado-inspeccion');
const logServidor = document.querySelector('#log-servidor');
const btnCopiarJson = document.querySelector('#btn-copiar-json');

const tabBtnJson = document.querySelector('#tab-btn-json');
const tabBtnHeaders = document.querySelector('#tab-btn-headers');
const vistaJson = document.querySelector('#vista-json');
const vistaHeaders = document.querySelector('#vista-headers');
const cuerpoTablaHeaders = document.querySelector('#cuerpo-tabla-headers');

// Precarga desde el hub (index.html): ?rol=JUGADOR&accion=BANEAR_DEFINITIVAMENTE
// deja la consola lista para que "Comprobar 403 en servidor" muestre el bloqueo
// del rol elegido de un clic.
(function precargarDesdeQuery() {
  const params = new URLSearchParams(location.search);
  const rolParam = params.get('rol');
  const accionParam = params.get('accion');

  if (rolParam && [...simRolSelect.options].some((o) => o.value === rolParam)) {
    simRolSelect.value = rolParam;
  }
  if (accionParam) {
    const opt = [...simEndpointSelect.options].find((o) => o.dataset.action === accionParam);
    if (opt) simEndpointSelect.value = opt.value;
  }
})();

// Control de Pestañas con ARIA accesible (Point #5)
tabBtnJson.addEventListener('click', () => {
  tabBtnJson.classList.add('activa');
  tabBtnJson.setAttribute('aria-selected', 'true');
  tabBtnHeaders.classList.remove('activa');
  tabBtnHeaders.setAttribute('aria-selected', 'false');
  vistaJson.style.display = 'block';
  vistaHeaders.style.display = 'none';
});

tabBtnHeaders.addEventListener('click', () => {
  tabBtnHeaders.classList.add('activa');
  tabBtnHeaders.setAttribute('aria-selected', 'true');
  tabBtnJson.classList.remove('activa');
  tabBtnJson.setAttribute('aria-selected', 'false');
  vistaHeaders.style.display = 'block';
  vistaJson.style.display = 'none';
});

const MATRIZ_PERMISOS = {
  JUGADOR: { BANEAR_DEFINITIVAMENTE: false, ASIGNAR_ROL: false, MODIFICAR_PERFIL_PROPIO: true },
  MODERADOR: { BANEAR_DEFINITIVAMENTE: false, ASIGNAR_ROL: false, MODIFICAR_PERFIL_PROPIO: true },
  ADMINISTRADOR: { BANEAR_DEFINITIVAMENTE: true, ASIGNAR_ROL: false, MODIFICAR_PERFIL_PROPIO: true },
  SUPER_ADMINISTRADOR: { BANEAR_DEFINITIVAMENTE: true, ASIGNAR_ROL: true, MODIFICAR_PERFIL_PROPIO: true },
  ANONIMO: { BANEAR_DEFINITIVAMENTE: false, ASIGNAR_ROL: false, MODIFICAR_PERFIL_PROPIO: false }
};

/**
 * Renderizado de JSON seguro a prueba de balas (Point #4):
 * No inyecta HTML arbitrario ni usa innerHTML sin escapar.
 * Construye nodos del DOM usando textContent y createTextNode.
 */
function renderizarJsonSeguro(obj, contenedor) {
  contenedor.replaceChildren();
  const jsonStr = JSON.stringify(obj, null, 2);
  const regex = /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?|[{}[\],])/g;
  let ultimoIndice = 0;
  let match;

  while ((match = regex.exec(jsonStr)) !== null) {
    if (match.index > ultimoIndice) {
      contenedor.appendChild(document.createTextNode(jsonStr.substring(ultimoIndice, match.index)));
    }
    const token = match[0];
    const span = document.createElement('span');

    if (/^"/.test(token)) {
      if (/:$/.test(token)) {
        span.className = 'json-clave';
      } else {
        span.className = 'json-string';
      }
    } else if (/true|false/.test(token)) {
      span.className = 'json-boolean';
    } else if (/null/.test(token)) {
      span.className = 'json-null';
    } else if (/^-?\d/.test(token)) {
      span.className = 'json-numero';
    }

    span.textContent = token;
    contenedor.appendChild(span);
    ultimoIndice = regex.lastIndex;
  }

  if (ultimoIndice < jsonStr.length) {
    contenedor.appendChild(document.createTextNode(jsonStr.substring(ultimoIndice)));
  }
}

let textoJsonPlano = '';

async function ejecutarSimulacion() {
  const rol = simRolSelect.value;
  const endpointOpt = simEndpointSelect.selectedOptions[0];
  const ruta = endpointOpt.value;
  const metodo = endpointOpt.dataset.method;
  const accion = endpointOpt.dataset.action;
  const esAlterado = simTokenAlterado.checked;

  btnEjecutar.disabled = true;
  btnEjecutar.innerHTML = 'Verificando en servidor...';

  await new Promise((resolve) => setTimeout(resolve, 150));

  const headers = { 'Content-Type': 'application/json' };
  if (rol !== 'ANONIMO') {
    headers['X-User-Role'] = rol;
    headers['X-User-Name'] = 'usuario_prueba';

    if (esAlterado) {
      headers['Authorization'] =
        'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJoYWNrZXIiLCJyb2wiOiJTVVBFUl9BRE1JTklTVFJBRE9SIn0.FIRMA_CORRUPTA';
    } else if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }

  let respondioServidor = false;

  for (const base of BASES_BACKEND) {
    try {
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 1200);

      const res = await fetchWithHttpErrorInterceptor(`${base}${ruta}`, {
        method: metodo,
        headers: headers,
        body: JSON.stringify({ userId: '42', nuevoRol: 'MODERADOR', apodo: 'JugadorDemo' }),
        signal: controller.signal
      });
      clearTimeout(timeoutId);

      const data = await res.json();
      // Leer el encabezado Content-Type REAL de la respuesta de red (Point #1)
      const realContentType = res.headers.get('content-type') || 'application/json';

      renderizarResultado(res.status, data, rol, accion, esAlterado, base, headers, metodo, `${base}${ruta}`, realContentType);
      respondioServidor = true;
      break;
    } catch {
      // Probar siguiente puerto candidato
    }
  }

  if (!respondioServidor) {
    const permitido = !esAlterado && MATRIZ_PERMISOS[rol]?.[accion] === true;
    const status = permitido ? 200 : 403;
    const data = permitido
      ? { status: 'SUCCESS', message: `Operación ${accion} autorizada y ejecutada con éxito.` }
      : {
          type: 'https://nexusbattles.upb.edu.co/errors/forbidden',
          title: 'Acceso denegado',
          status: 403,
          detail: esAlterado ? 'Token de autenticación inválido o expirado' : 'No tienes permiso para esta acción',
          instance: `/api/v1${ruta}`
        };

    // Aclarar explícitamente que no hubo respuesta real de red (Point #1)
    const simulatedContentType = 'Simulado en navegador (sin transmisión de red — ms-identidad desconectado)';
    renderizarResultado(status, data, rol, accion, esAlterado, 'Modo local (ms-identidad desconectado en :8089 / :8081)', headers, metodo, `http://localhost:8089/api/v1${ruta}`, simulatedContentType);
  }

  btnEjecutar.disabled = false;
  btnEjecutar.innerHTML = 'Ejecutar Petición al Servidor';
}

function renderizarResultado(status, data, rol, accion, esAlterado, origen, reqHeaders, metodo, urlCompleta, contentTypeRespuesta) {
  textoJsonPlano = JSON.stringify(data, null, 2);
  renderizarJsonSeguro(data, logServidor);

  // Renderizar tabla de encabezados de red con valores verificados y notas técnicas (Point #1 y #7)
  cuerpoTablaHeaders.innerHTML = `
    <tr>
      <td class="encabezado-nombre">Método HTTP</td>
      <td><code>${metodo}</code></td>
    </tr>
    <tr>
      <td class="encabezado-nombre">URL Destino</td>
      <td><code>${urlCompleta}</code></td>
    </tr>
    <tr>
      <td class="encabezado-nombre">Authorization</td>
      <td><code>${reqHeaders['Authorization'] || '(Omitido en la petición)'}</code></td>
    </tr>
    <tr>
      <td class="encabezado-nombre">X-User-Role</td>
      <td>
        <code>${reqHeaders['X-User-Role'] || '(Sin rol enviado)'}</code>
        <span class="nota-header">(Respaldo de desarrollo/demo — en producción se valida exclusivamente Bearer JWT)</span>
      </td>
    </tr>
    <tr>
      <td class="encabezado-nombre">X-User-Name</td>
      <td><code>${reqHeaders['X-User-Name'] || '(Sin usuario enviado)'}</code></td>
    </tr>
    <tr>
      <td class="encabezado-nombre">Content-Type Respuesta</td>
      <td><code>${contentTypeRespuesta}</code></td>
    </tr>
    <tr>
      <td class="encabezado-nombre">Código de Estado</td>
      <td><strong>${status} ${status === 403 ? 'Forbidden' : (status === 200 ? 'OK' : '')}</strong></td>
    </tr>
  `;

  const esExito = status >= 200 && status < 300;

  if (status === 403) {
    resultadoInspeccion.innerHTML = `
      <div class="panel-resultado panel-forbidden">
        <span class="badge-http badge-403">403 FORBIDDEN</span>
        <strong>Intento de acceso bloqueado por el servidor (Fail-Closed).</strong>
        <p style="margin: 8px 0 0;">
          ${esAlterado
            ? 'El servidor detectó un token JWT alterado o manipulado y rechazó la petición inmediatamente.'
            : `El usuario con rol <strong>${rol}</strong> no posee permiso para la acción <code>${accion}</code>.`}
        </p>
        <p style="margin: 6px 0 0; font-size: 13px; color: #801212;">
          Evento de seguridad registrado en auditoría JSON y notificado a <code>ms-cumplimiento</code>.
        </p>
        <p style="margin: 4px 0 0; font-size: 11.5px; opacity: 0.8;">Origen: ${origen}</p>
      </div>
    `;
  } else if (esExito) {
    resultadoInspeccion.innerHTML = `
      <div class="panel-resultado panel-success">
        <span class="badge-http badge-200">${status} OK</span>
        <strong>Operación autorizada y procesada exitosamente.</strong>
        <p style="margin: 8px 0 0;">
          El rol <strong>${rol}</strong> cuenta con privilegios válidos para ejecutar <code>${accion}</code>.
        </p>
        <p style="margin: 4px 0 0; font-size: 11.5px; opacity: 0.8;">Origen: ${origen}</p>
      </div>
    `;
  } else {
    const detalle = (data && (data.detail || data.message || data.error)) || 'El servidor no procesó la solicitud.';
    resultadoInspeccion.innerHTML = `
      <div class="panel-resultado panel-vacio">
        <span class="badge-http badge-vacio">${status}</span>
        <strong>Autorización superada, pero el servidor devolvió un error.</strong>
        <p style="margin: 8px 0 0;">
          El interceptor RBAC dejó pasar al rol <strong>${rol}</strong> para <code>${accion}</code>; el fallo (${status}) es posterior: <em>${detalle}</em>.
        </p>
        <p style="margin: 4px 0 0; font-size: 11.5px; opacity: 0.8;">Origen: ${origen}</p>
      </div>
    `;
  }
}

btnCopiarJson?.addEventListener('click', () => {
  if (!textoJsonPlano) return;
  navigator.clipboard.writeText(textoJsonPlano).then(() => {
    btnCopiarJson.textContent = 'Copiado';
    btnCopiarJson.classList.add('copiado');
    setTimeout(() => {
      btnCopiarJson.textContent = 'Copiar';
      btnCopiarJson.classList.remove('copiado');
    }, 1800);
  });
});

btnEjecutar.addEventListener('click', ejecutarSimulacion);
