/**
 * Essentia · base de datos (Google Apps Script)
 *
 * Recibe desde la app Essentia:
 *   - report       reportes de errores (hoja "Reportes"; scripts adjuntos en Drive)
 *   - translate    traducción de textos de juegos (caché compartida por juego en Drive)
 *   - tr_dump      todas las traducciones ya hechas de un juego
 *   - backup_put / backup_list / backup_get   respaldo de partidas (con clave)
 *
 * Instalación: ver backend/LEEME.md. Después de pegar este código, ejecuta
 * "setup" una vez (crea las carpetas y la clave de respaldo) y publícalo
 * como aplicación web (Ejecutar como: yo · Acceso: cualquier persona).
 */

var ROOT_NAME = 'Essentia · Base de datos';

function props_() { return PropertiesService.getScriptProperties(); }

function folder_(name, parent) {
  var it = parent ? parent.getFoldersByName(name) : DriveApp.getFoldersByName(name);
  if (it.hasNext()) return it.next();
  return parent ? parent.createFolder(name) : DriveApp.createFolder(name);
}

function root_() {
  var id = props_().getProperty('ROOT_ID');
  if (id) { try { return DriveApp.getFolderById(id); } catch (e) {} }
  var f = folder_(ROOT_NAME);
  props_().setProperty('ROOT_ID', f.getId());
  return f;
}

function sheet_() {
  var id = props_().getProperty('SHEET_ID');
  var ss = null;
  if (id) { try { ss = SpreadsheetApp.openById(id); } catch (e) { ss = null; } }
  if (!ss) {
    ss = SpreadsheetApp.create('Essentia · Reportes');
    DriveApp.getFileById(ss.getId()).moveTo(root_());
    props_().setProperty('SHEET_ID', ss.getId());
  }
  var sh = ss.getSheetByName('Reportes');
  if (!sh) {
    sh = ss.getSheets()[0];
    sh.setName('Reportes');
    sh.appendRow(['Fecha', 'Essentia', 'Equipo', 'Android', 'Juego', 'Versión del juego',
                  'Tipo de juego', 'Motor', 'Pantalla', 'Error', 'Detalle', 'Scripts', 'Id']);
    sh.setFrozenRows(1);
  }
  return sh;
}

/** Ejecútalo una vez desde el editor. Muestra la clave de respaldo en el registro. */
function setup() {
  root_();
  sheet_();
  folder_('Traducciones', root_());
  folder_('Respaldos', root_());
  folder_('Scripts de reportes', root_());
  var key = props_().getProperty('BACKUP_KEY');
  if (!key) {
    key = Utilities.getUuid().replace(/-/g, '').slice(0, 16);
    props_().setProperty('BACKUP_KEY', key);
  }
  Logger.log('Clave de respaldo: ' + key);
  return key;
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}

function doGet(e) {
  return json_({ ok: true, app: 'Essentia', version: 1 });
}

function doPost(e) {
  try {
    var req = JSON.parse(e.postData.contents);
    switch (req.action) {
      case 'report': return json_(report_(req));
      case 'translate': return json_(translate_(req));
      case 'tr_dump': return json_(trDump_(req));
      case 'backup_put': return json_(backupPut_(req));
      case 'backup_list': return json_(backupList_(req));
      case 'backup_get': return json_(backupGet_(req));
      case 'ping': return json_({ ok: true });
    }
    return json_({ ok: false, error: 'unknown action' });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

function clip_(s, n) { s = String(s == null ? '' : s); return s.length > n ? s.slice(0, n) + '…' : s; }

// ------------------------------------------------------------------ reports

function report_(r) {
  var id = Utilities.getUuid().slice(0, 8);
  var link = '';
  if (r.scripts) {
    var bytes = Utilities.base64Decode(r.scripts);
    var name = clip_(r.game, 60) + ' - ' + id + ' - Scripts.rxdata';
    var file = folder_('Scripts de reportes', root_())
      .createFile(Utilities.newBlob(bytes, 'application/octet-stream', name));
    link = file.getUrl();
  }
  sheet_().appendRow([new Date(), clip_(r.essentia, 20), clip_(r.device, 60), clip_(r.android, 10),
                      clip_(r.game, 100), clip_(r.gameVersion, 30), clip_(r.kind, 40), clip_(r.engine, 20),
                      clip_(r.screen, 20), clip_(r.error, 2000), clip_(r.detail, 20000), link, id]);
  return { ok: true, id: id };
}

// ------------------------------------------------------------------ translations

function gameKey_(g) {
  return String(g || 'juego').replace(/[^\w\- áéíóúñÁÉÍÓÚÑ]/g, '_').slice(0, 80) || 'juego';
}

function trFile_(game, lang) {
  var dir = folder_('Traducciones', root_());
  var name = gameKey_(game) + '.' + (lang || 'es') + '.json';
  var it = dir.getFilesByName(name);
  return it.hasNext() ? it.next() : dir.createFile(name, '{}', 'application/json');
}

function trLoad_(file) {
  try { return JSON.parse(file.getBlob().getDataAsString('UTF-8') || '{}'); } catch (e) { return {}; }
}

/** Translates a list in as few calls as possible (numbered lines). */
function translateList_(texts, lang) {
  var out = [];
  var batch = [], idx = [];
  function flush() {
    if (!batch.length) return;
    var joined = batch.map(function (t, i) { return '§' + (i + 1) + '§ ' + t; }).join('\n');
    var res = '';
    try { res = LanguageApp.translate(joined, '', lang); } catch (e) { res = ''; }
    var parts = {};
    var re = /§\s*(\d+)\s*§\s*([\s\S]*?)(?=§\s*\d+\s*§|$)/g, m;
    while ((m = re.exec(res)) !== null) parts[Number(m[1])] = m[2].trim();
    for (var i = 0; i < batch.length; i++) {
      var t = parts[i + 1];
      if (t == null || t === '') {
        try { t = LanguageApp.translate(batch[i], '', lang); } catch (e) { t = null; }
      }
      out[idx[i]] = t;
    }
    batch = []; idx = [];
  }
  var size = 0;
  for (var i = 0; i < texts.length; i++) {
    batch.push(texts[i]); idx.push(i); size += texts[i].length;
    if (batch.length >= 40 || size > 3500) { flush(); size = 0; }
  }
  flush();
  return out;
}

function translate_(r) {
  var lang = r.lang || 'es';
  var texts = (r.texts || []).slice(0, 200);
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var file = trFile_(r.game, lang);
    var cache = trLoad_(file);
    var missing = [];
    texts.forEach(function (t) { if (!(t in cache) && missing.indexOf(t) < 0) missing.push(t); });
    if (missing.length) {
      var done = translateList_(missing, lang);
      var changed = false;
      for (var i = 0; i < missing.length; i++) {
        if (done[i]) { cache[missing[i]] = done[i]; changed = true; }
      }
      if (changed) file.setContent(JSON.stringify(cache));
    }
    var res = {};
    texts.forEach(function (t) { if (t in cache) res[t] = cache[t]; });
    return { ok: true, translations: res };
  } finally {
    lock.releaseLock();
  }
}

function trDump_(r) {
  var file = trFile_(r.game, r.lang || 'es');
  return { ok: true, translations: trLoad_(file) };
}

// ------------------------------------------------------------------ backups (need the key)

function checkKey_(r) {
  var key = props_().getProperty('BACKUP_KEY');
  if (!key || r.key !== key) throw new Error('clave incorrecta');
}

function backupDir_(game) { return folder_(gameKey_(game), folder_('Respaldos', root_())); }

function backupPut_(r) {
  checkKey_(r);
  var dir = backupDir_(r.game);
  var stamp = Utilities.formatDate(new Date(), 'UTC', 'yyyyMMdd-HHmmss');
  var sessionDir = dir.createFolder(stamp);
  (r.files || []).forEach(function (f) {
    sessionDir.createFile(Utilities.newBlob(Utilities.base64Decode(f.data), 'application/octet-stream', f.name));
  });
  // keep the last 10 backups of each game
  var all = [], it = dir.getFolders();
  while (it.hasNext()) all.push(it.next());
  all.sort(function (a, b) { return a.getName() < b.getName() ? 1 : -1; });
  for (var i = 10; i < all.length; i++) all[i].setTrashed(true);
  return { ok: true, id: stamp };
}

function backupList_(r) {
  checkKey_(r);
  var dir = backupDir_(r.game), out = [], it = dir.getFolders();
  while (it.hasNext()) {
    var d = it.next(), files = [], fi = d.getFiles();
    while (fi.hasNext()) { var f = fi.next(); files.push({ name: f.getName(), size: f.getSize() }); }
    out.push({ id: d.getName(), files: files });
  }
  out.sort(function (a, b) { return a.id < b.id ? 1 : -1; });
  return { ok: true, backups: out };
}

function backupGet_(r) {
  checkKey_(r);
  var it = backupDir_(r.game).getFoldersByName(r.id);
  if (!it.hasNext()) return { ok: false, error: 'no existe' };
  var d = it.next(), files = [], fi = d.getFiles();
  while (fi.hasNext()) {
    var f = fi.next();
    files.push({ name: f.getName(), data: Utilities.base64Encode(f.getBlob().getBytes()) });
  }
  return { ok: true, files: files };
}
