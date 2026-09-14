const prefixes = [
  'action', 'approval', 'aria', 'capability', 'diagnostic', 'disposition', 'editor', 'field',
  'material', 'notice', 'placeholder', 'procedure', 'reason', 'scenario', 'status', 'task'
];

// Keep the existing conservative key scan, but distinguish direct Java path
// arguments from localized text. Do not globally exempt filenames: a key such
// as localized("task.json", ...) still needs a translation.
export function collectLocalizationKeys(source, extension) {
  const pathArguments = new Set();
  if (extension === '.java') {
    const tokens = [...source.matchAll(
      /\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|"""[\s\S]*?"""|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'|[A-Za-z_$][\w$]*|[^\s]/g
    )].filter(token => !token[0].startsWith('//') && !token[0].startsWith('/*'));
    for (let index = 3; index < tokens.length; index++) {
      if (tokens[index - 1][0] !== '(' || tokens[index - 3][0] !== '.') continue;
      const method = tokens[index - 2][0];
      const owner = tokens[index - 4]?.[0];
      if (method === 'resolve' || method === 'resolveSibling'
          || (owner === 'Path' && method === 'of') || (owner === 'Paths' && method === 'get')) {
        pathArguments.add(tokens[index].index);
      }
    }
  }
  const keyPattern = new RegExp(`["']((?:${prefixes.join('|')})\\.[A-Za-z0-9_.-]+)["']`, 'g');
  const keys = new Set();
  for (const match of source.matchAll(keyPattern)) {
    if (!match[1].endsWith('.') && !pathArguments.has(match.index)) keys.add(match[1]);
  }
  return keys;
}
