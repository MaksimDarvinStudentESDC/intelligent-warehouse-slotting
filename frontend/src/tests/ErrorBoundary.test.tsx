
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import ErrorBoundary from '../components/feedback/ErrorBoundary';

const originalConsoleError = console.error;

afterEach(() => {
  cleanup();
  console.error = originalConsoleError;
});

function BrokenComponent({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) {
    throw new Error('Test render failure');
  }

  return <p>Recovered content</p>;
}

describe('ErrorBoundary', () => {
  it('renders children normally', () => {
    render(
        <ErrorBoundary>
          <p>Normal content</p>
        </ErrorBoundary>
    );

    expect(screen.getByText('Normal content')).toBeTruthy();
  });

  it('shows a fallback when a child throws', () => {
    console.error = vi.fn();

    render(
        <ErrorBoundary>
          <BrokenComponent shouldThrow />
        </ErrorBoundary>
    );

    expect(screen.getByRole('alert')).toBeTruthy();
    expect(screen.getByText('Something went wrong')).toBeTruthy();
  });

  it('retries rendering when the underlying problem is resolved', () => {
    console.error = vi.fn();

    let broken = true;

    function Recoverable() {
      if (broken) {
        throw new Error('Temporary test failure');
      }

      return <p>Recovered content</p>;
    }

    render(
        <ErrorBoundary>
          <Recoverable />
        </ErrorBoundary>
    );

    broken = false;

    fireEvent.click(
        screen.getByRole('button', { name: /try again/i })
    );

    expect(screen.getByText('Recovered content')).toBeTruthy();
  });
});
