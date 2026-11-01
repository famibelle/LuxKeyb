// node anim.js : rend anim.html et écrit les images de l'animation.
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage();
  p.on('pageerror', e => console.log('erreur:', e.message));
  await p.goto('file://' + require('path').resolve('anim.html'));
  await p.waitForFunction('window.IMAGES !== undefined', null, { timeout: 120000 });
  const imgs = await p.evaluate(() => window.IMAGES);
  require('fs').mkdirSync('images', { recursive: true });
  imgs.forEach((d, i) => require('fs').writeFileSync(`images/${String(i).padStart(2, '0')}.png`, Buffer.from(d.split(',')[1], 'base64')));
  console.log(imgs.length, 'images');
  await b.close();
})();
