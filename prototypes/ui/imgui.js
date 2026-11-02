'use strict';

/*
 * A small immediate mode imitation of the ImGui widgets of the preview panel.
 * A page gives Im.mount one draw function. Each change runs the draw function again and rebuilds the DOM.
 * Widget state that ImGui keeps by id (open headers, field text, the open popup) lives in Im.state by key.
 */
const Im = (() => {
  const state = { open: {}, popup: null, focus: null, drag: null, fields: {} };
  let mountRoot = null;
  let drawFn = null;
  let afterFn = null;
  let cur = null;
  const stack = [];
  const ids = [];
  let deferred = [];
  let rebuilding = false;
  let layer = null;
  let tooltip = null;

  // ---------------------------------------------------------------- blocks

  /** The mock block list. A property maps to its values; the first value is the default. */
  const BLOCKS = [
    ['stone', '#7d7d7d'], ['cobblestone', '#6e6e6e'], ['stone_bricks', '#7a7978'], ['mossy_stone_bricks', '#6c7a59'],
    ['smooth_stone', '#a3a3a3'], ['andesite', '#888a8a'], ['polished_andesite', '#848786'], ['diorite', '#bdbcbd'],
    ['granite', '#956755'], ['tuff', '#6c6d66'], ['deepslate', '#505053', { axis: ['y', 'x', 'z'] }],
    ['deepslate_bricks', '#474749'], ['gravel', '#847f7e'], ['dirt', '#866043'], ['dirt_path', '#94793f'],
    ['grass_block', '#5f9f35', { snowy: ['false', 'true'] }], ['oak_planks', '#a2834f'],
    ['oak_log', '#6b5130', { axis: ['y', 'x', 'z'] }], ['spruce_log', '#3b2714', { axis: ['y', 'x', 'z'] }],
    ['basalt', '#4b4a4e', { axis: ['y', 'x', 'z'] }], ['quartz_pillar', '#ebe5de', { axis: ['y', 'x', 'z'] }],
    ['iron_bars', '#8b8d8c', { east: ['false', 'true'], north: ['false', 'true'], south: ['false', 'true'], waterlogged: ['false', 'true'], west: ['false', 'true'] }],
    ['chain', '#3d4250', { axis: ['y', 'x', 'z'], waterlogged: ['false', 'true'] }],
    ['oxidized_copper', '#52a283'], ['end_stone', '#dbdea0'], ['end_stone_bricks', '#d9dc9e'], ['purpur_block', '#a77ba7'],
    ['snow', '#f4fbfb', { layers: ['1', '2', '3', '4', '5', '6', '7', '8'] }],
    ['sea_pickle', '#6a7a3a', { pickles: ['1', '2', '3', '4'], waterlogged: ['true', 'false'] }],
    ['wheat', '#c8b25a', { age: ['0', '1', '2', '3', '4', '5', '6', '7'] }],
  ].map(([id, color, props]) => ({ id, color, props: props || {}, name: id.split('_').map(w => w[0].toUpperCase() + w.slice(1)).join(' ') }));
  const BLOCK_BY_ID = Object.fromEntries(BLOCKS.map(b => [b.id, b]));

  /** The block and properties of a block state text, or null when the text is no state of the list. */
  function parseBlock(text) {
    const m = /^\s*(?:minecraft:)?([a-z0-9_]+)\s*(?:\[([^\]]*)\])?\s*$/.exec(text);
    if (!m) return null;
    const block = BLOCK_BY_ID[m[1]];
    if (!block) return null;
    const props = {};
    if (m[2] !== undefined && m[2].trim() !== '') {
      for (const pair of m[2].split(',')) {
        const kv = pair.split('=').map(s => s.trim());
        if (kv.length !== 2 || !block.props[kv[0]] || !block.props[kv[0]].includes(kv[1])) return null;
        props[kv[0]] = kv[1];
      }
    }
    return { block, props };
  }

  /** The canonical text: the id, then the properties that differ from the default. */
  function blockText(parsed) {
    const extra = Object.entries(parsed.props).filter(([k, v]) => parsed.block.props[k][0] !== v);
    return parsed.block.id + (extra.length ? '[' + extra.map(([k, v]) => k + '=' + v).join(',') + ']' : '');
  }

  /** The block state JSON of the vanilla codec. It writes every property. */
  function blockJson(text) {
    const parsed = parseBlock(text);
    if (!parsed) return { Name: 'minecraft:air' };
    const out = { Name: 'minecraft:' + parsed.block.id };
    const names = Object.keys(parsed.block.props).sort();
    if (names.length) {
      out.Properties = {};
      for (const name of names) out.Properties[name] = parsed.props[name] || parsed.block.props[name][0];
    }
    return out;
  }

  function blockFromJson(json) {
    if (!json || typeof json.Name !== 'string') return null;
    const props = json.Properties ? Object.entries(json.Properties).map(([k, v]) => k + '=' + v).join(',') : '';
    const parsed = parseBlock(json.Name + (props ? '[' + props + ']' : ''));
    return parsed ? blockText(parsed) : null;
  }

  function searchBlocks(text, filter) {
    const query = text.split('[')[0].trim().toLowerCase().replace(/^minecraft:/, '');
    if (!query) return [];
    return BLOCKS.filter(b => (!filter || filter(b)) && (b.id.includes(query) || b.name.toLowerCase().includes(query))).slice(0, 8);
  }

  // ---------------------------------------------------------------- build helpers

  function h(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }
  function add(e) { cur.appendChild(e); return e; }
  function within(e, fn) {
    stack.push(cur);
    cur = e;
    try { fn(); } finally { cur = stack.pop(); }
    return e;
  }
  function key(k) { return ids.concat([k]).join('/'); }
  function withId(k, fn) { ids.push(String(k)); try { fn(); } finally { ids.pop(); } }
  function tip(e, text) { if (text) e.dataset.tip = text; return e; }
  function find(k) { return document.querySelector('[data-key="' + CSS.escape(k) + '"]'); }
  function label(e, text) { e.dataset.label = text; return e; }

  // ---------------------------------------------------------------- render

  function render() {
    if (!drawFn) return;
    rebuilding = true;
    const active = document.activeElement;
    const focusKey = active && active.dataset ? active.dataset.key : null;
    const selection = focusKey && typeof active.selectionStart === 'number' ? [active.selectionStart, active.selectionEnd] : null;
    const sx = window.scrollX;
    const sy = window.scrollY;
    const frag = document.createDocumentFragment();
    deferred = [];
    ids.length = 0;
    cur = frag;
    drawFn();
    mountRoot.replaceChildren(frag);
    layer.replaceChildren();
    // The page report can move the content, so it runs before the popups take their places.
    if (afterFn) afterFn();
    for (let i = 0; i < deferred.length; i++) buildPopup(deferred[i]);
    if (focusKey) {
      const n = find(focusKey);
      if (n) {
        n.focus({ preventScroll: true });
        if (selection && n.setSelectionRange) n.setSelectionRange(selection[0], selection[1]);
      }
    }
    window.scrollTo(sx, sy);
    tooltip.style.display = 'none';
    rebuilding = false;
  }

  function defer(popup) { popup.ids = ids.slice(); deferred.push(popup); }

  function buildPopup(p) {
    const anchor = find(p.anchor);
    if (!anchor) return;
    const el = h('div', 'im-popup' + (p.window ? ' window' : ''));
    el.dataset.popup = p.anchor;
    layer.appendChild(el);
    const saved = ids.slice();
    ids.length = 0;
    ids.push(...p.ids);
    within(el, p.build);
    ids.length = 0;
    ids.push(...saved);
    const r = (p.anchorRect ? p.anchorRect(anchor) : anchor.getBoundingClientRect());
    const width = p.width ? p.width(r) : r.width;
    el.style.width = width + 'px';
    const viewW = document.documentElement.clientWidth;
    const viewH = document.documentElement.clientHeight;
    const left = Math.max(4, Math.min(r.left, viewW - width - 4));
    if (p.fitBelow) {
      const below = viewH - r.bottom - 4;
      const wanted = el.scrollHeight;
      el.style.height = Math.max(p.fitBelow, Math.min(wanted, below)) + 'px';
    }
    el.style.left = (left + window.scrollX) + 'px';
    el.style.top = (r.bottom + window.scrollY) + 'px';
  }

  function closePopup() {
    if (!state.popup) return;
    for (const e of layer.querySelectorAll('.im-popup:not(.window)')) {
      if (e.dataset.popup === state.popup) e.remove();
    }
    const anchor = find(state.popup);
    if (anchor) anchor.classList.remove('active');
    state.popup = null;
  }

  function init() {
    layer = h('div', 'im-layer');
    tooltip = h('div', 'im-tooltip');
    document.body.append(layer, tooltip);
    window.addEventListener('pointerdown', e => {
      if (!state.popup) return;
      const inside = e.target.closest && e.target.closest('.im-popup');
      const owner = e.target.closest && e.target.closest('[data-key]');
      if (inside && inside.dataset.popup === state.popup) return;
      if (owner && owner.dataset.key === state.popup) return;
      closePopup();
    }, true);
    window.addEventListener('pointermove', e => {
      if (state.drag) {
        state.drag.move(e.clientX);
        tooltip.style.display = 'none';
        return;
      }
      const t = e.target.closest && e.target.closest('[data-tip]');
      if (t) {
        tooltip.textContent = t.dataset.tip;
        tooltip.style.display = 'block';
        const w = tooltip.offsetWidth;
        const x = Math.min(e.clientX + 16, document.documentElement.clientWidth - w - 4);
        tooltip.style.left = Math.max(4, x) + 'px';
        tooltip.style.top = (e.clientY + 20) + 'px';
      } else {
        tooltip.style.display = 'none';
      }
    });
    window.addEventListener('pointerup', () => {
      if (state.drag) {
        state.drag = null;
        render();
      }
    });
    window.addEventListener('keydown', e => {
      if (e.key === 'Escape' && state.popup) {
        closePopup();
      }
    });
  }

  // ---------------------------------------------------------------- layout widgets

  function col(fn, cls) { return within(add(h('div', 'im-col' + (cls ? ' ' + cls : ''))), fn); }
  function line(fn) { return within(add(h('div', 'im-line')), fn); }
  function indent(fn, width) {
    const e = add(h('div', 'im-col im-indent'));
    if (width !== undefined) e.style.paddingLeft = width + 'px';
    return within(e, fn);
  }
  function box(fn) { return within(add(h('div', 'im-col im-box')), fn); }
  function disabled(off, fn) { return within(add(h('div', 'im-col' + (off ? ' im-disabled' : ''))), fn); }
  function fill(fn) { return within(add(h('div', 'im-col im-fill')), fn); }
  function spacing() { add(h('div', 'im-spacing')); }
  function separator() { add(h('div', 'im-sep')); }

  function text(str, opts) {
    const o = opts || {};
    const e = add(h('div', 'im-text' + (o.framed ? ' framed' : '') + (o.dim ? ' dim' : '') + (o.wrap ? ' wrap' : ''), str));
    label(e, str);
    return tip(e, o.tip);
  }

  /** A grid of label and field rows. The fields start at one column, like the block fields of the panel. */
  function labelGrid(fn) {
    const g = add(h('div', 'im-labelgrid'));
    g.style.display = 'grid';
    g.style.gridTemplateColumns = 'max-content minmax(0, 1fr)';
    g.style.columnGap = 'var(--im-spacing-x)';
    g.style.rowGap = 'var(--im-spacing-y)';
    return within(g, fn);
  }
  function labelRow(name, fn) {
    text(name, { framed: true });
    within(add(h('div', 'im-col')), fn);
  }
  /** A cell that spans the label column and the field column of a label grid. */
  function span(fn) {
    const e = add(h('div', 'im-col'));
    e.style.gridColumn = '1 / -1';
    return within(e, fn);
  }

  // ---------------------------------------------------------------- headers and tabs

  function isOpen(k, defaultOpen) { return k in state.open ? state.open[k] : !!defaultOpen; }

  function collapsingHeader(id, title, defaultOpen, opts) {
    const k = key(id);
    const open = isOpen(k, defaultOpen);
    const o = opts || {};
    line(() => {
      if (o.radio) add(h('div', 'im-radio'));
      const e = add(h('div', 'im-header'));
      e.dataset.key = k;
      e.append(h('div', 'im-tri' + (open ? '' : ' right')), document.createTextNode(title));
      label(e, title);
      e.addEventListener('click', () => { state.open[k] = !open; render(); });
      if (o.right) {
        const b = h('div', 'im-button small', o.right);
        b.style.marginLeft = 'auto';
        e.append(b);
      }
    });
    return open;
  }

  function treeNode(id, title, defaultOpen, fn) {
    const k = key(id);
    const open = isOpen(k, defaultOpen);
    const e = add(h('div', 'im-tree'));
    e.dataset.key = k;
    e.append(h('div', 'im-tri' + (open ? '' : ' right')), document.createTextNode(title));
    label(e, title);
    e.addEventListener('click', () => { state.open[k] = !open; render(); });
    if (open) withId(id, () => indent(fn));
    return open;
  }

  /** A tab bar. Each tab is {id, label, closable}. Returns nothing; the callbacks change the page state. */
  function tabBar(id, tabs, selected, onSelect, onClose) {
    const bar = add(h('div', 'im-tabbar'));
    for (const t of tabs) {
      const e = h('div', 'im-tab' + (t.id === selected ? ' selected' : ''));
      e.dataset.key = key(id + '/' + t.id);
      label(e, t.label);
      e.append(document.createTextNode(t.label));
      e.addEventListener('click', () => { onSelect(t.id); render(); });
      if (t.closable) {
        const x = h('span', 'x', '×');
        x.addEventListener('click', ev => { ev.stopPropagation(); onClose(t.id); render(); });
        e.append(x);
      }
      bar.append(e);
    }
  }

  // ---------------------------------------------------------------- value widgets

  /** An integer slider over min..max with the text centered. Full width unless opts.width is set. */
  function slider(id, opts, onChange) {
    const k = key(id);
    const { value, min, max } = opts;
    const e = add(h('div', 'im-frame im-slider' + (opts.width ? '' : ' im-full') + (state.drag && state.drag.key === k ? ' active' : '')));
    if (opts.width) e.style.width = opts.width + 'px';
    e.dataset.key = k;
    label(e, opts.label);
    tip(e, opts.tip);
    const g = h('div', 'im-grab');
    g.style.setProperty('--n', String(max - min + 1));
    g.style.setProperty('--t', String(max > min ? (value - min) / (max - min) : 0));
    const t = h('div', 'im-slider-text');
    t.append(h('span', null, opts.label));
    e.append(g, t);
    e.addEventListener('pointerdown', ev => {
      if (ev.button !== 0) return;
      ev.preventDefault();
      if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
      let last = value;
      const move = x => {
        const el = find(k);
        if (!el) return;
        const r = el.getBoundingClientRect();
        const grab = Math.max((r.width - 4) / (max - min + 1), 12);
        const usable = r.width - 4 - grab;
        const t01 = usable > 0 ? Math.min(1, Math.max(0, (x - (r.left + 2 + grab / 2)) / usable)) : 0;
        const v = Math.round(min + t01 * (max - min));
        if (v !== last) {
          last = v;
          onChange(v);
          render();
        }
      };
      state.drag = { key: k, move };
      move(ev.clientX);
      render();
    });
    return e;
  }

  /** A drag field: a horizontal drag changes the value by one per opts.pixels pixels. */
  function dragInt(id, opts, onChange) {
    const k = key(id);
    const e = add(h('div', 'im-frame im-drag' + (state.drag && state.drag.key === k ? ' active' : '')));
    e.style.width = (opts.width || 64) + 'px';
    e.style.flex = 'none';
    e.dataset.key = k;
    label(e, opts.label);
    tip(e, opts.tip);
    const t = h('div', 'im-drag-text');
    t.append(h('span', null, opts.label));
    e.append(t);
    e.addEventListener('pointerdown', ev => {
      if (ev.button !== 0) return;
      ev.preventDefault();
      const startX = ev.clientX;
      const start = opts.value;
      let last = start;
      state.drag = {
        key: k,
        move: x => {
          const v = Math.min(opts.max, Math.max(opts.min, start + Math.round((x - startX) / (opts.pixels || 6))));
          if (v !== last) { last = v; onChange(v); render(); }
        },
      };
      render();
    });
    return e;
  }

  /**
   * A combo. Each item is {value, label, off, tip}; an item with hidden is left out.
   * The popup sits under the frame, with the width of the frame.
   */
  function combo(id, opts, onChange) {
    const k = key(id);
    const open = state.popup === k;
    const e = add(h('div', 'im-frame im-combo' + (opts.width ? '' : ' im-full') + (open ? ' active' : '')));
    if (opts.width) { e.style.width = opts.width + 'px'; e.style.flex = 'none'; }
    e.dataset.key = k;
    label(e, opts.preview);
    tip(e, opts.tip);
    const t = h('div', 'im-combo-text');
    t.append(h('span', null, opts.preview));
    const arrow = h('div', 'im-combo-arrow');
    arrow.append(h('div', 'im-tri'));
    e.append(t, arrow);
    e.addEventListener('pointerdown', ev => {
      if (ev.button !== 0) return;
      ev.preventDefault();
      state.popup = open ? null : k;
      render();
    });
    if (open) {
      defer({
        anchor: k,
        build: () => col(() => {
          for (const item of opts.items) {
            if (item.hidden) continue;
            const s = add(h('div', 'im-selectable' + (item.value === opts.value ? ' selected' : '') + (item.off ? ' off' : ''), item.label));
            tip(s, item.tip);
            if (!item.off) {
              s.addEventListener('click', () => { state.popup = null; onChange(item.value); render(); });
            }
          }
        }),
      });
    }
    return e;
  }

  function checkbox(id, text, value, onChange, opts) {
    const k = key(id);
    const e = add(h('div', 'im-check'));
    e.dataset.key = k;
    label(e, text);
    tip(e, opts && opts.tip);
    const b = h('div', 'im-check-box');
    if (value) {
      b.innerHTML = '<svg width="15" height="15" viewBox="0 0 15 15"><path d="M2 8 L6 12 L13 3" fill="none" stroke="rgb(66,150,250)" stroke-width="2.6"/></svg>';
    }
    e.append(b, h('div', 'im-check-label', text));
    e.addEventListener('click', () => { onChange(!value); render(); });
    return e;
  }

  function button(id, text, onClick, opts) {
    const o = opts || {};
    const k = key(id);
    const e = add(h('div', 'im-button' + (o.small ? ' small' : '') + (o.square ? ' square' : ''), text));
    e.dataset.key = k;
    label(e, text);
    tip(e, o.tip);
    if (o.width) e.style.width = o.width + 'px';
    if (o.off) {
      e.classList.add('im-disabled');
    } else {
      e.addEventListener('click', () => { onClick(); render(); });
    }
    return e;
  }

  /**
   * A text field. opts.invalid gives it the red background.
   * onInput runs on each edit. The page renders after it.
   */
  function inputText(id, opts, onInput) {
    const k = key(id);
    const wrap = add(h('div', 'im-frame' + (opts.width ? '' : ' im-full') + (opts.invalid ? ' invalid' : '')));
    if (opts.width) { wrap.style.width = opts.width + 'px'; wrap.style.flex = 'none'; }
    label(wrap, opts.value || opts.hint || id);
    tip(wrap, opts.tip);
    const i = h('input', 'im-input');
    i.type = 'text';
    i.spellcheck = false;
    i.autocomplete = 'off';
    i.value = opts.value;
    i.placeholder = opts.hint || '';
    i.dataset.key = k;
    i.addEventListener('input', () => { onInput(i.value); render(); });
    i.addEventListener('focus', () => { if (!rebuilding && opts.onFocus) { opts.onFocus(); render(); } });
    i.addEventListener('blur', () => { if (!rebuilding && opts.onBlur) opts.onBlur(); });
    i.addEventListener('keydown', ev => {
      if (ev.key === 'Escape') i.blur();
      if (opts.onKey) opts.onKey(ev);
    });
    wrap.append(i);
    return wrap;
  }

  /**
   * A block field. The field keeps its own text. Bad text turns it red and keeps the last good block.
   * While it has focus and text, a list of up to 8 matches with icons shows under it, over the panel.
   */
  function blockField(id, opts, onChange) {
    const k = key(id);
    let f = state.fields[k];
    if (!f) f = state.fields[k] = { text: opts.value, shown: opts.value, valid: true, open: false };
    if (f.shown !== opts.value && state.focus !== k) {
      f.text = opts.value;
      f.shown = opts.value;
      f.valid = true;
    }
    const matches = state.focus === k && f.open ? searchBlocks(f.text, opts.filter) : [];
    const pick = b => {
      f.text = b.id;
      f.valid = true;
      f.shown = b.id;
      f.open = false;
      onChange(b.id);
      render();
    };
    const e = inputText(id, {
      value: f.text,
      hint: opts.hint,
      invalid: !f.valid,
      tip: opts.tip,
      onFocus: () => { state.focus = k; f.open = true; },
      onBlur: () => {
        state.focus = null;
        f.open = false;
        for (const p of layer.querySelectorAll('.im-popup')) if (p.dataset.popup === k) p.remove();
      },
      onKey: ev => {
        if (ev.key === 'Enter' && matches.length) { ev.preventDefault(); pick(matches[0]); }
      },
    }, value => {
      f.text = value;
      f.open = true;
      const parsed = parseBlock(value);
      f.valid = !!parsed && (!opts.filter || opts.filter(parsed.block));
      if (f.valid) {
        const canonical = blockText(parsed);
        if (canonical !== f.shown) {
          f.shown = canonical;
          onChange(canonical);
        }
      }
    });
    if (matches.length) {
      defer({
        anchor: k,
        anchorRect: a => a.parentElement.getBoundingClientRect(),
        fitBelow: 40,
        build: () => col(() => {
          for (const b of matches) {
            const s = add(h('div', 'im-selectable'));
            const icon = h('div', 'im-icon');
            icon.style.background = b.color;
            s.append(icon, document.createTextNode(b.name));
            s.addEventListener('pointerdown', ev => { ev.preventDefault(); pick(b); });
          }
        }),
      });
    }
    return e;
  }

  /** A floating window over the panel, under the anchor. It has a title bar with a close button. */
  function floatingWindow(anchorKey, title, width, onClose, fn) {
    defer({
      anchor: anchorKey,
      window: true,
      width: r => Math.max(r.width, width),
      build: () => {
        const bar = add(h('div', 'im-title'));
        bar.append(document.createTextNode(title));
        const x = h('span', 'x', '×');
        x.addEventListener('click', () => { onClose(); render(); });
        bar.append(x);
        within(add(h('div', 'im-window-body im-col')), fn);
      },
    });
  }

  /** A square arrow button that opens or closes an editor. */
  function fold(id, open, onClick, tipText) {
    const k = key(id);
    const e = add(h('div', 'im-fold' + (open ? ' open' : '')));
    e.dataset.key = k;
    tip(e, tipText);
    e.append(h('div', 'im-tri' + (open ? '' : ' right')));
    e.addEventListener('click', () => { onClick(); render(); });
    return e;
  }

  /** A one-line read-only frame: a dim kind word, then the text. A click runs onClick. */
  function summary(id, kind, textValue, onClick, tipText) {
    const k = key(id);
    const e = add(h('div', 'im-summary'));
    e.dataset.key = k;
    label(e, kind + ' ' + textValue);
    tip(e, tipText);
    e.append(h('span', 'kind', kind + ' '), document.createTextNode(textValue));
    e.addEventListener('click', () => { onClick(); render(); });
    return e;
  }

  // ---------------------------------------------------------------- page helpers

  function toast(title, message) {
    const old = document.querySelector('.toast');
    if (old) old.remove();
    const t = h('div', 'toast');
    t.append(h('b', null, title), document.createTextNode(message));
    document.body.append(t);
    setTimeout(() => t.remove(), 4000);
  }

  function copyText(textValue) {
    const fallback = () => {
      const a = h('textarea');
      a.value = textValue;
      document.body.append(a);
      a.select();
      try { document.execCommand('copy'); } catch (err) { /* The page shows the JSON anyway. */ }
      a.remove();
    };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(textValue).catch(fallback);
    } else {
      fallback();
    }
  }

  function pasteText(onText) {
    const ask = () => {
      const t = window.prompt('Paste the JSON here. The file page cannot read the clipboard.');
      if (t != null) { onText(t); render(); }
    };
    if (navigator.clipboard && navigator.clipboard.readText) {
      navigator.clipboard.readText().then(t => { onText(t); render(); }, ask);
    } else {
      ask();
    }
  }

  /**
   * Marks each item that passes the content edge of its panel, or whose frame clips its text.
   * Returns the labels of the marked items.
   */
  function markClipped(root) {
    const found = [];
    for (const panel of root.querySelectorAll('.im-panel')) {
      const pr = panel.getBoundingClientRect();
      const right = pr.right - parseFloat(getComputedStyle(panel).paddingRight) + 0.5;
      for (const e of panel.querySelectorAll('[data-label]')) {
        e.classList.remove('clipped');
        const r = e.getBoundingClientRect();
        if (r.width === 0) continue;
        let clipped = r.right > right;
        const span = e.querySelector('.im-slider-text > span, .im-combo-text > span, .im-drag-text > span');
        if (span) {
          const box = span.parentElement.getBoundingClientRect();
          const pad = parseFloat(getComputedStyle(span.parentElement).paddingLeft) || 0;
          if (span.getBoundingClientRect().width > box.width - 2 * pad + 0.5) clipped = true;
        }
        const input = e.querySelector('input');
        if (input && input.scrollWidth > input.clientWidth + 0.5) clipped = true;
        if (e.scrollWidth > e.clientWidth + 0.5) clipped = true;
        if (clipped) {
          e.classList.add('clipped');
          found.push(e.dataset.label);
        }
      }
    }
    return found;
  }

  /** A panel of the given width in ImGui units, splitter included. */
  function panel(width, fn, cls) {
    const e = add(h('div', 'im-panel' + (cls ? ' ' + cls : '')));
    e.style.width = width + 'px';
    return within(e, () => col(fn));
  }

  /** Adds a page element that is not an ImGui widget. */
  function html(tag, cls, textValue) { return add(h(tag, cls, textValue)); }
  function inside(e, fn) { return within(e, fn); }

  function mount(root, fn, after) {
    mountRoot = root;
    drawFn = fn;
    afterFn = after || null;
    if (!layer) init();
    render();
    window.addEventListener('resize', render);
    if (document.fonts && document.fonts.ready) document.fonts.ready.then(render);
  }

  return {
    state, BLOCKS, parseBlock, blockText, blockJson, blockFromJson, searchBlocks,
    mount, render, withId, key, find,
    panel, col, line, indent, box, disabled, fill, spacing, separator, text, labelGrid, labelRow, span,
    collapsingHeader, treeNode, tabBar, slider, dragInt, combo, checkbox, button, inputText, blockField,
    floatingWindow, fold, summary, toast, copyText, pasteText, markClipped, html, inside,
  };
})();
