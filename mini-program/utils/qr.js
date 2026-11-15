function parseUserNo(value) {
  if (typeof value !== 'string') return '';
  const raw = value.trim();
  // 先按文本解析，保留 0003、长整数和普通数字编号，避免 JSON 数字转换丢失精度。
  if (/^[A-Za-z0-9_-]{1,64}$/.test(raw)) return raw;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed) ||
        parsed.type && parsed.type !== 'bind') return '';
    const candidate = parsed.userNo;
    if (typeof candidate !== 'string') return '';
    return /^[A-Za-z0-9_-]{1,64}$/.test(candidate) ? candidate : '';
  } catch (error) {
    return '';
  }
}

function parsePcTicket(value) {
  if (typeof value !== 'string' || value.length > 1024) return '';
  try {
    const parsed = JSON.parse(value);
    // PC 登录必须是显式类型 + 随机凭证，绝不把纯用户编号当作登录票据。
    if (!parsed || parsed.type !== 'pc-login' || typeof parsed.ticket !== 'string') return '';
    return /^[A-Za-z0-9_-]{24,256}$/.test(parsed.ticket) ? parsed.ticket : '';
  } catch (error) {
    return '';
  }
}
module.exports = { parseUserNo, parsePcTicket };
