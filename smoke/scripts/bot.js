// Minimal protocol client for the CI smoke test (26.1.x only: mineflayer data does not cover 26.2/26.3 yet).
// Reads commands from stdin: "chat <text>", "raw_held <slot>", "quit". Prints events to stdout.
const mineflayer = require('mineflayer');
const [host, port, username, version] = process.argv.slice(2);
const bot = mineflayer.createBot({ host, port: Number(port), username, version, auth: 'offline' });
const out = (line) => process.stdout.write(line + '\n');

bot.once('spawn', () => out('BOT_SPAWN'));
bot.on('message', (m) => out('CHAT ' + m.toString().replace(/\s*\n\s*/g, ' | ')));
bot.on('kicked', (reason) => out('BOT_KICKED ' + JSON.stringify(reason).slice(0, 300)));
bot.on('error', (e) => out('BOT_ERROR ' + String(e && e.message).slice(0, 300)));
bot.on('end', (reason) => { out('BOT_END ' + reason); setTimeout(() => process.exit(0), 200); });

let buffer = '';
process.stdin.on('data', (chunk) => {
  buffer += chunk.toString();
  let idx;
  while ((idx = buffer.indexOf('\n')) >= 0) {
    const line = buffer.slice(0, idx).trim();
    buffer = buffer.slice(idx + 1);
    if (line.startsWith('chat ')) bot.chat(line.slice(5));
    else if (line.startsWith('raw_held ')) {
      try { bot._client.write('held_item_slot', { slotId: Number(line.slice(9)) }); out('RAW_HELD_SENT'); }
      catch (e) { out('RAW_HELD_ERROR ' + e.message); }
    } else if (line === 'quit') bot.quit();
  }
});
