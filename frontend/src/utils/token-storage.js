const TOKEN_KEY = 'serviceToken';

function safeGet(storage, key) {
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(storage, key, value) {
  try {
    storage.setItem(key, value);
  } catch {
    // Ignore storage failures and keep runtime stable.
  }
}

function safeRemove(storage, key) {
  try {
    storage.removeItem(key);
  } catch {
    // Ignore storage failures and keep runtime stable.
  }
}

export function getToken() {
  const sessionToken = safeGet(sessionStorage, TOKEN_KEY);
  if (sessionToken) return sessionToken;

  // Backward compatibility: migrate legacy localStorage token on first read.
  const legacyToken = safeGet(localStorage, TOKEN_KEY);
  if (legacyToken) {
    safeSet(sessionStorage, TOKEN_KEY, legacyToken);
    safeRemove(localStorage, TOKEN_KEY);
    return legacyToken;
  }

  return null;
}

export function setToken(token) {
  if (!token) return;
  safeSet(sessionStorage, TOKEN_KEY, token);
  // Remove token from localStorage to reduce XSS persistence risk.
  safeRemove(localStorage, TOKEN_KEY);
}

export function clearToken() {
  safeRemove(sessionStorage, TOKEN_KEY);
  safeRemove(localStorage, TOKEN_KEY);
}

export function hasToken() {
  return !!getToken();
}
