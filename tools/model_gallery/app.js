// Галерея моделей предметов RP Medicine: вид в слоте, от 1-го и 3-го лица — по тем же преобразованиям, что в игре
// (ItemTransform, ItemInHandRenderer, ItemInHandLayer), правка display по контекстам и отметки «нравится / нет».
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

const CTX = ['gui', 'firstperson_righthand', 'thirdperson_righthand', 'ground'];
const DEF = {
  gui: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
  firstperson_righthand: { rotation: [0, -90, 25], translation: [1.13, 3.2, 1.13], scale: [0.68, 0.68, 0.68] },
  thirdperson_righthand: { rotation: [0, 0, 0], translation: [0, 3, 1], scale: [0.55, 0.55, 0.55] },
  ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.5, 0.5, 0.5] },
};

let items = [], review = { items: {} }, cur = null, ctx = 'gui', filter = 'all';
const texCache = new Map(), modelCache = new Map();
const $ = s => document.querySelector(s);

// ------------------------------------------------------------------ данные
async function load() {
  items = await (await fetch('data/items.json', { cache: 'no-store' })).json();
  review = await (await fetch('/api/review', { cache: 'no-store' })).json();
  review.items ??= {};
  renderList();
  const last = localStorage.getItem('rpm_gallery_item');
  select(items.find(i => i.id === last) || items[0]);
}

let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(async () => {
    $('#saved').textContent = 'сохраняю…';
    const r = await fetch('/api/review', { method: 'POST', body: JSON.stringify(review) });
    const j = await r.json();
    $('#saved').textContent = r.ok ? 'сохранено ' + j.saved_at : 'ошибка сохранения';
  }, 400);
}

function rec(id) { return review.items[id] ??= {}; }

/** Текущий display: правка из отзыва, иначе исходный из модели, иначе ванильный по умолчанию. */
function disp(item, c) {
  const r = review.items[item.id];
  const d = r?.display?.[c] || item.display[c] || DEF[c];
  return {
    rotation: [...(d.rotation || [0, 0, 0])],
    translation: [...(d.translation || [0, 0, 0])],
    scale: [...(d.scale || [1, 1, 1])],
  };
}

function changed(item) { return !!review.items[item.id]?.display && Object.keys(review.items[item.id].display).length > 0; }

// ------------------------------------------------------------------ список
function renderList() {
  const q = $('#search').value.trim().toLowerCase();
  const ul = $('#list');
  ul.innerHTML = '';
  let nLike = 0, nDis = 0;
  for (const it of items) {
    const r = review.items[it.id] || {};
    if (r.mark === 'like') nLike++;
    if (r.mark === 'dislike') nDis++;
    if (q && !it.name.toLowerCase().includes(q) && !it.id.includes(q)) continue;
    if (filter === 'like' && r.mark !== 'like') continue;
    if (filter === 'dislike' && r.mark !== 'dislike') continue;
    if (filter === 'none' && r.mark) continue;
    if (filter === 'changed' && !changed(it)) continue;
    const li = document.createElement('li');
    li.dataset.id = it.id;
    li.innerHTML = `<img src="${it.icon || ''}" alt=""><span>${it.name}${changed(it) ? ' <span class="ch">вид</span>' : ''}${r.comment ? ' 💬' : ''}</span>
      <span class="st">${r.mark === 'like' ? '👍' : r.mark === 'dislike' ? '👎' : ''}</span>`;
    if (cur && cur.id === it.id) li.classList.add('sel');
    li.onclick = () => select(it);
    ul.appendChild(li);
  }
  $('#stats').textContent = `${items.length} предметов · 👍 ${nLike} · 👎 ${nDis}`;
}

$('#search').oninput = renderList;
document.querySelectorAll('#filters button').forEach(b => b.onclick = () => {
  document.querySelectorAll('#filters button').forEach(x => x.classList.toggle('on', x === b));
  filter = b.dataset.f;
  renderList();
});

// ------------------------------------------------------------------ модель
function loadTex(url) {
  if (!texCache.has(url)) {
    texCache.set(url, new Promise(res => new THREE.TextureLoader().load(url, t => {
      t.flipY = false;
      t.magFilter = THREE.NearestFilter;
      t.minFilter = THREE.NearestFilter;
      t.colorSpace = THREE.SRGBColorSpace;
      res(t);
    }, undefined, () => res(null))));
  }
  return texCache.get(url);
}

/** Модель в пикселях блока (0–16) -> группа в блоках с началом в центре блока (как после translate(-0,5)). */
async function buildModel(item) {
  if (!modelCache.has(item.id)) {
    modelCache.set(item.id, (async () => {
      const quads = await (await fetch(`data/models/${item.id}.json`, { cache: 'no-store' })).json();
      const byTex = new Map();
      for (const q of quads) {
        if (!byTex.has(q.t)) byTex.set(q.t, []);
        byTex.get(q.t).push(q);
      }
      const proto = [];
      for (const [url, qs] of byTex) {
        const pos = [], uv = [], idx = [];
        for (const q of qs) {
          const b = pos.length / 3;
          for (let k = 0; k < 4; k++) { pos.push(...q.p[k]); uv.push(q.uv[k][0], q.uv[k][1]); }
          idx.push(b, b + 1, b + 2, b, b + 2, b + 3);
        }
        const g = new THREE.BufferGeometry();
        g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
        g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
        g.setIndex(idx);
        g.computeVertexNormals();
        proto.push({ g, tex: await loadTex(url) });
      }
      return proto;
    })());
  }
  const proto = await modelCache.get(item.id);
  const inner = new THREE.Group();
  for (const { g, tex } of proto) {
    const m = new THREE.MeshLambertMaterial({ map: tex, side: THREE.DoubleSide, alphaTest: 0.1, transparent: true });
    inner.add(new THREE.Mesh(g, m));
  }
  inner.scale.setScalar(1 / 16);
  inner.position.set(-0.5, -0.5, -0.5);
  const holder = new THREE.Group();
  holder.add(inner);
  return holder;
}

/** display Minecraft: translate(t/16) · rotationXYZ · scale. */
function applyDisplay(group, d) {
  group.position.set(d.translation[0] / 16, d.translation[1] / 16, d.translation[2] / 16);
  group.rotation.set(THREE.MathUtils.degToRad(d.rotation[0]), THREE.MathUtils.degToRad(d.rotation[1]),
    THREE.MathUtils.degToRad(d.rotation[2]), 'XYZ');
  group.scale.set(d.scale[0], d.scale[1], d.scale[2]);
}

// ------------------------------------------------------------------ сцены
function makeRenderer(canvas, alpha = false) {
  const r = new THREE.WebGLRenderer({ canvas, antialias: false, alpha });
  r.setPixelRatio(1);
  return r;
}

function lights(scene, flat) {
  if (flat) { scene.add(new THREE.AmbientLight(0xffffff, 2.6)); return; }
  scene.add(new THREE.AmbientLight(0xffffff, 1.2));
  const a = new THREE.DirectionalLight(0xffffff, 1.6); a.position.set(-0.6, 1, 0.9); scene.add(a);
  const b = new THREE.DirectionalLight(0xffffff, 0.5); b.position.set(0.7, 0.3, -0.6); scene.add(b);
}

// Слот: ортокамера ровно на 1 блок (= 16 px слота), как GuiGraphics.renderItem (scale 16).
const gui = { r: makeRenderer($('#v-gui')), r1: makeRenderer($('#v-gui1')), cam: new THREE.OrthographicCamera(-0.5, 0.5, 0.5, -0.5, -10, 10) };
gui.cam.position.z = 5;

// 1-е лицо: камера в глазу, FOV 70; предмет — translate(0,56; −0,52; −0,72) (ItemInHandRenderer.applyItemArmTransform).
const fp = { r: makeRenderer($('#v-fp')), cam: new THREE.PerspectiveCamera(70, 16 / 10, 0.01, 100) };

// 3-е лицо: игрок по HumanoidModel, предмет в правой руке (ItemInHandLayer).
const tp = { r: makeRenderer($('#v-tp')), cam: new THREE.PerspectiveCamera(45, 16 / 10, 0.05, 100) };
tp.cam.position.set(-1.9, 1.5, 2.6);
tp.ctl = new OrbitControls(tp.cam, $('#v-tp'));
tp.ctl.target.set(-0.2, 0.95, 0);
tp.ctl.update();

const free = { r: makeRenderer($('#v-free')), cam: new THREE.PerspectiveCamera(40, 16 / 10, 0.01, 100) };
free.cam.position.set(1.2, 0.9, 1.6);
free.ctl = new OrbitControls(free.cam, $('#v-free'));
free.ctl.target.set(0, 0, 0);
free.ctl.update();

function slotBackground() {
  const c = document.createElement('canvas');
  c.width = c.height = 18;
  const g = c.getContext('2d');
  g.fillStyle = '#373737'; g.fillRect(0, 0, 18, 18);
  g.fillStyle = '#ffffff'; g.fillRect(1, 17, 17, 1); g.fillRect(17, 1, 1, 17);
  g.fillStyle = '#8b8b8b'; g.fillRect(1, 1, 16, 16);
  const t = new THREE.CanvasTexture(c);
  t.magFilter = THREE.NearestFilter;
  return t;
}
const SLOT_BG = slotBackground();

function skyGround(scene) {
  scene.background = new THREE.Color(0x87b5ff);
  const ground = new THREE.Mesh(new THREE.PlaneGeometry(60, 60), new THREE.MeshLambertMaterial({ color: 0x6aa04a }));
  ground.rotation.x = -Math.PI / 2;
  return ground;
}

// Модель игрока: части HumanoidModel (пиксели, ось y вниз) — опора и куб.
const PARTS = {
  head: { pivot: [0, 0, 0], from: [-4, -8, -4], size: [8, 8, 8], color: 0xc69c7a },
  body: { pivot: [0, 0, 0], from: [-4, 0, -2], size: [8, 12, 4], color: 0x3d6fb0 },
  rightArm: { pivot: [-5, 2, 0], from: [-3, -2, -2], size: [4, 12, 4], color: 0xb88c6a, rot: [-Math.PI / 10, 0, 0] },
  leftArm: { pivot: [5, 2, 0], from: [-1, -2, -2], size: [4, 12, 4], color: 0xc69c7a },
  rightLeg: { pivot: [-1.9, 12, 0], from: [-2, 0, -2], size: [4, 12, 4], color: 0x2d3550 },
  leftLeg: { pivot: [1.9, 12, 0], from: [-2, 0, -2], size: [4, 12, 4], color: 0x2d3550 },
};

/** Матрица корня сущности: YP(180) · scale(−1,−1,1) · scale(0,9375) · translate(0, −1,501, 0) (LivingEntityRenderer). */
function entityMatrix() {
  const m = new THREE.Matrix4().makeRotationY(Math.PI);
  m.multiply(new THREE.Matrix4().makeScale(-1, -1, 1));
  m.multiply(new THREE.Matrix4().makeScale(0.9375, 0.9375, 0.9375));
  m.multiply(new THREE.Matrix4().makeTranslation(0, -1.501, 0));
  return m;
}

/** ModelPart.translateAndRotate: translate(pivot/16) · rotationZYX(z, y, x). */
function partMatrix(p) {
  const m = new THREE.Matrix4().makeTranslation(p.pivot[0] / 16, p.pivot[1] / 16, p.pivot[2] / 16);
  const r = p.rot || [0, 0, 0];
  m.multiply(new THREE.Matrix4().makeRotationFromEuler(new THREE.Euler(r[0], r[1], r[2], 'ZYX')));
  return m;
}

function buildPlayer() {
  const root = new THREE.Group();
  const em = entityMatrix();
  for (const p of Object.values(PARTS)) {
    const geo = new THREE.BoxGeometry(p.size[0] / 16, p.size[1] / 16, p.size[2] / 16);
    geo.translate((p.from[0] + p.size[0] / 2) / 16, (p.from[1] + p.size[1] / 2) / 16, (p.from[2] + p.size[2] / 2) / 16);
    const mesh = new THREE.Mesh(geo, new THREE.MeshLambertMaterial({ color: p.color }));
    mesh.matrixAutoUpdate = false;
    mesh.matrix.copy(em.clone().multiply(partMatrix(p)));
    root.add(mesh);
  }
  return root;
}

/** Предмет в правой руке: рука · XP(−90) · YP(180) · translate(1/16; 0,125; −0,625), дальше display. */
function handMatrix() {
  const m = entityMatrix().multiply(partMatrix(PARTS.rightArm));
  m.multiply(new THREE.Matrix4().makeRotationX(-Math.PI / 2));
  m.multiply(new THREE.Matrix4().makeRotationY(Math.PI));
  m.multiply(new THREE.Matrix4().makeTranslation(1 / 16, 0.125, -0.625));
  return m;
}

let scenes = null;

async function show(item) {
  // Слот
  const gs = new THREE.Scene();
  lights(gs, item.light === 'front');
  const bg = new THREE.Mesh(new THREE.PlaneGeometry(18 / 16, 18 / 16), new THREE.MeshBasicMaterial({ map: SLOT_BG }));
  bg.position.z = -3;
  gs.add(bg);
  const gm = await buildModel(item);
  gs.add(gm);

  // 1-е лицо
  const fs = new THREE.Scene();
  lights(fs, false);
  const fground = skyGround(fs);
  fground.position.y = -1.62;
  fs.add(fground);
  const fm = await buildModel(item);
  const fpRoot = new THREE.Group();
  fpRoot.position.set(0.56, -0.52, -0.72);
  fpRoot.add(fm);
  fs.add(fpRoot);

  // 3-е лицо
  const ts = new THREE.Scene();
  lights(ts, false);
  ts.add(skyGround(ts));
  ts.add(buildPlayer());
  const tm = await buildModel(item);
  const handRoot = new THREE.Group();
  handRoot.matrixAutoUpdate = false;
  handRoot.matrix.copy(handMatrix());
  handRoot.add(tm);
  ts.add(handRoot);

  // Модель сама по себе
  const xs = new THREE.Scene();
  lights(xs, false);
  xs.background = new THREE.Color(0x2a2d35);
  const xm = await buildModel(item);
  xs.add(xm);
  xs.add(new THREE.GridHelper(1, 16, 0x555555, 0x3a3a3a)).position.y = -0.5;

  scenes = { gs, gm, fs, fm, ts, tm, xs };
  refreshDisplay();
}

function refreshDisplay() {
  if (!scenes || !cur) return;
  applyDisplay(scenes.gm, disp(cur, 'gui'));
  applyDisplay(scenes.fm, disp(cur, 'firstperson_righthand'));
  applyDisplay(scenes.tm, disp(cur, 'thirdperson_righthand'));
}

function fit(r, w, h) {
  const c = r.domElement;
  if (c.width !== w || c.height !== h) r.setSize(w, h, false);
}

function loop() {
  requestAnimationFrame(loop);
  if (!scenes) return;
  fit(gui.r, 240, 240); gui.r.render(scenes.gs, gui.cam);
  fit(gui.r1, 36, 36); gui.r1.render(scenes.gs, gui.cam);
  for (const [v, s, el] of [[fp, scenes.fs, '#v-fp'], [tp, scenes.ts, '#v-tp'], [free, scenes.xs, '#v-free']]) {
    const c = $(el);
    const w = c.clientWidth || 400, h = c.clientHeight || Math.round(w / 1.6);
    if (v.cam.aspect !== w / h) { v.cam.aspect = w / h; v.cam.updateProjectionMatrix(); }
    fit(v.r, w, h);
    v.r.render(s, v.cam);
  }
}
loop();

// ------------------------------------------------------------------ выбор и правка
async function select(item) {
  if (!item) return;
  cur = item;
  localStorage.setItem('rpm_gallery_item', item.id);
  $('#name').textContent = item.name;
  $('#kind').textContent = `${item.id} · ${{ geo: '3D (client/geo)', json: '3D (JSON)', flat: 'плоская' }[item.kind] || item.kind}`;
  const r = review.items[item.id] || {};
  document.querySelectorAll('#mark button').forEach(b => b.classList.toggle('on', (r.mark || '') === b.dataset.m && !!b.dataset.m));
  $('#comment').value = r.comment || '';
  document.querySelectorAll('#list li').forEach(li => li.classList.toggle('sel', li.dataset.id === item.id));
  const kind = $('#kind').textContent;
  $('#kind').textContent = kind + ' · загрузка…';
  try {
    await show(item);
    if (cur === item) $('#kind').textContent = kind;
  } catch (e) {
    console.error(e);
    $('#kind').textContent += ' · ошибка: ' + e;
  }
  buildSliders();
}

document.querySelectorAll('#mark button').forEach(b => b.onclick = () => {
  const r = rec(cur.id);
  r.mark = b.dataset.m || undefined;
  if (!r.mark) delete r.mark;
  document.querySelectorAll('#mark button').forEach(x => x.classList.toggle('on', x === b && !!b.dataset.m));
  save();
  renderList();
});
$('#comment').oninput = () => {
  const r = rec(cur.id);
  r.comment = $('#comment').value;
  if (!r.comment) delete r.comment;
  save();
};

document.querySelectorAll('#ctxtabs button').forEach(b => b.onclick = () => {
  document.querySelectorAll('#ctxtabs button').forEach(x => x.classList.toggle('on', x === b));
  ctx = b.dataset.c;
  buildSliders();
});

const AXES = [
  ['rotation', 'Поворот', -180, 180, 1],
  ['translation', 'Сдвиг', -16, 16, 0.25],
  ['scale', 'Размер', 0.05, 3, 0.01],
];

function setValue(kind, axis, v) {
  const r = rec(cur.id);
  r.display ??= {};
  const d = disp(cur, ctx);
  if (kind === 'scale' && axis === 3) d.scale = [v, v, v];
  else d[kind][axis] = v;
  r.display[ctx] = d;
  refreshDisplay();
  save();
}

function buildSliders() {
  const box = $('#sliders');
  box.innerHTML = '';
  const d = disp(cur, ctx);
  const orig = cur.display[ctx] || DEF[ctx];
  for (const [kind, label, min, max, step] of AXES) {
    const axes = kind === 'scale' ? [3, 0, 1, 2] : [0, 1, 2];
    for (const a of axes) {
      const name = a === 3 ? `${label} (всё)` : `${label} ${'XYZ'[a]}`;
      const val = a === 3 ? d.scale[0] : d[kind][a];
      const o = a === 3 ? (orig.scale || [1])[0] : (orig[kind] || [0, 0, 0])[a];
      const row = document.createElement('div');
      row.className = 'row' + (Math.abs(val - o) > 1e-6 ? ' changed' : '');
      row.innerHTML = `<label title="исходное: ${o}">${name}</label><input type="range" min="${min}" max="${max}" step="${step}" value="${val}">
        <input type="number" min="${min}" max="${max}" step="${step}" value="${val}">`;
      const [rng, num] = row.querySelectorAll('input');
      rng.oninput = () => { num.value = rng.value; setValue(kind, a, +rng.value); row.classList.toggle('changed', Math.abs(+rng.value - o) > 1e-6); };
      num.onchange = () => { rng.value = num.value; setValue(kind, a, +num.value); row.classList.toggle('changed', Math.abs(+num.value - o) > 1e-6); };
      box.appendChild(row);
    }
  }
}

$('#reset').onclick = () => {
  const r = review.items[cur.id];
  if (r?.display) {
    delete r.display[ctx];
    if (!Object.keys(r.display).length) delete r.display;
  }
  refreshDisplay();
  buildSliders();
  save();
  renderList();
};
$('#copyfp').onclick = () => {
  const r = rec(cur.id);
  r.display ??= {};
  r.display[ctx] = disp(cur, 'firstperson_righthand');
  refreshDisplay();
  buildSliders();
  save();
};

load();
