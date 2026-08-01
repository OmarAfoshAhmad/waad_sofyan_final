import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';

describe('frontend test infrastructure smoke test', () => {
  it('renders a component and asserts on jsdom output', () => {
    render(<div data-testid="smoke">ok</div>);
    expect(screen.getByTestId('smoke')).toHaveTextContent('ok');
  });
});
