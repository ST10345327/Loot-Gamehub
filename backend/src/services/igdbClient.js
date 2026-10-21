'use strict';

const { Errors } = require('../utils/errors');

const API_BASE_URL = 'https://api.igdb.com/v4';
const OAUTH_URL = 'https://id.twitch.tv/oauth2/token';

/**
 * Small IGDB API client.
 *
 * The client:
 * 1. Gets a Twitch OAuth access token.
 * 2. Uses that token to call IGDB.
 * 3. Automatically refreshes the token when necessary.
 *
 * Credentials are supplied through config.igdb.
 */
class IgdbClient {
  constructor({ clientId, clientSecret, fetchImpl = globalThis.fetch, timeoutMs = 10_000 }) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.fetchImpl = fetchImpl;
    this.timeoutMs = timeoutMs;

    this.accessToken = null;
    this.tokenExpiresAt = 0;

    this.isConfigured =
      typeof clientId === 'string' &&
      clientId.trim().length > 0 &&
      typeof clientSecret === 'string' &&
      clientSecret.trim().length > 0 &&
      typeof fetchImpl === 'function' &&
      Number.isFinite(timeoutMs) &&
      timeoutMs > 0;
  }

  async request(url, options) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    try {
      return await this.fetchImpl(url, { ...options, signal: controller.signal });
    } catch (error) {
      if (error && error.name === 'AbortError') {
        throw Errors.upstream('The game service request timed out.');
      }
      throw Errors.upstream();
    } finally {
      clearTimeout(timeout);
    }
  }

  async responseJson(response) {
    try {
      return await response.json();
    } catch {
      throw Errors.upstream('The game service returned an invalid response.');
    }
  }

  /**
   * Gets a valid Twitch access token.
   */
  async getAccessToken() {
    if (!this.isConfigured) {
      throw Errors.notConfigured();
    }

    // Reuse the existing token if it has not expired.
    if (
      this.accessToken &&
      Date.now() < this.tokenExpiresAt
    ) {
      return this.accessToken;
    }

    const params = new URLSearchParams({
      client_id: this.clientId,
      client_secret: this.clientSecret,
      grant_type: 'client_credentials',
    });

    const response = await this.request(`${OAUTH_URL}?${params.toString()}`, {
      method: 'POST',
    });

    if (!response.ok) {
      throw Errors.upstream();
    }

    const data = await this.responseJson(response);
    if (
      !data ||
      typeof data.access_token !== 'string' ||
      data.access_token.length === 0 ||
      !Number.isFinite(Number(data.expires_in)) ||
      Number(data.expires_in) <= 0
    ) {
      throw Errors.upstream('The game service returned an invalid authentication response.');
    }

    this.accessToken = data.access_token;

    // Refresh slightly before the actual expiry time.
    const expiresInMs = Number(data.expires_in) * 1000;
    this.tokenExpiresAt = Date.now() + expiresInMs - 60_000;

    return this.accessToken;
  }

  /**
   * Sends an Apicalypse query to IGDB.
   *
   * Example:
   *   igdb.query('games', 'fields name; limit 10;')
   */
  async query(endpoint, queryBody) {
    if (!this.isConfigured) {
      throw Errors.notConfigured();
    }

    const accessToken = await this.getAccessToken();

    const response = await this.request(`${API_BASE_URL}/${endpoint}`, {
      method: 'POST',
      headers: {
        'Client-ID': this.clientId,
        Authorization: `Bearer ${accessToken}`,
        'Content-Type': 'text/plain',
      },
      body: queryBody,
    });

    // If the token has expired unexpectedly, clear it and retry once.
    if (response.status === 401) {
      this.accessToken = null;
      this.tokenExpiresAt = 0;

      const newToken = await this.getAccessToken();

      const retryResponse = await this.request(`${API_BASE_URL}/${endpoint}`, {
        method: 'POST',
        headers: {
          'Client-ID': this.clientId,
          Authorization: `Bearer ${newToken}`,
          'Content-Type': 'text/plain',
        },
        body: queryBody,
      });

      if (!retryResponse.ok) {
        throw Errors.upstream();
      }

      return this.parseQueryResponse(await this.responseJson(retryResponse));
    }

    if (!response.ok) {
      throw Errors.upstream();
    }

    return this.parseQueryResponse(await this.responseJson(response));
  }

  parseQueryResponse(data) {
    if (
      Array.isArray(data) ||
      (data && typeof data === 'object' && Number.isInteger(data.count) && data.count >= 0)
    ) {
      return data;
    }
    throw Errors.upstream('The game service returned an unexpected response.');
  }
}

module.exports = { IgdbClient };