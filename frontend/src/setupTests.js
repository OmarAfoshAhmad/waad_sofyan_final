import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Vitest VM pools can reuse one DOM across files; always isolate rendered trees.
afterEach(() => cleanup());
