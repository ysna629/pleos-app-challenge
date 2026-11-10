import test from 'node:test';
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';
import { decodeRecord, mapLinks, smsBody, smsHref, validateRecord } from './record.mjs';

const trip = () => ({ v: 1, k: 'trip', s: 1761800000000, e: 1761803000000, a: 126000, b: 126124, p: 'B', spot: '지하 5층 A4', addr: '수정구 시흥동', lat: 37.4502, lng: 127.1465, ph: '01012345678', m: 'K' });
const park = () => ({ v: 1, k: 'park', e: 1761803000000, spot: '주차타워 3번', addr: '', m: 'N' });
const encode = data => Buffer.from(deflateRawSync(JSON.stringify(data))).toString('base64url');

test('decodes the link the car makes', async () => {
  const data = await decodeRecord(encode(trip()));
  assert.equal(data.spot, '지하 5층 A4');
  assert.equal(data.k, 'trip');
});

test('rejects records that are not ours', async () => {
  await assert.rejects(decodeRecord('not base64!'));
  assert.throws(() => validateRecord({ ...trip(), k: 'other' }));
  assert.throws(() => validateRecord({ ...trip(), lat: 95 }));
  assert.throws(() => validateRecord({ ...trip(), ph: 'abc' }));
  assert.throws(() => validateRecord({ ...trip(), lng: undefined }));
});

test('map buttons start with the car\'s choice and only exist with a position', () => {
  const links = mapLinks(trip());
  assert.deepEqual(links.map(l => l.key), ['K', 'N', 'G']);
  assert.ok(links[0].href.startsWith('https://map.kakao.com/link/map/'));
  assert.ok(links[0].href.endsWith('37.45020,127.14650'));
  assert.deepEqual(mapLinks(park()), []);
});

test('SMS text matches what the car used to send directly', () => {
  assert.equal(smsBody(trip(), 'Asia/Seoul'),
    '[Route94] 지하 5층 A4 · 수정구 시흥동\n10/30 14:43 · 12.4km 업무\nhttps://map.kakao.com/link/map/%EC%A3%BC%EC%B0%A8,37.45020,127.14650');
  assert.equal(smsBody(park(), 'Asia/Seoul'), '[Route94] 주차타워 3번\n10/30 14:43 주차');
});

test('SMS link addresses my number and uses the iOS separator on iPhone', () => {
  assert.ok(smsHref(trip(), 'Asia/Seoul', 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)').startsWith('sms:01012345678&body='));
  assert.ok(smsHref(trip(), 'Asia/Seoul', 'Mozilla/5.0 (Linux; Android 14)').startsWith('sms:01012345678?body='));
  assert.ok(smsHref(park(), 'Asia/Seoul', '').startsWith('sms:?body='));
});
