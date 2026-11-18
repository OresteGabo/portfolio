import { importBondPdf } from './pdf-import.js';
let controller;
let chunks = [];
const reply = (id, data) => Gabo.result(id, JSON.stringify(data));
window.gabo = {
  append(chunk) { chunks.push(chunk); },
  async run(id, command, payload) {
    try {
      if (command === 'projection') {
        reply(id, PortfolioLogic.project(payload));
      } else if (command === 'pdf') {
        const raw = atob(chunks.join('')); chunks = [];
        const bytes = Uint8Array.from(raw, c => c.charCodeAt(0));
        const file = new File([bytes], 'purchase.pdf', { type: 'application/pdf' });
        controller = new AbortController();
        const result = await importBondPdf(file, controller.signal, message => Gabo.progress(message), bytes);
        reply(id, result);
      }
    } catch (error) {
      chunks = [];
      reply(id, { error: command === 'pdf' ? 'Could not read this purchase PDF. Use a BNR result or RSE purchase contract note, up to 10 MB and 10 pages. You can enter the purchase manually.' : 'The projection could not be calculated.' });
    } finally { controller = undefined; }
  },
  cancel() { controller?.abort(); chunks = []; }
};
Gabo.ready();
