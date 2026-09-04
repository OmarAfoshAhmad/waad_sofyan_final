export const isValidClaimQuantity = (value) => {
  const quantity = Number(value);
  return Number.isInteger(quantity) && quantity > 0;
};

export const invalidQuantityLineNumbers = (lines = []) =>
  lines
    .map((line, index) => ({ line, number: index + 1 }))
    .filter(({ line }) => (line?.service || line?.serviceName) && !isValidClaimQuantity(line.quantity))
    .map(({ number }) => number);

/**
 * A line's accepted amount is never sent by the server as its own field --
 * it is what's left of the requested total after everything refused from
 * it (price-excess refusal, limit-excess refusal, and any manual refusal,
 * whichever reading is larger). Never negative: a line cannot accept more
 * than it requested.
 */
export const lineAcceptedAmount = (line = {}) => {
  const total = Number(line.total) || 0;
  const refusedAmount = Number(line.refusedAmount) || 0;
  const priceRefused = Number(line.priceRefused) || 0;
  const limitRefused = Number(line.limitRefused) || 0;
  const refused = Math.max(refusedAmount, priceRefused + limitRefused);
  return Math.max(0, total - refused);
};
