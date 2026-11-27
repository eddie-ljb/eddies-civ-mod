// Baut die CodeXperience-Webseite.
//
// Alle Inhalte werden beim Build aus dem Repository erzeugt:
//   ../wiki/*.md        -> Wiki-Seiten (mit Navigation, Inhaltsverzeichnis, Suchindex)
//   ../CHANGELOG.md     -> Changelog (filter- und durchsuchbar)
//   ../gradle.properties-> Versionen der Mods
//   mods.json           -> Mod-Steckbriefe
//   GitHub-Releases     -> Download-Links (nur wenn erreichbar, sonst übersprungen)
// Es gibt keine Laufzeit-Abhängigkeit außer "marked".

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execSync } from 'node:child_process';
import { Marked } from 'marked';

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, '..');
const dist = path.join(here, 'dist');
const read = (p) => fs.readFileSync(p, 'utf8');
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const warnings = [];
const warn = (m) => { warnings.push(m); console.warn('WARN ' + m); };

/* ---------- Konfiguration ---------- */
const site = JSON.parse(read(path.join(here, 'site.json')));
const mods = JSON.parse(read(path.join(here, 'mods.json')));

function detectRepo() {
  if (process.env.GITHUB_REPOSITORY) return process.env.GITHUB_REPOSITORY;
  try {
    const url = execSync('git config --get remote.origin.url', { cwd: repoRoot, stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim();
    const m = url.match(/github\.com[:/]+([^/]+\/[^/.]+)/);
    if (m) return m[1];
  } catch { /* ignore */ }
  return site.repo;
}
const repo = detectRepo();
const [owner, repoName] = repo.split('/');
const siteUrl = process.env.SITE_URL || `https://${owner}.github.io/${repoName}/`;

const props = (file) => {
  const out = {};
  for (const line of read(file).split('\n')) {
    const m = line.match(/^\s*([\w.]+)\s*=\s*(.*?)\s*$/);
    if (m) out[m[1]] = m[2];
  }
  return out;
};

/* ---------- Releases (optional, nur Build-Zeit) ---------- */
async function fetchReleases() {
  const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'codexperience-site' };
  if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  try {
    const url = `https://api.github.com/repos/${repo}/releases?per_page=20`;
    let res = await fetch(url, { headers, signal: AbortSignal.timeout(15000) });
    if (res.status === 401 && headers.Authorization) { // ungültiges Token: anonym erneut versuchen (öffentliches Repo)
      delete headers.Authorization;
      res = await fetch(url, { headers, signal: AbortSignal.timeout(15000) });
    }
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const json = await res.json();
    return json.filter((r) => !r.draft).map((r) => ({
      tag: r.tag_name, name: r.name || r.tag_name, prerelease: r.prerelease, published: r.published_at, url: r.html_url,
      assets: (r.assets || []).filter((a) => /\.jar$/i.test(a.name) && !/(sources|dev|javadoc)/i.test(a.name))
        .map((a) => ({ name: a.name, size: a.size, url: a.browser_download_url, downloads: a.download_count })),
    }));
  } catch (e) {
    warn('Releases nicht geladen (' + e.message + ') – Download-Bereich zeigt nur den Link zur Release-Seite.');
    return [];
  }
}

/* ---------- Markdown ---------- */
const slugify = (s) => s.toLowerCase().replace(/<[^>]+>/g, '').replace(/[`*_~]/g, '').trim()
  .replace(/[^\p{L}\p{N}\s-]/gu, '').replace(/\s+/g, '-');

function makeMarked({ headingIds = false, linkMap = null, onLink = null } = {}) {
  const m = new Marked({ gfm: true, breaks: false });
  const seen = new Map();
  const headings = [];
  m.use({
    renderer: {
      heading({ tokens, depth }) {
        const inner = this.parser.parseInline(tokens);
        if (!headingIds) return `<h${depth}>${inner}</h${depth}>\n`;
        const base = slugify(tokens.map((t) => t.raw ?? t.text ?? '').join('')) || 'abschnitt';
        const n = seen.get(base) ?? 0; seen.set(base, n + 1);
        const id = n ? `${base}-${n}` : base;
        headings.push({ depth, id, text: tokens.map((t) => t.text ?? t.raw ?? '').join('').replace(/[`*_]/g, '') });
        return `<h${depth} id="${id}">${inner}<a class="anchor" href="#${id}" aria-label="Link zu diesem Abschnitt">#</a></h${depth}>\n`;
      },
      link({ href, title, tokens }) {
        let h = href;
        if (linkMap && !/^[a-z][a-z0-9+.-]*:|^#|^\//i.test(h)) {
          const [file, hash] = h.split('#');
          if (/\.md$/i.test(file)) {
            const slug = file.replace(/\.md$/i, '');
            if (onLink) onLink(slug);
            h = (slug === 'Home' ? 'index' : slug) + '.html' + (hash ? '#' + hash : '');
          }
        }
        const ext = /^https?:/i.test(h);
        return `<a href="${esc(h)}"${title ? ` title="${esc(title)}"` : ''}${ext ? ' target="_blank" rel="noopener"' : ''}>${this.parser.parseInline(tokens)}</a>`;
      },
      table(token) {
        // Standard-Ausgabe von marked, in scrollbaren Container gepackt
        const head = token.header.map((c, i) => `<th${token.align[i] ? ` align="${token.align[i]}"` : ''}>${this.parser.parseInline(c.tokens)}</th>`).join('');
        const rows = token.rows.map((r) => '<tr>' + r.map((c, i) => `<td${token.align[i] ? ` align="${token.align[i]}"` : ''}>${this.parser.parseInline(c.tokens)}</td>`).join('') + '</tr>').join('\n');
        return `<div class="table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${rows}</tbody></table></div>\n`;
      },
    },
  });
  return { m, headings };
}

const plain = (md) => md.replace(/```[\s\S]*?```/g, ' ').replace(/`([^`]*)`/g, '$1').replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
  .replace(/^[>#\-*+\s|:]+/gm, '').replace(/[*_~|]/g, '').replace(/\s+/g, ' ').trim();

/* ---------- Icons ---------- */
const I = {
  github: '<path d="M12 .5a11.5 11.5 0 0 0-3.64 22.41c.58.1.79-.25.79-.56v-2c-3.2.7-3.87-1.37-3.87-1.37-.52-1.33-1.28-1.69-1.28-1.69-1.05-.71.08-.7.08-.7 1.16.08 1.77 1.19 1.77 1.19 1.03 1.77 2.7 1.26 3.36.96.1-.75.4-1.26.73-1.55-2.55-.29-5.24-1.28-5.24-5.69 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.46.11-3.05 0 0 .97-.31 3.17 1.18a11 11 0 0 1 5.77 0c2.2-1.49 3.17-1.18 3.17-1.18.63 1.59.23 2.76.11 3.05.74.81 1.19 1.83 1.19 3.09 0 4.42-2.7 5.4-5.27 5.68.41.36.78 1.06.78 2.13v3.16c0 .31.21.67.8.56A11.5 11.5 0 0 0 12 .5z" fill="currentColor" stroke="none"/>',
  moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  download: '<path d="M12 3v12m0 0l-4-4m4 4l4-4M5 21h14"/>',
  ext: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
  book: '<path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5zM19 19v2H6"/>',
  town: '<path d="M3 21V10l5-3 5 3v11M13 21V4l8 4v13M3 21h18M7 14h2m-2 3h2m6-9h2m-2 3h2m-2 3h2"/>',
  people: '<circle cx="9" cy="8" r="3.2"/><path d="M3 20c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5M16 5.2a3.2 3.2 0 0 1 0 5.6M18 14.8c2 .6 3.5 2.4 3.5 5.2"/>',
  vote: '<path d="M4 14l8-4 8 4v6H4zM9 10V4h6v6M10 7h4"/>',
  law: '<path d="M12 3v18M6 21h12M5 7h14M5 7l-3 7a3.5 3.5 0 0 0 6 0zM19 7l-3 7a3.5 3.5 0 0 0 6 0z"/>',
  coin: '<circle cx="12" cy="12" r="9"/><path d="M14.8 9.2c-.5-1-1.5-1.5-2.8-1.5-1.7 0-2.8.9-2.8 2.1 0 3 5.8 1.4 5.8 4.3 0 1.2-1.2 2.2-3 2.2-1.4 0-2.5-.6-3-1.7M12 6v1.7m0 8.6V18"/>',
  court: '<path d="M3 9l9-5 9 5M5 9v9m4-9v9m6-9v9m4-9v9M3 21h18M3 18h18"/>',
  build: '<path d="M14.7 6.3a4 4 0 0 0 5 5l-9.4 9.4a2.1 2.1 0 0 1-3-3zM14.7 6.3l-2-2 3-1 3 3-1 3"/>',
  map: '<path d="M9 4L3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14m6-12v14"/>',
  bug: '<path d="M9 7a3 3 0 0 1 6 0M7 10h10v4a5 5 0 0 1-10 0zM3 12h4m10 0h4M4 19l3-2m13 2l-3-2M4 5l3 2m13-2l-3 2M12 10v9"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/>',
};
const icon = (n, cls = '') => `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${I[n] || I.town}</svg>`;

/* ---------- Layout ---------- */
function layout({ title, description, root = '', nav = '', body, bodyClass = '', cfg = null }) {
  const full = title === site.name ? site.name : `${title} · ${site.name}`;
  const navItems = [
    ['home', 'Start', 'index.html'], ['mods', 'Mods', 'mods/index.html'], ['changelog', 'Changelog', 'changelog.html'],
    ['wiki', 'Wiki', 'wiki/index.html'],
  ];
  return `<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(full)}</title>
<meta name="description" content="${esc(description || site.description)}">
<meta name="theme-color" content="#0b0f14">
<meta property="og:title" content="${esc(full)}">
<meta property="og:description" content="${esc(description || site.description)}">
<meta property="og:type" content="website">
<link rel="icon" href="${root}favicon.svg" type="image/svg+xml">
<link rel="stylesheet" href="${root}assets/style.css">
<script>try{var t=localStorage.getItem('cx-theme');if(t)document.documentElement.setAttribute('data-theme',t)}catch(e){}</script>
</head>
<body class="${bodyClass}">
<a class="skip" href="#main">Zum Inhalt springen</a>
<header class="site-header">
  <div class="wrap nav">
    <a class="brand" href="${root}index.html"><span class="logo" aria-hidden="true">&lt;/&gt;</span><span>Code<span class="x">X</span>perience</span></a>
    <nav class="nav-links" id="nav-links" aria-label="Hauptnavigation">
      ${navItems.map(([k, l, h]) => `<a href="${root}${h}"${nav === k ? ' aria-current="page"' : ''}>${l}</a>`).join('')}
      <a class="cta" href="${root}issues.html">Issue melden</a>
    </nav>
    <div class="nav-actions">
      <a class="icon-btn" href="https://github.com/${repo}" aria-label="GitHub-Repository" target="_blank" rel="noopener">${icon('github')}</a>
      <button class="icon-btn" id="theme-toggle" type="button" aria-label="Hell/Dunkel umschalten">${icon('moon')}</button>
      <button class="icon-btn menu-btn" id="menu-toggle" type="button" aria-label="Menü" aria-expanded="false" aria-controls="nav-links">${icon('menu')}</button>
    </div>
  </div>
</header>
<main id="main">
${body}
</main>
<footer class="site-footer">
  <div class="wrap">
    <div class="foot">
      <div>
        <a class="brand" href="${root}index.html"><span class="logo" aria-hidden="true">&lt;/&gt;</span><span>Code<span class="x">X</span>perience</span></a>
        <p>${esc(site.tagline)}</p>
      </div>
      <div><h4>Mods</h4><ul>${mods.map((m) => `<li><a href="${root}mods/${m.id}.html">${esc(m.name)}</a></li>`).join('')}<li><a href="${root}mods/index.html">Alle Mods</a></li></ul></div>
      <div><h4>Dokumentation</h4><ul><li><a href="${root}wiki/index.html">Wiki</a></li><li><a href="${root}changelog.html">Changelog</a></li><li><a href="${root}wiki/Befehlsuebersicht.html">Befehlsübersicht</a></li></ul></div>
      <div><h4>Community</h4><ul><li><a href="${root}issues.html">Issue melden</a></li><li><a href="https://github.com/${repo}" target="_blank" rel="noopener">GitHub</a></li><li><a href="https://github.com/${repo}/releases" target="_blank" rel="noopener">Releases</a></li></ul></div>
    </div>
    <div class="foot-bottom"><span>© ${new Date().getFullYear()} CodeXperience</span><span>Automatisch erzeugt aus dem Repository · Stand ${buildDate}</span></div>
  </div>
</footer>
${cfg ? `<script type="application/json" id="cfg">${JSON.stringify(cfg).replace(/</g, '\\u003c')}</script>` : ''}
<script src="${root}assets/app.js" defer></script>
</body>
</html>
`;
}
const buildDate = new Date().toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
const write = (rel, content) => { const p = path.join(dist, rel); fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, content); };

/* ---------- Wiki ---------- */
function buildWiki() {
  const dir = path.join(repoRoot, 'wiki');
  const files = fs.readdirSync(dir).filter((f) => f.endsWith('.md'));
  const pages = new Map();
  for (const f of files) {
    const slug = f.replace(/\.md$/, '');
    let md = read(path.join(dir, f));
    md = md.replace(/^\[Home\]\(Home\.md\)\s*›[^\n]*\n+/, ''); // Brotkrumen-Zeile entfällt, die Seite hat eigene
    const h1 = md.match(/^#\s+(.+)$/m);
    pages.set(slug, { slug, file: f, md, title: h1 ? h1[1].replace(/[`*_]/g, '') : slug.replace(/-/g, ' '), out: slug === 'Home' ? 'index' : slug });
  }
  // Gruppen aus Home.md: "## Für Spieler" gefolgt von Listeneinträgen mit Link
  const groups = [];
  const home = pages.get('Home');
  if (home) {
    let cur = null;
    for (const line of home.md.split('\n')) {
      const g = line.match(/^##\s+(.+)$/);
      if (g) { cur = { name: g[1].trim(), items: [] }; groups.push(cur); continue; }
      const l = line.match(/^\s*[-*]\s+\[([^\]]+)\]\(([^)#]+)\.md\)\s*(?:[–-]\s*(.*))?$/);
      if (l && cur) cur.items.push({ slug: l[2], label: l[1], desc: l[3] || '' });
    }
  }
  const grouped = groups.filter((g) => g.items.length);
  const listed = new Set(grouped.flatMap((g) => g.items.map((i) => i.slug)));
  const rest = [...pages.keys()].filter((s) => s !== 'Home' && !listed.has(s));
  if (rest.length) grouped.push({ name: 'Weitere Seiten', items: rest.map((s) => ({ slug: s, label: pages.get(s).title, desc: '' })) });
  const order = ['Home', ...grouped.flatMap((g) => g.items.map((i) => i.slug))].filter((s) => pages.has(s));

  const navHtml = (current) => `<aside class="wiki-nav" aria-label="Wiki-Navigation">
    <div class="search"><label class="sr-only" for="wiki-search">Wiki durchsuchen</label>
      <input id="wiki-search" data-root="../" type="search" placeholder="Wiki durchsuchen  ( / )" autocomplete="off">
      <div class="results" id="wiki-results" hidden></div></div>
    <button class="btn small wiki-toggle" id="wiki-toggle" type="button" aria-expanded="false" style="margin-top:10px">Alle Seiten</button>
    <div class="wiki-groups">
      <ul style="margin-top:14px"><li><a href="index.html"${current === 'Home' ? ' aria-current="page"' : ''}>Übersicht</a></li></ul>
      ${grouped.map((g) => `<h4>${esc(g.name)}</h4><ul>${g.items.filter((i) => pages.has(i.slug)).map((i) => `<li><a href="${pages.get(i.slug).out}.html"${i.slug === current ? ' aria-current="page"' : ''}>${esc(i.label)}</a></li>`).join('')}</ul>`).join('')}
    </div></aside>`;

  const index = [];
  const broken = new Set();
  for (const p of pages.values()) {
    const { m, headings } = makeMarked({ headingIds: true, linkMap: true, onLink: (s) => { if (!pages.has(s)) broken.add(`${p.file} → ${s}.md`); } });
    let html = m.parse(p.md);
    const toc = headings.filter((h) => h.depth >= 2 && h.depth <= 3);
    const i = order.indexOf(p.slug), prev = pages.get(order[i - 1]), next = pages.get(order[i + 1]);
    const isHome = p.slug === 'Home';
    const desc = plain(p.md.split('\n').filter((l) => l.trim() && !l.startsWith('#')).slice(0, 2).join(' ')).slice(0, 180);
    const body = `<div class="page-head" style="padding:16px 0"><div class="wrap"><div class="crumbs"><a href="../index.html">Start</a> › <a href="index.html">Wiki</a>${isHome ? '' : ' › ' + esc(p.title)}</div></div></div>
<div class="wrap wiki">
  ${navHtml(p.slug)}
  <article class="prose">
    ${html}
    ${isHome ? `<div class="grid cols-3" style="margin-top:2em">${grouped.map((g) => `<div class="card"><h3 style="margin-top:0">${esc(g.name)}</h3><p>${g.items.length} Seiten</p></div>`).join('')}</div>` : ''}
    <div class="pager">${prev ? `<a href="${prev.out}.html"><small>← Zurück</small>${esc(prev.title)}</a>` : '<span></span>'}${next ? `<a class="next" href="${next.out}.html"><small>Weiter →</small>${esc(next.title)}</a>` : ''}</div>
    <div class="page-meta">
      <a href="https://github.com/${repo}/edit/${site.branch}/wiki/${p.file}" target="_blank" rel="noopener">Seite auf GitHub bearbeiten</a>
      <a href="../issues.html?type=docs&amp;page=${encodeURIComponent(p.slug)}">Fehler oder Lücke melden</a>
    </div>
  </article>
  <nav class="toc" aria-label="Auf dieser Seite">${toc.length > 1 ? `<h4>Auf dieser Seite</h4><ul>${toc.map((h) => `<li class="l${h.depth}"><a href="#${h.id}">${esc(h.text)}</a></li>`).join('')}</ul>` : ''}</nav>
</div>`;
    write(`wiki/${p.out}.html`, layout({ title: p.title, description: desc, root: '../', nav: 'wiki', body }));
    index.push({ url: `wiki/${p.out}.html`, title: p.title, headings: headings.filter((h) => h.depth >= 2).map((h) => h.text), text: plain(p.md) });
  }
  for (const b of broken) warn('Defekter Wiki-Link: ' + b);
  write('wiki-index.json', JSON.stringify(index));
  return { pages, grouped, order, count: pages.size };
}

/* ---------- Changelog ---------- */
function buildChangelog(meta) {
  const md = read(path.join(repoRoot, 'CHANGELOG.md'));
  const releases = [];
  let cur = null;
  const lines = md.split('\n');
  for (const line of lines) {
    const h = line.match(/^##\s+(.+)$/);
    if (h) { cur = { heading: h[1].trim(), lines: [] }; releases.push(cur); continue; }
    if (cur) cur.lines.push(line);
  }
  const { m } = makeMarked();
  const tagOf = (lead) => /\bneu\b|\(neu|neu\)/i.test(lead) ? 'neu' : /korrektur|fix|nachbesserung|behoben/i.test(lead) ? 'fix' : 'change';
  const tagLabel = { neu: 'Neu', fix: 'Korrektur', change: 'Änderung' };
  const stat = { neu: 0, fix: 0, change: 0 };
  const all = [];
  const rel = releases.map((r) => {
    const mm = r.heading.match(/^([\d.]+)\s*(?:\((.*)\))?/);
    const version = mm ? mm[1] : r.heading, state = mm && mm[2] ? mm[2] : '';
    // Abschnitte (###) und Listeneinträge
    const subs = [{ name: '', entries: [] }];
    const tokens = m.lexer(r.lines.join('\n'));
    for (const t of tokens) {
      if (t.type === 'heading') { subs.push({ name: t.text, entries: [] }); continue; }
      const sub = subs[subs.length - 1];
      if (t.type === 'list') {
        for (const item of t.items) {
          const first = item.tokens[0];
          const strong = first?.tokens?.find((x) => x.type === 'strong');
          const lead = strong ? strong.text : '';
          const tag = tagOf(lead || item.text.slice(0, 80));
          const html = m.parser(item.tokens);
          sub.entries.push({ tag, html, lead });
        }
      } else if (t.type === 'paragraph') {
        sub.intro = (sub.intro || '') + m.parser([t]);
      }
    }
    for (const s of subs) for (const e of s.entries) { stat[e.tag]++; all.push({ version, ...e }); }
    return { version, state, subs };
  });
  const total = rel.reduce((n, r) => n + r.subs.reduce((k, s) => k + s.entries.length, 0), 0);

  const body = `<div class="page-head"><div class="wrap"><div class="crumbs"><a href="index.html">Start</a> › Changelog</div>
  <h1>Changelog</h1><p>Alle Änderungen des Eddies Civ Mod – automatisch aus der <code>CHANGELOG.md</code> des Repositories erzeugt. ${total} Einträge in ${rel.length} Versionen.</p></div></div>
<div class="wrap" style="padding:36px 0 72px" id="changelog">
  <div class="cl-tools">
    <div class="search"><label class="sr-only" for="cl-search">Changelog durchsuchen</label><input id="cl-search" type="search" placeholder="Changelog durchsuchen …"></div>
    <div class="chips" role="group" aria-label="Filter">
      <button class="chip" type="button" data-tag="all" aria-pressed="true">Alle (${total})</button>
      <button class="chip" type="button" data-tag="neu" aria-pressed="false">Neu (${stat.neu})</button>
      <button class="chip" type="button" data-tag="fix" aria-pressed="false">Korrekturen (${stat.fix})</button>
      <button class="chip" type="button" data-tag="change" aria-pressed="false">Änderungen (${stat.change})</button>
    </div>
  </div>
  ${rel.map((r) => `<section class="release" id="v${esc(r.version)}">
    <header><h2>${esc(r.version)}</h2>${r.state ? `<span class="badge ok">${esc(r.state)}</span>` : ''}${r.version === meta.version ? '' : ''}</header>
    ${r.subs.map((s) => `<div class="sub">${s.name ? `<h3>${esc(s.name)}</h3>` : ''}${s.intro ? `<div class="prose">${s.intro}</div>` : ''}
      ${s.entries.map((e) => `<div class="entry" data-tag="${e.tag}"><div class="tags"><span class="badge ${e.tag}">${tagLabel[e.tag]}</span></div><div class="body">${e.html}</div></div>`).join('\n')}</div>`).join('\n')}
  </section>`).join('\n')}
  <div class="empty" id="cl-empty" hidden>Keine Einträge für diese Auswahl.</div>
</div>`;
  write('changelog.html', layout({ title: 'Changelog', description: 'Alle Änderungen des Eddies Civ Mod, durchsuchbar und filterbar.', nav: 'changelog', body }));
  return { latest: all.slice(0, 6), total, releases: rel.length };
}

/* ---------- Mods ---------- */
const fmtSize = (b) => b > 1048576 ? (b / 1048576).toFixed(1) + ' MB' : Math.round(b / 1024) + ' KB';
const fmtDate = (d) => new Date(d).toLocaleDateString('de-DE', { day: '2-digit', month: 'short', year: 'numeric' });

function buildMods(releases, changelog, wiki) {
  const enriched = mods.map((mod) => {
    const p = mod.gradleProps ? props(path.resolve(here, mod.gradleProps)) : {};
    const meta = { version: p.version || '?', minecraft: p.minecraft_version || '?', loader: p.loader_version || '?', fabricApi: p.fabric_api_version || '?' };
    const fill = (s) => s.replace(/\{(\w+)\}/g, (_, k) => meta[k] ?? '');
    return { ...mod, meta, requirements: (mod.requirements || []).map(fill) };
  });
  const latest = releases.find((r) => !r.prerelease) || releases[0];
  const card = (m, root) => `<a class="card mod-card" href="${root}mods/${m.id}.html">
    <div class="ico">${icon('town')}</div><div><h3>${esc(m.name)}</h3><p class="tag">${esc(m.tagline)}</p></div>
    <p>${esc(m.summary)}</p>
    <div class="badges meta"><span class="badge ok">${esc(m.status)}</span><span class="badge">v${esc(m.meta.version)}</span><span class="badge">MC ${esc(m.meta.minecraft)}</span><span class="badge">Fabric</span></div></a>`;

  for (const m of enriched) {
    const dl = latest ? `<div class="panel"><h3>Download <span class="badge ok">${esc(latest.tag)}</span></h3><div class="inner">
        ${latest.assets.length ? latest.assets.map((a) => `<div class="dl"><div><b>${esc(a.name)}</b><small>${fmtSize(a.size)} · ${a.downloads} Downloads</small></div><a class="btn primary small" href="${esc(a.url)}">${icon('download')}Laden</a></div>`).join('') : `<p style="margin:0;color:var(--muted);font-size:.9rem">Für dieses Release liegt keine JAR bei.</p>`}
        <p style="margin:12px 0 0;font-size:.85rem;color:var(--muted)">Veröffentlicht am ${fmtDate(latest.published)}${latest.prerelease ? ' (Vorabversion)' : ''}.</p></div></div>`
      : `<div class="panel"><h3>Download</h3><div class="inner"><p style="margin:0 0 12px;color:var(--muted);font-size:.9rem">Alle Versionen findest du auf GitHub.</p><a class="btn primary" href="https://github.com/${repo}/releases">${icon('download')}Zu den Releases</a></div></div>`;
    const olderReleases = releases.slice(0, 8);
    const body = `<div class="page-head"><div class="wrap"><div class="crumbs"><a href="../index.html">Start</a> › <a href="index.html">Mods</a> › ${esc(m.name)}</div>
      <h1>${esc(m.name)}</h1><p>${esc(m.tagline)}</p>
      <div class="badges" style="margin-top:16px"><span class="badge ok">${esc(m.status)}</span><span class="badge">Version ${esc(m.meta.version)}</span><span class="badge">Minecraft ${esc(m.meta.minecraft)}</span><span class="badge">Fabric</span></div></div></div>
<div class="wrap mod-grid">
  <div>
    <div class="prose"><p style="font-size:1.1rem">${esc(m.summary)}</p></div>
    <h2 style="margin:1.6em 0 16px;font-size:1.4rem">Funktionen</h2>
    <div class="grid cols-2">${m.features.map((f) => `<div class="card"><div class="ico">${icon(f.icon)}</div><h3>${esc(f.title)}</h3><p>${esc(f.text)}</p></div>`).join('')}</div>
    <h2 style="margin:2em 0 12px;font-size:1.4rem">Installation</h2>
    <div class="prose"><p>${m.install}</p><ul>${m.requirements.map((r) => `<li>${esc(r)}</li>`).join('')}</ul></div>
    <h2 style="margin:2em 0 12px;font-size:1.4rem">Zuletzt geändert</h2>
    <div class="panel">${changelog.latest.map((e) => `<div class="entry" style="padding:14px 18px;border-color:var(--border)" ><div class="tags"><span class="badge ${e.tag}">${{ neu: 'Neu', fix: 'Korrektur', change: 'Änderung' }[e.tag]}</span></div><div class="body">${e.html}</div></div>`).join('')}</div>
    <p style="margin-top:14px"><a class="btn" href="../changelog.html">Gesamten Changelog lesen</a></p>
  </div>
  <aside class="stack">
    ${dl}
    <div class="panel"><h3>Details</h3><div class="inner"><ul class="kv">
      <li><span>Mod-Version</span><b>${esc(m.meta.version)}</b></li><li><span>Minecraft</span><b>${esc(m.meta.minecraft)}</b></li>
      <li><span>Fabric Loader</span><b>${esc(m.meta.loader)}+</b></li><li><span>Fabric API</span><b>${esc(m.meta.fabricApi)}</b></li><li><span>Java</span><b>25</b></li></ul></div></div>
    <div class="panel"><h3>Mehr</h3><div class="inner stack" style="gap:10px">
      <a class="btn" href="../wiki/${m.wikiHome ? m.wikiHome : 'index.html'}">${icon('book')}Wiki lesen</a>
      <a class="btn" href="../issues.html?mod=${encodeURIComponent(m.id)}">${icon('bug')}Fehler melden</a>
      ${m.modrinth ? `<a class="btn" href="https://modrinth.com/mod/${esc(m.modrinth)}" target="_blank" rel="noopener">${icon('ext')}Modrinth</a>` : ''}
      <a class="btn" href="https://github.com/${repo}" target="_blank" rel="noopener">${icon('github')}Quellcode</a></div></div>
    ${olderReleases.length > 1 ? `<div class="panel"><h3>Frühere Releases</h3><div class="inner"><ul class="kv">${olderReleases.map((r) => `<li><a href="${esc(r.url)}" target="_blank" rel="noopener">${esc(r.tag)}</a><span>${fmtDate(r.published)}</span></li>`).join('')}</ul></div></div>` : ''}
  </aside>
</div>`;
    write(`mods/${m.id}.html`, layout({ title: m.name, description: m.summary, root: '../', nav: 'mods', body }));
  }

  write('mods/index.html', layout({ title: 'Mods', description: 'Alle Mods von CodeXperience.', root: '../', nav: 'mods', body: `<div class="page-head"><div class="wrap"><div class="crumbs"><a href="../index.html">Start</a> › Mods</div><h1>Unsere Mods</h1><p>Alles, was CodeXperience baut – jeweils mit Download, Funktionsübersicht und Dokumentation.</p></div></div>
<div class="wrap" style="padding:40px 0 80px"><div class="grid cols-2">${enriched.map((m) => card(m, '../')).join('')}</div></div>` }));
  return { enriched, latest, card };
}

/* ---------- Issues ---------- */
function buildIssues(enriched, wiki) {
  const types = {
    bug: { label: 'Fehler', desc: 'Etwas funktioniert nicht wie dokumentiert.', prefix: '[Bug]', labels: ['bug'] },
    feature: { label: 'Wunsch', desc: 'Eine neue Funktion oder Verbesserung.', prefix: '[Feature]', labels: ['enhancement'] },
    question: { label: 'Frage', desc: 'Du kommst nicht weiter.', prefix: '[Frage]', labels: ['question'] },
    docs: { label: 'Wiki', desc: 'Fehler oder Lücke in der Doku.', prefix: '[Wiki]', labels: ['documentation'] },
  };
  const modOpts = enriched.map((m) => `<option value="${esc(m.name)}" data-version="${esc(m.meta.version)}" data-mc="${esc(m.meta.minecraft)}">${esc(m.name)}</option>`).join('');
  const pageOpts = ['<option value="">– keine bestimmte Seite –</option>', ...wiki.order.map((s) => `<option value="${esc(s)}">${esc(wiki.pages.get(s).title)}</option>`)].join('');
  const body = `<div class="page-head"><div class="wrap"><div class="crumbs"><a href="index.html">Start</a> › Issue melden</div><h1>Issue melden</h1>
  <p>Fülle das Formular aus, wir bauen daraus ein sauberes GitHub-Issue. Zum Absenden öffnet sich GitHub mit vorausgefülltem Text – dafür brauchst du ein kostenloses GitHub-Konto.</p></div></div>
<div class="wrap issue-layout">
  <form id="issue-form" novalidate>
    <div class="types" role="group" aria-label="Art der Meldung">${Object.entries(types).map(([k, t]) => `<button type="button" class="type" data-type="${k}" aria-pressed="false"><b>${t.label}</b><span>${t.desc}</span></button>`).join('')}</div>
    <div class="field"><label for="f-title">Titel <small>(kurz und konkret)</small></label><input id="f-title" data-f="title" type="text" maxlength="120" autocomplete="off" placeholder="z. B. Bauamt legt keine Straße an, wenn …"></div>
    <div class="row">
      <div class="field" data-for="bug feature question"><label for="f-mod">Mod</label><select id="f-mod" data-f="mod">${modOpts}</select></div>
      <div class="field" data-for="bug feature question"><label for="f-version">Version</label><input id="f-version" data-f="version" type="text" placeholder="z. B. 1.1.0"></div>
    </div>
    <div class="field" data-for="docs"><label for="f-page">Wiki-Seite</label><select id="f-page" data-f="page">${pageOpts}</select></div>
    <div class="field" data-for="feature"><label for="f-problem">Welches Problem möchtest du lösen?</label><textarea id="f-problem" data-f="problem" placeholder="Was fehlt dir oder stört dich aktuell?"></textarea></div>
    <div class="field" data-for="bug feature question docs"><label for="f-desc" id="l-desc">Beschreibung</label><textarea id="f-desc" data-f="desc" placeholder="Beschreibe es so genau wie möglich."></textarea></div>
    <div class="field" data-for="bug question"><label for="f-steps">Schritte zum Nachstellen <small>(bzw. was du versucht hast)</small></label><textarea id="f-steps" data-f="steps" placeholder="1. …&#10;2. …&#10;3. …"></textarea></div>
    <div class="row">
      <div class="field" data-for="bug"><label for="f-expected">Erwartet</label><textarea id="f-expected" data-f="expected" style="min-height:80px"></textarea></div>
      <div class="field" data-for="bug"><label for="f-actual">Tatsächlich</label><textarea id="f-actual" data-f="actual" style="min-height:80px"></textarea></div>
    </div>
    <div class="row">
      <div class="field" data-for="bug"><label for="f-mc">Minecraft-Version</label><input id="f-mc" data-f="mc" type="text"></div>
      <div class="field" data-for="bug"><label for="f-env">Wo tritt es auf?</label><select id="f-env" data-f="env"><option>Dedizierter Server</option><option>Einzelspieler / LAN</option><option>Client</option></select></div>
    </div>
    <div class="field" data-for="bug"><label for="f-log">Log oder Fehlermeldung <small>(optional)</small></label><textarea id="f-log" data-f="log" style="font-family:var(--mono);font-size:.85rem" placeholder="logs/latest.log – nur die relevanten Zeilen"></textarea></div>
    <div class="field" data-for="feature"><label for="f-alt">Alternativen <small>(optional)</small></label><textarea id="f-alt" data-f="alt" style="min-height:80px"></textarea></div>
    <div class="field" data-for="docs"><label for="f-suggest">Vorschlag für den Text <small>(optional)</small></label><textarea id="f-suggest" data-f="suggest" style="min-height:80px"></textarea></div>
    <div class="actions">
      <button class="btn primary" id="submit" type="submit">${icon('github')}Auf GitHub erstellen</button>
      <button class="btn" id="copy" type="button">Text kopieren</button>
      <span class="status" id="status" role="status" aria-live="polite"></span>
    </div>
    <div class="note warn" id="long-note" hidden style="margin-top:16px">Der Text ist sehr lang für einen Link. Falls GitHub ihn abschneidet, nutze „Text kopieren“ und füge ihn dort ein.</div>
  </form>
  <aside class="side">
    <div class="panel"><h3>Vorschau</h3><div class="inner"><pre class="preview" id="preview"></pre></div></div>
    <div class="panel"><h3>Offene Issues</h3><div class="inner" style="padding-bottom:0"><p id="open-status" class="status" style="margin:0 0 8px">Lade …</p></div><ul class="issue-list" id="open-issues"></ul></div>
    <div class="note">Das Formular sendet nichts selbst ab – es erzeugt nur einen vorausgefüllten Link zu <a href="https://github.com/${repo}/issues" target="_blank" rel="noopener">${repo}</a>. Es werden keine Daten bei uns gespeichert.</div>
  </aside>
</div>`;
  write('issues.html', layout({ title: 'Issue melden', description: 'Fehler melden, Wünsche äußern oder Fragen stellen – direkt als GitHub-Issue.', nav: 'issues', body, cfg: { repo, types, siteUrl } }));
}

/* ---------- Startseite ---------- */
function buildHome(ctx) {
  const { enriched, latest, card } = ctx.mods;
  const primary = enriched[0];
  const body = `<section class="hero"><div class="wrap hero-inner">
  <div>
    <div class="eyebrow">Minecraft · Fabric · Mods mit Tiefgang</div>
    <h1>Mods, die Welten <em>zum Leben</em> erwecken.</h1>
    <p class="lead">CodeXperience baut Minecraft-Mods für Server, die mehr sein wollen als Survival: Gemeinden gründen, Gesetze beschließen, Firmen führen, vor Gericht ziehen – gemeinsam mit Dorfbewohnern, die als echte Bürger mitspielen.</p>
    <div class="hero-cta"><a class="btn primary" href="mods/index.html">Mods entdecken</a><a class="btn" href="wiki/index.html">${icon('book')}Zum Wiki</a>${latest ? `<a class="btn" href="${esc(latest.assets[0]?.url || latest.url)}">${icon('download')}${esc(latest.tag)} laden</a>` : ''}</div>
  </div>
  <div class="term" aria-label="Beispiel einer Spielsitzung"><div class="term-bar"><i></i><i></i><i></i><span>Beispiel · Minecraft-Chat</span></div>
<pre><span class="c"># Straße eintragen, Grundstück zuschneiden und ausschreiben</span>
<span class="p">/</span>civ strasse anlegen <span class="o">Mühlenweg</span>
<span class="p">/</span>civ grundstueck erstellen <span class="o">wohnen</span>
<span class="p">/</span>civ grundstueck anbieten <span class="w">12,5</span>

<span class="c"># Die Gemeinde stimmt über ein Gesetz ab</span>
<span class="p">/</span>civ abstimmen <span class="w">1</span> <span class="o">ja</span>

<span class="c"># Firma gründen und an die Börse bringen</span>
<span class="p">/</span>civ firma gruenden <span class="w">100</span> <span class="o">Lindenau Handelshaus</span></pre></div>
</div></section>
<div class="stats">
  <div class="stat"><b>${enriched.length}</b><span>${enriched.length === 1 ? 'Mod' : 'Mods'}</span></div>
  <div class="stat"><b>${esc(primary.meta.version)}</b><span>aktuelle Version</span></div>
  <div class="stat"><b>${esc(primary.meta.minecraft)}</b><span>Minecraft</span></div>
  <div class="stat"><b>${ctx.wiki.count}</b><span>Wiki-Seiten</span></div>
  <div class="stat"><b>${ctx.changelog.total}</b><span>Changelog-Einträge</span></div>
</div>
<section class="block"><div class="wrap">
  <div class="section-head"><div><div class="eyebrow">Mods</div><h2>Was wir bauen</h2></div><a class="btn" href="mods/index.html">Alle Mods</a></div>
  <div class="grid cols-2">${enriched.map((m) => card(m, '')).join('')}</div>
</div></section>
<section class="block"><div class="wrap">
  <div class="section-head"><div><div class="eyebrow">${esc(primary.name)}</div><h2>Eine Gesellschaft in deiner Welt</h2><p>Von der ersten Gemeinde bis zur Nation: acht Systeme, die ineinandergreifen.</p></div></div>
  <div class="grid cols-3">${primary.features.map((f) => `<div class="card"><div class="ico">${icon(f.icon)}</div><h3>${esc(f.title)}</h3><p>${esc(f.text)}</p></div>`).join('')}</div>
</div></section>
<section class="block"><div class="wrap">
  <div class="section-head"><div><div class="eyebrow">Changelog</div><h2>Zuletzt geändert</h2></div><a class="btn" href="changelog.html">Alle ${ctx.changelog.total} Einträge</a></div>
  <div class="panel">${ctx.changelog.latest.slice(0, 4).map((e) => `<div class="entry" style="padding:16px 20px"><div class="tags"><span class="badge ${e.tag}">${{ neu: 'Neu', fix: 'Korrektur', change: 'Änderung' }[e.tag]}</span></div><div class="body">${e.html}</div></div>`).join('')}</div>
</div></section>
<section class="block"><div class="wrap">
  <div class="section-head"><div><div class="eyebrow">Wiki</div><h2>Nachschlagen statt raten</h2><p>Das Wiki wächst mit dem Mod: Jede Seite stammt aus dem Repository und ist durchsuchbar.</p></div><a class="btn" href="wiki/index.html">Zum Wiki</a></div>
  <div class="grid cols-3">${ctx.wiki.grouped.map((g) => `<div class="card"><h3 style="margin-top:0">${esc(g.name)}</h3><ul style="margin:10px 0 0;padding-left:1.1em;color:var(--muted);font-size:.93rem">${g.items.slice(0, 5).map((i) => `<li><a href="wiki/${ctx.wiki.pages.get(i.slug)?.out}.html">${esc(i.label)}</a></li>`).join('')}</ul></div>`).join('')}</div>
</div></section>
<section class="block"><div class="wrap"><div class="card" style="display:flex;gap:24px;align-items:center;justify-content:space-between;flex-wrap:wrap;padding:30px">
  <div><h2 style="margin:0 0 6px;font-size:1.5rem">Fehler gefunden? Idee?</h2><p style="margin:0;color:var(--muted)">Melde sie direkt als GitHub-Issue – das Formular führt dich durch.</p></div>
  <a class="btn primary" href="issues.html">Issue melden</a></div></div></section>`;
  write('index.html', layout({ title: site.name, description: site.description, nav: 'home', body }));
}

/* ---------- Main ---------- */
fs.rmSync(dist, { recursive: true, force: true });
fs.mkdirSync(path.join(dist, 'assets'), { recursive: true });
fs.copyFileSync(path.join(here, 'src/style.css'), path.join(dist, 'assets/style.css'));
fs.copyFileSync(path.join(here, 'src/app.js'), path.join(dist, 'assets/app.js'));
write('favicon.svg', `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="8" fill="#3ddc97"/><text x="16" y="21" text-anchor="middle" font-family="monospace" font-weight="700" font-size="14" fill="#04210f">&lt;/&gt;</text></svg>`);
write('.nojekyll', '');

const releases = await fetchReleases();
write('data/releases.json', JSON.stringify(releases));
const wiki = buildWiki();
const meta = { version: props(path.resolve(here, mods[0].gradleProps)).version };
const changelog = buildChangelog(meta);
const modsCtx = buildMods(releases, changelog, wiki);
buildIssues(modsCtx.enriched, wiki);
buildHome({ mods: modsCtx, changelog, wiki });
write('404.html', layout({ title: 'Nicht gefunden', root: '', body: `<div class="wrap" style="padding:96px 0;text-align:center"><div class="eyebrow">404</div><h1>Diese Seite gibt es nicht.</h1><p style="color:var(--muted)">Vielleicht hilft dir das <a href="wiki/index.html">Wiki</a> weiter.</p></div>` }));
write('sitemap.xml', `<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${
  walk(dist).filter((f) => f.endsWith('.html') && f !== '404.html').map((f) => `<url><loc>${siteUrl}${f}</loc></url>`).join('')}</urlset>`);

function walk(d, base = d) {
  return fs.readdirSync(d, { withFileTypes: true }).flatMap((e) => e.isDirectory() ? walk(path.join(d, e.name), base) : [path.relative(base, path.join(d, e.name)).split(path.sep).join('/')]);
}
console.log(`OK: ${walk(dist).length} Dateien, ${wiki.count} Wiki-Seiten, ${changelog.total} Changelog-Einträge, ${releases.length} Releases, ${warnings.length} Warnungen -> ${path.relative(process.cwd(), dist) || dist}`);
