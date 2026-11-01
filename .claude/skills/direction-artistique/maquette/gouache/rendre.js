// node rendre.js page.html sortie.png : écrit window.SORTIE (data URL) dans un fichier.
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  p.on('pageerror', e => console.log('erreur:', e.message));
  await p.goto('file://' + require('path').resolve(process.argv[2]));
  await p.waitForFunction('window.SORTIE !== undefined', null, { timeout: 60000 });
  for (const [v, f] of [['SORTIE2', process.argv[4]], ['SORTIE3', process.argv[5]], ['SORTIE4', process.argv[6]]]) if (f) { const d2 = await p.evaluate(n => window[n], v); require('fs').writeFileSync(f, Buffer.from(d2.split(',')[1], 'base64')); }
  const d = await p.evaluate(() => window.SORTIE);
  require('fs').writeFileSync(process.argv[3], Buffer.from(d.split(',')[1], 'base64'));
  await b.close();
})();
