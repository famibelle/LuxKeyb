// node anim.js [page.html] [dossier] : rend la page (anim.html par défaut) et écrit les images de l'animation (cinq_plans.html : cinq plans et nuages qui traversent).
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  p.on('pageerror', e => console.log('erreur:', e.message));
  await p.goto('file://' + require('path').resolve(process.argv[2] || 'anim.html'));
  await p.waitForFunction('window.IMAGES !== undefined', null, { timeout: 120000 });
  const imgs = await p.evaluate(() => window.IMAGES);
  require('fs').mkdirSync(process.argv[3] || 'images', { recursive: true });
  imgs.forEach((d, i) => require('fs').writeFileSync(`${process.argv[3] || 'images'}/${String(i).padStart(2, '0')}.png`, Buffer.from(d.split(',')[1], 'base64')));
  console.log(imgs.length, 'images');
  await b.close();
})();
