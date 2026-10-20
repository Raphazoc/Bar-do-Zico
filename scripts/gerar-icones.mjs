// Gera os icones a partir de SVG editavel, usando o Playwright do projeto.
import { chromium } from '../apps/web/node_modules/playwright/index.mjs';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const directory = fileURLToPath(new URL('../assets/icons/', import.meta.url));
await mkdir(directory, { recursive: true });
const browser = await chromium.launch({ headless: true });
try {
  for (const training of [false, true]) {
    const name = training ? 'bar-do-zico-treinamento' : 'bar-do-zico';
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256">
  <rect x="8" y="8" width="240" height="240" rx="54" fill="#173f35"/>
  <rect x="15" y="15" width="226" height="226" rx="47" fill="none" stroke="#c8a76b" stroke-width="3"/>
  <path d="M163 101h19c36 0 36 66 0 66h-19" fill="none" stroke="#c8a76b" stroke-width="18" stroke-linejoin="round"/>
  <path d="M67 91h101v98c0 12-9 21-21 21H88c-12 0-21-9-21-21Z" fill="#c8a76b"/>
  <path d="M90 117v64m27-64v64m27-64v64" stroke="#fff4d8" stroke-opacity=".65" stroke-width="9" stroke-linecap="round"/>
  <path d="M70 106c-22-2-25-32-4-39 0-24 32-34 46-14 18-18 49-6 48 16 25-1 33 33 9 40H87c-6 15-17 10-17-3Z" fill="#fff7e5"/>
  ${training ? '<circle cx="203" cy="203" r="39" fill="#f1bd62" stroke="#173f35" stroke-width="7"/><path d="M183 185h40m-20 0v38" stroke="#173f35" stroke-width="10" stroke-linecap="round"/>' : ''}
</svg>`;
    await writeFile(`${directory}/${name}.svg`, svg);
    const page = await browser.newPage({ viewport: { width: 256, height: 256 }, deviceScaleFactor: 1 });
    await page.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block;width:100vw;height:100vh}</style>${svg}`);
    const sizes = [16, 24, 32, 48, 64, 128, 256];
    const images = [];
    for (const size of sizes) {
      await page.setViewportSize({ width: size, height: size });
      images.push(await page.screenshot({ omitBackground: true }));
    }
    const header = Buffer.alloc(6 + sizes.length * 16);
    header.writeUInt16LE(1, 2);
    header.writeUInt16LE(sizes.length, 4);
    let offset = header.length;
    sizes.forEach((size, i) => {
      const entry = 6 + i * 16;
      header[entry] = header[entry + 1] = size === 256 ? 0 : size;
      header.writeUInt16LE(1, entry + 4);
      header.writeUInt16LE(32, entry + 6);
      header.writeUInt32LE(images[i].length, entry + 8);
      header.writeUInt32LE(offset, entry + 12);
      offset += images[i].length;
    });
    await writeFile(`${directory}/${name}.ico`, Buffer.concat([header, ...images]));
    await writeFile(`${directory}/${name}.png`, images.at(-1));
    await page.close();
    console.log(`Icone gerado: assets/icons/${name}.ico`);
  }
} finally {
  await browser.close();
}
