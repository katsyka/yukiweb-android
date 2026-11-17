// Shared by every chrome page (and the new tab page): applies the saved theme as CSS
// variables and follows changes live — all of them share one origin, so `storage` fires.
const THEMES = {
  light: { name: 'Light', acc: '#ffd23f', chrome: '#e9ebef', surface: '#ffffff', text: '#16181e', text2: '#454a57', dim: '#6b7080', dim2: '#8a8f9c', soft: '#f1f2f5', soft2: '#f6f7f9', hover: '#e6e8ec', line: '#d9dce2', line2: '#eceef2', line3: '#c4c7ce', page: '#f6f7f9' },
  dark: { name: 'Dark', acc: '#ffd23f', chrome: '#18191d', surface: '#232429', text: '#ececf0', text2: '#c9cad2', dim: '#9a9cab', dim2: '#7d8090', soft: '#2e2f35', soft2: '#292a2f', hover: '#383940', line: '#383940', line2: '#303137', line3: '#55575f', page: '#1c1d21', dark: 1 },
  midnight: { name: 'Midnight', acc: '#7aa2ff', chrome: '#0f1424', surface: '#171e33', text: '#e6eaf5', text2: '#c3cae0', dim: '#8e98b8', dim2: '#6f7a9c', soft: '#202a45', soft2: '#1b2440', hover: '#283454', line: '#2a3554', line2: '#222c48', line3: '#46527a', page: '#121a2e', dark: 1 },
  sakura: { name: 'Sakura', acc: '#ff8fb3', chrome: '#f6e7ec', surface: '#fffafb', text: '#2a1a20', text2: '#5c4650', dim: '#8b6f7a', dim2: '#a38c95', soft: '#f8edf1', soft2: '#fbf3f6', hover: '#f1dde4', line: '#ead3db', line2: '#f2e3e9', line3: '#d6b8c3', page: '#fbf3f6' },
  forest: { name: 'Forest', acc: '#7bd389', chrome: '#e3ebe4', surface: '#fbfdfb', text: '#15211a', text2: '#3f5246', dim: '#62786a', dim2: '#83978a', soft: '#edf3ee', soft2: '#f3f7f4', hover: '#dfe9e1', line: '#cfdcd2', line2: '#e4ece6', line3: '#b5c6b9', page: '#f3f7f4' },
};
// Built-in new tab wallpapers (walls/<id>.jpg, thumbnails in walls/t/). dark = light text on top.
// Photos: Unsplash via picsum.photos. "City" is the original wall.jpg.
const WALLS = [
  ['bw_1015', 'Fjord', 1], ['bw_1018', 'Cliffs', 1], ['bw_1036', 'Snow', 0], ['bw_1039', 'Waterfall', 1], ['bw_1044', 'River', 1],
  ['bw_1050', 'Coast', 1], ['bw_1076', 'Tower', 1], ['bw_29', 'Peaks', 1],
  ['c_1022', 'Aurora', 1], ['c_1019', 'Storm', 1], ['c_10', 'Forest coast', 1], ['c_1002', 'Earth', 1], ['c_184', 'Dunes', 1],
  ['c_1056', 'Clouds', 0], ['c_16', 'Calm sea', 0], ['c_110', 'Sunset', 1], ['c_314', 'Road', 1], ['c_11', 'Valley', 0],
].map(([id, name, dark]) => ({ id, name, dark: !!dark }));
const ACCENTS = ['#ffd23f', '#ff8a3d', '#ff5c7a', '#ff8fb3', '#b18cff', '#7aa2ff', '#3ec7e0', '#7bd389'];

function themeGet() {
  try { return JSON.parse(localStorage.getItem('yw-theme')) || {} } catch { return {} }
}
// Mix a hex color toward black (k<0) or white (k>0).
function shade(hex, k) {
  const n = parseInt(hex.slice(1), 16), c = [n >> 16, n >> 8 & 255, n & 255];
  return '#' + c.map(v => Math.round(k < 0 ? v * (1 + k) : v + (255 - v) * k).toString(16).padStart(2, '0')).join('');
}
function themeApply() {
  const s = themeGet(), t = THEMES[s.name] || THEMES.light, acc = s.acc || t.acc;
  const n = parseInt(acc.slice(1), 16), lum = (0.299 * (n >> 16) + 0.587 * (n >> 8 & 255) + 0.114 * (n & 255)) / 255;
  const r = document.documentElement.style;
  for (const k of Object.keys(THEMES.light)) if (k !== 'name' && k !== 'acc') r.setProperty('--' + k, t[k]);
  r.setProperty('--acc', acc);
  r.setProperty('--acc2', shade(acc, -0.08));
  r.setProperty('--on-acc', lum > 0.6 ? '#16181e' : '#ffffff');
  // A tint of the accent for soft highlights, and an accent readable as text on the surface.
  r.setProperty('--acc-soft', t.dark ? shade(acc, -0.72) : shade(acc, 0.82));
  r.setProperty('--acc-text', t.dark ? acc : shade(acc, -0.42));
  r.colorScheme = t.dark ? 'dark' : 'light';
  document.documentElement.classList.toggle('dark', !!t.dark);
}
themeApply();
addEventListener('storage', e => { if (e.key === 'yw-theme' || e.key === null) themeApply(); });
