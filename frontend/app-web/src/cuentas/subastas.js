/**
 * Subastas - Módulo interactivo de pujas y compras inmediatas (HU-SUB-004)
 *
 * Cumple con PILA_T_1 y .claude/rules/frontend-web.md:
 * - HTML5 + CSS3 + ES2022 nativo sin dependencias de frameworks
 * - 4 estados obligatorios (RNF-USA-003): carga, éxito, vacío, error
 * - Resolución de contraste WCAG 2.2 AA y fichas de tokens
 * - Límites de participación: 10 subastas activas, intervalo de 5 s
 * - Vistas integradas: Explorar, Mis subastas, Detalle y Cierre múltiple
 * - Sistema de avisos cruzados en vivo tipo toast (esquina inferior derecha)
 */

export const PALETA_RAREZA = {
  comun: { fondo: '#E7EAF0', texto: '#57627A', borde: '#9FABC9', icono: '🛡️' },
  rara: { fondo: '#DFEEF8', texto: '#095E8C', borde: '#095E8C', icono: '⚔️' },
  epica: { fondo: '#EDE5FA', texto: '#5B27B4', borde: '#5B27B4', icono: '🪓' },
  legendaria: { fondo: '#FBF0DE', texto: '#9A6800', borde: '#9A6800', icono: '🏹' }
};

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
    segundosRestantes: 8,
    ganando: true,
    superado: false,
    autoLimite: 2000,
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
    oferta: 880,
    compraInmediata: 1500,
    mediaMercado: 900,
    segundosRestantes: 96,
    ganando: false,
    superado: true,
    autoLimite: 1200,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'thar_vex',
    rivales: 2,
    aporte: { poder: 0, vida: 90, defensa: 18 },
    historial: [
      { apodo: 'thar_vex', monto: 880, tipo: 'Automática', cuando: 'hace 12 s', esTu: false },
      { apodo: 'andres_nv', monto: 800, tipo: 'Manual', cuando: 'hace 45 s', esTu: true }
    ]
  },
  {
    id: 'amuleto-brasa',
    nombre: 'Amuleto de Brasa Eterna',
    tipo: 'Accesorio · Cuello',
    descripcion: 'Gema ígnea que late con el calor de las forjas primigenias. Otorga gran afinidad arcana y salud.',
    rareza: 'legendaria',
    nivel: 25,
    vendedor: 'aerith_moon',
    oferta: 2400,
    compraInmediata: 4800,
    mediaMercado: 2600,
    segundosRestantes: 640,
    ganando: true,
    superado: false,
    autoLimite: 2000,
    esperaSegundos: 0,
    retenido: 2400,
    rival: 'valkyria_99',
    rivales: 4,
    aporte: { poder: 25, vida: 50, defensa: 5 },
    historial: [
      { apodo: 'andres_nv', monto: 2400, tipo: 'Automática', cuando: 'hace 1 min', esTu: true },
      { apodo: 'valkyria_99', monto: 2350, tipo: 'Manual', cuando: 'hace 2 min', esTu: false }
    ]
  },
  {
    id: 'daga-hueso',
    nombre: 'Daga de Hueso Pulido',
    tipo: 'Arma · Una mano',
    descripcion: 'Tallada en colmillo de behemoth. Ligera y letal en ataques furtivos.',
    rareza: 'comun',
    nivel: 12,
    vendedor: 'zephyr_mage',
    oferta: 310,
    compraInmediata: 700,
    mediaMercado: 400,
    segundosRestantes: 1820,
    ganando: false,
    superado: false,
    autoLimite: 0,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'novato_12',
    rivales: 1,
    aporte: { poder: 8, vida: 10, defensa: 0 },
    historial: [
      { apodo: 'novato_12', monto: 310, tipo: 'Manual', cuando: 'hace 5 min', esTu: false }
    ]
  },
  {
    id: 'yelmo-vigia',
    nombre: 'Yelmo del Vigía',
    tipo: 'Armadura · Cabeza',
    descripcion: 'Casco de acero templado con visor blindado. Mejora la resistencia y percepción táctica.',
    rareza: 'rara',
    nivel: 18,
    vendedor: 'barkeep_tom',
    oferta: 1100,
    compraInmediata: 2200,
    mediaMercado: 1400,
    segundosRestantes: 5400,
    ganando: false,
    superado: false,
    autoLimite: 1500,
    esperaSegundos: 0,
    retenido: 0,
    rival: 'ignis_red',
    rivales: 2,
    aporte: { poder: 6, vida: 60, defensa: 14 },
    historial: [
      { apodo: 'ignis_red', monto: 1100, tipo: 'Manual', cuando: 'hace 10 min', esTu: false }
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

export const EVENTOS_CIERRE_DEFAULT = [
  {
    id: 'hacha-obsidiana',
    nombre: 'Hacha de Obsidiana Fracturada',
    rareza: 'epica',
    tipoDesenlace: 'adjudicada',
    montoFinal: 1350,
    montoCobrado: 1350,
    montoDevuelto: 0,
    ganador: 'andres_nv',
    esGanador: true,
    motivo: '¡Adjudicada a tu inventario! La mejor oferta se mantuvo hasta el cierre.'
  },
  {
    id: 'grebas-centinela',
    nombre: 'Grebas del Centinela Caído',
    rareza: 'rara',
    tipoDesenlace: 'superada_rival',
    montoFinal: 1240,
    montoCobrado: 0,
    montoDevuelto: 880,
    ganador: 'thar_vex',
    esGanador: false,
    motivo: 'Ganó thar_vex con 1.240 cr · su automática respondió'
  },
  {
    id: 'amuleto-brasa',
    nombre: 'Amuleto de Brasa Eterna',
    rareza: 'legendaria',
    tipoDesenlace: 'superada_tope',
    montoFinal: 2450,
    topePropio: 2400,
    montoCobrado: 0,
    montoDevuelto: 2400,
    ganador: 'valkyria_99',
    esGanador: false,
    motivo: 'Tu automática paró en su tope de 2.400 cr · cerró en 2.450 cr'
  }
];

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

export function calcularSumaTopesAuto(subastas = []) {
  return subastas.reduce((acc, sub) => acc + (Number(sub.autoLimite) || 0), 0);
}

export function verificarSobreCompromiso(total, subastas = []) {
  const sumaTopes = calcularSumaTopesAuto(subastas);
  const sobreCompromiso = sumaTopes > total;
  const faltante = sobreCompromiso ? sumaTopes - total : 0;
  return { sobreCompromiso, sumaTopes, total, faltante };
}

export function calcularBalanceNetoCierre(eventosCierre = [], saldoTotal = CONFIG_REGLAS.creditosTotales, retenidoEnOtras = 720) {
  const cobrado = eventosCierre.reduce((acc, ev) => acc + (Number(ev.montoCobrado) || 0), 0);
  const devuelto = eventosCierre.reduce((acc, ev) => acc + (Number(ev.montoDevuelto) || 0), 0);
  const saldoLibre = Math.max(0, saldoTotal - retenidoEnOtras - cobrado);
  return {
    cobrado,
    devuelto,
    neto: devuelto - cobrado,
    saldoLibre
  };
}

export function generarConsejoTactico(eventoCierre, saldoLibre = 4130) {
  if (!eventoCierre) return null;
  const nombre = eventoCierre.nombre || 'el objeto';
  const tope = eventoCierre.topePropio || 0;
  const montoFinal = eventoCierre.montoFinal || 0;
  const diferencia = Math.max(0, montoFinal - tope);
  const margenRecomendado = diferencia > 0 ? diferencia * 2 : 100;
  const nombreCorto = nombre.includes('Amuleto') ? 'Amuleto' : nombre.split(' ')[0].replace(/^(el|la|los|las)\s+/i, '');

  return {
    titulo: `El ${nombreCorto} se te escapó por ${formatearCreditos(diferencia)} cr.`,
    cuerpo: `Tu tope estaba en ${formatearCreditos(tope)} y cerró en ${formatearCreditos(montoFinal)}. Con ${formatearCreditos(margenRecomendado)} cr más de margen era tuyo — y tenías ${formatearCreditos(saldoLibre)} libres.`,
    diferencia,
    margenRecomendado
  };
}

export function calcularEstadoTopesConcurrencia(subastas = [], config = CONFIG_REGLAS) {
  const maxSubastas = config.maxSubastasSimultaneas || 10;
  const maxPujas = config.maxPujasActivas || 50;

  const nSubastas = subastas.length;
  const nPujasGanando = subastas.filter((s) => s.ganando).length;

  const ratioSubastas = nSubastas / maxSubastas;
  const ratioPujas = nPujasGanando / maxPujas;

  const alertaSubastas = ratioSubastas >= 0.8;
  const alertaPujas = ratioPujas >= 0.8;

  return {
    subastas: {
      actual: nSubastas,
      max: maxSubastas,
      ratio: ratioSubastas,
      alerta: alertaSubastas,
      topeAlcanzado: nSubastas >= maxSubastas,
      pista: nSubastas >= maxSubastas
        ? 'Has llegado al tope: no puedes entrar en más.'
        : alertaSubastas
          ? `Aviso de tope (80%): te quedan ${maxSubastas - nSubastas} subastas.`
          : 'Margen de sobra.'
    },
    pujas: {
      actual: nPujasGanando,
      max: maxPujas,
      ratio: ratioPujas,
      alerta: alertaPujas,
      topeAlcanzado: nPujasGanando >= maxPujas,
      pista: nPujasGanando >= maxPujas
        ? 'Has llegado al tope de 50 pujas activas.'
        : alertaPujas
          ? `Aviso de tope (80%): te quedan ${maxPujas - nPujasGanando} pujas.`
          : 'Margen de sobra.'
    }
  };
}

// =========================================================================
// Controlador y renderizador interactivo DOM
// =========================================================================

export class ControladorSubastas {
  constructor({
    contenedor,
    subastas = SUBASTAS_INICIALES,
    heroes = HEROES_BASE,
    config = CONFIG_REGLAS,
    eventosCierre = EVENTOS_CIERRE_DEFAULT
  } = {}) {
    this.contenedor = contenedor;
    this.subastas = JSON.parse(JSON.stringify(subastas));
    this.heroes = JSON.parse(JSON.stringify(heroes));
    this.config = Object.assign({}, CONFIG_REGLAS, config);
    this.eventosCierre = JSON.parse(JSON.stringify(eventosCierre));
    this.heroeId = this.heroes[0]?.id || 'kaelen';
    this.vista = 'lista'; // 'lista' | 'explorar' | 'mis-subastas' | 'detalle' | 'cierre-multiple'
    this.origenVista = 'explorar';
    this.subastaActivaId = null;
    this.confirmandoCompra = false;
    this.resultadoCierre = null;
    this.montoPersonalizado = null;
    this.limiteAutoPersonalizado = null;
    this.estadoDatos = 'exito'; // 'carga' | 'exito' | 'vacio' | 'error'
    this.mensajeError = null;
    this.intervalId = null;
    this.avisoCruzado = null; // { id, nombre, oferta, rival, segundosRestantes }
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
      if (this.avisoCruzado) {
        const subAviso = this.subastas.find((s) => s.id === this.avisoCruzado.id);
        if (subAviso) {
          this.avisoCruzado.segundosRestantes = subAviso.segundosRestantes;
        } else if (this.avisoCruzado.segundosRestantes > 0) {
          this.avisoCruzado.segundosRestantes -= 1;
        }
        cambio = true;
      }
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

  cambiarVista(nuevaVista) {
    this.vista = nuevaVista;
    this.render();
  }

  abrirExplorar() {
    this.vista = 'explorar';
    this.render();
  }

  abrirMisSubastas() {
    this.vista = 'mis-subastas';
    this.render();
  }

  abrirCierreMultiple() {
    this.vista = 'cierre-multiple';
    this.render();
  }

  abrirDetalle(id, opciones = {}) {
    if (this.vista !== 'detalle') {
      this.origenVista = this.vista;
    }
    this.subastaActivaId = id || this.subastaActivaId || this.subastas[0]?.id;
    this.vista = 'detalle';
    this.confirmandoCompra = false;
    this.resultadoCierre = opciones.resultadoCierre || null;
    this.montoPersonalizado = null;
    this.limiteAutoPersonalizado = null;
    this.render();
  }

  volverALista() {
    const destino = this.origenVista === 'mis-subastas'
      ? 'mis-subastas'
      : (this.origenVista === 'cierre-multiple'
          ? 'cierre-multiple'
          : (this.origenVista === 'explorar' ? 'explorar' : 'lista'));
    this.vista = destino;
    this.subastaActivaId = null;
    this.confirmandoCompra = false;
    this.resultadoCierre = null;
    this.render();
  }

  seleccionarHeroe(id) {
    this.heroeId = id;
    this.render();
  }

  lanzarAvisoCruzado(subastaOId) {
    const sub = typeof subastaOId === 'string'
      ? this.subastas.find((s) => s.id === subastaOId)
      : subastaOId;
    if (!sub) return;
    this.avisoCruzado = {
      id: sub.id,
      nombre: sub.nombre,
      oferta: sub.oferta,
      rival: sub.rival || 'rival',
      segundosRestantes: sub.segundosRestantes
    };
    this.render();
  }

  descartarAvisoCruzado() {
    this.avisoCruzado = null;
    this.render();
  }

  irDesdeAvisoCruzado() {
    if (!this.avisoCruzado) return;
    const id = this.avisoCruzado.id;
    this.avisoCruzado = null;
    this.abrirDetalle(id);
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
          // Si el usuario no está viendo esta subasta en detalle, se dispara el aviso cruzado tipo toast
          if (this.vista !== 'detalle' || this.subastaActivaId !== sub.id) {
            this.avisoCruzado = {
              id: sub.id,
              nombre: sub.nombre,
              oferta: contraoferta,
              rival: sub.rival,
              segundosRestantes: sub.segundosRestantes
            };
          }
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
    if (!this.contenedor) return;
    const elementosTiempo = this.contenedor.querySelectorAll('[data-tiempo-subasta]');
    elementosTiempo.forEach((el) => {
      const id = el.getAttribute('data-tiempo-subasta');
      const sub = this.subastas.find((s) => s.id === id) || (this.avisoCruzado && this.avisoCruzado.id === id ? this.avisoCruzado : null);
      if (sub) {
        el.textContent = formatearTiempo(sub.segundosRestantes);
        if (sub.segundosRestantes <= 10 && sub.segundosRestantes > 0) {
          el.classList.add('tiempo-urgente', 'animacion-latido');
        } else {
          el.classList.remove('tiempo-urgente', 'animacion-latido');
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

    if (this.vista === 'mis-subastas') {
      contenidoHtml = this.generarHtmlMisSubastas({ total, retenido, libre, subastasGanando, superadas });
    } else if (this.vista === 'cierre-multiple') {
      contenidoHtml = this.generarHtmlCierreMultiple({ total, libre });
    } else if (this.vista === 'detalle') {
      contenidoHtml = this.generarHtmlDetalle({ total, retenido, libre, superadas });
    } else {
      // 'lista' | 'explorar'
      contenidoHtml = this.generarHtmlExplorar({ total, retenido, libre, subastasGanando, superadas });
    }

    if (this.avisoCruzado) {
      contenidoHtml += this.generarHtmlToastCruzado();
    }

    this.contenedor.innerHTML = contenidoHtml;
    this.conectarEventos();
  }

  generarHtmlPestanas({ superadas = 0 } = {}) {
    const esExplorar = this.vista === 'lista' || this.vista === 'explorar';
    const esMisSubastas = this.vista === 'mis-subastas';
    const esCierre = this.vista === 'cierre-multiple';
    const esDetalle = this.vista === 'detalle';
    const subActiva = this.getSubastaActiva();

    return `
      <nav class="subastas-tabs" role="tablist" aria-label="Secciones de subastas">
        <button type="button" role="tab" class="tab-btn ${esExplorar ? 'tab-btn--activo' : ''}" data-tab="explorar" aria-selected="${esExplorar}">
          Explorar subastas
        </button>
        <button type="button" role="tab" class="tab-btn ${esMisSubastas ? 'tab-btn--activo' : ''}" data-tab="mis-subastas" aria-selected="${esMisSubastas}">
          Mis subastas activas
          ${superadas > 0 ? `<span class="badge-tab-aviso" title="Te superaron en ${superadas}">${superadas}</span>` : `<span class="badge-tab-neutral">${this.subastas.length}</span>`}
        </button>
        <button type="button" role="tab" class="tab-btn ${esCierre ? 'tab-btn--activo' : ''}" data-tab="cierre-multiple" aria-selected="${esCierre}">
          Cierre múltiple
          <span class="badge-tab-neutral">${this.eventosCierre.length}</span>
        </button>
        <button type="button" role="tab" class="tab-btn ${esDetalle ? 'tab-btn--activo' : ''}" data-tab="detalle" aria-selected="${esDetalle}">
          ${esDetalle && subActiva ? `Detalle: ${subActiva.nombre.split(' ')[0]}` : 'Detalle de subasta'}
        </button>
      </nav>
    `;
  }

  generarHtmlExplorar({ total, retenido, libre, subastasGanando, superadas }) {
    const pctRetenido = total > 0 ? ((retenido / total) * 100).toFixed(1) : 0;
    const pctLibre = total > 0 ? ((libre / total) * 100).toFixed(1) : 100;

    return `
      <div class="subastas-app">
        ${this.generarHtmlPestanas({ superadas })}

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
    let badgeEstado = '<span class="badge badge-neutral">Sin pujar</span>';
    let claseBorde = '';
    let textoBoton = 'Ver subasta';
    let claseBoton = 'btn-contorno';

    if (sub.ganando) {
      badgeEstado = '<span class="badge badge-exito">Vas ganando</span>';
      claseBorde = 'tarjeta-ganando borde-ganando';
    } else if (sub.superado) {
      badgeEstado = '<span class="badge badge-error">Te superaron</span>';
      claseBorde = 'tarjeta-superada borde-superada';
    } else {
      claseBorde = 'borde-sin-puja';
    }

    if (sub.superado) {
      textoBoton = 'Recuperarla';
      claseBoton = 'btn-primario btn-recuperar';
    } else if (urgente) {
      textoBoton = 'Ir ahora';
      claseBoton = 'btn-primario btn-ir-ahora animacion-latido';
    } else {
      textoBoton = 'Ver subasta';
      claseBoton = 'btn-contorno btn-ver-subasta';
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
            <span class="tiempo-cifra cifra ${urgente ? 'tiempo-urgente animacion-latido' : ''}" data-tiempo-subasta="${sub.id}">
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

  generarHtmlMisSubastas({ total, retenido, libre, superadas }) {
    const sobreCompromiso = verificarSobreCompromiso(total, this.subastas);
    const estadoTopes = calcularEstadoTopesConcurrencia(this.subastas, this.config);
    const porUrgencia = [...this.subastas].sort((a, b) => a.segundosRestantes - b.segundosRestantes);

    // Segmentos para la barra interactiva
    const conRetencion = this.subastas.filter((s) => (s.retenido || 0) > 0);
    const PALETA_TRAMOS = ['#9A6800', '#C89A1E', '#B37D14', '#D48806', '#E6A23C', '#8A4A00'];
    const tramos = conRetencion.map((sub, i) => {
      const ancho = total > 0 ? ((sub.retenido / total) * 100).toFixed(1) : '0';
      const color = PALETA_TRAMOS[i % PALETA_TRAMOS.length];
      return {
        id: sub.id,
        nombre: sub.nombre,
        ancho: `${ancho}%`,
        color,
        titulo: `${sub.nombre}: ${formatearCreditos(sub.retenido)} cr`
      };
    });
    const libreAncho = total > 0 ? ((libre / total) * 100).toFixed(1) : '100';
    tramos.push({
      id: 'libre',
      nombre: 'Libre para pujar',
      ancho: `${libreAncho}%`,
      color: '#0B6B31',
      titulo: `Libre: ${formatearCreditos(libre)} cr`
    });

    const leyenda = conRetencion.map((sub, i) => ({
      color: PALETA_TRAMOS[i % PALETA_TRAMOS.length],
      texto: `${sub.nombre.split(' ')[0]} · ${formatearCreditos(sub.retenido)} cr`
    })).concat([{
      color: '#0B6B31',
      texto: `Libre · ${formatearCreditos(libre)} cr`
    }]);

    return `
      <div class="subastas-app vista-mis-subastas">
        ${this.generarHtmlPestanas({ superadas })}

        <div class="mis-subastas-cabecera">
          <h1 class="titulo-grande">Mis subastas</h1>
          <p class="texto-pista">Dónde estás participando ahora mismo y cuánto tienes comprometido en cada subasta.</p>
        </div>

        <!-- Panel de Créditos con Barra Segmentada -->
        <section class="panel-creditos-segmentada" aria-label="Desglose financiero de créditos">
          <h2 class="titulo-mediano">Tus créditos</h2>

          <div class="creditos-tarjetas-grid">
            <div class="tarjeta-credito tarjeta-credito-total">
              <div class="tarjeta-credito-etiqueta">Tienes en total</div>
              <div class="tarjeta-credito-valor cifra">${formatearCreditos(total)} cr</div>
            </div>
            <div class="tarjeta-credito tarjeta-credito-retenido">
              <div class="tarjeta-credito-etiqueta">Retenido en subastas</div>
              <div class="tarjeta-credito-valor tarjeta-credito-valor--retenido cifra">${formatearCreditos(retenido)} cr</div>
            </div>
            <div class="tarjeta-credito ${libre < 500 ? 'tarjeta-credito-libre--baja' : 'tarjeta-credito-libre'}">
              <div class="tarjeta-credito-etiqueta">Libre para pujar</div>
              <div class="tarjeta-credito-valor ${libre < 500 ? 'tarjeta-credito-valor--libre-baja' : 'tarjeta-credito-valor--libre'} cifra">${formatearCreditos(libre)} cr</div>
            </div>
          </div>

          <!-- Barra segmentada interactiva -->
          <div class="barra-segmentada-tramos" role="progressbar" aria-label="Distribución de créditos en subastas">
            ${tramos.map((t) => `
              <div class="tramo-subasta" style="width: ${t.ancho}; background: ${t.color};" title="${t.titulo}"></div>
            `).join('')}
          </div>

          <!-- Leyenda de tramos -->
          <div class="leyenda-tramos">
            ${leyenda.map((l) => `
              <span class="item-leyenda">
                <span class="leyenda-punto" style="background: ${l.color};"></span>
                <span>${l.texto}</span>
              </span>
            `).join('')}
          </div>

          <!-- Alerta de Sobre-compromiso -->
          ${sobreCompromiso.sobreCompromiso ? `
            <div class="alerta-sobrecompromiso" role="alert">
              <div class="sobrecompromiso-icono">⚠️</div>
              <div>
                <div class="sobrecompromiso-titulo">Tus automáticas prometen más de lo que tienes</div>
                <div class="sobrecompromiso-texto">
                  Si todas llegaran a su tope harían falta <strong class="cifra">${formatearCreditos(sobreCompromiso.sumaTopes)} cr</strong>,
                  y solo tienes <strong class="cifra">${formatearCreditos(total)} cr</strong>. Las últimas en responder fallarán. Baja algún tope o desactiva una.
                </div>
              </div>
            </div>
          ` : ''}
        </section>

        <!-- Medidores Visuales de Topes de Concurrencia -->
        <section class="grid-topes-concurrencia" aria-label="Topes reglamentarios de concurrencia">
          <!-- Tope 1: Subastas Simultáneas -->
          <div class="tarjeta-tope ${estadoTopes.subastas.topeAlcanzado ? 'tarjeta-tope--critico' : (estadoTopes.subastas.alerta ? 'tarjeta-tope--alerta' : '')}">
            <div class="tope-cabecera">
              <span class="tope-nombre">Subastas en las que participas</span>
              <span class="tope-cifra cifra" style="color: ${estadoTopes.subastas.topeAlcanzado ? 'var(--error)' : (estadoTopes.subastas.alerta ? 'var(--advertencia)' : 'var(--exito)')}">
                ${estadoTopes.subastas.actual} de ${estadoTopes.subastas.max}
              </span>
            </div>
            <div class="tope-barra-fondo">
              <div class="tope-barra-progreso" style="width: ${Math.min(100, estadoTopes.subastas.ratio * 100)}%; background: ${estadoTopes.subastas.topeAlcanzado ? 'var(--error)' : (estadoTopes.subastas.alerta ? 'var(--advertencia)' : 'var(--exito)')};"></div>
            </div>
            <div class="tope-alerta-texto" style="color: ${estadoTopes.subastas.topeAlcanzado ? 'var(--error)' : (estadoTopes.subastas.alerta ? 'var(--advertencia)' : 'var(--texto-3)')}">
              ${estadoTopes.subastas.pista}
            </div>
          </div>

          <!-- Tope 2: Pujas Activas Ganando -->
          <div class="tarjeta-tope ${estadoTopes.pujas.topeAlcanzado ? 'tarjeta-tope--critico' : (estadoTopes.pujas.alerta ? 'tarjeta-tope--alerta' : '')}">
            <div class="tope-cabecera">
              <span class="tope-nombre">Pujas tuyas que van ganando</span>
              <span class="tope-cifra cifra" style="color: ${estadoTopes.pujas.topeAlcanzado ? 'var(--error)' : (estadoTopes.pujas.alerta ? 'var(--advertencia)' : 'var(--exito)')}">
                ${estadoTopes.pujas.actual} de ${estadoTopes.pujas.max}
              </span>
            </div>
            <div class="tope-barra-fondo">
              <div class="tope-barra-progreso" style="width: ${Math.min(100, estadoTopes.pujas.ratio * 100)}%; background: ${estadoTopes.pujas.topeAlcanzado ? 'var(--error)' : (estadoTopes.pujas.alerta ? 'var(--advertencia)' : 'var(--exito)')};"></div>
            </div>
            <div class="tope-alerta-texto" style="color: ${estadoTopes.pujas.topeAlcanzado ? 'var(--error)' : (estadoTopes.pujas.alerta ? 'var(--advertencia)' : 'var(--texto-3)')}">
              ${estadoTopes.pujas.pista}
            </div>
          </div>
        </section>

        <!-- Listado ordenado por urgencia de vencimiento -->
        <section class="seccion-mis-subastas" aria-label="Listado de mis subastas activas">
          <div class="encabezado-mis-subastas">
            <h2 class="titulo-seccion">En curso</h2>
            <span class="contador-mis-subastas">${porUrgencia.length} subastas</span>
            <div style="flex-grow: 1;"></div>
            <span class="texto-pista">Ordenadas por lo que se acaba antes</span>
          </div>

          <div class="lista-mis-subastas">
            ${porUrgencia.map((sub) => this.generarFilaMiSubasta(sub)).join('')}
          </div>
        </section>
      </div>
    `;
  }

  generarFilaMiSubasta(sub) {
    const urgente = sub.segundosRestantes <= 10 && sub.segundosRestantes > 0;
    const rarezaInfo = PALETA_RAREZA[sub.rareza] || PALETA_RAREZA.comun;

    let claseBorde = 'borde-sin-puja';
    let badgeEstado = '<span class="badge badge-neutral">Sin pujar</span>';
    let textoBoton = 'Ver subasta';
    let claseBoton = 'btn-contorno btn-ver-subasta';

    if (sub.ganando) {
      claseBorde = 'borde-ganando';
      badgeEstado = '<span class="badge badge-exito">Vas ganando</span>';
    } else if (sub.superado) {
      claseBorde = 'borde-superada';
      badgeEstado = '<span class="badge badge-error">Te superaron</span>';
    }

    if (sub.superado) {
      textoBoton = 'Recuperarla';
      claseBoton = 'btn-primario btn-recuperar';
    } else if (urgente) {
      textoBoton = 'Ir ahora';
      claseBoton = 'btn-primario btn-ir-ahora animacion-latido';
    } else {
      textoBoton = 'Ver subasta';
      claseBoton = 'btn-contorno btn-ver-subasta';
    }

    return `
      <article class="fila-mi-subasta ${claseBorde} ${urgente ? 'urgente' : ''}" data-id="${sub.id}">
        <div class="fila-icono-rareza" style="background: ${rarezaInfo.fondo}; border: 1px solid ${rarezaInfo.borde};">
          ${rarezaInfo.icono}
        </div>

        <div class="fila-info-principal">
          <h3 class="fila-nombre">${sub.nombre}</h3>
          <div class="fila-badges">
            <span class="badge badge-${sub.rareza}">${sub.rareza.toUpperCase()}</span>
            ${badgeEstado}
            ${sub.autoLimite > 0 ? `
              <span class="chip-automatica-tope" title="Puja automática configurada">
                ⚡ hasta ${formatearCreditos(sub.autoLimite)} cr
              </span>
            ` : ''}
          </div>
        </div>

        <div class="fila-oferta">
          <div class="etiqueta-sm">Oferta vigente</div>
          <div class="fila-oferta-monto cifra" style="color: var(--advertencia);">${formatearCreditos(sub.oferta)} cr</div>
          <div class="postor-texto ${sub.ganando ? 'texto-exito' : ''}">
            ${sub.ganando ? 'Tu puja' : `de ${sub.rival || 'rival'}`}
          </div>
        </div>

        <div class="fila-retenido">
          <div class="etiqueta-sm">Tú tienes retenido</div>
          <div class="fila-retenido-monto cifra" style="color: ${sub.retenido > 0 ? 'var(--advertencia)' : 'var(--texto-3)'};">
            ${sub.retenido > 0 ? `${formatearCreditos(sub.retenido)} cr` : '—'}
          </div>
        </div>

        <div class="fila-acciones-tiempo">
          <div class="reloj-fila ${urgente ? 'animacion-latido' : ''}" data-tiempo-subasta="${sub.id}">
            ⏱️ <span class="cifra">${formatearTiempo(sub.segundosRestantes)}</span>
          </div>
          <button type="button" class="btn ${claseBoton}" data-abrir="${sub.id}">
            ${textoBoton}
          </button>
        </div>
      </article>
    `;
  }

  generarHtmlCierreMultiple({ total }) {
    const eventos = this.eventosCierre;
    const balance = calcularBalanceNetoCierre(eventos, total);
    const ganadas = eventos.filter((e) => e.esGanador).length;
    const superadas = eventos.filter((e) => !e.esGanador).length;

    const eventoConTope = eventos.find((e) => e.tipoDesenlace === 'superada_tope') || eventos.find((e) => !e.esGanador);
    const consejo = generarConsejoTactico(eventoConTope, balance.saldoLibre);

    return `
      <div class="subastas-app vista-cierre-multiple">
        ${this.generarHtmlPestanas({ superadas: 0 })}

        <div class="panel-cierre-multiple">
          <div class="cierre-titular-bloque">
            <h1 class="cierre-titular">${eventos.length} CERRARON</h1>
            <p class="cierre-subtitulo">Ganaste ${ganadas}, te superaron en ${superadas}. Esto es lo que cambió en tu saldo.</p>
          </div>

          <!-- Resumen de Balance Neto -->
          <div class="cierre-neto-grid" aria-label="Balance financiero neto del cierre">
            <div class="caja-neto">
              <span class="cifra-neto cifra-neto--cobrado cifra">−${formatearCreditos(balance.cobrado)}</span>
              <span class="etiqueta-neto">cobrado</span>
            </div>
            <div class="caja-neto">
              <span class="cifra-neto cifra-neto--devuelto cifra">+${formatearCreditos(balance.devuelto)}</span>
              <span class="etiqueta-neto">devuelto</span>
            </div>
            <div class="caja-neto caja-neto--libre">
              <span class="cifra-neto cifra-neto--libre cifra">${formatearCreditos(balance.saldoLibre)}</span>
              <span class="etiqueta-neto">libre ahora</span>
            </div>
          </div>

          <!-- Filas de desenlaces -->
          <div class="lista-eventos-cierre">
            ${eventos.map((ev) => {
              const rarezaInfo = PALETA_RAREZA[ev.rareza] || PALETA_RAREZA.comun;
              let claseEvento = 'evento--superada-rival';
              let montoHtml = `<div class="evento-cifra cifra" style="color: var(--exito);">+${formatearCreditos(ev.montoDevuelto)}</div><div class="etiqueta-sm">devuelto</div>`;
              let btnAccion = `<button type="button" class="btn btn-contorno btn-sm btn-buscar-parecidas" data-id="${ev.id}">Parecidas</button>`;

              if (ev.tipoDesenlace === 'adjudicada') {
                claseEvento = 'evento--adjudicada';
                montoHtml = `<div class="evento-cifra cifra" style="color: var(--advertencia);">−${formatearCreditos(ev.montoCobrado)}</div><div class="etiqueta-sm">cobrado</div>`;
                btnAccion = `<button type="button" class="btn btn-acento btn-sm btn-ver-adjudicada" data-id="${ev.id}">Ver</button>`;
              } else if (ev.tipoDesenlace === 'superada_tope') {
                claseEvento = 'evento--superada-tope';
              }

              return `
                <div class="fila-evento-cierre ${claseEvento}">
                  <div class="evento-icono" style="background: ${rarezaInfo.fondo}; border: 1px solid ${rarezaInfo.borde};">
                    ${rarezaInfo.icono}
                  </div>
                  <div class="evento-info">
                    <h3 class="evento-titulo">${ev.nombre}</h3>
                    <div class="evento-motivo">${ev.motivo}</div>
                  </div>
                  <div class="evento-monto">
                    ${montoHtml}
                  </div>
                  <div class="evento-accion">
                    ${btnAccion}
                  </div>
                </div>
              `;
            }).join('')}
          </div>

          <!-- Caja de Consejo Táctico Personalizado -->
          ${consejo ? `
            <div class="caja-consejo-tactico" role="region" aria-label="Consejo táctico">
              <div class="consejo-icono">⚡</div>
              <div class="consejo-contenido">
                <div class="consejo-titulo">${consejo.titulo}</div>
                <div>${consejo.cuerpo}</div>
              </div>
            </div>
          ` : ''}

          <!-- Botones de Navegación del Cierre -->
          <div class="cierre-acciones-pie">
            <button type="button" class="btn btn-primario" id="btn-cierre-a-mis-subastas">Ver mis subastas</button>
            <button type="button" class="btn btn-contorno" id="btn-cierre-a-explorar">Al listado</button>
          </div>
        </div>
      </div>
    `;
  }

  generarHtmlDetalle({ libre, superadas = 0 }) {
    const sub = this.getSubastaActiva();
    const hero = this.getHeroeActivo();
    const comp = calcularComparacionHeroe(hero, sub);
    const minPuja = calcularMinimoPuja(sub.oferta, this.config.incrementoMinimo);
    const disponibleAqui = libre + (sub.retenido || 0);
    const cerrada = sub.segundosRestantes <= 0 || this.resultadoCierre !== null;
    const urgente = sub.segundosRestantes <= 10 && !cerrada;

    const subastasOtras = this.subastas.filter((s) => s.id !== sub.id);
    const otrasConRetenido = subastasOtras.filter((s) => (s.retenido || 0) > 0).length;
    const retenidoEnOtras = subastasOtras.reduce((acc, s) => acc + (s.retenido || 0), 0);

    const textoVolver = this.origenVista === 'mis-subastas'
      ? '← Volver a mis subastas'
      : (this.origenVista === 'cierre-multiple' ? '← Volver a cierre múltiple' : '← Volver al listado de subastas');

    return `
      <div class="subastas-app vista-detalle">
        ${this.generarHtmlPestanas({ superadas })}

        <!-- Barra de navegación contextual -->
        <div class="barra-volver">
          <button type="button" class="btn btn-texto" id="btn-volver">
            ${textoVolver}
          </button>
          <span class="separador-pipe">|</span>
          <span class="saldo-contextual">Disponible libre: <strong>${formatearCreditos(libre)} cr</strong> (en esta subasta: <strong>${formatearCreditos(disponibleAqui)} cr</strong>)</span>
        </div>

        <!-- Alerta de resultado final si cerró o se adjudicó -->
        ${(this.resultadoCierre === 'comprada' || this.resultadoCierre === 'adjudicada') ? `
          <div class="alerta alerta-exito-cierre" role="alert" style="margin-bottom: 20px; background: #DFF1E6; border: 1px solid #0B6B31; border-radius: 8px; padding: 20px; text-align: center;">
            <h2 class="titulo-grande" style="color: #0B6B31; margin-bottom: 6px;">¡ES TUYA!</h2>
            <p style="font-size: 16px; color: var(--texto-1); margin-bottom: 12px;">
              ${this.resultadoCierre === 'comprada' ? '¡Has comprado este objeto de inmediato!' : 'La subasta cerró exitosamente y el objeto ha sido adjudicado a tu inventario.'}
            </p>
            <div style="display: flex; justify-content: center; gap: 16px; margin-bottom: 16px;">
              <div style="background: #FFFFFF; padding: 10px 16px; border-radius: 6px; border: 1px solid #9FABC9;">
                <span style="font-size: 12px; color: var(--texto-2); display: block;">Monto pagado:</span>
                <strong class="cifra" style="font-size: 20px; color: #0B6B31;">${formatearCreditos(this.resultadoCierre === 'comprada' ? sub.compraInmediata : sub.oferta)} cr</strong>
              </div>
              <div style="background: #FFFFFF; padding: 10px 16px; border-radius: 6px; border: 1px solid #9FABC9;">
                <span style="font-size: 12px; color: var(--texto-2); display: block;">Saldo libre resultante:</span>
                <strong class="cifra" style="font-size: 20px; color: #0B6B31;">${formatearCreditos(libre)} cr</strong>
              </div>
            </div>
            <div style="display: flex; justify-content: center; gap: 12px;">
              <button type="button" class="btn btn-primario" id="btn-resultado-mis-subastas">Ver mis subastas</button>
              <button type="button" class="btn btn-contorno" id="btn-resultado-explorar">Al listado</button>
            </div>
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
                  <div class="tiempo-cierre cifra ${urgente ? 'tiempo-urgente animacion-latido' : ''}" data-tiempo-subasta="${sub.id}">
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

            <!-- Desglose de Retención Contextual (Main.dc.html) -->
            <div class="panel-resumen-sidebar" style="background: var(--superficie); border: 1px solid var(--borde); border-radius: 8px; padding: 16px 20px; margin-top: 16px;">
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;">
                <span style="font-size: 13px; color: var(--texto-2);">Libre para pujar</span>
                <span class="cifra" style="font-size: 16px; font-weight: 600; color: var(--exito);">${formatearCreditos(libre)} cr</span>
              </div>
              <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;">
                <span style="font-size: 13px; color: var(--texto-2);">Retenido aquí</span>
                <span class="cifra" style="font-size: 16px; font-weight: 600; color: ${sub.retenido > 0 ? 'var(--advertencia)' : 'var(--texto-3)'};">${sub.retenido > 0 ? `${formatearCreditos(sub.retenido)} cr` : '—'}</span>
              </div>
              <div style="display: flex; align-items: center; justify-content: space-between;">
                <span style="font-size: 13px; color: var(--texto-2);">Retenido en otras (${otrasConRetenido})</span>
                <span class="cifra" style="font-size: 16px; font-weight: 600; color: #8A4A00;">${formatearCreditos(retenidoEnOtras)} cr</span>
              </div>
              <div style="height: 1px; background: var(--fondo); margin: 12px 0;"></div>
              <button type="button" class="btn btn-contorno" id="btn-ver-todas-mis-subastas" style="width: 100%; min-height: 32px; font-size: 13px; font-weight: 600;">
                Ver todas mis subastas
              </button>
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

  generarHtmlToastCruzado() {
    if (!this.avisoCruzado) return '';
    return `
      <aside class="toast-cruzado-flotante" role="alert" aria-live="polite">
        <div class="toast-cruzado-cabecera">
          <div class="toast-titulo-contenedor">
            <span class="toast-icono">⚠️</span>
            <strong class="toast-titulo">¡Te superaron en otra subasta!</strong>
          </div>
          <button type="button" class="btn-cerrar-toast" aria-label="Cerrar aviso cruzado">×</button>
        </div>
        <p class="toast-mensaje">
          <strong>${this.avisoCruzado.nombre}</strong> · ahora <strong class="cifra">${formatearCreditos(this.avisoCruzado.oferta)} cr</strong> ·
          quedan <span class="cifra" data-tiempo-subasta="${this.avisoCruzado.id}">${formatearTiempo(this.avisoCruzado.segundosRestantes)}</span>
        </p>
        <div class="toast-acciones">
          <button type="button" class="btn btn-primario btn-sm btn-toast-ir" data-id="${this.avisoCruzado.id}">Ir</button>
          <button type="button" class="btn btn-contorno btn-sm btn-toast-descartar">Descartar</button>
        </div>
      </aside>
    `;
  }

  conectarEventos() {
    // Pestañas de navegación
    this.contenedor.querySelectorAll('.tab-btn[data-tab]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const tab = btn.getAttribute('data-tab');
        if (tab === 'explorar') this.abrirExplorar();
        else if (tab === 'mis-subastas') this.abrirMisSubastas();
        else if (tab === 'detalle') this.abrirDetalle(this.subastaActivaId || this.subastas[0]?.id);
        else if (tab === 'cierre-multiple') this.abrirCierreMultiple();
      });
    });

    // Botones de resultado de compra o adjudicación
    this.contenedor.querySelector('#btn-resultado-mis-subastas')?.addEventListener('click', () => {
      this.abrirMisSubastas();
    });
    this.contenedor.querySelector('#btn-resultado-explorar')?.addEventListener('click', () => {
      this.abrirExplorar();
    });

    // Botón de ver todas mis subastas desde el sidebar de detalle
    this.contenedor.querySelector('#btn-ver-todas-mis-subastas')?.addEventListener('click', () => {
      this.abrirMisSubastas();
    });

    // Abrir detalle desde tarjeta en Explorar o Mis subastas
    this.contenedor.querySelectorAll('.btn-abrir, .btn-ver-subasta, .btn-recuperar, .btn-ir-ahora').forEach((btn) => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-abrir') || btn.closest('[data-id]')?.getAttribute('data-id');
        if (id) this.abrirDetalle(id);
      });
    });

    this.contenedor.querySelectorAll('.tarjeta-subasta, .fila-mi-subasta').forEach((tarj) => {
      tarj.addEventListener('click', () => {
        const id = tarj.getAttribute('data-id');
        if (id) this.abrirDetalle(id);
      });
    });

    // Cierre múltiple
    this.contenedor.querySelector('#btn-cierre-a-mis-subastas')?.addEventListener('click', () => {
      this.abrirMisSubastas();
    });

    this.contenedor.querySelector('#btn-cierre-a-explorar')?.addEventListener('click', () => {
      this.abrirExplorar();
    });

    this.contenedor.querySelectorAll('.btn-ver-adjudicada').forEach((btn) => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        if (id) this.abrirDetalle(id, { resultadoCierre: 'adjudicada' });
      });
    });

    this.contenedor.querySelectorAll('.btn-buscar-parecidas').forEach((btn) => {
      btn.addEventListener('click', () => {
        this.abrirExplorar();
      });
    });

    // Toast cruzado
    this.contenedor.querySelector('.btn-toast-ir')?.addEventListener('click', () => {
      this.irDesdeAvisoCruzado();
    });

    this.contenedor.querySelector('.btn-toast-descartar')?.addEventListener('click', () => {
      this.descartarAvisoCruzado();
    });

    this.contenedor.querySelector('.btn-cerrar-toast')?.addEventListener('click', () => {
      this.descartarAvisoCruzado();
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
