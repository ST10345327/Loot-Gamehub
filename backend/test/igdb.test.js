'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const q = require('../src/services/igdbQueries');
const { toGameSummary, toGameDetails } = require('../src/services/igdbMapper');
const { createGameService, sortGames } = require('../src/services/gameService');
const { parseSearchParams } = require('../src/services/searchParams');
const { IgdbClient } = require('../src/services/igdbClient');
const { TtlCache } = require('../src/utils/ttlCache');

// ---- query building -------------------------------------------------------------

test('search query escapes quotes and backslashes in the text', () => {
  assert.equal(q.escapeText('say "hi" \\ there'), 'say \\"hi\\" \\\\ there');
  const body = q.buildSearchQuery({ q: 'a"; drop', limit: 20, offset: 0 });
  assert.ok(body.includes('search "a\\"; drop";'));
});

test('search query combines filters with & and uses IGDB sorting only when browsing', () => {
  const browse = q.buildSearchQuery({ genre: 'rpg', platform: 'pc', minRating: 8, sort: 'rating', limit: 20, offset: 40 });
  assert.match(browse, /genres = \(12\)/);
  assert.match(browse, /platforms = \(6\)/);
  assert.match(browse, /total_rating >= 80/);
  assert.match(browse, /sort total_rating desc;/);
  assert.match(browse, /limit 20; offset 40;/);

  const text = q.buildSearchQuery({ q: 'zelda', sort: 'rating', limit: 20, offset: 0 });
  assert.ok(!text.includes('sort '), 'text search must not send a sort clause');
});

test('release period filters convert years to unix seconds', () => {
  const body = q.buildSearchQuery({ releasedAfter: 2020, releasedBefore: 2024, limit: 5, offset: 0 });
  assert.match(body, /first_release_date >= 1577836800/); // 2020-01-01
  assert.match(body, /first_release_date < 1704067200/); // 2024-01-01
});

test('discover queries depend on the category and the clock', () => {
  const now = 1_700_000_000;
  assert.match(q.buildDiscoverQuery('upcoming', now), new RegExp(`first_release_date > ${now}`));
  assert.match(q.buildDiscoverQuery('top-rated', now), /sort total_rating desc/);
  assert.throws(() => q.buildDiscoverQuery('nope', now), /invalid/i);
});

// ---- mapping --------------------------------------------------------------------

const rawGame = {
  id: 119133,
  name: 'Elden Ring',
  first_release_date: 1645747200, // 2022-02-25
  total_rating: 95.4,
  cover: { image_id: 'co4jni' },
  genres: [{ name: 'Role-playing (RPG)' }],
  platforms: [{ name: 'PC (Microsoft Windows)' }],
  summary: 'An action RPG.',
  involved_companies: [
    { developer: true, publisher: false, company: { name: 'FromSoftware' } },
    { developer: false, publisher: true, company: { name: 'Bandai Namco' } },
  ],
  screenshots: [{ image_id: 'sc1' }],
  similar_games: [{ id: 1, name: 'Dark Souls', cover: { image_id: 'c2' } }],
};

test('mapper converts an IGDB record to our summary JSON', () => {
  const g = toGameSummary(rawGame);
  assert.equal(g.rating, 9.5);
  assert.equal(g.releaseYear, 2022);
  assert.equal(g.releaseDate, '2022-02-25');
  assert.equal(g.coverUrl, 'https://images.igdb.com/igdb/image/upload/t_cover_big/co4jni.jpg');
  assert.deepEqual(g.genres, ['Role-playing (RPG)']);
});

test('mapper tolerates missing fields', () => {
  const g = toGameSummary({ id: 1, name: 'Bare' });
  assert.equal(g.rating, null);
  assert.equal(g.coverUrl, null);
  assert.equal(g.releaseYear, null);
  assert.deepEqual(g.platforms, []);
});

test('mapper splits developers and publishers', () => {
  const d = toGameDetails(rawGame);
  assert.deepEqual(d.developers, ['FromSoftware']);
  assert.deepEqual(d.publishers, ['Bandai Namco']);
  assert.equal(d.similarGames[0].name, 'Dark Souls');
  assert.match(d.screenshots[0], /t_screenshot_big\/sc1\.jpg$/);
});

// ---- query-string parsing -------------------------------------------------------

test('parseSearchParams applies defaults and rejects bad values', () => {
  const ok = parseSearchParams({ q: '  mario ', genre: 'rpg', minRating: '7' });
  assert.equal(ok.q, 'mario');
  assert.equal(ok.limit, 20);
  assert.equal(ok.offset, 0);
  assert.equal(ok.minRating, 7);

  assert.throws(() => parseSearchParams({ genre: 'constructor' }), (e) => e.code === 'VALIDATION_ERROR');
  assert.throws(() => parseSearchParams({ limit: '999' }), (e) => e.code === 'VALIDATION_ERROR');
  assert.throws(() => parseSearchParams({ sort: 'random' }), (e) => e.code === 'VALIDATION_ERROR');
});

// ---- game service (with a fake IGDB) --------------------------------------------

test('service sorts a text-search page itself', () => {
  const games = [
    { name: 'B', rating: 5, releaseDate: '2010-01-01' },
    { name: 'A', rating: null, releaseDate: null },
    { name: 'C', rating: 9, releaseDate: '2020-01-01' },
  ];
  assert.deepEqual(sortGames(games, 'rating').map((g) => g.name), ['C', 'B', 'A']);
  assert.deepEqual(sortGames(games, 'title').map((g) => g.name), ['A', 'B', 'C']);
  assert.deepEqual(sortGames(games, 'release').map((g) => g.name), ['C', 'B', 'A']);
});

test('service caches discovery rows and reports hasMore', async () => {
  let calls = 0;
  let clock = 0;
  const igdb = { query: async () => { calls += 1; return [rawGame]; } };
  const service = createGameService({ igdb, now: () => clock });

  await service.discover('popular');
  await service.discover('popular');
  assert.equal(calls, 1, 'second call is served from cache');

  clock += 11 * 60 * 1000; // past the 10 minute TTL
  await service.discover('popular');
  assert.equal(calls, 2);

  const page = await service.search({ q: 'x', limit: 1, offset: 0 });
  assert.equal(page.hasMore, true);
});

test('service random picks a position inside the match window', async () => {
  const seen = [];
  const igdb = {
    query: async (endpoint, body) => {
      seen.push(body);
      return endpoint === 'games/count' ? { count: 1000 } : [rawGame];
    },
  };
  const service = createGameService({ igdb, random: () => 0.5 });
  const game = await service.random({ genre: 'rpg' });
  assert.equal(game.name, 'Elden Ring');
  assert.match(seen[1], /offset 250;/); // 0.5 * min(1000, 500)

  const empty = createGameService({ igdb: { query: async () => ({ count: 0 }) } });
  await assert.rejects(() => empty.random({}), (e) => e.code === 'NOT_FOUND');
});

// ---- IGDB client (all HTTP calls use an injected fake transport) ------------------

function fakeFetch(handlers) {
  const calls = [];
  const impl = async (url, options) => {
    calls.push({ url, options });
    return handlers.shift()(url, options);
  };
  return { impl, calls };
}
const json = (status, body) => ({ ok: status < 400, status, json: async () => body, text: async () => JSON.stringify(body) });

test('client fetches a token once, then reuses it', async () => {
  const { impl, calls } = fakeFetch([
    () => json(200, { access_token: 'tok', expires_in: 3600 }),
    () => json(200, [{ id: 1 }]),
    () => json(200, [{ id: 2 }]),
  ]);
  const client = new IgdbClient({ clientId: 'test-client-id', clientSecret: 'test-client-secret', fetchImpl: impl });

  assert.deepEqual(await client.query('games', 'fields name;'), [{ id: 1 }]);
  assert.deepEqual(await client.query('games', 'fields name;'), [{ id: 2 }]);
  assert.equal(calls.filter((c) => c.url.includes('oauth2/token')).length, 1);
  assert.equal(calls.length, 3);
  assert.equal(calls[0].options.method, 'POST');
  assert.match(calls[1].url, /api\.igdb\.com\/v4\/games$/);
  assert.equal(calls[1].options.body, 'fields name;');
  assert.match(calls[1].options.headers.Authorization, /^Bearer tok$/);
  assert.equal(calls[1].options.signal instanceof AbortSignal, true);
});

test('client refreshes the token after a 401 and retries', async () => {
  const { impl } = fakeFetch([
    () => json(200, { access_token: 'old', expires_in: 3600 }),
    () => json(401, {}),
    () => json(200, { access_token: 'new', expires_in: 3600 }),
    () => json(200, [{ id: 9 }]),
  ]);
  const client = new IgdbClient({ clientId: 'test-client-id', clientSecret: 'test-client-secret', fetchImpl: impl });
  assert.deepEqual(await client.query('games', 'x'), [{ id: 9 }]);
});

test('client rejects OAuth and IGDB HTTP failures without exposing upstream bodies', async () => {
  const oauth = new IgdbClient({
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    fetchImpl: async () => json(401, { message: 'upstream detail' }),
  });
  await assert.rejects(() => oauth.query('games', 'x'), (error) => {
    assert.equal(error.code, 'UPSTREAM_ERROR');
    assert.equal(error.message.includes('upstream detail'), false);
    return true;
  });

  const { impl } = fakeFetch([
    () => json(200, { access_token: 'tok', expires_in: 3600 }),
    () => json(500, { message: 'upstream detail' }),
  ]);
  const igdb = new IgdbClient({ clientId: 'test-client-id', clientSecret: 'test-client-secret', fetchImpl: impl });
  await assert.rejects(() => igdb.query('games', 'x'), (error) => error.code === 'UPSTREAM_ERROR');
});

test('client rejects malformed or unexpected OAuth and IGDB responses', async () => {
  const malformedOAuth = new IgdbClient({
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    fetchImpl: async () => ({ ok: true, json: async () => { throw new SyntaxError('invalid JSON'); } }),
  });
  await assert.rejects(() => malformedOAuth.query('games', 'x'), (error) => error.code === 'UPSTREAM_ERROR');

  const invalidOAuth = new IgdbClient({
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    fetchImpl: async () => json(200, { access_token: '', expires_in: 3600 }),
  });
  await assert.rejects(() => invalidOAuth.query('games', 'x'), (error) => error.code === 'UPSTREAM_ERROR');

  let malformedCalls = 0;
  const malformedIgdb = new IgdbClient({
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    fetchImpl: async () => {
      malformedCalls += 1;
      return malformedCalls === 1
        ? json(200, { access_token: 'tok', expires_in: 3600 })
        : { ok: true, json: async () => { throw new SyntaxError('invalid JSON'); } };
    },
  });
  await assert.rejects(() => malformedIgdb.query('games', 'x'), (error) => error.code === 'UPSTREAM_ERROR');

  let unexpectedCalls = 0;
  const unexpectedIgdb = new IgdbClient({
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    fetchImpl: async () => {
      unexpectedCalls += 1;
      return json(200, unexpectedCalls === 1
        ? { access_token: 'tok', expires_in: 3600 }
        : { games: [] });
    },
  });
  await assert.rejects(() => unexpectedIgdb.query('games', 'x'), (error) => error.code === 'UPSTREAM_ERROR');
});

test('client rejects missing or invalid configuration without making requests', async () => {
  let calls = 0;
  const fetchImpl = async () => { calls += 1; };
  for (const config of [
    { clientId: null, clientSecret: null, fetchImpl },
    { clientId: 'test-client-id', clientSecret: '', fetchImpl },
    { clientId: 'test-client-id', clientSecret: 'test-client-secret', fetchImpl: null },
    { clientId: 'test-client-id', clientSecret: 'test-client-secret', fetchImpl, timeoutMs: 0 },
  ]) {
    const client = new IgdbClient(config);
    await assert.rejects(() => client.query('games', 'x'), (error) => error.code === 'UPSTREAM_NOT_CONFIGURED');
  }
  assert.equal(calls, 0);
});

test('client maps network failures and timeouts to upstream errors', async () => {
  const networkFailure = new IgdbClient({
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    fetchImpl: async () => { throw new Error('network unavailable'); },
  });
  await assert.rejects(() => networkFailure.query('games', 'x'), (error) => error.code === 'UPSTREAM_ERROR');

  const timeout = new IgdbClient({
    clientId: 'test-client-id',
    clientSecret: 'test-client-secret',
    timeoutMs: 5,
    fetchImpl: async (_url, options) => new Promise((_resolve, reject) => {
      options.signal.addEventListener('abort', () => {
        const error = new Error('aborted');
        error.name = 'AbortError';
        reject(error);
      }, { once: true });
    }),
  });
  await assert.rejects(
    () => timeout.query('games', 'x'),
    (error) => error.code === 'UPSTREAM_ERROR' && /timed out/i.test(error.message)
  );
});

test('TtlCache expires entries', () => {
  let t = 0;
  const cache = new TtlCache(1000, () => t);
  cache.set('a', 1);
  assert.equal(cache.get('a'), 1);
  t = 1001;
  assert.equal(cache.get('a'), undefined);
});