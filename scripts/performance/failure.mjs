export function sanitizeFailure(value, paths = []) {
  let message = String(value ?? 'Unknown measurement failure');
  for (const path of paths.filter(Boolean).sort((a, b) => b.length - a.length)) {
    for (const spelling of new Set([path, path.replaceAll('\\', '/'), path.replaceAll('/', '\\')]))
      message = message.split(spelling).join('<local-path>');
  }
  message = message.replace(/\bBearer\s+[^\s"']+/gi, 'Bearer <redacted>')
    .replace(/("?(?:token|password|secret|api[_-]?key|authorization)"?\s*[:=]\s*)("[^"]*"|'[^']*'|[^,\s]+)/gi, '$1<redacted>');
  return message.slice(0, 4000);
}
