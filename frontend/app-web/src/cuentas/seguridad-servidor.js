/**
 * HU-RBAC-004 — Autorización Server-Side y Fail-Closed.
 *
 * Lógica de la vista `seguridad-servidor.html`: simulador de petición
 * directa al backend (bypass) y render de la respuesta RFC 7807.
 *
 * Convención del repo (`.claude/rules/frontend-web.md`): un `.html` + un
 * `.js` del mismo nombre por vista. Antes vivía embebido en el HTML.
 *
 * Los 4 estados de RNF-USA-003 sobre la respuesta del servidor:
 *   - carga  : petición en vuelo
 *   - exito  : 200, acción autorizada
 *   - error  : 403 del servidor, o sin conexión (degradación a modo local,
 *              nunca en silencio — Project Charter, Riesgo #7)
 *   - vacio  : respuesta 2xx sin cuerpo
 */
import { fetchWithHttpErrorInterceptor } from '../comun/interceptors/http-error.interceptor.js';
import { construirBarra } from '../comun/barra-navegacion.js';

// Montar la barra superior funcional compartida (HU-INV-004)
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

const API_BASE = 'http://localhost:8081/api/v1';
const simRolSelect = document.querySelector('#sim-rol');
const simEndpointSelect = document.querySelector('#sim-endpoint');
const simTokenAlterado = document.querySelector('#sim-token-alterado');
const btnEjecutar = document.querySelector('#btn-ejecutar-peticion');
const resultadoInspeccion = document.querySelector('#resultado-inspeccion');
const logServidor = document.querySelector('#log-servidor');
const btnCopiarJson = document.querySelector('#btn-copiar-json');

const MATRIZ_PERMISOS = {
  JUGADOR: { BANEAR_DEFINITIVAMENTE: false, ASIGNAR_ROL: false },
  MODERADOR: { BANEAR_DEFINITIVAMENTE: false, ASIGNAR_ROL: false },
  ADMINISTRADOR: { BANEAR_DEFINITIVAMENTE: true, ASIGNAR_ROL: false },
  SUPER_ADMINISTRADOR: { BANEAR_DEFINITIVAMENTE: true, ASIGNAR_ROL: true },
  ANONIMO: { BANEAR_DEFINITIVAMENTE: false, ASIGNAR_ROL: false }
};

/** Estado "carga": la petición está en vuelo. */
function renderCargando() {
  resultadoInspeccion.innerHTML = `
    <div class="panel-resultado panel-cargando" role="status" aria-live="polite">
      <span class="badge-http badge-carga">···</span>
      <strong>Enviando petición al servidor…</strong>
      <p style="margin: 8px 0 0;">Validando Bearer JWT y permisos en el interceptor de seguridad.</p>
    </div>
  `;
  logServidor.textContent = 'Esperando respuesta del servidor…';
}

/** Estado "vacío": respuesta 2xx sin cuerpo. */
function renderVacio(status) {
  resultadoInspeccion.innerHTML = `
    <div class="panel-resultado panel-vacio" role="status">
      <span class="badge-http badge-vacio">${status}</span>
      <strong>El servidor respondió sin cuerpo.</strong>
      <p style="margin: 8px 0 0;">La operación se aceptó pero no devolvió contenido para inspeccionar.</p>
    </div>
  `;
  logServidor.textContent = '(respuesta sin cuerpo)';
}

/**
 * Estados "éxito" (200) y "error" (403).
 * @param {boolean} modoLocal true si el backend no respondió y el resultado
 *   se calculó localmente (se avisa, no se oculta).
 */
function renderizarResultado(status, data, rol, accion, esAlterado, modoLocal) {
  logServidor.textContent = JSON.stringify(data, null, 2);

  const avisoLocal = modoLocal
    ? `<p class="nota-local">⚠ Sin conexión con el backend (${API_BASE}). Resultado calculado en modo local para la demostración.</p>`
    : '';

  if (status === 403) {
    resultadoInspeccion.innerHTML = `
      ${avisoLocal}
      <div class="panel-resultado panel-forbidden">
        <span class="badge-http badge-403">403 FORBIDDEN</span>
        <strong>¡Intento de acceso bloqueado por el servidor! (Fail-Closed)</strong>
        <p style="margin: 8px 0 0;">
          ${esAlterado
            ? 'El servidor detectó un token JWT alterado o manipulado y rechazó la petición inmediatamente.'
            : `El usuario con rol <strong>${rol}</strong> no posee permiso para la acción <code>${accion}</code>.`}
        </p>
        <p style="margin: 6px 0 0; font-size: 13px; color: #801212;">
          🔒 Evento de seguridad registrado en auditoría JSON y notificado a <code>ms-cumplimiento</code>.
        </p>
      </div>
    `;
  } else {
    resultadoInspeccion.innerHTML = `
      ${avisoLocal}
      <div class="panel-resultado panel-success">
        <span class="badge-http badge-200">200 OK</span>
        <strong>¡Petición autorizada con éxito!</strong>
        <p style="margin: 8px 0 0;">
          El usuario con rol <strong>${rol}</strong> tiene los permisos requeridos para ejecutar <code>${accion}</code> en el servidor.
        </p>
      </div>
    `;
  }
}

function resultadoLocal(rol, accion, ruta, esAlterado) {
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
  return { status, data };
}

async function ejecutarSimulacion() {
  const rol = simRolSelect.value;
  const endpointOpt = simEndpointSelect.selectedOptions[0];
  const ruta = endpointOpt.value;
  const metodo = endpointOpt.dataset.method;
  const accion = endpointOpt.dataset.action;
  const esAlterado = simTokenAlterado.checked;

  btnEjecutar.disabled = true;
  btnEjecutar.innerHTML = '🔄 Verificando en servidor...';
  renderCargando();

  await new Promise((resolve) => setTimeout(resolve, 220));

  try {
    const headers = { 'Content-Type': 'application/json' };
    if (rol !== 'ANONIMO') {
      headers['Authorization'] = esAlterado ? 'Bearer token.corrupto.invalido' : `Bearer demo-token-${rol.toLowerCase()}`;
    }

    const res = await fetchWithHttpErrorInterceptor(`${API_BASE}${ruta}`, {
      method: metodo,
      headers
    });

    const texto = await res.text();
    if (res.ok && texto.trim() === '') {
      renderVacio(res.status);
      return;
    }

    let data;
    try {
      data = texto ? JSON.parse(texto) : {};
    } catch {
      data = { raw: texto };
    }
    renderizarResultado(res.status, data, rol, accion, esAlterado, false);
  } catch {
    // El backend no respondió: degradación controlada a modo local, avisando.
    const { status, data } = resultadoLocal(rol, accion, ruta, esAlterado);
    renderizarResultado(status, data, rol, accion, esAlterado, true);
  } finally {
    btnEjecutar.disabled = false;
    btnEjecutar.innerHTML = '🚀 Ejecutar Petición al Servidor';
  }
}

btnCopiarJson?.addEventListener('click', () => {
  if (!logServidor.textContent) return;
  navigator.clipboard.writeText(logServidor.textContent).then(() => {
    btnCopiarJson.textContent = '✓ Copiado';
    btnCopiarJson.classList.add('copiado');
    setTimeout(() => {
      btnCopiarJson.textContent = '📋 Copiar';
      btnCopiarJson.classList.remove('copiado');
    }, 1800);
  });
});

btnEjecutar.addEventListener('click', ejecutarSimulacion);
