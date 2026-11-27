/* CodeXperience – kleine, abhängigkeitsfreie Client-Logik */
(function () {
  'use strict';
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };
  var esc = function (s) { return String(s).replace(/[&<>"']/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]; }); };
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (e) { /* ignore */ } }
  };
  var cfgEl = $('#cfg');
  var cfg = {};
  try { cfg = cfgEl ? JSON.parse(cfgEl.textContent) : {}; } catch (e) { cfg = {}; }

  /* Theme */
  var themeBtn = $('#theme-toggle');
  if (themeBtn) themeBtn.addEventListener('click', function () {
    var cur = document.documentElement.getAttribute('data-theme');
    if (!cur) cur = matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    var next = cur === 'light' ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', next);
    store.set('cx-theme', next);
  });

  /* Mobile nav */
  var menuBtn = $('#menu-toggle'), links = $('#nav-links');
  if (menuBtn && links) menuBtn.addEventListener('click', function () {
    var open = links.classList.toggle('open');
    menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
  });

  /* Wiki: mobile sidebar */
  var wt = $('#wiki-toggle'), wn = $('.wiki-nav');
  if (wt && wn) wt.addEventListener('click', function () {
    var open = wn.classList.toggle('open');
    wt.setAttribute('aria-expanded', open ? 'true' : 'false');
  });

  /* Wiki: Suche */
  var sInput = $('#wiki-search');
  if (sInput) {
    var box = $('#wiki-results'), index = null, loading = null, active = -1;
    var root = sInput.getAttribute('data-root') || '';
    var load = function () {
      if (index) return Promise.resolve(index);
      if (!loading) loading = fetch(root + 'wiki-index.json').then(function (r) { return r.json(); }).then(function (j) { index = j; return j; });
      return loading;
    };
    var tokens = function (q) { return q.toLowerCase().split(/[^\p{L}\p{N}_\/-]+/u).filter(Boolean); };
    var snippet = function (text, toks) {
      var low = text.toLowerCase(), pos = -1;
      for (var i = 0; i < toks.length; i++) { var p = low.indexOf(toks[i]); if (p >= 0 && (pos < 0 || p < pos)) pos = p; }
      if (pos < 0) return esc(text.slice(0, 110));
      var from = Math.max(0, pos - 40), s = text.slice(from, from + 130);
      s = esc(s);
      toks.forEach(function (t) { s = s.replace(new RegExp('(' + t.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'ig'), '<mark>$1</mark>'); });
      return (from > 0 ? '…' : '') + s + '…';
    };
    var run = function () {
      var q = sInput.value.trim();
      if (q.length < 2) { box.hidden = true; return; }
      load().then(function (idx) {
        var toks = tokens(q), res = [];
        idx.forEach(function (p) {
          var score = 0, t = p.title.toLowerCase(), h = p.headings.join(' ').toLowerCase(), b = p.text.toLowerCase();
          for (var i = 0; i < toks.length; i++) {
            var k = toks[i], s = 0;
            if (t.indexOf(k) >= 0) s += 10;
            if (h.indexOf(k) >= 0) s += 5;
            var at = b.indexOf(k);
            if (at >= 0) { s += 1; var n = 0, from = at; while (n < 5 && (from = b.indexOf(k, from + 1)) >= 0) { n++; } s += n * .2; }
            if (!s) { score = 0; break; }
            score += s;
          }
          if (score) res.push({ p: p, score: score });
        });
        res.sort(function (a, b) { return b.score - a.score; });
        res = res.slice(0, 8);
        box.innerHTML = res.length ? res.map(function (r) {
          return '<a href="' + root + esc(r.p.url) + '"><b>' + esc(r.p.title) + '</b><small>' + snippet(r.p.text, toks) + '</small></a>';
        }).join('') : '<div class="none">Keine Treffer für „' + esc(q) + '“.</div>';
        box.hidden = false; active = -1;
      }).catch(function () { box.innerHTML = '<div class="none">Suche nicht verfügbar.</div>'; box.hidden = false; });
    };
    sInput.addEventListener('input', run);
    sInput.addEventListener('focus', function () { load(); if (sInput.value.trim().length >= 2) run(); });
    sInput.addEventListener('keydown', function (e) {
      var items = $$('a', box);
      if (e.key === 'Escape') { box.hidden = true; sInput.blur(); }
      else if (e.key === 'ArrowDown' && items.length) { e.preventDefault(); active = Math.min(active + 1, items.length - 1); items[active].focus(); }
      else if (e.key === 'Enter' && items.length) { location.href = items[0].href; }
    });
    box.addEventListener('keydown', function (e) {
      var items = $$('a', box);
      if (e.key === 'ArrowDown') { e.preventDefault(); active = Math.min(active + 1, items.length - 1); items[active].focus(); }
      if (e.key === 'ArrowUp') { e.preventDefault(); active -= 1; if (active < 0) sInput.focus(); else items[active].focus(); }
      if (e.key === 'Escape') { box.hidden = true; sInput.focus(); }
    });
    document.addEventListener('click', function (e) { if (!e.target.closest('.search')) box.hidden = true; });
    document.addEventListener('keydown', function (e) {
      if (e.key === '/' && !/input|textarea|select/i.test(document.activeElement.tagName)) { e.preventDefault(); sInput.focus(); }
    });
  }

  /* Wiki: Inhaltsverzeichnis hervorheben */
  var tocLinks = $$('.toc a');
  if (tocLinks.length && 'IntersectionObserver' in window) {
    var map = {};
    tocLinks.forEach(function (a) { map[a.getAttribute('href').slice(1)] = a; });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting && map[en.target.id]) {
          tocLinks.forEach(function (a) { a.classList.remove('active'); });
          map[en.target.id].classList.add('active');
        }
      });
    }, { rootMargin: '-80px 0px -70% 0px' });
    $$('.prose h2[id], .prose h3[id]').forEach(function (h) { io.observe(h); });
  }

  /* Changelog: Filter und Suche */
  var clRoot = $('#changelog');
  if (clRoot) {
    var entries = $$('.entry', clRoot), q = $('#cl-search'), chips = $$('.chip[data-tag]'), empty = $('#cl-empty'), tag = 'all';
    var apply = function () {
      var term = (q.value || '').toLowerCase().trim(), shown = 0;
      entries.forEach(function (e) {
        var ok = (tag === 'all' || e.getAttribute('data-tag') === tag) && (!term || e.textContent.toLowerCase().indexOf(term) >= 0);
        e.hidden = !ok; if (ok) shown++;
      });
      $$('.release', clRoot).forEach(function (r) {
        $$('.sub', r).forEach(function (s) { s.hidden = !$$('.entry:not([hidden])', s).length; });
        r.hidden = !$$('.entry:not([hidden])', r).length;
      });
      empty.hidden = shown > 0;
    };
    chips.forEach(function (c) { c.addEventListener('click', function () {
      tag = c.getAttribute('data-tag');
      chips.forEach(function (x) { x.setAttribute('aria-pressed', x === c ? 'true' : 'false'); });
      apply();
    }); });
    q.addEventListener('input', apply);
  }

  /* Issue-Formular */
  var form = $('#issue-form');
  if (form) {
    var repo = cfg.repo, TYPES = cfg.types, state = { type: 'bug' };
    var fields = {};
    $$('[data-f]', form).forEach(function (el) { fields[el.getAttribute('data-f')] = el; });
    var val = function (k) { return fields[k] ? fields[k].value.trim() : ''; };
    var typeBtns = $$('.type', form);
    var preview = $('#preview'), status = $('#status'), longNote = $('#long-note');
    var params = new URLSearchParams(location.search);

    var setType = function (t) {
      if (!TYPES[t]) t = 'bug';
      state.type = t;
      typeBtns.forEach(function (b) { b.setAttribute('aria-pressed', b.getAttribute('data-type') === t ? 'true' : 'false'); });
      $$('[data-for]', form).forEach(function (f) {
        f.hidden = f.getAttribute('data-for').split(' ').indexOf(t) < 0;
      });
      update();
    };
    var section = function (title, text) { return text ? '### ' + title + '\n' + text + '\n\n' : ''; };
    var build = function () {
      var t = state.type, b = '';
      var mod = val('mod'), ver = val('version');
      if (t === 'bug') {
        b += section('Beschreibung', val('desc'));
        b += section('Schritte zum Nachstellen', val('steps'));
        b += section('Erwartetes Verhalten', val('expected'));
        b += section('Tatsächliches Verhalten', val('actual'));
        b += '### Umgebung\n- Mod: ' + (mod || '–') + '\n- Version: ' + (ver || '–') + '\n- Minecraft: ' + (val('mc') || '–') + '\n- Spielmodus: ' + (val('env') || '–') + '\n\n';
        b += section('Log / Fehlermeldung', val('log') ? '```\n' + val('log') + '\n```' : '');
      } else if (t === 'feature') {
        b += section('Problem / Motivation', val('problem'));
        b += section('Vorschlag', val('desc'));
        b += section('Alternativen', val('alt'));
        b += '### Mod\n' + (mod || '–') + (ver ? ' (' + ver + ')' : '') + '\n\n';
      } else if (t === 'question') {
        b += section('Frage', val('desc'));
        b += section('Was habe ich schon versucht?', val('steps'));
        b += '### Mod\n' + (mod || '–') + (ver ? ' (' + ver + ')' : '') + '\n\n';
      } else if (t === 'docs') {
        var pg = fields.page && fields.page.value;
        var pgTitle = fields.page && fields.page.selectedOptions[0] ? fields.page.selectedOptions[0].textContent : '';
        if (pg) b += '### Wiki-Seite\n[' + pgTitle + '](' + cfg.siteUrl + 'wiki/' + pg + '.html) (`wiki/' + pg + '.md`)\n\n';
        b += section('Was ist falsch oder fehlt?', val('desc'));
        b += section('Vorschlag für den Text', val('suggest'));
      }
      b += '---\n_Eingereicht über das Formular auf ' + (cfg.siteUrl || 'der Projektseite') + '_';
      return b.trim();
    };
    var title = function () {
      var raw = val('title'), prefix = TYPES[state.type].prefix;
      return raw ? prefix + ' ' + raw : '';
    };
    var url = function () {
      var u = new URL('https://github.com/' + repo + '/issues/new');
      u.searchParams.set('title', title());
      u.searchParams.set('body', build());
      u.searchParams.set('labels', TYPES[state.type].labels.join(','));
      return u.toString();
    };
    var valid = function () {
      if (!val('title') || val('title').length < 5) return 'Bitte einen aussagekräftigen Titel angeben (mind. 5 Zeichen).';
      var needed = state.type === 'docs' ? ['desc'] : state.type === 'bug' ? ['desc'] : ['desc'];
      for (var i = 0; i < needed.length; i++) if (val(needed[i]).length < 10) return 'Bitte die Beschreibung etwas ausführlicher ausfüllen.';
      return '';
    };
    var update = function () {
      preview.textContent = (title() ? '# ' + title() + '\n\n' : '') + build();
      longNote.hidden = url().length < 7500;
      var err = valid();
      $('#submit').disabled = !!err;
      status.textContent = err;
      similar();
    };

    /* Ähnliche offene Issues + Liste */
    var open = [], list = $('#open-issues'), listStatus = $('#open-status');
    var renderList = function (hl) {
      if (!open.length) return;
      var toks = hl ? hl.toLowerCase().split(/[^\p{L}\p{N}]+/u).filter(function (w) { return w.length > 3; }) : [];
      var scored = open.map(function (i) {
        var t = i.title.toLowerCase(), s = 0; toks.forEach(function (k) { if (t.indexOf(k) >= 0) s++; });
        return { i: i, s: s };
      });
      var any = toks.length && scored.some(function (x) { return x.s > 0; });
      if (any) scored = scored.filter(function (x) { return x.s > 0; }).sort(function (a, b) { return b.s - a.s; });
      listStatus.textContent = any ? 'Ähnliche offene Issues – vielleicht gibt es dein Anliegen schon:' : 'Zuletzt aktualisierte offene Issues:';
      list.innerHTML = scored.slice(0, 8).map(function (x) {
        var i = x.i;
        return '<li' + (any ? ' class="similar"' : '') + '><a href="' + esc(i.html_url) + '" target="_blank" rel="noopener">#' + i.number + ' ' + esc(i.title) + '</a><small>' +
          (i.labels || []).map(function (l) { return esc(l.name); }).join(', ') + (i.labels && i.labels.length ? ' · ' : '') + i.comments + ' Kommentare</small></li>';
      }).join('');
    };
    var similar = function () { renderList(val('title')); };
    fetch('https://api.github.com/repos/' + repo + '/issues?state=open&per_page=60&sort=updated')
      .then(function (r) { if (!r.ok) throw new Error(r.status); return r.json(); })
      .then(function (j) { open = j.filter(function (i) { return !i.pull_request; }); if (!open.length) listStatus.textContent = 'Keine offenen Issues – prima!'; renderList(val('title')); })
      .catch(function () { listStatus.textContent = 'Offene Issues konnten gerade nicht geladen werden.'; });

    typeBtns.forEach(function (b) { b.addEventListener('click', function () { setType(b.getAttribute('data-type')); }); });
    $$('[data-f]', form).forEach(function (el) { el.addEventListener('input', update); el.addEventListener('change', update); });

    /* Vorbelegung über URL: ?type=docs&page=Karte&mod=… */
    if (params.get('mod') && fields.mod) fields.mod.value = params.get('mod');
    if (params.get('page') && fields.page) fields.page.value = params.get('page');
    if (params.get('title') && fields.title) fields.title.value = params.get('title');
    var autoVersion = fields.mod && fields.mod.selectedOptions[0] && fields.mod.selectedOptions[0].getAttribute('data-version');
    if (fields.version && autoVersion && !fields.version.value) fields.version.value = autoVersion;
    if (fields.mod) fields.mod.addEventListener('change', function () {
      var v = fields.mod.selectedOptions[0].getAttribute('data-version'); if (v) fields.version.value = v; update();
    });
    if (fields.mc && !fields.mc.value && fields.mod && fields.mod.selectedOptions[0]) fields.mc.value = fields.mod.selectedOptions[0].getAttribute('data-mc') || '';

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var err = valid(); if (err) { status.textContent = err; return; }
      window.open(url(), '_blank', 'noopener');
      status.textContent = 'GitHub wurde in einem neuen Tab geöffnet. Dort noch auf „Submit new issue“ klicken (GitHub-Konto nötig).';
    });
    $('#copy').addEventListener('click', function () {
      var text = (title() ? title() + '\n\n' : '') + build();
      var done = function () { status.textContent = 'Text kopiert.'; };
      if (navigator.clipboard) navigator.clipboard.writeText(text).then(done, function () { status.textContent = 'Kopieren nicht möglich – bitte die Vorschau markieren.'; });
      else status.textContent = 'Kopieren nicht möglich – bitte die Vorschau markieren.';
    });
    setType(params.get('type') || 'bug');
  }
})();
