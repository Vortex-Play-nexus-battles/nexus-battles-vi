// Migra historias de usuario desde Jira hacia Issues de GitHub y las da de alta
// en el Project v2 de la organizacion con todos sus campos.
//
//   node scripts/migrar-backlog.mjs
//
// Lee Jira en vivo: no hay copia de los datos en el repositorio, asi que lo que
// se migra es siempre lo que Jira dice en ese momento.
//
// Variables de entorno
//   JIRA_SITE       proyectonexusbattles.atlassian.net
//   JIRA_EMAIL      correo de la cuenta de Atlassian
//   JIRA_TOKEN      token de API de Atlassian
//   JQL             consulta de Jira
//   GH_TOKEN        token con escritura de Issues del repo y de proyectos de la organizacion
//   REPO            owner/repo
//   ORG             login de la organizacion
//   PROJECT_NUMBER  numero del Project v2
//   SPRINT_PROJECT  titulo de la iteracion del Project, por ejemplo "Sprint 1"
//   DRY_RUN         "true" para no escribir nada
//
// Idempotente: omite toda historia cuya clave de Jira ya aparezca en el cuerpo
// de un Issue del repositorio. Relanzarlo no duplica.

const {
  JIRA_SITE, JIRA_EMAIL, JIRA_TOKEN, GH_TOKEN, ORG,
  JQL = 'project = SCRUM AND issuetype = Historia AND sprint IS NOT EMPTY ORDER BY key ASC',
  SPRINT_PROJECT = 'Sprint 1',
} = process.env;
const [OWNER, REPO_NAME] = (process.env.REPO || '').split('/');
const PROJECT_NUMBER = Number(process.env.PROJECT_NUMBER || 1);
const DRY_RUN = String(process.env.DRY_RUN).toLowerCase() === 'true';

for (const [k, v] of Object.entries({ JIRA_SITE, JIRA_EMAIL, JIRA_TOKEN, GH_TOKEN, ORG })) {
  if (!v) { console.error(`Falta la variable ${k}`); process.exit(1); }
}
if (!OWNER || !REPO_NAME) { console.error('Falta REPO'); process.exit(1); }

// --------------------------------------------------------------- diccionarios
// Prefijo de la historia -> opcion del campo "Modulo" del Project.
const MODULO = {
  'HU-HER': 'Héroes y personajes',       'HU-INV': 'Jugador e inventario',
  'HU-PRD': 'Administración de productos','HU-AUD': 'Auditoría',
  'HU-AUT': 'Seguridad y acceso',        'HU-CAR': 'Carro de compras',
  'HU-RBAC': 'Roles y permisos',         'HU-USR': 'Usuarios y comentarios',
  'HU-ADM': 'Administración general',    'HU-COM': 'Comentarios',
  'HU-COR': 'Correo electrónico',        'HU-JUE': 'Juego en línea',
  'HU-NOT': 'Notificaciones',            'HU-SAL': 'Juego en línea',
  'HU-MIS': 'Misiones',                  'HU-TOR': 'Torneos',
  'HU-PAG': 'Pagos',                     'HU-SUB': 'Subastas',
  'HU-CHA': 'Chatbot',                   'HU-PRI': 'Privacidad',
  'HU-MET': 'Métricas y analítica',
};
// Etiqueta de grupo en Jira -> equipo y carpeta del monorepo.
const EQUIPO  = { 'Grupo-2': 'Thomas', 'Grupo-4': 'Santiago', 'Grupo-6': 'Simón' };
const CARPETA = { 'Grupo-2': 'services/contenido/', 'Grupo-4': 'services/cuentas/', 'Grupo-6': 'services/plataforma/' };
const PRIORIDAD = { Highest: 'Alta', High: 'Alta', Medium: 'Media', Low: 'Baja', Lowest: 'Baja' };
const ESTADO = { 'Por hacer': 'Backlog', 'En curso': 'In progress', 'En revisión': 'In review', 'Finalizado': 'Done' };
const ETIQUETA_COLOR = {
  'historia-usuario': '1D76DB', 'grupo-2': '5319E7', 'grupo-4': '0E8A16',
  'grupo-6': 'B60205', 'migrado-jira': 'C5DEF5',
};

const dormir = (ms) => new Promise(r => setTimeout(r, ms));
const norm = (s) => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '')
  .toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

// --------------------------------------------------- Atlassian Document Format
// La API v3 devuelve la descripcion como arbol ADF. Se aplana a texto sin
// reinterpretar nada: solo se concatenan los nodos de texto en orden.
function adf(nodo, nivel = 0) {
  if (!nodo) return '';
  if (typeof nodo === 'string') return nodo;
  if (Array.isArray(nodo)) return nodo.map(n => adf(n, nivel)).join('');
  switch (nodo.type) {
    case 'text':       return nodo.text || '';
    case 'hardBreak':  return '\n';
    case 'paragraph':  return adf(nodo.content, nivel) + '\n\n';
    case 'heading':    return '**' + adf(nodo.content, nivel).trim() + '**\n\n';
    case 'bulletList':
    case 'orderedList':return adf(nodo.content, nivel + 1) + (nivel === 0 ? '\n' : '');
    case 'listItem':   return '  '.repeat(Math.max(0, nivel - 1)) + '- ' + adf(nodo.content, nivel).trim() + '\n';
    case 'codeBlock':  return '```\n' + adf(nodo.content, nivel).trim() + '\n```\n\n';
    case 'blockquote': return '> ' + adf(nodo.content, nivel).trim() + '\n\n';
    case 'rule':       return '\n---\n\n';
    case 'emoji':      return (nodo.attrs && (nodo.attrs.text || nodo.attrs.shortName)) || '';
    case 'mention':    return (nodo.attrs && nodo.attrs.text) || '';
    case 'inlineCard':
    case 'blockCard':  return (nodo.attrs && nodo.attrs.url) || '';
    default:           return adf(nodo.content, nivel);
  }
}
const limpiar = (t) => String(t).replace(/‌/g, '').replace(/[ \t]+\n/g, '\n')
  .replace(/\n{3,}/g, '\n\n').trim();

// ------------------------------------------------------------------ peticiones
async function jira(ruta, params = {}) {
  const url = new URL(`https://${JIRA_SITE}${ruta}`);
  for (const [k, v] of Object.entries(params)) if (v != null) url.searchParams.set(k, v);
  const auth = Buffer.from(`${JIRA_EMAIL}:${JIRA_TOKEN}`).toString('base64');
  const r = await fetch(url, { headers: { Authorization: `Basic ${auth}`, Accept: 'application/json' } });
  if (!r.ok) throw new Error(`Jira ${ruta} -> ${r.status} ${await r.text()}`);
  return r.json();
}

async function rest(metodo, ruta, datos) {
  const r = await fetch(`https://api.github.com${ruta}`, {
    method: metodo,
    headers: { Authorization: `Bearer ${GH_TOKEN}`, Accept: 'application/vnd.github+json',
               'X-GitHub-Api-Version': '2022-11-28', 'Content-Type': 'application/json' },
    body: datos ? JSON.stringify(datos) : undefined,
  });
  if (!r.ok) throw new Error(`GitHub ${metodo} ${ruta} -> ${r.status} ${await r.text()}`);
  return r.status === 204 ? null : r.json();
}

async function gql(query, variables) {
  const r = await fetch('https://api.github.com/graphql', {
    method: 'POST',
    headers: { Authorization: `Bearer ${GH_TOKEN}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, variables }),
  });
  const j = await r.json();
  if (j.errors) throw new Error('GraphQL: ' + JSON.stringify(j.errors));
  return j.data;
}

// ------------------------------------------------------------ 1. leer de Jira
const CAMPOS = ['summary','description','labels','customfield_10020','customfield_10016',
                'status','priority','assignee','parent','subtasks','issuelinks','issuetype'];

console.log(`Jira: ${JIRA_SITE}`);
console.log(`JQL : ${JQL}\n`);

const crudas = [];
let token = null;
do {
  const p = await jira('/rest/api/3/search/jql',
    { jql: JQL, maxResults: 100, fields: CAMPOS.join(','), nextPageToken: token });
  crudas.push(...(p.issues || []));
  token = p.isLast ? null : p.nextPageToken;
} while (token);
console.log(`${crudas.length} historias leidas de Jira\n`);

const historias = crudas.map(it => {
  const f = it.fields;
  const sp = (f.customfield_10020 || [])[0] || {};
  const etiquetas = f.labels || [];
  const grupo = etiquetas.find(l => /^Grupo-\d+$/.test(l)) || null;
  const titulo = String(f.summary || '').replace(/\s+/g, ' ').trim();
  const m = titulo.match(/^(HU-[A-Z]+)/);
  const prefijo = m ? m[1] : null;
  const descripcion = limpiar(adf(f.description));
  return {
    jiraId: it.key,
    jiraUrl: `https://${JIRA_SITE}/browse/${it.key}`,
    titulo,
    grupo,
    equipo: grupo ? EQUIPO[grupo] : null,
    carpeta: grupo ? CARPETA[grupo] : null,
    modulo: prefijo ? MODULO[prefijo] : null,
    epicaKey: f.parent ? f.parent.key : null,
    epica: f.parent ? f.parent.fields.summary : null,
    requisitos: [...new Set((descripcion.match(/RF-[A-Z]{2,5}-\d{3}/g) || []))].sort(),
    puntos: f.customfield_10016,
    prioridadJira: f.priority ? f.priority.name : null,
    prioridad: f.priority ? PRIORIDAD[f.priority.name] : null,
    estadoJira: f.status.name,
    estado: ESTADO[f.status.name],
    sprintJira: sp.name || null,
    sprintInicio: (sp.startDate || '').slice(0, 10),
    sprintFin: (sp.endDate || '').slice(0, 10),
    responsableJira: f.assignee ? f.assignee.displayName : 'sin asignar',
    descripcion,
    subtareas: (f.subtasks || []).map(s => ({ key: s.key, titulo: s.fields.summary, estado: s.fields.status.name })),
    dependencias: (f.issuelinks || []).flatMap(l => {
      if (l.outwardIssue) return [{ rel: l.type.outward, key: l.outwardIssue.key, titulo: l.outwardIssue.fields.summary }];
      if (l.inwardIssue)  return [{ rel: l.type.inward,  key: l.inwardIssue.key,  titulo: l.inwardIssue.fields.summary }];
      return [];
    }),
  };
});

// Aviso, no fallo: se migra igual y el campo queda vacio.
for (const h of historias) {
  if (!h.grupo)  console.warn(`  ! ${h.jiraId} sin etiqueta de grupo`);
  if (!h.modulo) console.warn(`  ! ${h.jiraId} sin modulo reconocido a partir del prefijo`);
  if (!h.estado) console.warn(`  ! ${h.jiraId} estado "${h.estadoJira}" sin equivalencia`);
}

// ---------------------------------------------------------- 2. cuerpo del Issue
function cuerpo(h) {
  const b = [];
  b.push('### Jira ID', `[${h.jiraId}](${h.jiraUrl})`, '');
  b.push('### Épica', h.epica ? `${h.epicaKey} — ${h.epica}` : '_Sin épica en Jira_', '');
  b.push('### Grupo', h.grupo ? `${h.grupo} · ${h.equipo} · \`${h.carpeta}\`` : '_Sin grupo en Jira_', '');
  b.push('### Requisito', h.requisitos.length ? h.requisitos.join(', ') : '_No declarado en Jira_', '');
  b.push('### Historia y criterios de aceptación', h.descripcion || '_Sin descripción en Jira_', '');
  if (h.subtareas.length) {
    b.push(`### Subtareas (${h.subtareas.length})`);
    for (const s of h.subtareas) b.push(`- [${s.estado === 'Finalizado' ? 'x' : ' '}] ${s.key} — ${s.titulo}`);
    b.push('');
  }
  b.push('### Dependencias');
  if (h.dependencias.length) for (const d of h.dependencias) b.push(`- ${d.rel} **${d.key}** — ${d.titulo}`);
  else b.push('_Ninguna declarada en Jira_');
  b.push('', '### Metadatos migrados desde Jira', '', '| Campo | Valor |', '|---|---|');
  b.push(`| Story Points | ${h.puntos ?? 'sin estimar'} |`);
  b.push(`| Prioridad (Jira) | ${h.prioridadJira} |`);
  b.push(`| Estado (Jira) | ${h.estadoJira} |`);
  b.push(`| Sprint (Jira) | ${h.sprintJira} · ${h.sprintInicio} → ${h.sprintFin} |`);
  b.push(`| Responsable (Jira) | ${h.responsableJira} |`);
  b.push('', '---', '');
  b.push('> Migrado desde Jira por el workflow `migrar-backlog-jira`. Los datos son literales: no se han '
       + 'reinterpretado descripciones, estimaciones, prioridades ni dependencias. El responsable se conserva '
       + 'como texto porque un nombre de Jira no equivale a un usuario de GitHub.');
  return b.join('\n');
}

// --------------------------------------------------------------- 3. el Project
const proyecto = (await gql(`
  query($org:String!,$num:Int!){ organization(login:$org){ projectV2(number:$num){
    id title
    fields(first:50){ nodes{
      ... on ProjectV2Field { id name dataType }
      ... on ProjectV2SingleSelectField { id name options { id name } }
      ... on ProjectV2IterationField { id name configuration { iterations { id title } } }
    } } } } }`, { org: ORG, num: PROJECT_NUMBER })).organization.projectV2;

const campos = new Map();
for (const f of proyecto.fields.nodes) if (f && f.name) campos.set(norm(f.name), f);
console.log(`\nProject: ${proyecto.title}`);
for (const f of campos.values()) {
  const extra = f.options ? ` [${f.options.map(o => o.name).join(' | ')}]`
    : f.configuration ? ` [${f.configuration.iterations.map(i => i.title).join(' | ')}]` : '';
  console.log(`  campo: ${f.name}${extra}`);
}

// ------------------------------------------------------------- 4. idempotencia
const yaMigradas = new Set();
for (let p = 1; ; p++) {
  const lote = await rest('GET', `/repos/${OWNER}/${REPO_NAME}/issues?state=all&per_page=100&page=${p}`);
  if (!lote.length) break;
  for (const i of lote) { const m = String(i.body || '').match(/SCRUM-\d+/); if (m) yaMigradas.add(m[0]); }
  if (lote.length < 100) break;
}
console.log(`\n${yaMigradas.size} historias ya presentes en el repositorio`);

// ---------------------------------------------------------------- 5. etiquetas
if (!DRY_RUN) {
  const actuales = new Set((await rest('GET', `/repos/${OWNER}/${REPO_NAME}/labels?per_page=100`)).map(l => l.name));
  for (const [nombre, color] of Object.entries(ETIQUETA_COLOR)) {
    if (actuales.has(nombre)) continue;
    await rest('POST', `/repos/${OWNER}/${REPO_NAME}/labels`,
      { name: nombre, color, description: 'Backlog migrado desde Jira' });
    console.log(`  + etiqueta ${nombre}`);
  }
}

// ---------------------------------------------------------------- 6. migracion
console.log(`\n${DRY_RUN ? '=== SIMULACION: no se escribe nada ===' : '=== MIGRACION ==='}\n`);
let creadas = 0, omitidas = 0, avisos = 0;

for (const h of historias) {
  if (yaMigradas.has(h.jiraId)) { console.log(`= ${h.jiraId} ya migrada`); omitidas++; continue; }
  if (DRY_RUN) {
    console.log(`~ ${h.jiraId} | ${h.equipo || '?'} | ${h.modulo || '?'} | ${h.puntos ?? '?'} pts | ${h.estado || '?'} | ${h.titulo}`);
    creadas++; continue;
  }

  const etiquetas = ['historia-usuario', 'migrado-jira'];
  if (h.grupo) etiquetas.push(h.grupo.toLowerCase());
  const issue = await rest('POST', `/repos/${OWNER}/${REPO_NAME}/issues`,
    { title: h.titulo, body: cuerpo(h), labels: etiquetas });
  console.log(`+ #${issue.number}  ${h.jiraId}  ${h.titulo}`);

  const item = (await gql(
    `mutation($p:ID!,$c:ID!){ addProjectV2ItemById(input:{projectId:$p,contentId:$c}){ item{id} } }`,
    { p: proyecto.id, c: issue.node_id })).addProjectV2ItemById.item.id;

  const valores = {
    'Jira ID': h.jiraId,
    'Requisito': h.requisitos.join(', '),
    'Dependencia': h.dependencias.map(d => `${d.rel} ${d.key}`).join(' · '),
    'Story Points': h.puntos,
    'Equipo': h.equipo,
    'Prioridad': h.prioridad,
    'Tipo': 'Historia de usuario',
    'Módulo': h.modulo,
    'Status': h.estado,
    'Sprint': SPRINT_PROJECT,
  };

  for (const [nombre, valor] of Object.entries(valores)) {
    if (valor === '' || valor == null) continue;
    const f = campos.get(norm(nombre));
    if (!f) { console.warn(`  ! el Project no tiene el campo "${nombre}"`); avisos++; continue; }
    let value;
    if (f.options) {
      const o = f.options.find(o => norm(o.name) === norm(valor));
      if (!o) { console.warn(`  ! "${valor}" no es una opcion de "${f.name}"`); avisos++; continue; }
      value = { singleSelectOptionId: o.id };
    } else if (f.configuration) {
      const its = f.configuration.iterations;
      const i = its.find(i => norm(i.title) === norm(valor)) || its[0];
      if (!i) { avisos++; continue; }
      if (norm(i.title) !== norm(valor)) console.warn(`  ! no existe la iteracion "${valor}"; se usa "${i.title}"`);
      value = { iterationId: i.id };
    } else if (f.dataType === 'NUMBER') {
      value = { number: Number(valor) };
    } else {
      value = { text: String(valor) };
    }
    await gql(`mutation($p:ID!,$i:ID!,$f:ID!,$v:ProjectV2FieldValue!){
      updateProjectV2ItemFieldValue(input:{projectId:$p,itemId:$i,fieldId:$f,value:$v}){ projectV2Item{id} } }`,
      { p: proyecto.id, i: item, f: f.id, v: value });
  }
  creadas++;
  await dormir(700);
}

console.log(`\nResumen: ${creadas} ${DRY_RUN ? 'se crearian' : 'creadas'}, ${omitidas} omitidas, ${avisos} avisos de campo`);
