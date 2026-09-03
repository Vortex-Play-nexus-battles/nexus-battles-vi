/**
 * ==========================================================================
 * Controlador del Hub Central de Cuentas (index.html)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin frameworks)
 * ==========================================================================
 */

import { construirBarra } from '../comun/barra-navegacion.js';
import { contarAccionesPermitidas, TOTAL_ACCIONES } from './matriz-rbac.js';

const CLAVE_TOKEN = 'nexus.token';
const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_APODO = 'nexus.apodoActual';

const ACCION_A_SIMULADOR = {
  GESTIONAR_CUENTAS: 'BANEAR_DEFINITIVAMENTE',
  CREAR_ADMIN_MODERADOR: 'ASIGNAR_ROL',
  ASIGNAR_ROL: 'ASIGNAR_ROL',
  BANEAR_DEFINITIVAMENTE: 'BANEAR_DEFINITIVAMENTE',
  MODIFICAR_PERFIL_PROPIO: 'MODIFICAR_PERFIL_PROPIO',
  TEST_SEGURIDAD: 'BANEAR_DEFINITIVAMENTE',
  CONSULTA_RBAC: 'BANEAR_DEFINITIVAMENTE'
};

const ESTRUCTURA_FASES = [
  {
    numero: '1',
    titulo: 'Fase 1: Onboarding y Acceso',
    autor: 'Cristian Camilo Chaparro',
    pasos: [
      {
        codigo: 'HU-AUT-001',
        titulo: 'Registro de Jugador',
        url: './registro.html',
        textoBoton: 'Abrir Registro',
        accionRequerida: 'CREAR_CUENTA_JUGADOR',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR', 'ANONIMO']
      },
      {
        codigo: 'HU-AUT-004',
        titulo: 'Inicio de Sesión',
        url: './login.html',
        textoBoton: 'Iniciar Sesión',
        accionRequerida: 'AUTENTICAR',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR', 'ANONIMO']
      }
    ]
  },
  {
    numero: '2',
    titulo: 'Fase 2: Identidad y Personalización',
    autor: 'Santiago Sanabria Uribe',
    pasos: [
      {
        codigo: 'HU-PER-001',
        titulo: 'Mi Perfil de Jugador',
        url: './perfil.html',
        textoBoton: 'Ver Mi Perfil',
        accionRequerida: 'MODIFICAR_PERFIL_PROPIO',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      }
    ]
  },
  {
    numero: '3',
    titulo: 'Fase 3: Control de Acceso y Seguridad',
    autor: 'Andrés Núñez',
    pasos: [
      {
        codigo: 'HU-RBAC-001',
        titulo: 'Panel de Permisos RBAC',
        url: './matriz-permisos.html',
        textoBoton: 'Inspeccionar Matriz',
        accionRequerida: 'CONSULTA_RBAC',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      },
      {
        codigo: 'HU-RBAC-004',
        titulo: 'Verificación Server-Side',
        url: './seguridad-servidor.html',
        textoBoton: 'Consola Servidor',
        accionRequerida: 'TEST_SEGURIDAD',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      }
    ]
  },
  {
    numero: '4',
    titulo: 'Fase 4: Gobernanza y Sanciones',
    autor: 'Santiago Sanabria & Edwin',
    pasos: [
      {
        codigo: 'HU-USR-003',
        titulo: 'Gestión de Usuarios y Sanciones',
        url: './gestion-usuarios.html',
        textoBoton: 'Panel de Gestión Admin',
        accionRequerida: 'GESTIONAR_CUENTAS',
        disponiblePara: ['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      },
      {
        codigo: 'HU-RBAC-003',
        titulo: 'Alta de Administradores',
        url: './crear-cuenta-admin.html',
        textoBoton: 'Crear Cuenta Admin',
        accionRequerida: 'CREAR_ADMIN_MODERADOR',
        disponiblePara: ['SUPER_ADMINISTRADOR']
      }
    ]
  }
];

const METADATA_ROLES = {
  JUGADOR: {
    nombre: 'Jugador (Nivel 1)'
  },
  MODERADOR: {
    nombre: 'Moderador (Nivel 2)'
  },
  ADMINISTRADOR: {
    nombre: 'Administrador (Nivel 3)'
  },
  SUPER_ADMINISTRADOR: {
    nombre: 'Super Administrador (Nivel 4)',
    notaAcciones: '(Default-Deny)'
  }
};

function etiquetaAcciones(rol) {
  const nota = METADATA_ROLES[rol]?.notaAcciones ? ` ${METADATA_ROLES[rol].notaAcciones}` : '';
  return `${contarAccionesPermitidas(rol)} / ${TOTAL_ACCIONES} Acciones${nota}`;
}

function montarBarraOficial() {
  const token = sessionStorage.getItem(CLAVE_TOKEN);
  const contenedorBarra = document.getElementById('contenedor-barra');
  if (contenedorBarra) {
    contenedorBarra.replaceChildren(
      construirBarra({
        seccionActiva: 'cuenta',
        sesion: { autenticado: Boolean(token) },
        navegar: (ruta) => {
          if (ruta === '/inventario') {
            window.location.href = '../contenido/inventario/inventario.html';
          } else {
            window.location.href = './index.html';
          }
        }
      })
    );
  }
}

function montarNavegacionContextual(autenticado, rol, apodo) {
  const linksNav = document.getElementById('links-navegacion');
  const chipArea = document.getElementById('chip-sesion-area');

  linksNav.replaceChildren();
  chipArea.replaceChildren();

  const linkHub = document.createElement('a');
  linkHub.href = './index.html';
  linkHub.className = 'nav-link activo';
  linkHub.textContent = 'Hub';
  linksNav.appendChild(linkHub);

  if (autenticado) {
    const linksAutenticado = [
      { texto: 'Mi Perfil', url: './perfil.html' },
      { texto: 'Matriz RBAC', url: './matriz-permisos.html' },
      { texto: 'Seguridad Servidor', url: './seguridad-servidor.html' }
    ];

    if (rol === 'ADMINISTRADOR' || rol === 'SUPER_ADMINISTRADOR') {
      linksAutenticado.push({ texto: 'Gestión Admin', url: './gestion-usuarios.html' });
    }

    linksAutenticado.forEach((item) => {
      const a = document.createElement('a');
      a.href = item.url;
      a.className = 'nav-link';
      a.textContent = item.texto;
      linksNav.appendChild(a);
    });

    const chip = document.createElement('span');
    chip.className = 'chip-usuario';
    chip.textContent = `${apodo} (${rol})`;

    const btnCerrar = document.createElement('button');
    btnCerrar.type = 'button';
    btnCerrar.className = 'btn-cerrar-link';
    btnCerrar.textContent = 'Cerrar Sesión';
    btnCerrar.addEventListener('click', () => {
      sessionStorage.clear();
      window.location.reload();
    });

    chipArea.appendChild(chip);
    chipArea.appendChild(btnCerrar);
  } else {
    const linkLogin = document.createElement('a');
    linkLogin.href = './login.html';
    linkLogin.className = 'nav-link';
    linkLogin.textContent = 'Iniciar Sesión';

    const linkRegistro = document.createElement('a');
    linkRegistro.href = './registro.html';
    linkRegistro.className = 'nav-link';
    linkRegistro.textContent = 'Registrarse';

    const linkMatriz = document.createElement('a');
    linkMatriz.href = './matriz-permisos.html';
    linkMatriz.className = 'nav-link';
    linkMatriz.textContent = 'Matriz RBAC';

    const linkSeg = document.createElement('a');
    linkSeg.href = './seguridad-servidor.html';
    linkSeg.className = 'nav-link';
    linkSeg.textContent = 'Seguridad Servidor';

    linksNav.appendChild(linkLogin);
    linksNav.appendChild(linkRegistro);
    linksNav.appendChild(linkMatriz);
    linksNav.appendChild(linkSeg);

    const chipAnon = document.createElement('span');
    chipAnon.className = 'chip-usuario';
    chipAnon.textContent = 'Modo Demostración';
    chipArea.appendChild(chipAnon);
  }
}

function renderizarHero(autenticado, rol, apodo) {
  const meta = METADATA_ROLES[rol] || METADATA_ROLES.JUGADOR;

  const saludoUsuario = document.getElementById('saludo-usuario');
  const heroSubtitulo = document.getElementById('hero-subtitulo');
  const heroApodo = document.getElementById('hero-apodo');
  const heroRolBadge = document.getElementById('hero-rol-badge');
  const heroAccionesBadge = document.getElementById('hero-acciones-badge');
  const heroTag = document.getElementById('hero-tag');

  if (autenticado) {
    heroTag.textContent = 'Sesión Activa';
    saludoUsuario.textContent = `¡Bienvenido, ${apodo}!`;
    heroSubtitulo.textContent = meta.nombre;
    heroApodo.textContent = apodo;
    heroRolBadge.textContent = `Rol: ${rol}`;
    heroAccionesBadge.textContent = etiquetaAcciones(rol);
  } else {
    heroTag.textContent = 'Modo Demostración';
    saludoUsuario.textContent = 'Portal de Cuentas';
    heroSubtitulo.textContent = meta.nombre;
    heroApodo.textContent = 'Invitado';
    heroRolBadge.textContent = `Rol Simulado: ${rol}`;
    heroAccionesBadge.textContent = etiquetaAcciones(rol);
  }
}

function renderizarFases(rolActual) {
  const contenedor = document.getElementById('flujo-fases');
  contenedor.replaceChildren();

  ESTRUCTURA_FASES.forEach((fase) => {
    const bloque = document.createElement('section');
    bloque.className = 'fase-bloque';

    const enc = document.createElement('header');
    enc.className = 'fase-encabezado';
    enc.innerHTML = `
      <div class="fase-info">
        <div class="fase-numero">${fase.numero}</div>
        <div>
          <h2 class="fase-titulo">${fase.titulo}</h2>
        </div>
      </div>
      <span class="fase-autor">${fase.autor}</span>
    `;
    bloque.appendChild(enc);

    const grid = document.createElement('div');
    grid.className = 'cuadricula-tarjetas';

    fase.pasos.forEach((paso) => {
      const estaPermitido = paso.disponiblePara.includes(rolActual);
      const tarjeta = document.createElement('article');
      tarjeta.className = `tarjeta-paso ${estaPermitido ? 'habilitada' : 'restringida'}`;

      if (estaPermitido) {
        tarjeta.innerHTML = `
          <div>
            <div class="paso-etiqueta-fila">
              <span class="paso-codigo">${paso.codigo}</span>
              <span class="badge-estado badge-estado--activo">Habilitado</span>
            </div>
            <h3 class="tarjeta-titulo">${paso.titulo}</h3>
          </div>
          <div class="tarjeta-pie">
            <span class="tarjeta-meta">Permiso: <code>${paso.accionRequerida}</code></span>
            <a href="${paso.url}" class="btn-primario btn-paso">${paso.textoBoton}</a>
          </div>
        `;
      } else {
        const accionSim = ACCION_A_SIMULADOR[paso.accionRequerida] || '';
        const url403 = `./seguridad-servidor.html?rol=${encodeURIComponent(rolActual)}` +
          (accionSim ? `&accion=${encodeURIComponent(accionSim)}` : '');
        tarjeta.innerHTML = `
          <div>
            <div class="paso-etiqueta-fila">
              <span class="paso-codigo paso-codigo--restringido">${paso.codigo}</span>
              <span class="badge-estado badge-estado--bloqueado">Restringido</span>
            </div>
            <h3 class="tarjeta-titulo">${paso.titulo}</h3>
          </div>
          <div class="tarjeta-pie">
            <span class="tarjeta-meta">Acción: <code>${paso.accionRequerida}</code></span>
            <a href="${url403}" class="btn-paso btn-paso--403">Comprobar 403</a>
          </div>
        `;
      }

      grid.appendChild(tarjeta);
    });

    bloque.appendChild(grid);
    contenedor.appendChild(bloque);
  });
}

function inicializarHub() {
  montarBarraOficial();

  const token = sessionStorage.getItem(CLAVE_TOKEN);
  const autenticado = Boolean(token);
  const rolReal = sessionStorage.getItem(CLAVE_ROL);
  const apodo = sessionStorage.getItem(CLAVE_APODO) || 'Usuario';

  let rolActual = (autenticado && METADATA_ROLES[rolReal]) ? rolReal : 'JUGADOR';

  const conmutadores = document.querySelectorAll('.btn-conmutador-rol');

  function actualizarTodo(rol) {
    rolActual = rol;
    conmutadores.forEach((b) => {
      const activo = b.dataset.rol === rol;
      b.classList.toggle('activo', activo);
      b.setAttribute('aria-pressed', String(activo));
    });

    montarNavegacionContextual(autenticado, rolActual, apodo);
    renderizarHero(autenticado, rolActual, apodo);
    renderizarFases(rolActual);
  }

  conmutadores.forEach((btn) => {
    btn.addEventListener('click', () => {
      if (!autenticado) {
        sessionStorage.setItem(CLAVE_ROL, btn.dataset.rol);
      }
      actualizarTodo(btn.dataset.rol);
    });
  });

  const btnCerrarHero = document.getElementById('btn-cerrar-sesion');
  btnCerrarHero?.addEventListener('click', () => {
    sessionStorage.clear();
    window.location.reload();
  });

  actualizarTodo(rolActual);
}

document.addEventListener('DOMContentLoaded', inicializarHub);
