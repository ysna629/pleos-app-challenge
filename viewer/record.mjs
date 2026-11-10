// One trip or parking note from the car, carried in the link fragment. Mirrors RecordLink.kt.

const PURPOSES = { C: '출퇴근', B: '업무', P: '비업무' };
const MAPS = {
  K: { label: '카카오맵', link: (lat, lng) => `https://map.kakao.com/link/map/${encodeURIComponent('주차')},${lat},${lng}` },
  N: { label: '네이버 지도', link: (lat, lng) => `https://m.map.naver.com/map.naver?lat=${lat}&lng=${lng}&pinTitle=${encodeURIComponent('주차')}&pinType=site&dlevel=11` },
  G: { label: '구글 지도', link: (lat, lng) => `https://www.google.com/maps/search/?api=1&query=${lat},${lng}` },
};

const str = (value, max) => typeof value === 'string' && value.length <= max;
const time = value => Number.isInteger(value) && value > 0 && value < 4102444800000;

export function validateRecord(data) {
  if (!data || data.v !== 1 || (data.k !== 'trip' && data.k !== 'park')) throw new Error('Unknown record');
  if (!time(data.e) || !str(data.spot ?? '', 200) || !str(data.addr ?? '', 200)) throw new Error('Invalid record');
  if (data.k === 'trip' && (!time(data.s) || !Number.isInteger(data.a) || !Number.isInteger(data.b) || !(data.p in PURPOSES))) throw new Error('Invalid trip');
  if ((data.lat === undefined) !== (data.lng === undefined)) throw new Error('Invalid position');
  if (data.lat !== undefined && !(Number.isFinite(data.lat) && Number.isFinite(data.lng) && Math.abs(data.lat) <= 90 && Math.abs(data.lng) <= 180)) throw new Error('Invalid position');
  if (data.ph !== undefined && !/^\d{10,15}$/.test(data.ph)) throw new Error('Invalid phone');
  if (data.m !== undefined && !(data.m in MAPS)) throw new Error('Invalid map');
  return data;
}

export async function decodeRecord(hash) {
  if (!hash || hash.length > 4096 || !/^[A-Za-z0-9_-]+$/.test(hash)) throw new Error('Invalid link');
  const b64 = hash.replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64 + '='.repeat((4 - b64.length % 4) % 4));
  const bytes = Uint8Array.from(bin, c => c.charCodeAt(0));
  const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
  const text = await new Response(stream).text();
  if (text.length > 16384) throw new Error('Link too large');
  return validateRecord(JSON.parse(text));
}

export function formatDate(at, zone = 'Asia/Seoul') {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: zone, year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(at));
}

export function shortDate(at, zone = 'Asia/Seoul') {
  const parts = Object.fromEntries(new Intl.DateTimeFormat('ko-KR', { timeZone: zone, month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(new Date(at)).map(p => [p.type, p.value]));
  return `${parts.month}/${parts.day} ${parts.hour}:${parts.minute}`;
}

export const km = tenths => `${(tenths / 10).toLocaleString('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}km`;

/** Every map the position opens in; the one the car chose first. */
export function mapLinks(data) {
  if (data.lat === undefined) return [];
  const lat = data.lat.toFixed(5), lng = data.lng.toFixed(5);
  const order = [data.m ?? 'K', ...Object.keys(MAPS).filter(k => k !== (data.m ?? 'K'))];
  return order.map(k => ({ key: k, label: MAPS[k].label, href: MAPS[k].link(lat, lng) }));
}

/** Same text the car used to put straight into the SMS composer. */
export function smsBody(data, zone) {
  const spot = data.spot || '주차 위치 기록 안 함';
  const place = data.addr ? ` · ${data.addr}` : '';
  const link = mapLinks(data)[0];
  const when = shortDate(data.e, zone);
  const line = data.k === 'trip' ? `${when} · ${km(Math.max(0, data.b - data.a))} ${PURPOSES[data.p]}` : `${when} 주차`;
  return `[Route94] ${spot}${place}\n${line}${link ? `\n${link.href}` : ''}`;
}

/** iOS wants `sms:number&body=`, everything else `sms:number?body=`. */
export function smsHref(data, zone, userAgent = '') {
  const ios = /iPhone|iPad|iPod/i.test(userAgent);
  return `sms:${data.ph ?? ''}${ios ? '&' : '?'}body=${encodeURIComponent(smsBody(data, zone))}`;
}

export const purposeLabel = code => PURPOSES[code] ?? '';
