'use strict';

/*
 * The block state providers of Minecraft 1.21.1 for the mock pages: the default values, the JSON, the summary line and the editor.
 * The editor draws with Im, so a page loads imgui.js first.
 */
const Providers = (() => {
  const TYPES = [
    ['simple', 'Simple state', 'minecraft:simple_state_provider'],
    ['weighted', 'Weighted', 'minecraft:weighted_state_provider'],
    ['noise_threshold', 'Noise threshold', 'minecraft:noise_threshold_provider'],
    ['noise', 'Noise', 'minecraft:noise_provider'],
    ['dual_noise', 'Dual noise', 'minecraft:dual_noise_provider'],
    ['rotated', 'Rotated block', 'minecraft:rotated_block_provider'],
    ['randomized_int', 'Randomized int', 'minecraft:randomized_int_state_provider'],
  ];
  const SCALES = [0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1];

  function typeName(type) { return TYPES.find(t => t[0] === type)[1]; }
  function typeId(type) { return TYPES.find(t => t[0] === type)[2]; }
  function round(x) { return Math.round(x * 1000) / 1000; }

  function noiseState(seed, octave, amplitudes, scale) {
    return { seedText: String(seed), seed, octave, ampText: amplitudes, amps: amplitudes.split(',').map(Number), scale };
  }

  function newProvider(type) {
    return {
      type,
      simple: { state: 'stone_bricks' },
      weighted: { entries: [{ state: 'stone_bricks', weight: 3 }, { state: 'mossy_stone_bricks', weight: 1 }, { state: 'andesite', weight: 1 }] },
      noise: Object.assign(noiseState(2345, -3, '1.0, 1.0', 4), { states: ['stone', 'andesite', 'tuff'] }),
      noise_threshold: Object.assign(noiseState(2345, -3, '1.0', 3), {
        threshold: 4, highChance: 7, defaultState: 'stone_bricks', low: ['mossy_stone_bricks'], high: ['cobblestone', 'gravel'],
      }),
      dual_noise: Object.assign(noiseState(2345, -3, '1.0', 5), {
        states: ['stone_bricks', 'mossy_stone_bricks', 'cobblestone', 'andesite'], varietyMin: 1, varietyMax: 3,
        slow: noiseState(0, -10, '1.0', 9),
      }),
      rotated: { block: 'oak_log' },
      randomized_int: { source: 'sea_pickle', property: 'pickles', mode: 'uniform', min: 1, max: 4, constant: 2 },
    };
  }

  // ------------------------------------------------------------------ JSON

  function noiseJson(n) { return { firstOctave: n.octave, amplitudes: n.amps }; }

  function providerJson(p) {
    const type = typeId(p.type);
    switch (p.type) {
      case 'simple': return { type, state: Im.blockJson(p.simple.state) };
      case 'weighted': return { type, entries: p.weighted.entries.map(e => ({ data: Im.blockJson(e.state), weight: e.weight })) };
      case 'noise': {
        const n = p.noise;
        return { type, seed: n.seed, noise: noiseJson(n), scale: SCALES[n.scale], states: n.states.map(Im.blockJson) };
      }
      case 'noise_threshold': {
        const n = p.noise_threshold;
        return {
          type, seed: n.seed, noise: noiseJson(n), scale: SCALES[n.scale],
          threshold: round(-1 + 0.05 * n.threshold), high_chance: round(n.highChance * 0.05),
          default_state: Im.blockJson(n.defaultState), low_states: n.low.map(Im.blockJson), high_states: n.high.map(Im.blockJson),
        };
      }
      case 'dual_noise': {
        const n = p.dual_noise;
        return {
          type, seed: n.seed, noise: noiseJson(n), scale: SCALES[n.scale], states: n.states.map(Im.blockJson),
          variety: [n.varietyMin, n.varietyMax], slow_noise: noiseJson(n.slow), slow_scale: SCALES[n.slow.scale],
        };
      }
      case 'rotated': return { type, state: Im.blockJson(p.rotated.block) };
      case 'randomized_int': {
        const r = p.randomized_int;
        return {
          type,
          source: { type: 'minecraft:simple_state_provider', state: Im.blockJson(r.source) },
          property: r.property,
          values: r.mode === 'constant' ? r.constant : { type: 'minecraft:uniform', min_inclusive: r.min, max_inclusive: r.max },
        };
      }
    }
    return null;
  }

  // ------------------------------------------------------------------ summaries

  function summaryOf(p) {
    switch (p.type) {
      case 'simple': return p.simple.state;
      case 'weighted': return p.weighted.entries.map(e => e.weight + ' ' + e.state).join(', ');
      case 'noise': return p.noise.states.join(' / ');
      case 'noise_threshold': {
        const n = p.noise_threshold;
        return n.defaultState + ' | ' + n.low.length + ' low | ' + n.high.length + ' high';
      }
      case 'dual_noise': return p.dual_noise.states.join(' / ');
      case 'rotated': return p.rotated.block;
      case 'randomized_int': {
        const r = p.randomized_int;
        return r.source + ' ' + r.property + ' ' + (r.mode === 'constant' ? r.constant : r.min + '-' + r.max);
      }
    }
    return '';
  }

  // ------------------------------------------------------------------ editor widgets

  function intProps(blockId) {
    const parsed = Im.parseBlock(blockId);
    if (!parsed) return [];
    return Object.entries(parsed.block.props).filter(([, values]) => values.every(v => /^\d+$/.test(v))).map(([name, values]) => ({
      name, min: Math.min(...values.map(Number)), max: Math.max(...values.map(Number)),
    }));
  }

  /** A text field for a number list or an integer. Bad text turns it red. The value keeps the last good parse. */
  function numberField(id, hint, textValue, parse, onText, onValue) {
    const value = parse(textValue);
    Im.inputText(id, { value: textValue, hint, invalid: value === null }, t => {
      onText(t);
      const parsed = parse(t);
      if (parsed !== null) onValue(parsed);
    });
  }
  function parseSeed(t) { return /^\s*-?\d+\s*$/.test(t) ? Number(t) : null; }
  function parseAmps(t) {
    const parts = t.split(',').map(s => s.trim());
    if (!parts.length || parts.some(s => !/^-?\d+(\.\d+)?$/.test(s))) return null;
    return parts.map(Number);
  }

  function noiseWidgets(n, prefix, title) {
    Im.slider(prefix + 'octave', { value: n.octave, min: -10, max: 0, label: title + ' Octave: ' + n.octave }, x => { n.octave = x; });
    Im.labelGrid(() => {
      Im.labelRow('Amplitudes', () => numberField(prefix + 'amps', '1.0, 1.0', n.ampText, parseAmps, t => { n.ampText = t; }, v => { n.amps = v; }));
    });
    Im.slider(prefix + 'scale', { value: n.scale, min: 0, max: SCALES.length - 1, label: title + ' Scale: ' + SCALES[n.scale] },
      x => { n.scale = x; });
  }

  function seedRow(n) {
    Im.labelGrid(() => {
      Im.labelRow('Seed', () => numberField('seed', '0', n.seedText, parseSeed, t => { n.seedText = t; }, v => { n.seed = v; }));
    });
  }

  /** A list of block states: one field per row with a minus button, then an add button. */
  function stateList(id, title, list, min) {
    Im.text(title);
    Im.withId(id, () => {
      list.forEach((state, i) => Im.withId(i, () => Im.line(() => {
        Im.fill(() => Im.blockField('state', { value: state, hint: 'Block state' }, b => { list[i] = b; }));
        Im.button('remove', '-', () => { list.splice(i, 1); }, { square: true, off: list.length <= min, tip: 'Remove this state' });
      })));
      Im.button('add', '+ State', () => { list.push(list.length ? list[list.length - 1] : 'stone'); }, { small: true });
    });
  }

  function editor(p) {
    Im.combo('type', {
      preview: 'Type: ' + typeName(p.type),
      value: p.type,
      items: TYPES.map(([value, text, id]) => ({ value, label: text, tip: id })),
      tip: typeId(p.type),
    }, x => { p.type = x; });
    switch (p.type) {
      case 'simple':
        Im.labelGrid(() => Im.labelRow('State', () => Im.blockField('state', { value: p.simple.state, hint: 'Block state' }, b => { p.simple.state = b; })));
        break;
      case 'weighted': {
        const entries = p.weighted.entries;
        const total = entries.reduce((a, e) => a + e.weight, 0);
        Im.text('Entries');
        entries.forEach((entry, i) => Im.withId(i, () => Im.line(() => {
          Im.fill(() => Im.blockField('state', { value: entry.state, hint: 'Block state' }, b => { entry.state = b; }));
          Im.dragInt('weight', {
            value: entry.weight, min: 1, max: 999, label: String(entry.weight), width: 48,
            tip: 'Weight. Drag to change. ' + Math.round(100 * entry.weight / total) + '% of the picks.',
          }, x => { entry.weight = x; });
          Im.button('remove', '-', () => { entries.splice(i, 1); }, { square: true, off: entries.length < 2, tip: 'Remove this entry' });
        })));
        Im.button('add', '+ Entry', () => { entries.push({ state: entries[entries.length - 1].state, weight: 1 }); }, { small: true });
        break;
      }
      case 'noise': {
        const n = p.noise;
        seedRow(n);
        noiseWidgets(n, '', 'Noise');
        stateList('states', 'States', n.states, 1);
        break;
      }
      case 'noise_threshold': {
        const n = p.noise_threshold;
        seedRow(n);
        noiseWidgets(n, '', 'Noise');
        Im.slider('threshold', { value: n.threshold, min: 0, max: 40, label: 'Threshold: ' + round(-1 + 0.05 * n.threshold).toFixed(2) },
          x => { n.threshold = x; });
        Im.slider('high_chance', { value: n.highChance, min: 0, max: 20, label: 'High Chance: ' + n.highChance * 5 + '%' },
          x => { n.highChance = x; });
        Im.labelGrid(() => Im.labelRow('Default', () => Im.blockField('default', { value: n.defaultState, hint: 'Block state' }, b => { n.defaultState = b; })));
        stateList('low', 'Low states', n.low, 1);
        stateList('high', 'High states', n.high, 1);
        break;
      }
      case 'dual_noise': {
        const n = p.dual_noise;
        seedRow(n);
        noiseWidgets(n, '', 'Noise');
        Im.slider('variety_min', { value: n.varietyMin, min: 1, max: 64, label: 'Variety Min: ' + n.varietyMin }, x => {
          n.varietyMin = x;
          if (n.varietyMax < x) n.varietyMax = x;
        });
        Im.slider('variety_max', { value: n.varietyMax, min: 1, max: 64, label: 'Variety Max: ' + n.varietyMax }, x => {
          n.varietyMax = x;
          if (n.varietyMin > x) n.varietyMin = x;
        });
        noiseWidgets(n.slow, 'slow_', 'Slow');
        stateList('states', 'States', n.states, 1);
        break;
      }
      case 'rotated':
        Im.labelGrid(() => Im.labelRow('Block', () => Im.blockField('block', {
          value: p.rotated.block, hint: 'Block with axis', filter: b => !!b.props.axis,
          tip: 'Only a block with the property axis. The placement turns the axis.',
        }, b => { p.rotated.block = b; })));
        break;
      case 'randomized_int': {
        const r = p.randomized_int;
        const props = intProps(r.source);
        Im.labelGrid(() => Im.labelRow('Source', () => Im.blockField('source', {
          value: r.source, hint: 'Block state', filter: b => intProps(b.id).length > 0,
          tip: 'The source is a full provider in the codec. The mock shows a simple state.',
        }, b => {
          r.source = b;
          const next = intProps(b);
          if (!next.some(q => q.name === r.property)) r.property = next.length ? next[0].name : '';
        })));
        const prop = props.find(q => q.name === r.property) || props[0];
        Im.combo('property', {
          preview: 'Property: ' + (prop ? prop.name : 'none'),
          value: r.property,
          items: props.map(q => ({ value: q.name, label: q.name + ' (' + q.min + '-' + q.max + ')' })),
        }, x => { r.property = x; });
        Im.combo('mode', {
          preview: 'Values: ' + (r.mode === 'constant' ? 'Constant' : 'Uniform'),
          value: r.mode,
          items: [{ value: 'uniform', label: 'Uniform' }, { value: 'constant', label: 'Constant' }],
        }, x => { r.mode = x; });
        if (prop) {
          const clamp = x => Math.min(prop.max, Math.max(prop.min, x));
          r.min = clamp(r.min);
          r.max = clamp(r.max);
          r.constant = clamp(r.constant);
          if (r.mode === 'constant') {
            Im.slider('constant', { value: r.constant, min: prop.min, max: prop.max, label: 'Value: ' + r.constant }, x => { r.constant = x; });
          } else {
            Im.slider('min', { value: r.min, min: prop.min, max: prop.max, label: 'Min: ' + r.min }, x => { r.min = x; if (r.max < x) r.max = x; });
            Im.slider('max', { value: r.max, min: prop.min, max: prop.max, label: 'Max: ' + r.max }, x => { r.max = x; if (r.min > x) r.min = x; });
          }
        }
        break;
      }
    }
  }

  return { TYPES, typeName, typeId, newProvider, providerJson, summaryOf, editor };
})();
