/**
 * ==========================================================================
 * Controlador del Hub Central de Cuentas (index.html)
 * Pila Tecnológica: Vanilla JS ES2022 (Sin frameworks)
 * Arquitectura: Vistas adaptativas según el Rol RBAC activo (HU-RBAC-001)
 * ==========================================================================
 */

import { construirBarra } from '../comun/barra-navegacion.js';

const CLAVE_TOKEN = 'nexus.token';
const CLAVE_ROL = 'nexus.rolActual';
const CLAVE_APODO = 'nexus.apodoActual';

const CONFIGURACION_ROLES = {
  JUGADOR: {
    nivelTexto: 'Nivel 1',
    tituloHero: 'Portal del Jugador',
    descripcionHero: 'Tu cuenta tiene permisos activos para partidas, personalización de perfil y comentarios en la comunidad. Las funciones administrativas están restringidas por la directiva RBAC.',
    badgeClase: 'badge-nivel-1',
    accionesAutorizadas: '4 / 12 Acciones Autorizadas',
    modulosHabilitados: [
      {
        etiqueta: 'Identidad y Personalización',
        titulo: 'Mi Perfil de Jugador',
        descripcion: 'Consulta tus estadísticas, redacta tu biografía y personaliza tu avatar de combate entre los 10 arquetipos oficiales.',
        permiso: 'MODIFICAR_PERFIL_PROPIO',
        url: './perfil.html',
        textoBoton: 'Ver Mi Perfil'
      },
      {
        etiqueta: 'Control de Acceso (HU-RBAC-001)',
        titulo: 'Panel de Mis Permisos RBAC',
        descripcion: 'Inspecciona la matriz de 48 combinaciones en tiempo real. La directiva reactiva oculta los módulos que exceden tu nivel.',
        permiso: 'CONSULTA_RBAC',
        url: './matriz-permisos.html',
        textoBoton: 'Inspeccionar Permisos'
      },
      {
        etiqueta: 'Laboratorio de Seguridad (HU-RBAC-004)',
        titulo: 'Prueba de Bypass y Servidor Fail-Closed',
        descripcion: 'Simula un intento de ataque como Jugador hacia endpoints administrativos y observa el bloqueo 403 Forbidden del servidor.',
        permiso: 'TEST_SEGURIDAD',
        url: './seguridad-servidor.html',
        textoBoton: 'Probar Simulador de Bypass'
      }
    ],
    modulosRestringidos: [
      {
        etiqueta: 'Acceso Denegado (Requiere Nivel 3)',
        titulo: 'Gestión de Cuentas y Sanciones',
        descripcion: 'Buscar usuarios, suspender cuentas o aplicar baneos definitivos requiere privilegios de Administrador.',
        requiereRol: 'ADMINISTRADOR',
        accionRequerida: 'GESTIONAR_CUENTAS'
      },
      {
        etiqueta: 'Acceso Denegado (Requiere Nivel 4)',
        titulo: 'Nombramiento de Administradores',
        descripcion: 'Crear nuevas cuentas administrativas y asignar roles es una facultad exclusiva de Super Administrador.',
        requiereRol: 'SUPER_ADMINISTRADOR',
        accionRequerida: 'CREAR_ADMIN_MODERADOR'
      }
    ]
  },
  MODERADOR: {
    nivelTexto: 'Nivel 2',
    tituloHero: 'Panel de Supervisión y Moderación',
    descripcionHero: 'Tienes asignadas facultades de control comunitario: moderación de comentarios, emisión de advertencias y suspensiones temporales a infractores.',
    badgeClase: 'badge-nivel-2',
    accionesAutorizadas: '7 / 12 Acciones Autorizadas',
    modulosHabilitados: [
      {
        etiqueta: 'Supervisión de Comunidad',
        titulo: 'Moderación y Sanciones Temporales',
        descripcion: 'Panel para emitir advertencias a jugadores y ejecutar suspensiones de cuenta con vencimiento temporal.',
        permiso: 'SUSPENDER_USUARIOS',
        url: './gestion-usuarios.html',
        textoBoton: 'Abrir Panel de Moderación'
      },
      {
        etiqueta: 'Control de Acceso (HU-RBAC-001)',
        titulo: 'Auditoría de Permisos de Moderador',
        descripcion: 'Verifica las 7 acciones habilitadas para tu rol frente a la matriz oficial 12×4 expuesta por ms-identidad.',
        permiso: 'CONSULTA_RBAC',
        url: './matriz-permisos.html',
        textoBoton: 'Auditar Matriz RBAC'
      },
      {
        etiqueta: 'Laboratorio de Seguridad (HU-RBAC-004)',
        titulo: 'Verificación de Límites Server-Side',
        descripcion: 'Comprueba que el interceptor bloquea si un moderador intenta aplicar un baneo permanente o modificar la tienda.',
        permiso: 'TEST_SEGURIDAD',
        url: './seguridad-servidor.html',
        textoBoton: 'Verificar Límites en Servidor'
      },
      {
        etiqueta: 'Identidad',
        titulo: 'Mi Perfil',
        descripcion: 'Administra tu información personal y avatar de moderador.',
        permiso: 'MODIFICAR_PERFIL_PROPIO',
        url: './perfil.html',
        textoBoton: 'Ver Perfil'
      }
    ],
    modulosRestringidos: [
      {
        etiqueta: 'Acceso Denegado (Requiere Nivel 3)',
        titulo: 'Baneo Definitivo y Tienda',
        descripcion: 'Expulsar permanentemente cuentas del videojuego o gestionar el catálogo de tienda requiere rol de Administrador.',
        requiereRol: 'ADMINISTRADOR',
        accionRequerida: 'BANEAR_DEFINITIVAMENTE'
      },
      {
        etiqueta: 'Acceso Denegado (Requiere Nivel 4)',
        titulo: 'Asignación de Roles a Usuarios',
        descripcion: 'Elevar privilegios o nombrar administradores está reservado para el Super Administrador.',
        requiereRol: 'SUPER_ADMINISTRADOR',
        accionRequerida: 'ASIGNAR_ROL'
      }
    ]
  },
  ADMINISTRADOR: {
    nivelTexto: 'Nivel 3',
    tituloHero: 'Consola de Administración del Sistema',
    descripcionHero: 'Facultades administrativas plenas sobre la economía del juego, productos de la tienda, gestión integral de cuentas y aplicación de baneos permanentes.',
    badgeClase: 'badge-nivel-3',
    accionesAutorizadas: '10 / 12 Acciones Autorizadas',
    modulosHabilitados: [
      {
        etiqueta: 'Gobernanza y Sanciones',
        titulo: 'Gestión Integral de Usuarios',
        descripcion: 'Búsqueda avanzada de jugadores, emisión de sanciones disciplinarias y aplicación de baneos permanentes inmediatos.',
        permiso: 'GESTIONAR_CUENTAS',
        url: './gestion-usuarios.html',
        textoBoton: 'Gestionar Cuentas'
      },
      {
        etiqueta: 'Control de Acceso (HU-RBAC-001)',
        titulo: 'Matriz de Permisos de Administrador',
        descripcion: 'Inspecciona las 10 acciones autorizadas para Administrador y confirma el bloqueo de acciones de Super Admin.',
        permiso: 'CONSULTA_RBAC',
        url: './matriz-permisos.html',
        textoBoton: 'Consultar Matriz RBAC'
      },
      {
        etiqueta: 'Seguridad Perimetral (HU-RBAC-004)',
        titulo: 'Pruebas de Interceptor y Fail-Closed',
        descripcion: 'Ejecuta peticiones reales de administración al backend y verifica los registros de auditoría JSON en ms-cumplimiento.',
        permiso: 'TEST_SEGURIDAD',
        url: './seguridad-servidor.html',
        textoBoton: 'Abrir Consola Servidor'
      },
      {
        etiqueta: 'Identidad',
        titulo: 'Mi Perfil de Administrador',
        descripcion: 'Administra tus datos personales y configuración.',
        permiso: 'MODIFICAR_PERFIL_PROPIO',
        url: './perfil.html',
        textoBoton: 'Ver Perfil'
      }
    ],
    modulosRestringidos: [
      {
        etiqueta: 'Acceso Denegado (Requiere Nivel 4)',
        titulo: 'Nombramiento de Administradores y Asignación de Roles',
        descripcion: 'Solo el Super Administrador puede otorgar roles a otros usuarios o crear nuevas cuentas con privilegios administrativos.',
        requiereRol: 'SUPER_ADMINISTRADOR',
        accionRequerida: 'CREAR_ADMIN_MODERADOR'
      }
    ]
  },
  SUPER_ADMINISTRADOR: {
    nivelTexto: 'Nivel 4 · Total',
    tituloHero: 'Gobernanza y Control Total de la Plataforma',
    descripcionHero: 'Máxima autoridad del sistema. Control de nombramiento de administradores, asignación de roles, auditoría de seguridad y supervisión de microservicios.',
    badgeClase: 'badge-nivel-4',
    accionesAutorizadas: '11 / 12 Acciones Autorizadas (Default-Deny en Registro)',
    modulosHabilitados: [
      {
        etiqueta: 'Alta de Autoridades',
        titulo: 'Nombramiento de Administradores y Moderadores',
        descripcion: 'Formulario seguro para registrar nuevas cuentas con privilegios de gestión comunitaria y administración de plataforma.',
        permiso: 'CREAR_ADMIN_MODERADOR',
        url: './crear-cuenta-admin.html',
        textoBoton: 'Crear Cuenta Administrativa'
      },
      {
        etiqueta: 'Gobernanza Global',
        titulo: 'Gestión Total de Cuentas y Roles',
        descripcion: 'Modificación de roles de usuario en caliente, baneos definitivos y auditoría de cuentas activas.',
        permiso: 'ASIGNAR_ROL',
        url: './gestion-usuarios.html',
        textoBoton: 'Control Global de Usuarios'
      },
      {
        etiqueta: 'Control de Acceso (HU-RBAC-001)',
        titulo: 'Matriz Global de 48 Combinaciones',
        descripcion: 'Inspecciona la capacidad total (11/12) y demuestra el Default-Deny en la acción CREAR_CUENTA_JUGADOR.',
        permiso: 'CONSULTA_RBAC',
        url: './matriz-permisos.html',
        textoBoton: 'Auditar Matriz Global'
      },
      {
        etiqueta: 'Seguridad Server-Side (HU-RBAC-004)',
        titulo: 'Verificación Server-Side y Fail-Closed',
        descripcion: 'Pruebas de validación criptográfica de tokens y respuestas Problem Details RFC 7807 UTF-8.',
        permiso: 'TEST_SEGURIDAD',
        url: './seguridad-servidor.html',
        textoBoton: 'Consola de Seguridad'
      },
      {
        etiqueta: 'Identidad',
        titulo: 'Mi Perfil',
        descripcion: 'Administra tus datos personales y credenciales.',
        permiso: 'MODIFICAR_PERFIL_PROPIO',
        url: './perfil.html',
        textoBoton: 'Ver Perfil'
      }
    ],
    modulosRestringidos: [
      {
        etiqueta: 'Acceso Restringido por Diseño (Default-Deny Real)',
        titulo: 'Auto-Registro Público de Jugador',
        descripcion: 'La acción CREAR_CUENTA_JUGADOR es un auto-registro público del jugador novato. Por principio de Mínimo Privilegio, no es una función de Super Admin.',
        requiereRol: 'PÚBLICO',
        accionRequerida: 'CREAR_CUENTA_JUGADOR'
      }
    ]
  }
};

function montarBarra() {
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

function inicializarHub() {
  montarBarra();

  const token = sessionStorage.getItem(CLAVE_TOKEN);
  let rolActual = sessionStorage.getItem(CLAVE_ROL) || 'JUGADOR';
  const apodo = sessionStorage.getItem(CLAVE_APODO) || 'Usuario';

  if (!CONFIGURACION_ROLES[rolActual]) {
    rolActual = 'JUGADOR';
  }

  // Elementos del DOM
  const heroAutenticado = document.getElementById('hero-autenticado');
  const heroAnonimo = document.getElementById('hero-anonimo');
  const saludoUsuario = document.getElementById('saludo-usuario');
  const heroTituloRol = document.getElementById('hero-titulo-rol');
  const heroDescRol = document.getElementById('hero-desc-rol');
  const heroApodo = document.getElementById('hero-apodo');
  const heroRolBadge = document.getElementById('hero-rol-badge');
  const heroAccionesBadge = document.getElementById('hero-acciones-badge');
  const btnCerrarSesion = document.getElementById('btn-cerrar-sesion');

  const contenedorHabilitados = document.getElementById('contenedor-habilitados');
  const contenedorRestringidos = document.getElementById('contenedor-restringidos');
  const botonesConmutador = document.querySelectorAll('.btn-conmutador-rol');

  // Si hay sesión activa
  if (token) {
    heroAutenticado.style.display = 'block';
    heroAnonimo.style.display = 'none';

    renderizarVistaPorRol(rolActual, apodo);
  } else {
    heroAutenticado.style.display = 'none';
    heroAnonimo.style.display = 'block';
    renderizarVistaPorRol('JUGADOR', 'Invitado');
  }

  // Conmutador rápido de roles para la sustentación
  botonesConmutador.forEach((btn) => {
    btn.addEventListener('click', () => {
      const nuevoRol = btn.dataset.rol;
      sessionStorage.setItem(CLAVE_ROL, nuevoRol);
      renderizarVistaPorRol(nuevoRol, apodo);
    });
  });

  btnCerrarSesion?.addEventListener('click', () => {
    sessionStorage.clear();
    window.location.reload();
  });

  function renderizarVistaPorRol(rol, apodoUsuario) {
    const config = CONFIGURACION_ROLES[rol] || CONFIGURACION_ROLES.JUGADOR;

    // 1. Actualizar Hero
    saludoUsuario.textContent = `¡Bienvenido, ${apodoUsuario}!`;
    heroTituloRol.textContent = `${config.tituloHero} (${config.nivelTexto})`;
    heroDescRol.textContent = config.descripcionHero;
    heroApodo.textContent = apodoUsuario;
    heroRolBadge.textContent = `Rol: ${rol} (${config.nivelTexto})`;
    heroAccionesBadge.textContent = config.accionesAutorizadas;

    // 2. Actualizar estado visual de los botones del conmutador
    botonesConmutador.forEach((btn) => {
      const esActivo = btn.dataset.rol === rol;
      btn.classList.toggle('activo', esActivo);
      btn.setAttribute('aria-pressed', String(esActivo));
    });

    // 3. Renderizar Módulos Habilitados
    contenedorHabilitados.replaceChildren();
    config.modulosHabilitados.forEach((m) => {
      const card = document.createElement('article');
      card.className = 'tarjeta-paso tarjeta-habilitada';
      card.innerHTML = `
        <div>
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <span class="paso-numero">${m.etiqueta}</span>
            <span class="badge-acceso badge-acceso--permitido">Habilitado</span>
          </div>
          <h3 class="paso-titulo">${m.titulo}</h3>
          <p class="paso-desc">${m.descripcion}</p>
        </div>
        <div class="paso-pie">
          <span class="paso-autor">Permiso: <code>${m.permiso}</code></span>
          <a href="${m.url}" class="btn-primario" style="text-decoration: none; font-size: 12.5px; padding: 7px 16px;">
            ${m.textoBoton}
          </a>
        </div>
      `;
      contenedorHabilitados.appendChild(card);
    });

    // 4. Renderizar Módulos Restringidos
    contenedorRestringidos.replaceChildren();
    config.modulosRestringidos.forEach((m) => {
      const card = document.createElement('article');
      card.className = 'tarjeta-paso tarjeta-paso--restringida';
      card.innerHTML = `
        <div>
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <span class="paso-numero paso-numero--restringido">${m.etiqueta}</span>
            <span class="badge-acceso badge-acceso--denegado">Restringido</span>
          </div>
          <h3 class="paso-titulo" style="color: #64748b;">${m.titulo}</h3>
          <p class="paso-desc" style="color: #8a96b2;">${m.descripcion}</p>
        </div>
        <div class="paso-pie">
          <span class="paso-autor">Acción Bloqueada: <code>${m.accionRequerida}</code></span>
          <a href="./seguridad-servidor.html" class="btn-secundario" style="text-decoration: none; font-size: 12px; padding: 6px 12px; color: #b81a1a; border-color: #f8c8c8;">
            Comprobar Rechazo 403
          </a>
        </div>
      `;
      contenedorRestringidos.appendChild(card);
    });
  }
}

document.addEventListener('DOMContentLoaded', inicializarHub);
