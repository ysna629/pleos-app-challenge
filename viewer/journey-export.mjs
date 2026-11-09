const HEADERS = ['기록 구분','여행명','여행 시작','여행 종료','시간대','GPS 수집 거리(km)',
  '방문 순서','장소명','주소','도착 시각','출발 시각','체류 시간(분)','기록 상태','즐겨찾기','메모','위도','경도'];

export function validateJourney(data) {
  const integer = (n, min = 0) => Number.isSafeInteger(n) && n >= min;
  const date = (n) => integer(n) && n <= 8640000000000000;
  const str = (s, max) => typeof s === 'string' && s.length <= max;
  const fail = () => { throw new Error('Invalid journey data'); };
  if (!data || data.v !== 1 || data.kind !== 'journey' || !str(data.title, 2000) || !date(data.startedAt)
    || (data.endedAt !== null && (!date(data.endedAt) || data.endedAt < data.startedAt))
    || !date(data.exportedAt) || !str(data.zone, 100) || !Number.isFinite(data.km) || data.km < 0
    || typeof data.sample !== 'boolean' || !integer(data.part, 1) || !integer(data.parts, 1) || data.part > data.parts
    || !integer(data.offset) || !integer(data.total) || !Array.isArray(data.stops) || data.stops.length > 1000
    || data.offset + data.stops.length > data.total
    || (data.part === 1 && data.offset !== 0)
    || (data.part === data.parts && data.offset + data.stops.length !== data.total)) fail();
  new Intl.DateTimeFormat('en', {timeZone: data.zone}).format();
  for (const stop of data.stops) {
    if (!Array.isArray(stop) || stop.length !== 9) fail();
    const [arrive, depart, lat, lng, name, address, favorite, note, interrupted] = stop;
    if (!date(arrive) || (depart !== null && (!date(depart) || depart < arrive))
      || !((lat === null && lng === null) || (Number.isFinite(lat) && Number.isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180))
      || !str(name, 4000) || !str(address, 4000) || !str(note, 8000)
      || typeof favorite !== 'boolean' || typeof interrupted !== 'boolean') fail();
  }
  return data;
}

export async function decodeJourney(hash) {
  if (!hash || hash.length > 8192 || !/^[A-Za-z0-9_-]+$/.test(hash)) throw new Error('Invalid link');
  const b64 = hash.replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64 + '='.repeat((4 - b64.length % 4) % 4));
  const bytes = Uint8Array.from(bin, c => c.charCodeAt(0));
  const reader = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw')).getReader();
  const chunks = [];
  let length = 0;
  try {
    for (;;) {
      const {value, done} = await reader.read();
      if (done) break;
      length += value.length;
      if (length > 262144) { await reader.cancel(); throw new Error('Link too large'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  return validateJourney(JSON.parse(await new Blob(chunks).text()));
}

export function formatDate(at, zone) {
  if (at === null) return '';
  const parts = new Intl.DateTimeFormat('en-GB', {timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit',
    hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).formatToParts(new Date(at));
  const p = Object.fromEntries(parts.map(v => [v.type, v.value]));
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}:${p.second}`;
}

export function visitRows(data) {
  return data.stops.map(([arrive, depart, lat, lng, name, address, favorite, note, interrupted], index) => [
    data.sample ? '예시' : '실제', data.title, formatDate(data.startedAt, data.zone), formatDate(data.endedAt, data.zone),
    data.zone, data.km.toFixed(3), data.offset + index + 1, name, address, formatDate(arrive, data.zone),
    interrupted ? '' : formatDate(depart, data.zone), interrupted ? '' : Math.floor(Math.max(0, (depart ?? data.exportedAt) - arrive) / 60000),
    interrupted ? '기록 중단' : depart === null ? '머무는 중' : '완료', favorite ? '예' : '아니오', note, lat, lng,
  ]);
}

function cell(value) {
  let text = value === null || value === undefined ? '' : String(value);
  if (typeof value === 'string' && (/^[\t\r\n]/.test(text) || /^[=+@-]/.test(text.trimStart()))) text = "'" + text;
  return `"${text.replace(/"/g, '""')}"`;
}

export function buildCsv(data) {
  validateJourney(data);
  return '\uFEFF' + [HEADERS, ...visitRows(data)].map(row => row.map(cell).join(',')).join('\r\n') + '\r\n';
}

export function exportFilename(data) {
  const title = data.title.replace(/[\\/:*?"<>|\u0000-\u001f\u007f]/g, '_').slice(0,40) || '여행';
  return `${data.sample ? '예시_' : ''}여행기록_${formatDate(data.startedAt, data.zone).slice(0,10)}_${title}${data.parts > 1 ? `_part_${data.part}_of_${data.parts}` : ''}.csv`;
}

/** Recorded coordinates are parking positions, not confirmed venue entrances. */
export function kakaoMapLink(stop) {
  const [, , lat, lng, name, address] = stop;
  if (Number.isFinite(lat) && Number.isFinite(lng) && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180) {
    return `https://map.kakao.com/link/map/${encodeURIComponent(name || '주차 위치')},${lat},${lng}`;
  }
  const query = [address, name].filter(Boolean).join(' ').trim();
  return query ? `https://map.kakao.com/link/search/${encodeURIComponent(query)}` : null;
}

/** Explicit user-initiated app opening keeps coordinates attached to the action. */
export function kakaoAppLink(stop) {
  const web = kakaoMapLink(stop);
  if (!web) return null;
  if (web.includes('/link/map/')) return `kakaomap://look?p=${stop[2]},${stop[3]}`;
  return `kakaomap://search?q=${web.split('/link/search/')[1]}`;
}
