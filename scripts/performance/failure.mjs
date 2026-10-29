const REDACTED = '<redacted>';
const SENSITIVE_KEY = /^(?:token|password|secret|api[_-]?key|authorization)$/i;

function quotedEnd(text, start) {
  const quote = text[start];
  for (let index = start + 1; index < text.length; index++) {
    if (text[index] === '\\') index++;
    else if (text[index] === quote) return index + 1;
  }
  return null;
}

function decodeQuoted(token) {
  if (token[0] === '"') {
    try { return JSON.parse(token); } catch { /* Plain logs may use non-JSON escapes. */ }
  }
  return token.slice(1, -1).replace(/\\(['"\\])/g, '$1');
}

function encodeQuoted(value, quote) {
  if (quote === '"') return JSON.stringify(value);
  return `'${value.replaceAll('\\', '\\\\').replaceAll("'", "\\'")
    .replaceAll('\r', '\\r').replaceAll('\n', '\\n').replaceAll('\t', '\\t')}'`;
}

// A sensitive field may contain a whole object/array, or a quoted credential
// with escaped quotes. Consume its complete value rather than leaving a suffix.
function valueEnd(text, start) {
  if (text[start] === '"' || text[start] === "'") return quotedEnd(text, start) ?? text.length;
  if (text[start] === '{' || text[start] === '[') {
    const stack = [];
    for (let index = start; index < text.length; index++) {
      const character = text[index];
      if (character === '"' || character === "'") index = (quotedEnd(text, index) ?? text.length) - 1;
      else if (character === '{' || character === '[') stack.push(character === '{' ? '}' : ']');
      else if (character === stack.at(-1)) {
        stack.pop();
        if (!stack.length) return index + 1;
      }
    }
    return text.length;
  }
  const authentication = /^(?:Bearer|Basic)\s+/i.exec(text.slice(start));
  if (authentication) return valueEnd(text, start + authentication[0].length);
  let end = start;
  while (end < text.length && !/[\s,;}\]]/.test(text[end])) end++;
  return end;
}

export function sanitizeFailure(value, paths = []) {
  const spellings = [...new Set(paths.filter(Boolean).flatMap(path =>
    [path, path.replaceAll('\\', '/'), path.replaceAll('/', '\\')]))].sort((a, b) => b.length - a.length);
  const redactPaths = text => spellings.reduce((result, path) => result.split(path).join('<local-path>'), text);

  function sanitizeStructured(value) {
    if (typeof value === 'string') return sanitizeContent(value);
    if (Array.isArray(value)) return value.map(sanitizeStructured);
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, entry]) =>
      [redactPaths(key), SENSITIVE_KEY.test(key) ? REDACTED : sanitizeStructured(entry)]));
    return value;
  }

  function sanitizeContent(text) {
    let parsed;
    try { parsed = JSON.parse(text); }
    catch { return sanitizeText(redactPaths(text)); }
    return JSON.stringify(sanitizeStructured(parsed));
  }

  function sanitizeText(text) {
    let result = '';
    for (let index = 0; index < text.length;) {
      const character = text[index];
      const quoted = (character === '"' || character === "'") && !/[A-Za-z0-9_]/.test(text[index - 1] ?? '');
      const word = quoted ? null : /^[A-Za-z_][A-Za-z0-9_-]*/.exec(text.slice(index));
      if (!quoted && !word) { result += character; index++; continue; }
      const closingQuote = quoted ? quotedEnd(text, index) : null;
      const end = quoted ? closingQuote ?? text.length : index + word[0].length;
      const token = text.slice(index, end);
      const closed = closingQuote !== null;
      const decoded = quoted ? closed ? decodeQuoted(token) : token.slice(1) : token;
      let separator = end;
      while (/\s/.test(text[separator] ?? '') && separator < text.length) separator++;
      if (SENSITIVE_KEY.test(decoded) && [':', '='].includes(text[separator])) {
        let start = separator + 1;
        while (/\s/.test(text[start] ?? '') && start < text.length) start++;
        result += text.slice(index, start) + REDACTED;
        index = valueEnd(text, start);
      } else if (!quoted && /^Bearer$/i.test(token) && separator > end && separator < text.length) {
        result += text.slice(index, separator) + REDACTED;
        index = valueEnd(text, separator);
      } else {
        const sanitized = quoted ? sanitizeContent(decoded) : decoded;
        result += quoted && sanitized !== decoded ? closed ? encodeQuoted(sanitized, character) : character + sanitized : token;
        index = end;
      }
    }
    return result;
  }

  return sanitizeContent(String(value ?? 'Unknown measurement failure')).slice(0, 4000);
}
