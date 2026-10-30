// Planche de contrôle d'un lieu : node capture.js écrit planche.png et couleurs.json,
// puis python3 vegetal.py dit quelles couleurs Habillage prendrait pour de la verdure.
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1250, height: 900 }, deviceScaleFactor: 1 });
  p.on('console', m => console.log('console:', m.text()));
  p.on('pageerror', e => console.log('erreur:', e.message));
  await p.goto('file://' + __dirname + '/planche.html');
  await p.waitForFunction('window.PRET === true');
  await p.screenshot({ path: 'planche.png', fullPage: true });
  const coul = await p.evaluate(() => window.COULEURS_PAR_HEURE);
  require('fs').writeFileSync('couleurs.json', JSON.stringify(coul));
  await b.close();
})();
