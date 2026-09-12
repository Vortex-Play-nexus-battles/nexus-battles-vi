/**
 * Subastas - Módulo interactivo de pujas y compras inmediatas (HU-SUB-004)
 *
 * Cumple con PILA_T_1 y .claude/rules/frontend-web.md:
 * - HTML5 + CSS3 + ES2022 nativo sin dependencias de frameworks
 * - 4 estados obligatorios (RNF-USA-003): carga, éxito, vacío, error
 * - Resolución de contraste WCAG 2.2 AA y fichas de tokens
 * - Límites de participación: 10 subastas activas, intervalo de 5 s
 */

export const SUBASTAS_INICIALES = [
  {
    id: 'hacha-obsidiana',
    nombre: 'Hacha de Obsidiana Fracturada',
    tipo: 'Arma · Dos manos',
    descripcion: 'Hoja de obsidiana templada en el Abismo. Su filo no se degrada, pero cada golpe cobra una fracción de la vitalidad de quien la empuña.',
    rareza: 'epica',
    nivel: 24,
    vendedor: 'kaelthas_vx',
    oferta: 1350,
    compraInmediata: 2800,
    mediaMercado: 1800,
    segundosRestantes: 38,
    ganando: true,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 1350,
    rival: 'draconis_91',
    rivales: 3,
    aporte: { poder: 14, vida: 0, defensa: -3 },
    historial: [
      { apodo: 'andres_nv', monto: 1350, tipo: 'Manual', cuando: 'hace 9 s', esTu: true },
      { apodo: 'draconis_91', monto: 1300, tipo: 'Automática', cuando: 'hace 25 s', esTu: false },
      { apodo: 'kael_vortex', monto: 1200, tipo: 'Automática', cuando: 'hace 1 min', esTu: false },
      { apodo: 'andres_nv', monto: 1150, tipo: 'Manual', cuando: 'hace 2 min', esTu: true }
    ]
  },
  {
    id: 'grebas-centinela',
    nombre: 'Grebas del Centinela Caído',
    tipo: 'Armadura · Piernas',
    descripcion: 'Placas recogidas del campo de Vael. Pesan, pero aguantan lo que nada.',
    rareza: 'rara',
    nivel: 20,
    vendedor: 'valeria_iron',
    oferta: 750,
    compraInmediata: 1500,
    mediaMercado: 900,
    segundosRestantes: 184,
    ganando: false,
    superado: true,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'morrigan_x',
    rivales: 2,
    aporte: { poder: 0, vida: 90, defensa: 18 },
    historial: [
      { apodo: 'morrigan_x', monto: 750, tipo: 'Manual', cuando: 'hace 12 s', esTu: false },
      { apodo: 'andres_nv', monto: 700, tipo: 'Manual', cuando: 'hace 45 s', esTu: true }
    ]
  },
  {
    id: 'arco-cazador',
    nombre: 'Arco del Susurro Nocturno',
    tipo: 'Arma · Distancia',
    descripcion: 'Cuerda trenzada con tendón de quimera. Los disparos no emiten silbido al rasgar el viento.',
    rareza: 'legendaria',
    nivel: 25,
    vendedor: 'aerith_moon',
    oferta: 2100,
    compraInmediata: 4500,
    mediaMercado: 2400,
    segundosRestantes: 620,
    ganando: true,
    superado: false,
    autoLimite: 2500,
    esperaSegundos: 0,
    retenido: 2100,
    rival: 'fenrir_9',
    rivales: 4,
    aporte: { poder: 22, vida: -20, defensa: 0 },
    historial: [
      { apodo: 'andres_nv', monto: 2100, tipo: 'Automática', cuando: 'hace 1 min', esTu: true },
      { apodo: 'fenrir_9', monto: 2000, tipo: 'Manual', cuando: 'hace 2 min', esTu: false }
    ]
  },
  {
    id: 'baculo-cristal',
    nombre: 'Báculo de Runas Resonantes',
    tipo: 'Arma · Mágica',
    descripcion: 'Canaliza la resonancia arcana amplificando el alcance de los conjuros mayores.',
    rareza: 'rara',
    nivel: 22,
    vendedor: 'zephyr_mage',
    oferta: 600,
    compraInmediata: 1200,
    mediaMercado: 850,
    segundosRestantes: 950,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'ignis_red',
    rivales: 1,
    aporte: { poder: 18, vida: 40, defensa: -5 },
    historial: [
      { apodo: 'ignis_red', monto: 600, tipo: 'Manual', cuando: 'hace 5 min', esTu: false }
    ]
  },
  {
    id: 'anillo-veterano',
    nombre: 'Anillo de Hierro del Veterano',
    tipo: 'Accesorio · Dedo',
    descripcion: 'Sencillo pero forjado con temple impecable. Otorga tenacidad en asedios prolongados.',
    rareza: 'comun',
    nivel: 15,
    vendedor: 'barkeep_tom',
    oferta: 250,
    compraInmediata: 500,
    mediaMercado: 350,
    segundosRestantes: 1800,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'novato_12',
    rivales: 1,
    aporte: { poder: 5, vida: 30, defensa: 2 },
    historial: [
      { apodo: 'novato_12', monto: 250, tipo: 'Manual', cuando: 'hace 10 min', esTu: false }
    ]
  }
];

export const HEROES_BASE = [
  {
    id: 'kaelen',
    nombre: 'Kaelen',
    nivel: 26,
    clase: 'Guerrero',
    stats: { poder: 142, vida: 890, defensa: 64, ataque: '10 + 1d6', dano: '8 + 2d4' }
  },
  {
    id: 'lyra',
    nombre: 'Lyra',
    nivel: 21,
    clase: 'Exploradora',
    stats: { poder: 118, vida: 640, defensa: 48, ataque: '7 + 1d8', dano: '6 + 1d6' }
  }
];

export const CONFIG_REGLAS = {
  creditosTotales: 6200,
  incrementoMinimo: 50,
  intervaloSegundos: 5,
  maxSubastasSimultaneas: 10,
  maxPujasActivas: 50
};

// =========================================================================
// Funciones de cálculo y lógica pura (probables con Jest sin DOM)
// =========================================================================

export function formatearCreditos(n) {
  return Number(n || 0).toLocaleString('es-CO');
}

export function formatearTiempo(seg) {
  if (seg <= 0) return 'Cerrada';
  if (seg >= 3600) {
    const h = Math.floor(seg / 3600);
    const m = Math.floor((seg % 3600) / 60);
    return `${h} h ${m} m`;
  }
  const m = Math.floor(seg / 60);
  const s = seg % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
}

export function calcularSaldoRetenido(subastas = []) {
  return subastas.reduce((acc, sub) => acc + (sub.retenido || 0), 0);
}

export function calcularSaldoLibre(total, subastas = []) {
  return Math.max(0, total - calcularSaldoRetenido(subastas));
}

export function calcularMinimoPuja(ofertaActual, incremento = CONFIG_REGLAS.incrementoMinimo) {
  return (Number(ofertaActual) || 0) + (Number(incremento) || 50);
}

export function validarPuja(monto, subasta, saldoLibre, incremento = CONFIG_REGLAS.incrementoMinimo, esperaRestante = 0) {
  if (!subasta) {
    return { valida: false, motivo: 'Subasta no encontrada.' };
  }
  if (subasta.segundosRestantes <= 0) {
    return { valida: false, motivo: 'La subasta está cerrada.' };
  }
  if (esperaRestante > 0) {
    return { valida: false, motivo: `Debes esperar ${esperaRestante} s antes de volver a pujar en esta subasta.` };
  }
  const min = calcularMinimoPuja(subasta.oferta, incremento);
  if (monto < min) {
    return { valida: false, motivo: `La oferta debe ser de al menos ${formatearCreditos(min)} cr (+${incremento} cr).` };
  }
  const disponibleParaEsta = saldoLibre + (subasta.retenido || 0);
  if (monto > disponibleParaEsta) {
    return { valida: false, motivo: `Saldo insuficiente. Tienes ${formatearCreditos(disponibleParaEsta)} cr disponibles para esta subasta.` };
  }
  return { valida: true };
}

export function validarLimiteAuto(limite, subasta, saldoLibre, incremento = CONFIG_REGLAS.incrementoMinimo) {
  if (!subasta) return { valida: false, motivo: 'Subasta no encontrada.' };
  const min = calcularMinimoPuja(subasta.oferta, incremento);
  if (limite < min) {
    return { valida: false, motivo: `El tope de puja automática debe ser al menos ${formatearCreditos(min)} cr.` };
  }
  const disponibleParaEsta = saldoLibre + (subasta.retenido || 0);
  if (limite > disponibleParaEsta) {
    return { valida: false, motivo: `Saldo insuficiente para ese tope. Máximo disponible: ${formatearCreditos(disponibleParaEsta)} cr.` };
  }
  return { valida: true };
}

export function calcularComparacionHeroe(heroe, item) {
  if (!heroe || !item) return { comparaciones: [], nivelInsuficiente: false, deltaNivel: 0 };
  const nivelInsuficiente = heroe.nivel < item.nivel;
  const deltaNivel = item.nivel - heroe.nivel;
  const st = heroe.stats;
  const ap = item.aporte || { poder: 0, vida: 0, defensa: 0 };

  const comparaciones = [
    { stat: 'Poder', actual: st.poder, nuevo: st.poder + ap.poder, delta: ap.poder },
    { stat: 'Vida', actual: st.vida, nuevo: st.vida + ap.vida, delta: ap.vida },
    { stat: 'Defensa', actual: st.defensa, nuevo: st.defensa + ap.defensa, delta: ap.defensa }
  ];

  return { comparaciones, nivelInsuficiente, deltaNivel };
}

// =========================================================================
// Controlador y renderizador interactivo DOM
// =========================================================================

export class ControladorSubastas {
  constructor({ contenedor, subastas = SUBASTAS_INICIALES, heroes = HEROES_BASE, config = CONFIG_REGLAS } = {}) {
    this.contenedor = contenedor;
    this.subastas = JSON.parse(JSON.stringify(subastas));
    this.heroes = JSON.parse(JSON.stringify(heroes));
    this.config = Object.assign({}, CONFIG_REGLAS, config);
    this.heroeId = this.heroes[0]?.id || 'kaelen';
    this.vista = 'lista'; // 'lista' | 'detalle'
    this.subastaActivaId = null;
    this.confirmandoCompra = false;
    this.resultadoCierre = null;
    this.montoPersonalizado = null;
    this.limiteAutoPersonalizado = null;
    this.estadoDatos = 'exito'; // 'carga' | 'exito' | 'vacio' | 'error'
    this.mensajeError = null;
    this.intervalId = null;
  }

  iniciar() {
    this.iniciarTemporizador();
    this.render();
  }

  destruir() {
    if (this.intervalId) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
  }

  iniciarTemporizador() {
    this.destruir();
    this.intervalId = setInterval(() => {
      let cambio = false;
      this.subastas.forEach((sub) => {
        if (sub.segundosRestantes > 0) {
          sub.segundosRestantes -= 1;
          cambio = true;
        }
        if (sub.esperaSegundos > 0) {
          sub.esperaSegundos -= 1;
          cambio = true;
        }
      });
      if (cambio && this.contenedor) {
        this.actualizarTiemposEnDOM();
      }
    }, 1000);
  }

  getSaldoLibre() {
    return calcularSaldoLibre(this.config.creditosTotales, this.subastas);
  }

  getSaldoRetenido() {
    return calcularSaldoRetenido(this.subastas);
  }

  getSubastaActiva() {
    return this.subastas.find((s) => s.id === this.subastaActivaId) || this.subastas[0];
  }

  getHeroeActivo() {
    return this.heroes.find((h) => h.id === this.heroeId) || this.heroes[0];
  }

  abrirDetalle(id) {
    this.subastaActivaId = id;
    this.vista = 'detalle';
    this.confirmandoCompra = false;
    this.resultadoCierre = null;
    this.montoPersonalizado = null;
    this.limiteAutoPersonalizado = null;
    this.render();
  }

  volverALista() {
    this.vista = 'lista';
    this.subastaActivaId = null;
    this.confirmandoCompra = false;
    this.resultadoCierre = null;
    this.render();
  }

  seleccionarHeroe(id) {
    this.heroeId = id;
    this.render();
  }

  pujar(monto) {
    const sub = this.getSubastaActiva();
    const saldoLibre = this.getSaldoLibre();
    const validacion = validarPuja(monto, sub, saldoLibre, this.config.incrementoMinimo, sub.esperaSegundos);

    if (!validacion.valida) {
      alert(validacion.motivo);
      return false;
    }

    sub.oferta = monto;
    sub.retenido = monto;
    sub.ganando = true;
    sub.superado = false;
    sub.esperaSegundos = this.config.intervaloSegundos;
    sub.historial.unshift({
      apodo: 'andres_nv',
      monto,
      tipo: 'Manual',
      cuando: 'ahora',
      esTu: true
    });

    // Simulación de respuesta automática de rival tras 4 s
    if (sub.rival && sub.segundosRestantes > 10) {
      setTimeout(() => {
        if (sub.ganando && sub.segundosRestantes > 5) {
          const contraoferta = sub.oferta + this.config.incrementoMinimo;
          sub.oferta = contraoferta;
          sub.ganando = false;
          sub.superado = true;
          sub.retenido = 0; // Créditos restituidos
          sub.historial.unshift({
            apodo: sub.rival,
            monto: contraoferta,
            tipo: 'Automática',
            cuando: 'ahora',
            esTu: false
          });
          if (this.contenedor) this.render();
        }
      }, 4000);
    }

    this.render();
    return true;
  }

  configurarAutoPuja(limite) {
    const sub = this.getSubastaActiva();
    const saldoLibre = this.getSaldoLibre();
    const validacion = validarLimiteAuto(limite, sub, saldoLibre, this.config.incrementoMinimo);

    if (!validacion.valida) {
      alert(validacion.motivo);
      return false;
    }

    sub.autoLimite = limite;
    this.render();
    return true;
  }

  desactivarAutoPuja() {
    const sub = this.getSubastaActiva();
    if (sub) {
      sub.autoLimite = 0;
      this.render();
    }
  }

  solicitarCompraInmediata() {
    this.confirmandoCompra = true;
    this.render();
  }

  cancelarCompraInmediata() {
    this.confirmandoCompra = false;
    this.render();
  }

  confirmarCompraInmediata() {
    const sub = this.getSubastaActiva();
    const saldoLibre = this.getSaldoLibre() + (sub.retenido || 0);

    if (saldoLibre < sub.compraInmediata) {
      alert('No dispones de saldo suficiente para comprar de inmediato.');
      this.confirmandoCompra = false;
      this.render();
      return false;
    }

    sub.oferta = sub.compraInmediata;
    sub.retenido = sub.compraInmediata;
    sub.ganando = true;
    sub.superado = false;
    sub.segundosRestantes = 0;
    this.confirmandoCompra = false;
    this.resultadoCierre = 'comprada';
    this.render();
    return true;
  }

  actualizarTiemposEnDOM() {
    const elementosTiempo = this.contenedor.querySelectorAll('[data-tiempo-subasta]');
    elementosTiempo.forEach((el) => {
      const id = el.getAttribute('data-tiempo-subasta');
      const sub = this.subastas.find((s) => s.id === id);
      if (sub) {
        el.textContent = formatearTiempo(sub.segundosRestantes);
        if (sub.segundosRestantes <= 10 && sub.segundosRestantes > 0) {
          el.classList.add('tiempo-urgente');
        } else {
          el.classList.remove('tiempo-urgente');
        }
      }
    });
  }

  render() {
    if (!this.contenedor) return;

    if (this.estadoDatos === 'carga') {
      this.contenedor.innerHTML = `
        <div class="estado-contenedor estado-carga" role="status">
          <div class="spinner"></div>
          <p>Cargando subastas activas...</p>
        </div>
      `;
      return;
    }

    if (this.estadoDatos === 'error') {
      this.contenedor.innerHTML = `
        <div class="estado-contenedor estado-error" role="alert">
          <h3 class="titulo-mediano">Error al cargar las subastas</h3>
          <p>${this.mensajeError || 'No fue posible conectar con el servicio de subastas.'}</p>
          <button class="btn btn-primario" id="btn-reintentar">Reintentar</button>
        </div>
      `;
      this.contenedor.querySelector('#btn-reintentar')?.addEventListener('click', () => {
        this.estadoDatos = 'exito';
        this.render();
      });
      return;
    }

    if (this.estadoDatos === 'vacio' || this.subastas.length === 0) {
      this.contenedor.innerHTML = `
        <div class="estado-contenedor estado-vacio">
          <h3 class="titulo-mediano">No hay subastas en curso</h3>
          <p>Cuando los jugadores publiquen objetos en venta, aparecerán aquí para pujar.</p>
        </div>
      `;
      return;
    }

    const total = this.config.creditosTotales;
    const retenido = this.getSaldoRetenido();
    const libre = this.getSaldoLibre();
    const subastasGanando = this.subastas.filter((s) => s.ganando).length;
    const superadas = this.subastas.filter((s) => s.superado).length;

    let contenidoHtml = '';

    if (this.vista === 'lista') {
      contenidoHtml = this.generarHtmlLista({ total, retenido, libre, subastasGanando, superadas });
    } else {
      contenidoHtml = this.generarHtmlDetalle({ total, retenido, libre });
    }

    this.contenedor.innerHTML = contenidoHtml;
    this.conectarEventos();
  }

  generarHtmlLista({ total, retenido, libre, subastasGanando, superadas }) {
    const pctRetenido = total > 0 ? ((retenido / total) * 100).toFixed(1) : 0;
    const pctLibre = total > 0 ? ((libre / total) * 100).toFixed(1) : 100;

    return `
      <div class="subastas-app">
        <!-- Resumen de Saldos y Participación -->
        <header class="panel-resumen">
          <div class="resumen-titular">
            <div>
              <span class="eyebrow">MERCADO EN VIVO · HU-SUB-004</span>
              <h1 class="titulo-grande">Subastas y Pujas</h1>
            </div>
            <div class="resumen-saldo-total">
              <span class="etiqueta-saldo">Saldo total</span>
              <span class="valor-saldo cifra">${formatearCreditos(total)} cr</span>
            </div>
          </div>

          <div class="barra-distribucion">
            <div class="segmento-retenido" style="width: ${pctRetenido}%;" title="Retenido en pujas: ${formatearCreditos(retenido)} cr"></div>
            <div class="segmento-libre" style="width: ${pctLibre}%;" title="Libre: ${formatearCreditos(libre)} cr"></div>
          </div>

          <div class="resumen-metadatos">
            <div class="chip-info">
              <span class="punto-color punto-retenido"></span>
              <span>Retenido en pujas: <strong>${formatearCreditos(retenido)} cr</strong></span>
            </div>
            <div class="chip-info">
              <span class="punto-color punto-libre"></span>
              <span>Disponible libre: <strong>${formatearCreditos(libre)} cr</strong></span>
            </div>
            <div class="chip-info ${superadas > 0 ? 'alerta-superada' : ''}">
              <span>Vas ganando en: <strong>${subastasGanando}</strong></span>
              ${superadas > 0 ? `<span class="badge badge-error">¡Te superaron en ${superadas}!</span>` : ''}
            </div>
            <div class="chip-info">
              <span>Límite activo: <strong>${this.subastas.length} de ${this.config.maxSubastasSimultaneas} subastas</strong></span>
            </div>
          </div>
        </header>

        <!-- Cuadrícula de Subastas -->
        <section class="seccion-subastas" aria-label="Listado de subastas activas">
          <div class="encabezado-listado">
            <h2 class="titulo-seccion">Subastas en curso (${this.subastas.length})</h2>
            <span class="texto-pista">Ordenadas por tiempo restante · Selecciona una para ver el detalle y pujar</span>
          </div>

          <div class="grid-subastas">
            ${this.subastas.map((sub) => this.generarTarjetaSubasta(sub)).join('')}
          </div>
        </section>
      </div>
    `;
  }

  generarTarjetaSubasta(sub) {
    const urgente = sub.segundosRestantes <= 10 && sub.segundosRestantes > 0;
    const cerrada = sub.segundosRestantes <= 0;

    let badgeEstado = '<span class="badge badge-neutral">Sin pujar</span>';
    let claseBorde = '';
    let textoBoton = 'Ver subasta';
    let claseBoton = 'btn-contorno';

    if (sub.ganando) {
      badgeEstado = '<span class="badge badge-exito">Vas ganando</span>';
      claseBorde = 'tarjeta-ganando';
    } else if (sub.superado) {
      badgeEstado = '<span class="badge badge-error">Te superaron</span>';
      claseBorde = 'tarjeta-superada';
      textoBoton = 'Recuperarla';
      claseBoton = 'btn-primario';
    } else if (urgente) {
      textoBoton = 'Ir ahora';
      claseBoton = 'btn-primario';
    }

    return `
      <article class="tarjeta tarjeta-subasta ${claseBorde} ${urgente ? 'urgente' : ''}" data-id="${sub.id}">
        <div class="tarjeta-cabecera">
          <span class="badge badge-${sub.rareza}">${sub.rareza.toUpperCase()}</span>
          ${badgeEstado}
        </div>

        <div class="tarjeta-cuerpo">
          <h3 class="tarjeta-titulo">${sub.nombre}</h3>
          <p class="tarjeta-subtitulo">${sub.tipo} · Nivel req. ${sub.nivel}</p>
          <p class="tarjeta-desc">${sub.descripcion}</p>
        </div>

        <div class="tarjeta-finanzas">
          <div class="columna-oferta">
            <span class="etiqueta-sm">Oferta actual</span>
            <span class="monto-destacado cifra">${formatearCreditos(sub.oferta)} cr</span>
            <span class="postor-texto ${sub.ganando ? 'texto-exito' : ''}">
              ${sub.ganando ? 'Tu puja lidera' : `Mejor postor: ${sub.rival || 'rival'}`}
            </span>
          </div>
          <div class="columna-tiempo">
            <span class="etiqueta-sm">Tiempo restante</span>
            <span class="tiempo-cifra cifra ${urgente ? 'tiempo-urgente' : ''}" data-tiempo-subasta="${sub.id}">
              ${formatearTiempo(sub.segundosRestantes)}
            </span>
            <span class="compra-ya-texto">Comprar ya: ${formatearCreditos(sub.compraInmediata)} cr</span>
          </div>
        </div>

        <div class="tarjeta-acciones">
          <button type="button" class="btn ${claseBoton} btn-abrir" data-abrir="${sub.id}">
            ${textoBoton}
          </button>
        </div>
      </article>
    `;
  }

  generarHtmlDetalle({ total, retenido, libre }) {
    const sub = this.getSubastaActiva();
    const hero = this.getHeroeActivo();
    const comp = calcularComparacionHeroe(hero, sub);
    const minPuja = calcularMinimoPuja(sub.oferta, this.config.incrementoMinimo);
    const disponibleAqui = libre + (sub.retenido || 0);
    const cerrada = sub.segundosRestantes <= 0 || this.resultadoCierre !== null;
    const urgente = sub.segundosRestantes <= 10 && !cerrada;

    return `
      <div class="subastas-app vista-detalle">
        <!-- Barra de navegación contextual -->
        <div class="barra-volver">
          <button type="button" class="btn btn-texto" id="btn-volver">
            ← Volver al listado de subastas
          </button>
          <span class="separador-pipe">|</span>
          <span class="saldo-contextual">Disponible libre: <strong>${formatearCreditos(libre)} cr</strong> (en esta subasta: <strong>${formatearCreditos(disponibleAqui)} cr</strong>)</span>
        </div>

        <!-- Alerta de resultado final si cerró -->
        ${this.resultadoCierre === 'comprada' ? `
          <div class="alerta alerta-exito-cierre" role="alert">
            <h2 class="titulo-mediano">¡Has comprado este objeto de inmediato!</h2>
            <p>Se te adjudicó por <strong>${formatearCreditos(sub.compraInmediata)} cr</strong>. El objeto ha sido transferido a tu inventario.</p>
          </div>
        ` : ''}

        <div class="detalle-grid">
          <!-- Columna Izquierda: Información del Objeto y Comparativa con Héroe -->
          <section class="columna-info-objeto">
            <div class="panel-objeto">
              <div class="objeto-badges">
                <span class="badge badge-${sub.rareza}">${sub.rareza.toUpperCase()}</span>
                <span class="badge badge-neutral">Vendedor: ${sub.vendedor}</span>
                <span class="badge ${comp.nivelInsuficiente ? 'badge-error' : 'badge-exito'}">
                  Req. Nivel ${sub.nivel}
                </span>
              </div>

              <h1 class="titulo-grande titulo-objeto">${sub.nombre}</h1>
              <p class="objeto-tipo">${sub.tipo}</p>
              <p class="objeto-descripcion">${sub.descripcion}</p>

              <!-- Selector de Héroe para Comparación de Estadísticas -->
              <div class="selector-heroes-seccion">
                <span class="etiqueta-sm">Comparar compatibilidad con héroe activo:</span>
                <div class="selector-heroes-botones" role="radiogroup" aria-label="Elegir héroe">
                  ${this.heroes.map((h) => `
                    <button type="button" class="btn-heroe-chip ${h.id === this.heroeId ? 'heroe-elegido' : ''}" data-heroe="${h.id}">
                      <strong>${h.nombre}</strong> (Niv. ${h.nivel} · ${h.clase})
                    </button>
                  `).join('')}
                </div>
              </div>

              <!-- Alerta de nivel si aplica -->
              ${comp.nivelInsuficiente ? `
                <div class="alerta alerta-advertencia" role="alert">
                  <strong>⚠️ Nivel insuficiente:</strong> ${hero.nombre} es nivel ${hero.nivel}. Le faltan ${comp.deltaNivel} niveles para poder equipar este objeto (RN-INV-004).
                </div>
              ` : `
                <div class="alerta alerta-exito-suave">
                  <strong>✓ Compatible:</strong> ${hero.nombre} cumple el nivel requerido para equipar este objeto.
                </div>
              `}

              <!-- Tabla de Comparación de Atributos -->
              <div class="tabla-comparacion-contenedor">
                <table class="tabla-comparacion">
                  <thead>
                    <tr>
                      <th>Atributo</th>
                      <th>Actual (${hero.nombre})</th>
                      <th>Con ${sub.nombre.split(' ')[0]}</th>
                      <th>Diferencia</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${comp.comparaciones.map((c) => `
                      <tr>
                        <td><strong>${c.stat}</strong></td>
                        <td class="cifra">${c.actual}</td>
                        <td class="cifra">${c.nuevo}</td>
                        <td>
                          <span class="badge ${c.delta > 0 ? 'badge-exito' : (c.delta < 0 ? 'badge-error' : 'badge-neutral')}">
                            ${c.delta > 0 ? `+${c.delta}` : (c.delta === 0 ? 'igual' : c.delta)}
                          </span>
                        </td>
                      </tr>
                    `).join('')}
                  </tbody>
                </table>
              </div>
            </div>

            <!-- Historial de Pujas -->
            <div class="panel-historial">
              <h3 class="titulo-mediano">Historial de pujas (${sub.historial.length})</h3>
              <ul class="lista-historial">
                ${sub.historial.map((p) => `
                  <li class="item-historial ${p.esTu ? 'historial-propio' : ''}">
                    <span class="historial-postor ${p.esTu ? 'postor-tu' : ''}">${p.esTu ? 'Tú (' + p.apodo + ')' : p.apodo}</span>
                    <span class="historial-tipo">${p.tipo}</span>
                    <span class="historial-cuando">${p.cuando}</span>
                    <span class="historial-monto cifra"><strong>${formatearCreditos(p.monto)} cr</strong></span>
                  </li>
                `).join('')}
              </ul>
            </div>
          </section>

          <!-- Columna Derecha: Panel de Acción y Pujas -->
          <section class="columna-acciones-puja">
            <div class="panel-puja ${urgente ? 'panel-urgente' : ''}">
              <div class="cabecera-panel-puja">
                <div>
                  <span class="etiqueta-sm">Oferta actual</span>
                  <div class="precio-actual cifra">${formatearCreditos(sub.oferta)} cr</div>
                  <span class="postor-actual ${sub.ganando ? 'texto-exito' : ''}">
                    ${sub.ganando ? 'Vas ganando tú' : `Mejor postor: ${sub.rival || 'otro jugador'}`}
                  </span>
                </div>
                <div class="reloj-cierre">
                  <span class="etiqueta-sm">Cierre en</span>
                  <div class="tiempo-cierre cifra ${urgente ? 'tiempo-urgente' : ''}" data-tiempo-subasta="${sub.id}">
                    ${formatearTiempo(sub.segundosRestantes)}
                  </div>
                </div>
              </div>

              <!-- Atajos de Puja Rápida -->
              <div class="seccion-bloque">
                <span class="etiqueta-sm">Atajos de puja en un toque (+incremento):</span>
                <div class="atajos-grid">
                  <button type="button" class="btn btn-contorno btn-atajo" data-monto="${minPuja}" ${cerrada ? 'disabled' : ''}>
                    Pujar ${formatearCreditos(minPuja)} cr (+50)
                  </button>
                  <button type="button" class="btn btn-contorno btn-atajo" data-monto="${sub.oferta + 100}" ${cerrada ? 'disabled' : ''}>
                    Pujar ${formatearCreditos(sub.oferta + 100)} cr (+100)
                  </button>
                  <button type="button" class="btn btn-contorno btn-atajo" data-monto="${sub.oferta + 200}" ${cerrada ? 'disabled' : ''}>
                    Pujar ${formatearCreditos(sub.oferta + 200)} cr (+200)
                  </button>
                </div>
              </div>

              <!-- Oferta Manual -->
              <div class="seccion-bloque">
                <label for="input-monto-puja" class="etiqueta-sm">Oferta manual (mínimo ${formatearCreditos(minPuja)} cr):</label>
                <div class="campo-con-boton">
                  <input type="number" id="input-monto-puja" class="input-estandar" min="${minPuja}" step="10" value="${this.montoPersonalizado || minPuja}" ${cerrada ? 'disabled' : ''}>
                  <button type="button" id="btn-pujar-manual" class="btn btn-primario" ${cerrada ? 'disabled' : ''}>
                    ${sub.esperaSegundos > 0 ? `Espera ${sub.esperaSegundos} s` : 'Pujar'}
                  </button>
                </div>
                <span class="texto-pista">Intervalo regulado de 5 s entre pujas del mismo jugador en esta subasta.</span>
              </div>

              <!-- Compra Inmediata -->
              <div class="seccion-bloque bloque-compra-inmediata">
                <div class="fila-compra">
                  <div>
                    <span class="etiqueta-sm">Compra directa</span>
                    <div class="precio-compra cifra">${formatearCreditos(sub.compraInmediata)} cr</div>
                  </div>
                  <button type="button" id="btn-solicitar-compra" class="btn btn-acento" ${cerrada || disponibleAqui < sub.compraInmediata ? 'disabled' : ''}>
                    ${disponibleAqui < sub.compraInmediata ? `Faltan ${formatearCreditos(sub.compraInmediata - disponibleAqui)} cr` : 'Comprarla ya'}
                  </button>
                </div>
              </div>

              <!-- Puja Automática -->
              <div class="seccion-bloque bloque-automatica">
                <h4 class="titulo-pequeno">Puja automática con tope máximo</h4>
                <p class="texto-pista">El sistema pujará automáticamente el incremento mínimo cuando otro jugador te supere, hasta tu límite.</p>
                ${sub.autoLimite > 0 ? `
                  <div class="auto-activa-aviso">
                    <span>Tope activo: <strong>${formatearCreditos(sub.autoLimite)} cr</strong></span>
                    <button type="button" id="btn-desactivar-auto" class="btn btn-peligro-sm">Desactivar</button>
                  </div>
                ` : `
                  <div class="campo-con-boton">
                    <input type="number" id="input-limite-auto" class="input-estandar" placeholder="Tope máx (ej. ${formatearCreditos(minPuja + 400)})" min="${minPuja}" step="50" ${cerrada ? 'disabled' : ''}>
                    <button type="button" id="btn-activar-auto" class="btn btn-contorno" ${cerrada ? 'disabled' : ''}>
                      Activar
                    </button>
                  </div>
                `}
              </div>
            </div>
          </section>
        </div>

        <!-- Modal de Confirmación de Compra Inmediata -->
        ${this.confirmandoCompra ? `
          <div class="modal-overlay" id="modal-compra-inmediata" role="dialog" aria-modal="true" aria-labelledby="modal-titulo">
            <div class="modal-tarjeta">
              <h2 id="modal-titulo" class="titulo-mediano">Confirmar compra inmediata</h2>
              <p>Estás a punto de comprar <strong>${sub.nombre}</strong> de forma directa por <strong>${formatearCreditos(sub.compraInmediata)} cr</strong>.</p>
              <p class="texto-pista">Esta acción cerrará la subasta inmediatamente y transferirá el objeto a tu cuenta.</p>
              <div class="modal-acciones">
                <button type="button" class="btn btn-contorno" id="btn-cancelar-compra">Cancelar</button>
                <button type="button" class="btn btn-acento" id="btn-confirmar-compra">Confirmar compra por ${formatearCreditos(sub.compraInmediata)} cr</button>
              </div>
            </div>
          </div>
        ` : ''}
      </div>
    `;
  }

  conectarEventos() {
    // Abrir detalle desde tarjeta
    this.contenedor.querySelectorAll('.btn-abrir').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-abrir');
        this.abrirDetalle(id);
      });
    });

    this.contenedor.querySelectorAll('.tarjeta-subasta').forEach((tarj) => {
      tarj.addEventListener('click', () => {
        const id = tarj.getAttribute('data-id');
        this.abrirDetalle(id);
      });
    });

    // Volver a la lista
    const btnVolver = this.contenedor.querySelector('#btn-volver');
    if (btnVolver) {
      btnVolver.addEventListener('click', () => this.volverALista());
    }

    // Selector de héroe
    this.contenedor.querySelectorAll('.btn-heroe-chip').forEach((btn) => {
      btn.addEventListener('click', () => {
        const heroeId = btn.getAttribute('data-heroe');
        this.seleccionarHeroe(heroeId);
      });
    });

    // Atajos de puja rápida
    this.contenedor.querySelectorAll('.btn-atajo').forEach((btn) => {
      btn.addEventListener('click', () => {
        const monto = Number(btn.getAttribute('data-monto'));
        this.pujar(monto);
      });
    });

    // Input de puja manual
    const inputPuja = this.contenedor.querySelector('#input-monto-puja');
    const btnPujarManual = this.contenedor.querySelector('#btn-pujar-manual');
    if (inputPuja && btnPujarManual) {
      inputPuja.addEventListener('input', (e) => {
        this.montoPersonalizado = Number(e.target.value);
      });
      btnPujarManual.addEventListener('click', () => {
        const monto = Number(inputPuja.value);
        this.pujar(monto);
      });
    }

    // Compra inmediata
    const btnSolicitarCompra = this.contenedor.querySelector('#btn-solicitar-compra');
    if (btnSolicitarCompra) {
      btnSolicitarCompra.addEventListener('click', () => this.solicitarCompraInmediata());
    }

    const btnCancelarCompra = this.contenedor.querySelector('#btn-cancelar-compra');
    if (btnCancelarCompra) {
      btnCancelarCompra.addEventListener('click', () => this.cancelarCompraInmediata());
    }

    const btnConfirmarCompra = this.contenedor.querySelector('#btn-confirmar-compra');
    if (btnConfirmarCompra) {
      btnConfirmarCompra.addEventListener('click', () => this.confirmarCompraInmediata());
    }

    // Puja automática
    const btnActivarAuto = this.contenedor.querySelector('#btn-activar-auto');
    const inputLimiteAuto = this.contenedor.querySelector('#input-limite-auto');
    if (btnActivarAuto && inputLimiteAuto) {
      btnActivarAuto.addEventListener('click', () => {
        const limite = Number(inputLimiteAuto.value);
        this.configurarAutoPuja(limite);
      });
    }

    const btnDesactivarAuto = this.contenedor.querySelector('#btn-desactivar-auto');
    if (btnDesactivarAuto) {
      btnDesactivarAuto.addEventListener('click', () => this.desactivarAutoPuja());
    }
  }
}

export function montarSubastas(contenedor, opciones = {}) {
  const ctrl = new ControladorSubastas(Object.assign({ contenedor }, opciones));
  ctrl.iniciar();
  return ctrl;
}
