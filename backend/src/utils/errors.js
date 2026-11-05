'use strict';

/**
 * An error that is safe to show to the client. Anything that is NOT an ApiError is treated
 * as an unexpected bug and answered with a generic 500 (see middleware/errorHandler.js).
 *
 * The `code` is what the Android app switches on to show a translated message,
 * so codes are part of the API contract.
 */
class ApiError extends Error {
  constructor(status, code, message, details) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

const Errors = {
  validation: (details) =>
    new ApiError(400, 'VALIDATION_ERROR', 'Some fields are invalid.', details),
  unauthorized: (message = 'Authentication is required.') =>
    new ApiError(401, 'UNAUTHORIZED', message),
  tokenExpired: () =>
    new ApiError(401, 'TOKEN_EXPIRED', 'Your session has expired. Please sign in again.'),
  invalidCredentials: () =>
    new ApiError(401, 'INVALID_CREDENTIALS', 'Incorrect email or password.'),
  wrongPassword: () =>
    new ApiError(400, 'WRONG_PASSWORD', 'The current password is incorrect.'),
  emailTaken: () =>
    new ApiError(409, 'EMAIL_TAKEN', 'An account with this email already exists.'),
  usernameTaken: () =>
    new ApiError(409, 'USERNAME_TAKEN', 'This username is already taken.'),
  conflict: (message = 'Conflict occurred.') =>
    new ApiError(409, 'CONFLICT', message),
  notFound: (message = 'Not found.') => new ApiError(404, 'NOT_FOUND', message),
  internal: (message = 'An internal server error occurred.') =>
    new ApiError(500, 'INTERNAL_ERROR', message),
  upstream: (message = 'The game service is temporarily unavailable.') =>
    new ApiError(502, 'UPSTREAM_ERROR', message),
  notConfigured: () =>
    new ApiError(503, 'UPSTREAM_NOT_CONFIGURED', 'The game service is not configured on this server.'),
};

/** Express 4 does not catch rejected promises, so async handlers are wrapped with this. */
const asyncHandler = (fn) => (req, res, next) =>
  Promise.resolve(fn(req, res, next)).catch(next);

module.exports = { ApiError, Errors, asyncHandler };