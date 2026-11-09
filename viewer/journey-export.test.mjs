import test from 'node:test';
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';
import { buildCsv, decodeJourney, exportFilename, validateJourney } from './journey-export.mjs';

const fixture = () => ({ v: 1, kind: 'journey', title: '가족, 나들이', startedAt: 0, endedAt: null,
  exportedAt: 420000, zone: 'Asia/Seoul', km: 0, sample: true, offset: 0, part: 1, parts: 1, total: 3,
  stops: [
    [60000,180000,37.45,-127.14,'숲길 "식당"','서울',true,'첫 줄\n둘째 줄',false],
    [240000,null,null,null,'=1+1','',false,'  @SUM(1,2)',false],
    [300000,360000,null,null,'','',false,'',true],
  ] });

test('CSV preserves Korean, multiline fields and numeric coordinates', () => {
  const csv = buildCsv(fixture());
  assert.ok(csv.startsWith('\uFEFF'));
  assert.ok(csv.includes('"숲길 ""식당"""'));
  assert.ok(csv.includes('"첫 줄\n둘째 줄"'));
  assert.ok(csv.includes('"-127.14"'));
  assert.ok(csv.includes('"1970-01-01 09:01:00"'));
  assert.ok(csv.includes('"예시"'));
});

test('formula text is neutralized and unknown visit duration stays blank', () => {
  const csv = buildCsv(fixture());
  assert.ok(csv.includes('"\'=1+1"'));
  assert.ok(csv.includes('"\'  @SUM(1,2)"'));
  assert.ok(csv.includes('"3","머무는 중"'));
  assert.ok(csv.includes('"","기록 중단"'));
});

test('split files preserve original visit numbers and show part in filename', () => {
  const data = {...fixture(), part: 2, parts: 2, offset: 2, total: 3, stops: [fixture().stops[2]]};
  assert.ok(buildCsv(data).includes('"3","",""'));
  assert.ok(exportFilename(data).endsWith('_part_2_of_2.csv'));
  assert.ok(exportFilename(data).startsWith('예시_여행기록_1970-01-01_'));
});

test('invalid or unsupported payloads are rejected', () => {
  for (const patch of [{v:2},{kind:'logbook'},{stops:null},{zone:'Invalid/Zone'}, {parts:0}, {offset:10}, {km:NaN}]) {
    assert.throws(() => validateJourney({...fixture(), ...patch}));
  }
  const data = fixture(); data.stops[0][2] = 91;
  assert.throws(() => validateJourney(data));
  assert.throws(() => validateJourney({...fixture(), stops: [[1]]}));
});

test('raw deflate links decode and oversized/bad input is handled', async () => {
  const data = fixture();
  const hash = deflateRawSync(JSON.stringify(data)).toString('base64url');
  assert.deepEqual(await decodeJourney(hash), data);
  await assert.rejects(decodeJourney('bad!'));
  await assert.rejects(decodeJourney(''));
  await assert.rejects(decodeJourney('a'.repeat(9000)));
  const bomb = deflateRawSync(' '.repeat(300000)).toString('base64url');
  await assert.rejects(decodeJourney(bomb));
});

test('Kakao links point to recorded parking coordinates with safely encoded labels', async () => {
  const {kakaoMapLink} = await import('./journey-export.mjs');
  assert.equal(typeof kakaoMapLink, 'function');
  const stop = fixture().stops[0];
  assert.equal(kakaoMapLink(stop), `https://map.kakao.com/link/map/${encodeURIComponent('숲길 "식당"')},37.45,-127.14`);
});

test('Kakao links use address search without GPS and omit unlocatable visits', async () => {
  const {kakaoMapLink} = await import('./journey-export.mjs');
  assert.equal(typeof kakaoMapLink, 'function');
  const stop = [...fixture().stops[0]];
  stop[2] = stop[3] = null;
  assert.equal(kakaoMapLink(stop), `https://map.kakao.com/link/search/${encodeURIComponent('서울 숲길 "식당"')}`);
  stop[4] = stop[5] = '';
  assert.equal(kakaoMapLink(stop), null);
});

test('app links carry the saved coordinates directly without a web/store redirect', async () => {
  const {kakaoAppLink} = await import('./journey-export.mjs');
  assert.equal(typeof kakaoAppLink, 'function');
  assert.equal(kakaoAppLink(fixture().stops[0]), 'kakaomap://look?p=37.45,-127.14');
  const stop = [...fixture().stops[0]]; stop[2] = stop[3] = null;
  assert.equal(kakaoAppLink(stop), `kakaomap://search?q=${encodeURIComponent('서울 숲길 "식당"')}`);
  stop[4] = stop[5] = '';
  assert.equal(kakaoAppLink(stop), null);
});
