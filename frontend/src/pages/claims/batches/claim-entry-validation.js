export const isValidClaimQuantity = (value) => {
  const quantity = Number(value);
  return Number.isInteger(quantity) && quantity > 0;
};

export const invalidQuantityLineNumbers = (lines = []) =>
  lines
    .map((line, index) => ({ line, number: index + 1 }))
    .filter(({ line }) => (line?.service || line?.serviceName) && !isValidClaimQuantity(line.quantity))
    .map(({ number }) => number);

