// Smoke test for tools/formation_figure.html: it runs the page's own script against a stub DOM and checks the geometry
// against the numbers the mod's own probe prints (130 points on the five-figure at radius 8, spacing 1).
// Run it from anywhere: node tools/formation_figure_check.mjs
import fs from 'node:fs';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';

const script = fs.readFileSync(fileURLToPath(new URL('formation_figure.html', import.meta.url)), 'utf8')
    .match(/<script>([\s\S]*?)<\/script>/)[1];

const CTX = ['setTransform', 'fillRect', 'clearRect', 'beginPath', 'closePath', 'moveTo', 'lineTo', 'arc', 'stroke',
    'fill', 'save', 'restore', 'setLineDash', 'fillText', 'strokeText', 'translate', 'scale', 'measureText'];
const elements = new Map();
const VALUES = {progress: '1', radius: '8', spacing: '1', size: '2', count: '5', step: '2', duration: '60',
    precision: '4', colour: '#80c0ff', paint: '#ff0000', start: '0', end: '1', startangle: '0', speed: '3'};
const CHECKED = {snap: true, ring: true, angles: true, grid: true, rays: true, dust: true, order: true,
    figure: true, seedring: true, loop: true};

// Every moveTo / lineTo / arc the page strokes, tagged with the style in force: the geometry layer can be read back
// out of this, which is the only way to check that the lines grow the way the dust does.
const OPS = [];

function context2d() {
    const ctx = {};
    for (const name of CTX) ctx[name] = () => {};
    ctx.measureText = () => ({width: 0});
    ctx.createLinearGradient = () => ({addColorStop() {}});
    for (const name of ['moveTo', 'lineTo', 'arc'])
        ctx[name] = (...args) => OPS.push([name, ...args]);
    for (const name of ['strokeStyle', 'fillStyle']) {
        let value = null;
        Object.defineProperty(ctx, name, {
            get: () => value,
            set: next => {
                value = next;
                OPS.push([name, next]);
            }
        });
    }
    return ctx;
}

function element(id) {
    return {
        id,
        value: VALUES[id] ?? '',
        checked: CHECKED[id] ?? false,
        textContent: '',
        className: '',
        disabled: false,
        title: '',
        style: {},
        dataset: {},
        classList: {add() {}, remove() {}, toggle() {}},
        handlers: {},
        addEventListener(type, handler) {
            (this.handlers[type] = this.handlers[type] || []).push(handler);
        },
        removeEventListener() {},
        append() {},
        appendChild() {},
        replaceChildren() {},
        insertAdjacentHTML() {},
        select() {},
        focus() {},
        hasPointerCapture: () => false,
        setPointerCapture() {},
        releasePointerCapture() {},
        getBoundingClientRect: () => ({left: 0, top: 0, width: 520, height: 520, right: 520, bottom: 520}),
        getContext: () => context2d()
    };
}

const document = {
    getElementById(id) {
        if (!elements.has(id)) elements.set(id, element(id));
        return elements.get(id);
    },
    createElement: () => element('created'),
    querySelectorAll: () => [],
    querySelector: () => element('queried'),
    addEventListener() {},
    execCommand() {},
    body: element('body')
};

const sandbox = {
    document,
    window: {devicePixelRatio: 1, addEventListener() {}},
    navigator: {clipboard: {writeText: async () => {}}},
    requestAnimationFrame: () => 1,
    cancelAnimationFrame() {},
    Blob: class {},
    URL: {createObjectURL: () => 'blob:', revokeObjectURL() {}},
    console
};
vm.createContext(sandbox);
vm.runInContext(script, sandbox, {filename: 'formation_figure.html'});

const failures = [];
const close = (actual, expected, tolerance = 1e-12) => Math.abs(actual - expected) <= tolerance;
const check = (name, ok, detail) => { if (!ok) failures.push(name + (detail === undefined ? '' : '  → ' + detail)); };
const near = (a, b, tolerance = 1e-9) => close(a.x, b.x, tolerance) && close(a.z, b.z, tolerance);

const {starFigure, ringVertex, sampleStroke, sampledCount, strokeLength, circle, polyline, steps, toJson} = sandbox;
const progressBox = document.getElementById('progress');

// The vertex convention: index 0 due east, the angle increasing.
check('ring vertex 0 is due east', near(ringVertex(5, 0), {x: 1, z: 0})
    && near(ringVertex(5, 1), {x: Math.cos(2 * Math.PI / 5), z: Math.sin(2 * Math.PI / 5)}));

// The three shapes the mod ships, rebuilt from the {N/K} rule: same vertices, same routes, same cycle count.
const cyclesOf = (count, step) => starFigure(count, step, false).map(stroke =>
    stroke.points.map(point => {
        for (let index = 0; index < count; index++) if (near(point, ringVertex(count, index))) return index;
        return -1;
    }));
check('five is one pentagram cycle', JSON.stringify(cyclesOf(5, 2)) === JSON.stringify([[0, 2, 4, 1, 3, 0]]),
    JSON.stringify(cyclesOf(5, 2)));
check('six is two triangles', JSON.stringify(cyclesOf(6, 2)) === JSON.stringify([[0, 2, 4, 0], [1, 3, 5, 1]]),
    JSON.stringify(cyclesOf(6, 2)));
check('eight is two squares', JSON.stringify(cyclesOf(8, 2)) === JSON.stringify([[0, 2, 4, 6, 0], [1, 3, 5, 7, 1]]),
    JSON.stringify(cyclesOf(8, 2)));
check('seven over two is a single cycle', cyclesOf(7, 2).length === 1 && cyclesOf(7, 2)[0].length === 8);
check('a star with the ring leads with the circle',
    starFigure(5, 2, true)[0].type === 'circle' && starFigure(5, 2, true)[0].radius === 1
    && starFigure(5, 2, false).length === 1);

// The sampler has to land on the mod's own counts: at radius 8 and a spacing of 1 the five-figure is 52 + 78 = 130
// points. The spacing is the stroke's own, so the two strokes are copied with one written on them.
const [ring, pentagram] = starFigure(5, 2, true);
const fine = stroke => ({...stroke, spacing: 1});
check('the ring samples 52 points', sampleStroke(fine(ring), 8).length === 52, sampleStroke(fine(ring), 8).length);
check('the pentagram samples 78 points', sampleStroke(fine(pentagram), 8).length === 78,
    sampleStroke(fine(pentagram), 8).length);
check('the whole figure is the probe\'s 130 points',
    sampledCount(fine(ring), 8) + sampledCount(fine(pentagram), 8) === 130);
// The standard spacing is what a stroke that says nothing gets, and a spacing twice as coarse is half the points.
check('the count follows the stroke\'s own spacing and its default',
    sampledCount(ring, 8) === 154 && sampledCount(fine(ring), 8) === 52
    && sampledCount({...ring, spacing: 2}, 8) === 27,
    [sampledCount(ring, 8), sampledCount(fine(ring), 8), sampledCount({...ring, spacing: 2}, 8)].join(' '));
check('the ring length is one circumference', close(strokeLength(ring), Math.PI * 2)
    && close(strokeLength(pentagram), 5 * 2 * Math.sin(2 * Math.PI / 5), 1e-9), strokeLength(pentagram));
check('steps never exceeds the ceiling',
    steps(1e6, 4096, 0.01) === 2047 && steps(0, 8, 1) === 0 && steps(1e-9, 8, 1) === 1);

// Truncation is the game's rule: at half progress the ring reaches its antipode and the polyline its mid point.
const wholeRing = sampleStroke(fine(ring), 8).length;
const wholePentagram = sampleStroke(fine(pentagram), 8);
progressBox.value = '0.5';
const halfRing = sampleStroke(fine(ring), 8);
check('half a ring ends at the antipode', near(halfRing[halfRing.length - 1], {x: -1, z: 0}, 1e-9),
    JSON.stringify(halfRing[halfRing.length - 1]));
check('half a ring places fewer points', halfRing.length < wholeRing && halfRing.length > wholeRing / 2 - 2,
    halfRing.length + ' of ' + wholeRing);
const halfPentagram = sampleStroke(fine(pentagram), 8);
const halfway = sandbox.pointAt(pentagram.points, strokeLength(pentagram) / 2);
check('half a pentagram ends exactly half way along it',
    near(halfPentagram[halfPentagram.length - 1], halfway, 1e-9),
    JSON.stringify(halfPentagram[halfPentagram.length - 1]) + ' vs ' + JSON.stringify(halfway));
check('half a pentagram places fewer points', halfPentagram.length < wholePentagram.length,
    halfPentagram.length + ' of ' + wholePentagram.length);
progressBox.value = '0';
check('progress 0 draws nothing',
    sampleStroke(fine(ring), 8).length === 0 && sampleStroke(fine(pentagram), 8).length === 0);
progressBox.value = '1';

// The JSON the page opens with: the {5/2} figure as a definition, rounded, with the default centre and the whole
// window left out.
const parsed = JSON.parse(toJson());
check('the opening JSON is one definition holding two strokes',
    !Array.isArray(parsed) && Array.isArray(parsed.strokes) && parsed.strokes.length === 2,
    JSON.stringify(parsed).slice(0, 120));
check('the circle carries no centre when it is on the controller',
    parsed.strokes[0].type === 'mxt:circle' && parsed.strokes[0].radius === 1
    && parsed.strokes[0].center === undefined);
check('every stroke is written with the colour a fresh one gets',
    parsed.strokes.every(stroke => stroke.color === '#80c0ff' && stroke.gradient === undefined),
    JSON.stringify(parsed.strokes.map(stroke => stroke.color)));
check('a whole window is left out of the file',
    parsed.strokes.every(stroke => stroke.start === undefined && stroke.end === undefined
        && stroke.start_angle === undefined));
check('the polyline closes on its first vertex',
    parsed.strokes[1].type === 'mxt:polyline' && parsed.strokes[1].points.length === 6
    && JSON.stringify(parsed.strokes[1].points[0]) === '[1,0]'
    && JSON.stringify(parsed.strokes[1].points[5]) === '[1,0]', JSON.stringify(parsed.strokes[1].points));
check('the writer rounds to the chosen decimals',
    JSON.stringify(parsed.strokes[1].points[1]) === '[-0.809,0.5878]'
    && JSON.stringify(parsed.strokes[1].points[2]) === '[0.309,-0.9511]', JSON.stringify(parsed.strokes[1].points));

// The loader path: the same JSON read back has to reproduce the same figure.
const loaded = parsed.strokes.map(row => sandbox.readStroke(row));
check('reading the JSON back reproduces both strokes',
    loaded.every(stroke => typeof stroke === 'object')
    && close(strokeLength(loaded[0]), strokeLength(ring))
    && close(strokeLength(loaded[1]), strokeLength(pentagram), 1e-3),
    loaded.map(stroke => typeof stroke === 'string' ? stroke : strokeLength(stroke)));
check('a bare point pair is refused', typeof sandbox.readStroke({type: 'mxt:polyline', points: [[0, 0]], color: '#fff'}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 0, color: '#fff'}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:blob'}) === 'string'
    && typeof sandbox.readStroke({type: 'blob'}) === 'string');
// There is no figure-wide colour to fall back to, so a stroke with neither paint is a file the mod would refuse.
check('a stroke with no paint of its own is refused',
    typeof sandbox.readStroke({type: 'mxt:circle', radius: 1}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:polyline', points: [[0, 0], [1, 0]]}) === 'string');

// The namespaced id is the format; a bare one is the same id without its namespace and is read back as such.
check('a stroke type is read with or without its namespace',
    sandbox.strokeKind('mxt:circle') === 'circle' && sandbox.strokeKind('circle') === 'circle'
    && sandbox.strokeKind('mxt:polyline') === 'polyline' && sandbox.strokeKind('polyline') === 'polyline'
    && sandbox.strokeKind('minecraft:circle') === null && sandbox.strokeKind('hexagon') === null
    && sandbox.strokeKind(undefined) === null);
const bare = sandbox.readStroke({type: 'circle', radius: 0.5, center: [0.1, 0.2], color: '#123456'});
check('a stroke written without a namespace still loads, and is written back with one',
    typeof bare === 'object' && bare.type === 'circle' && bare.radius === 0.5
    && sandbox.strokeJson(bare).startsWith('{"type": "mxt:circle"'), sandbox.strokeJson(bare));

// ── the divisions: N rays out of the controller, angle 0 due east ────────────────────────────────────────────
const rays = sandbox.spokeFigure(5);
check('the divisions are N rays out of the controller', rays.length === 5
    && rays.every(ray => ray.type === 'polyline' && ray.points.length === 2
        && ray.points[0].x === 0 && ray.points[0].z === 0));
check('the first ray is due east and the rest are a fifth of a turn apart',
    near(rays[0].points[1], {x: 1, z: 0})
    && close(Math.atan2(rays[1].points[1].z, rays[1].points[1].x), 2 * Math.PI / 5)
    && near(rays[2].points[1], ringVertex(5, 2)));
check('every ray ends on the ring', rays.every(ray => close(Math.hypot(ray.points[1].x, ray.points[1].z), 1)));
// Snapping follows the same divisions: a click near a ray keeps its radius and lands exactly on the ray.
const onRay = sandbox.snap({x: 0.6 * Math.cos(2 * Math.PI / 5) + 0.02, z: 0.6 * Math.sin(2 * Math.PI / 5) - 0.01}, null);
check('a click near a division lands on it at the snapped radius',
    close(Math.hypot(onRay.x, onRay.z), 0.6, 1e-9)
    && close(Math.atan2(onRay.z, onRay.x), 2 * Math.PI / 5, 1e-9),
    JSON.stringify(onRay));
check('the controller is a snap target of its own',
    JSON.stringify(sandbox.snap({x: 0.01, z: -0.02}, null)) === '{"x":0,"z":0}');
check('a click nowhere near a division only takes the grid',
    JSON.stringify(sandbox.snap({x: 0.303, z: 0.542}, null)) === '{"x":0.3,"z":0.55}',
    JSON.stringify(sandbox.snap({x: 0.303, z: 0.542}, null)));

// ── paint: one colour, or a ramp along the stroke's own length ───────────────────────────────────────────────
const rampStroke = {type: 'mxt:polyline', points: [{x: 0, z: 0}, {x: 1, z: 0}],
    gradient: [{at: 0, color: '#ff0000'}, {at: 1, color: '#0000ff'}]};
check('a ramp is read along the stroke',
    sandbox.colourAt(rampStroke, 0) === '#ff0000' && sandbox.colourAt(rampStroke, 1) === '#0000ff'
    && sandbox.colourAt(rampStroke, 0.5) === '#800080' && sandbox.colourAt(rampStroke, 0.25) === '#bf0040',
    sandbox.colourAt(rampStroke, 0.25));
check('outside the outermost stops the colour is held',
    sandbox.colourAt({gradient: [{at: 0.25, color: '#ff0000'}, {at: 0.75, color: '#0000ff'}]}, 0) === '#ff0000'
    && sandbox.colourAt({gradient: [{at: 0.25, color: '#ff0000'}, {at: 0.75, color: '#0000ff'}]}, 1) === '#0000ff');
check('a stroke is solid, ramped or has no paint of its own',
    sandbox.paintKind({color: '#123456'}) === 'solid' && sandbox.paintKind(rampStroke) === 'gradient'
    && sandbox.paintKind({}) === 'default');
check('the writer emits the paint the reader takes back',
    sandbox.paintOf(rampStroke) === ', "color": [{"at": 0, "color": "#ff0000"}, {"at": 1, "color": "#0000ff"}]'
    && sandbox.paintOf({color: '#123456'}) === ', "color": "#123456"'
    // Paint is required, so even a stroke that carries none is written in the colour a fresh one gets.
    && sandbox.paintOf({}) === ', "color": "#80c0ff"',
    sandbox.paintOf(rampStroke));
const painted = sandbox.readStroke({type: 'mxt:circle', radius: 0.5, center: [0.2, -0.1],
    color: [{at: 1, color: '#0000FF'}, {at: 0, color: '#f00'}]});
check('the reader takes a ramp back, sorts it and normalises the colours',
    typeof painted === 'object' && painted.center.x === 0.2 && painted.center.z === -0.1
    && painted.gradient.length === 2 && painted.gradient[0].color === '#ff0000'
    && painted.gradient[1].color === '#0000ff' && painted.gradient[0].at === 0 && painted.gradient[1].at === 1,
    JSON.stringify(painted));
// One field, so a colour and a ramp cannot both be written: a list of stops is a ramp and anything else is a colour.
check('a ramp is the paint field written as stops, and needs two of them in order',
    typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: [{at: 0, color: '#fff'}]}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1,
        color: [{at: 2, color: '#fff'}, {at: 1, color: '#000'}]}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: 'red'}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: 7}) === 'string');

// Painting the selected stroke runs the whole path: selection, the ramp strip, the writer and the swatch.
sandbox.select(1);
check('a list row selects its stroke', document.getElementById('selected').textContent.includes('#2 polyline'),
    document.getElementById('selected').textContent);
document.getElementById('paint').value = '#ff0000';
sandbox.applyPaint();
const paintedJson = JSON.parse(toJson());
check('painting the selected stroke reaches the JSON',
    paintedJson.strokes[1].color === '#ff0000' && paintedJson.strokes[1].gradient === undefined
    && paintedJson.strokes[0].color === '#80c0ff', JSON.stringify(paintedJson));
check('the paint controls follow the selection',
    document.getElementById('stopadd').disabled === false
    && document.getElementById('stopdrop').disabled === true, 'stops');

// Turning that stroke into a ramp runs the other half of the strip: seed two stops, add one in the middle.
sandbox.addStop();
const ramped = JSON.parse(toJson()).strokes;
check('adding a stop turns the stroke into a ramp of three',
    Array.isArray(ramped[1].color) && ramped[1].color.length === 3
    && JSON.stringify(ramped[1].color[0]) === '{"at":0,"color":"#ff0000"}'
    && JSON.stringify(ramped[1].color[1]) === '{"at":0.5,"color":"#ff4040"}'
    && JSON.stringify(ramped[1].color[2]) === '{"at":1,"color":"#ff8080"}',
    JSON.stringify(ramped[1].color));
check('a ramp can lose a stop but not its ends',
    document.getElementById('stopdrop').disabled === false
    && (sandbox.dropStop(), JSON.parse(toJson()).strokes[1].color.length === 2),
    document.getElementById('stopdrop').disabled);
check('a ramp reads back as the figure it was',
    typeof sandbox.readStroke(JSON.parse(toJson()).strokes[1]) === 'object');

// ── circles are free: the centre may sit anywhere, and the start angle anywhere round ────────────────────────
const offCentre = sandbox.readStroke({type: 'mxt:circle', radius: 0.4, center: [0.25, -0.15], color: '#abcdef'});
check('a circle keeps a centre away from the controller',
    typeof offCentre === 'object' && offCentre.center.x === 0.25 && offCentre.center.z === -0.15
    && sandbox.startOf(offCentre).x === 0.65 && sandbox.startOf(offCentre).z === -0.15,
    JSON.stringify(offCentre));
check('an off-centre circle is written with its centre and read back the same',
    sandbox.strokeJson(offCentre) === '{"type": "mxt:circle", "radius": 0.4, "center": [0.25, -0.15], "color": "#abcdef"}'
    && sandbox.readStroke(JSON.parse(sandbox.strokeJson(offCentre))).center.z === -0.15,
    sandbox.strokeJson(offCentre));

// ── the window: which part of the draw-in a stroke belongs to ────────────────────────────────────────────────
const windowed = sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#ffffff', start: 0.5, end: 1});
check('a stroke reads its own window back',
    typeof windowed === 'object' && windowed.start === 0.5 && windowed.end === 1
    && sandbox.strokeJson(windowed) === '{"type": "mxt:circle", "radius": 1, "color": "#ffffff", "start": 0.5}',
    sandbox.strokeJson(windowed));
check('a window that never opens is refused, and both ends live inside the progress',
    typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', start: 0.5, end: 0.5}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', start: 0.8, end: 0.2}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', start: -0.5}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', end: 1.5}) === 'string');
check('a circle start_angle is read, written and refused outside one turn either way',
    sandbox.strokeJson(sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', start_angle: 90}))
        === '{"type": "mxt:circle", "radius": 1, "start_angle": 90, "color": "#ffffff"}'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', start_angle: 361}) === 'string');

// A window maps the figure's progress onto the stroke, so a stroke asking for the second half waits for it. Its own
// spacing is what its points are counted at, so the same stroke at a spacing of 1 is the eight-block line's nine points.
const waiting = sandbox.readStroke({type: 'mxt:polyline', points: [[0, 0], [1, 0]], color: '#ffffff',
    start: 0.5, spacing: 1});
progressBox.value = '0.25';
const early = sandbox.sampleStroke(waiting, 8);
check('a stroke is untouched until its window opens', early.length === 0, early.length);
progressBox.value = '0.75';
const half = sandbox.sampleStroke(waiting, 8);
check('half way through a window is half the stroke',
    half.length === 5 && near(half[half.length - 1], {x: 0.5, z: 0}, 1e-9),
    half.length + ' ' + JSON.stringify(half[half.length - 1] || null));
progressBox.value = '1';
check('a stroke is whole once its window has passed',
    sandbox.sampleStroke(waiting, 8).length === 9, sandbox.sampleStroke(waiting, 8).length);

// The rim handle is the circle's start, so dragging it turns the circle; with the divisions on it lands on one.
check('an angle reads in degrees past due east',
    sandbox.degrees(0) === 0 && sandbox.degrees(Math.PI / 2) === 90 && sandbox.degrees(-Math.PI / 2) === 270
    && sandbox.degrees(Math.PI * 4) === 0);
const turned = sandbox.snapAngle({x: 0.31, z: 0.95}, {x: 0, z: 0});
check('dragging the rim handle snaps to the divisions', turned === 72, turned);
const turnedCircle = sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#ffffff', start_angle: 90,
    spacing: 1});
check('a turned circle starts where it says, and samples from there',
    near(sandbox.startOf(turnedCircle), {x: 0, z: 1}, 1e-9)
    && near(sandbox.sampleStroke(turnedCircle, 8)[0], {x: 0, z: 1}, 1e-9),
    JSON.stringify(sandbox.startOf(turnedCircle)));

// The two inputs are the only way to set the window, so they have to keep it opening: the one being typed in wins.
sandbox.select(0);
const startBox = document.getElementById('start'), endBox = document.getElementById('end');
const turn = (box, value) => {
    box.value = value;
    [...(box.handlers.input || []), ...(box.handlers.change || [])].forEach(handler => handler());
};
turn(startBox, '2');
check('a start past the end pushes the end along instead of crossing it',
    Number(startBox.value) < Number(endBox.value) && JSON.parse(toJson()).strokes[0].start === 0.99
    && JSON.parse(toJson()).strokes[0].end === undefined, startBox.value + ' ' + endBox.value);
turn(endBox, '0');
check('an end before the start does the same the other way',
    Number(endBox.value) > Number(startBox.value)
    && JSON.parse(toJson()).strokes[0].start === 0.99, startBox.value + ' ' + endBox.value);
turn(startBox, '0');
turn(endBox, '1');
sandbox.select(1);
check('the angle box only lights up for a circle',
    document.getElementById('startangle').disabled === true
    && (sandbox.select(0), document.getElementById('startangle').disabled === false),
    'angle box');

// ── the animation: the game's draw-in, played on this page ───────────────────────────────────────────────────
// The default speed is the game's own, `duration_ticks` ticks at 20 a second; the slider is a preview knob only.
const playButton = document.getElementById('play'), speedBox = document.getElementById('speed');
const speedLabel = document.getElementById('speedlabel'), clockBox = document.getElementById('clock');
const loopBox = document.getElementById('loop');
check('the animation opens at the game\'s own speed',
    sandbox.speedFactor() === 1 && sandbox.playSeconds() === 3,
    sandbox.speedFactor() + 'x ' + sandbox.playSeconds() + 's');
turn(speedBox, '7');
check('the speed slider is read by the ramp and shown next to it',
    sandbox.speedFactor() === 4 && sandbox.playSeconds() === 0.75 && speedLabel.textContent === '4×',
    sandbox.speedFactor() + ' ' + sandbox.playSeconds() + ' ' + speedLabel.textContent);
turn(speedBox, '3');

// The loop is driven by hand: the stub's requestAnimationFrame never calls back, so each frame is a timestamp here.
playButton.handlers.click[0]();
sandbox.stepPlaying(1000);
sandbox.stepPlaying(2500);
check('half the draw-in is half the progress and half the ticks',
    close(Number(progressBox.value), 0.5, 1e-9) && clockBox.textContent === '30 / 60 ticks',
    progressBox.value + ' ' + clockBox.textContent);
sandbox.stepPlaying(4000);
check('a finished draw-in is held before the loop starts over',
    Number(progressBox.value) === 1 && playButton.textContent === 'Stop', progressBox.value);
sandbox.stepPlaying(4200);
check('the hold lasts rather than restarting on the next frame',
    Number(progressBox.value) === 1, progressBox.value);
sandbox.stepPlaying(4500);
check('the loop starts over from nothing', Number(progressBox.value) === 0, progressBox.value);
// With the loop off, the same ramp stops on the finished figure instead of repeating.
loopBox.checked = false;
sandbox.stepPlaying(9000);
check('without the loop the animation stops at the end',
    Number(progressBox.value) === 1 && playButton.textContent === 'Play', progressBox.value);

// Changing the speed while it runs re-anchors the ramp, so the picture stays where it is instead of jumping.
playButton.handlers.click[0]();
progressBox.value = '0.25';
turn(speedBox, '7');
sandbox.stepPlaying(Date.now());
check('changing the speed keeps the picture where it is', close(Number(progressBox.value), 0.25, 0.05),
    progressBox.value);
check('nothing about the preview reaches the file',
    !toJson().includes('speed') && !toJson().includes('loop'));
sandbox.stopPlaying();

// ── the line layer grows the way the dust does ───────────────────────────────────────────────────────────────
// A figure of the check's own, one unique colour per stroke, so the recorder's stream splits by style: a ring (a
// stroke swept by angle) and an L (a stroke walked by arc length). Loading it also clears the selection, which is what
// keeps the lines in their own colours rather than the picked one.
const jsonBox = document.getElementById('json'), loadButton = document.getElementById('load');
const opening = toJson();
const load = figure => {
    jsonBox.value = JSON.stringify(figure);
    loadButton.handlers.click[0]();
};
const PROBE = {strokes: [
    {type: 'mxt:circle', radius: 1, color: '#abcdef'},
    {type: 'mxt:polyline', points: [[0, 0], [1, 0], [1, 1]], color: '#123456'}
]};
load(PROBE);

const stroked = style => {
    OPS.length = 0;
    sandbox.redraw();
    const out = [];
    let current = null, keep = false;
    for (const op of OPS) {
        if (op[0] === 'strokeStyle') {
            current = op[1];
            keep = true;
        } else if (op[0] === 'fillStyle') keep = false;      // the dust layer paints, it does not stroke
        else if (keep && current === style) out.push(op);
    }
    return out;
};
const sweeps = ops => ops.filter(op => op[0] === 'arc').map(op => op[5] - op[4]);
const tails = ops => ops.filter(op => op[0] === 'lineTo');
const at = point => sandbox.toBoard(point);
// The board's own ring guide is one full-turn arc at the figure's scale, so every extra one is a ghost: the whole
// stroke staying on while the walk has only reached part of the way.
const bigArcs = () => OPS.filter(op => op[0] === 'arc' && op[3] > 100).length;

progressBox.value = '0.5';
let ringOps = stroked('#abcdef'), elbowOps = stroked('#123456');
check('half way in, the ring is drawn half a turn',
    close(sweeps(ringOps)[0], Math.PI, 1e-9), JSON.stringify(sweeps(ringOps)));
check('the part that is not drawn yet stays on as a ghost', bigArcs() === 3, String(bigArcs()));
check('half way in, the walked stroke ends at its own halfway point',
    tails(elbowOps).length === 1 && close(tails(elbowOps).pop()[1], at({x: 1, z: 0}).x, 1e-9),
    JSON.stringify(tails(elbowOps)));
progressBox.value = '0.75';
ringOps = stroked('#abcdef');
elbowOps = stroked('#123456');
check('three quarters in, both walks are three quarters long',
    close(sweeps(ringOps)[0], Math.PI * 1.5, 1e-9) && close(tails(elbowOps).pop()[1], at({x: 1, z: 0.5}).x, 1e-9)
    && close(tails(elbowOps).pop()[2], at({x: 1, z: 0.5}).y, 1e-9),
    JSON.stringify([sweeps(ringOps), tails(elbowOps).pop()]));

// A window moves a line's turn the way it moves the dust's, and a finished line leaves no ghost behind.
load({strokes: [{type: 'mxt:polyline', points: [[0, 0], [1, 0], [1, 1]], color: '#123456', start: 0.5}]});
progressBox.value = '0.25';
check('a line waits for its window before it is drawn', tails(stroked('#123456')).length === 0);
progressBox.value = '1';
const whole = tails(stroked('#123456'));
check('a finished line is whole and leaves no ghost',
    whole.length === 2 && close(whole.pop()[2], at({x: 1, z: 1}).y, 1e-9) && bigArcs() === 1,
    whole.length + ' lineTos, ' + bigArcs() + ' big arcs');

load(JSON.parse(opening));
loopBox.checked = true;
turn(speedBox, '3');
progressBox.value = '1';
sandbox.refresh();

// ── spacing and size belong to the stroke ────────────────────────────────────────────────────────────────────
// The two inputs are the only way to set them, and both are left out of the file while they are the standard ones.
const spacingBox = document.getElementById('spacing'), sizeBox = document.getElementById('size');
const openingRows = JSON.parse(opening).strokes;
check('a stroke that says nothing about its layout writes neither field',
    openingRows.every(row => row.spacing === undefined && row.size === undefined));
sandbox.select(1);
check('the layout inputs follow the selection',
    spacingBox.disabled === false && sizeBox.disabled === false
    && Number(spacingBox.value) === 0.33 && Number(sizeBox.value) === 2,
    spacingBox.value + ' ' + sizeBox.value);
turn(spacingBox, '1');
turn(sizeBox, '1.5');
const laid = JSON.parse(toJson()).strokes;
check('writing a stroke\'s own layout reaches the JSON and nobody else\'s',
    laid[1].spacing === 1 && laid[1].size === 1.5
    && laid[0].spacing === undefined && laid[0].size === undefined, JSON.stringify(laid));
check('the readout counts a stroke at its own spacing',
    sandbox.sampledCount(sandbox.readStroke(laid[1]), 8) === 78
    && sandbox.sampledCount(sandbox.readStroke(laid[0]), 8) === 154,
    sandbox.sampledCount(sandbox.readStroke(laid[1]), 8) + '');
check('the layout is read back and bounded like the module\'s used to be',
    sandbox.readStroke(laid[1]).spacing === 1 && sandbox.readStroke(laid[1]).size === 1.5
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', spacing: 0}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', spacing: 65}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', size: 0.001}) === 'string'
    && typeof sandbox.readStroke({type: 'mxt:circle', radius: 1, color: '#fff', size: 5}) === 'string');
turn(spacingBox, '0.33');
turn(sizeBox, '2');
check('the standard layout is left out again once it is back',
    JSON.parse(toJson()).strokes[1].spacing === undefined && JSON.parse(toJson()).strokes[1].size === undefined);

// The page has to have initialised without throwing, and the readouts to have been written.
const drawn = JSON.parse(toJson()).strokes.map(row => sandbox.readStroke(row));
const expected = drawn.reduce((sum, stroke) => sum + sandbox.sampledCount(stroke, 8), 0);
check('the page reported its opening figure',
    document.getElementById('hint_points').textContent === expected + ' points',
    document.getElementById('hint_points').textContent + ' vs ' + expected);
check('the stats line is filled in', document.getElementById('stats').textContent.includes('strokes: 2'),
    document.getElementById('stats').textContent);

console.log(failures.length ? failures.join('\n') : 'all formation tool checks hold');
process.exit(failures.length ? 1 : 0);
