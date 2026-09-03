/**
 * ==========================================================================
 * Controlador del Hub Central de Cuentas (index.html)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin frameworks)
 * Flujo Secuencial E2E del Grupo 4 (Cuentas, Cumplimiento y Comercio)
 * ==========================================================================
 */

import { construirBarra } from '../comun/barra-navegacion.js';

const CLAVE_TOKEN = 'nexus.token';
const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_APODO = 'nexus.apodoActual';

const ESTRUCTURA_FASES = [
  {
    numero: '1',
    titulo: 'Fase 1: Onboarding y Acceso',
    autor: 'Cristian Camilo Chaparro · HU-AUT-001 / HU-AUT-004',
    descripcion: 'Punto de entrada al videojuego: creación de cuenta validando correo institucional y autenticación con emisión de token JWT.',
    pasos: [
      {
        codigo: 'HU-AUT-001',
        titulo: 'Registro de Jugador',
        descripcion: 'Formulario de alta con validación de requisitos de contraseña, apodo único y subida de avatar oficial.',
        url: './registro.html',
        textoBoton: 'Abrir Registro',
        accionRequerida: 'CREAR_CUENTA_JUGADOR',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR', 'ANONIMO']
      },
      {
        codigo: 'HU-AUT-004',
        titulo: 'Inicio de Sesión y Emisión JWT',
        descripcion: 'Verificación criptográfica contra PostgreSQL (puerto 5433), detección de nuevo dispositivo y generación del Bearer Token.',
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
    autor: 'Santiago Sanabria Uribe · HU-PER-001',
    descripcion: 'Gestión de la ficha del jugador: consulta de datos con el JWT de la sesión y selección de avatar de combate.',
    pasos: [
      {
        codigo: 'HU-PER-001',
        titulo: 'Mi Perfil de Jugador',
        descripcion: 'Consulta de estadísticas, edición de biografía y selección de avatar entre los 10 arquetipos de la galería oficial.',
        url: './perfil.html',
        textoBoton: 'Ver Mi Perfil',
        accionRequerida: 'MODIFICAR_PERFIL_PROPIO',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      }
    ]
  },
  {
    numero: '3',
    titulo: 'Fase 3: Control de Acceso y Seguridad Perimetral',
    autor: 'Andrés Núñez · HU-RBAC-001 / HU-RBAC-004',
    descripcion: 'Núcleo de autorización y defensa en profundidad: modelo de permisos 12×4 en el cliente y validación estricta en el servidor.',
    pasos: [
      {
        codigo: 'HU-RBAC-001',
        titulo: 'Panel de Permisos y Directiva RBAC',
        descripcion: 'Inspección de la matriz de 48 combinaciones en caliente. La directiva reactiva has-permission oculta en el DOM las acciones que exceden tu rol.',
        url: './matriz-permisos.html',
        textoBoton: 'Inspeccionar Matriz RBAC',
        accionRequerida: 'CONSULTA_RBAC',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      },
      {
        codigo: 'HU-RBAC-004',
        titulo: 'Verificación Server-Side y Fail-Closed',
        descripcion: 'El SecurityInterceptor valida claims criptográficos y bloquea intentos de bypass con código 403 RFC 7807 UTF-8 y auditoría asíncrona.',
        url: './seguridad-servidor.html',
        textoBoton: 'Consola de Seguridad',
        accionRequerida: 'TEST_SEGURIDAD',
        disponiblePara: ['JUGADOR', 'MODERADOR', 'ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      }
    ]
  },
  {
    numero: '4',
    titulo: 'Fase 4: Gobernanza y Gestión de Cuentas',
    autor: 'Santiago Sanabria & Edwin · HU-USR-003 / HU-RBAC-003',
    descripcion: 'Operaciones avanzadas de administración: sanciones disciplinarias, baneo permanente y control de revocación de sesiones.',
    pasos: [
      {
        codigo: 'HU-USR-003',
        titulo: 'Gestión de Usuarios y Sanciones',
        descripcion: 'Búsqueda de cuentas de jugadores, aplicación de suspensiones temporales y ejecución de baneos definitivos.',
        url: './gestion-usuarios.html',
        textoBoton: 'Panel de Gestión Admin',
        accionRequerida: 'GESTIONAR_CUENTAS',
        disponiblePara: ['ADMINISTRADOR', 'SUPER_ADMINISTRADOR']
      },
      {
        codigo: 'HU-RBAC-003',
        titulo: 'Alta de Administradores y Cambio de Rol',
        descripcion: 'Nombramiento de nuevos administradores y actualización de versiones de token para invalidar sesiones degradadas.',
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
    nombre: 'Jugador (Nivel 1)',
    acciones: '4 / 12 Acciones Autorizadas',
    subtitulo: 'Portal del Jugador · Arena de Combate',
    descripcion: 'Sesión estándar de jugador. Puedes personalizar tu perfil, auditar tus permisos con la directiva reactiva y simular intentos de bypass en el laboratorio de seguridad.'
  },
  MODERADOR: {
    nombre: 'Moderador (Nivel 2)',
    acciones: '7 / 12 Acciones Autorizadas',
    subtitulo: 'Panel de Supervisión de Comunidad',
    descripcion: 'Sesión con facultades de moderación de contenido y sanciones disciplinarias temporales. Las operaciones de tienda y baneo definitivo están restringidas.'
  },
  ADMINISTRADOR: {
    nombre: 'Administrador (Nivel 3)',
    acciones: '10 / 12 Acciones Autorizadas',
    subtitulo: 'Consola de Administración del Sistema',
    descripcion: 'Sesión administrativa plena con control de economía, catálogo de tienda, gestión de usuarios y aplicación de baneos definitivos.'
  },
  SUPER_ADMINISTRADOR: {
    nombre: 'Super Administrador (Nivel 4)',
    acciones: '11 / 12 Acciones Autorizadas (Default-Deny en Registro)',
    subtitulo: 'Gobernanza y Control Total de Plataforma',
    descripcion: 'Máxima autoridad de The Nexus Battles VI. Facultades de nombramiento de administradores, asignación de roles y auditoría global de seguridad.'
  }
};

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

  // Enlace siempre visible: Hub Principal
  const linkHub = document.createElement('a');
  linkHub.href = './index.html';
  linkHub.className = 'nav-link activo';
  linkHub.textContent = 'Hub del Flujo';
  linksNav.appendChild(linkHub);

  if (autenticado) {
    // Links de usuario autenticado (cero Registro o Login estorbando)
    const linksAutenticado = [
      { texto: 'Mi Perfil', url: './perfil.html' },
      { texto: 'Matriz RBAC (HU-RBAC-001)', url: './matriz-permisos.html' },
      { texto: 'Seguridad Servidor (HU-RBAC-004)', url: './seguridad-servidor.html' }
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

    // Chip de sesión a la derecha
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
    // Links para visitante sin sesión
    const linkLogin = document.createElement('a');
    linkLogin.href = './login.html';
    linkLogin.className = 'nav-link';
    linkLogin.textContent = 'Iniciar Sesión';

    const linkRegistro = document.createElement('a');
    linkRegistro.href = './registro.html';
    linkRegistro.className = 'nav-link';
    linkRegistro.textContent = 'Registrarse';

    linksNav.appendChild(linkLogin);
    linksNav.appendChild(linkRegistro);

    const chipAnon = document.createElement('span');
    chipAnon.className = 'chip-usuario';
    chipAnon.textContent = 'Modo Demostración (Sin sesión)';
    chipArea.appendChild(chipAnon);
  }
}

function renderizarHero(autenticado, rol, apodo) {
  const meta = METADATA_ROLES[rol] || METADATA_ROLES.JUGADOR;

  const saludoUsuario = document.getElementById('saludo-usuario');
  const heroSubtitulo = document.getElementById('hero-subtitulo');
  const heroDesc = document.getElementById('hero-desc');
  const heroApodo = document.getElementById('hero-apodo');
  const heroRolBadge = document.getElementById('hero-rol-badge');
  const heroAccionesBadge = document.getElementById('hero-acciones-badge');
  const heroTag = document.getElementById('hero-tag');

  if (autenticado) {
    heroTag.textContent = 'Sesión Activa en ms-identidad';
    saludoUsuario.textContent = `¡Bienvenido, ${apodo}!`;
    heroSubtitulo.textContent = `${meta.subtitulo} · ${meta.nombre}`;
    heroDesc.textContent = meta.descripcion;
    heroApodo.textContent = apodo;
    heroRolBadge.textContent = `Rol: ${rol}`;
    heroAccionesBadge.textContent = meta.acciones;
  } else {
    heroTag.textContent = 'Demostración de Cuentas (Sprint 1)';
    saludoUsuario.textContent = 'Centro de Demostración E2E · Grupo 4';
    heroSubtitulo.textContent = 'Flujo de Cuentas, Cumplimiento y Comercio';
    heroDesc.textContent = 'Inicia sesión con tus credenciales para cargar tu token criptográfico en el flujo, o explora las fases a continuación seleccionando el rol con los botones superiores.';
    heroApodo.textContent = 'Invitado';
    heroRolBadge.textContent = `Rol Simulado: ${rol}`;
    heroAccionesBadge.textContent = meta.acciones;
  }
}

function renderizarFases(rolActual) {
  const contenedor = document.getElementById('flujo-fases');
  contenedor.replaceChildren();

  ESTRUCTURA_FASES.forEach((fase) => {
    const bloque = document.createElement('section');
    bloque.className = 'fase-bloque';

    // Encabezado de la Fase
    const enc = document.createElement('header');
    enc.className = 'fase-encabezado';
    enc.innerHTML = `
      <div class="fase-info">
        <div class="fase-numero">${fase.numero}</div>
        <div>
          <h2 class="fase-titulo">${fase.titulo}</h2>
          <p style="margin: 3px 0 0 0; font-size: 13.5px; color: #55617d;">${fase.descripcion}</p>
        </div>
      </div>
      <span class="fase-autor">${fase.autor}</span>
    `;
    bloque.appendChild(enc);

    // Cuadrícula de tarjetas de la fase
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
            <p class="tarjeta-desc">${paso.descripcion}</p>
          </div>
          <div class="tarjeta-pie">
            <span class="tarjeta-meta">Permiso: <code>${paso.accionRequerida}</code></span>
            <a href="${paso.url}" class="btn-primario" style="text-decoration: none; font-size: 12.5px; padding: 6px 14px;">
              ${paso.textoBoton}
            </a>
          </div>
        `;
      } else {
        tarjeta.innerHTML = `
          <div>
            <div class="paso-etiqueta-fila">
              <span class="paso-codigo" style="color: #991b1b; background: #fee2e2;">${paso.codigo}</span>
              <span class="badge-estado badge-estado--bloqueado">Restringido</span>
            </div>
            <h3 class="tarjeta-titulo" style="color: #64748b;">${paso.titulo}</h3>
            <p class="tarjeta-desc" style="color: #8a96b2;">
              ${paso.descripcion}
              <br><strong style="color: #b81a1a;">Requiere rol administrativo superior.</strong>
            </p>
          </div>
          <div class="tarjeta-pie">
            <span class="tarjeta-meta">Acción: <code>${paso.accionRequerida}</code></span>
            <a href="./seguridad-servidor.html" class="btn-secundario" style="text-decoration: none; font-size: 11.5px; padding: 5px 10px; color: #b81a1a; border-color: #fca5a5;">
              Comprobar 403
            </a>
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
  let rolActual = sessionStorage.getItem(CLAVE_ROL) || 'JUGADOR';
  const apodo = sessionStorage.getItem(CLAVE_APODO) || 'Usuario';

  if (!METADATA_ROLES[rolActual]) {
    rolActual = 'JUGADOR';
  }

  const conmutadores = document.querySelectorAll('.btn-conmutador-rol');

  function actualizarTodo(rol) {
    rolActual = rol;
    conmutadores.forEach((b) => {
      const activo = b.dataset.rol === rol;
      b.classList.toggle('activo', activo);
      b.setAttribute('aria-pressed', String(activo));
    });

    montarNavegacionContextual(Boolean(token), rolActual, apodo);
    renderizarHero(Boolean(token), rolActual, apodo);
    renderizarFases(rolActual);
  }

  conmutadores.forEach((btn) => {
    btn.addEventListener('click', () => {
      const nuevoRol = btn.dataset.rol;
      sessionStorage.setItem(CLAVE_ROL, nuevoRol);
      actualizarTodo(nuevoRol);
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
