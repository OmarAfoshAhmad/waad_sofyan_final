// Bumped on every successful login/logout so the axios 401 handler can tell
// a request belongs to a session that's no longer the current one, instead of
// treating it as proof the current (fresh) session just expired.
let epoch = 0;

export const getSessionEpoch = () => epoch;

export const bumpSessionEpoch = () => {
  epoch += 1;
  return epoch;
};
