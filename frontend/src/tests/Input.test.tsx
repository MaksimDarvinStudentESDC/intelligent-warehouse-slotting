import {afterEach, describe, expect, it} from 'vitest';
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import Input from '../components/ui/Input';

afterEach(cleanup);
describe('Input', () => {
  it('associates label with its input', () => {
    render(<Input id="sku" label="SKU" />);
    const input = screen.getByRole('textbox', {name: 'SKU'}) as HTMLInputElement;
    fireEvent.change(input, {target: {value: 'ABC-123'}});
    expect(input.value).toBe('ABC-123');
  });
  it('shows a validation error accessibly', () => {
    render(<Input id="sku" label="SKU" error="SKU is required" hint="Enter SKU" />);
    const input = screen.getByRole('textbox', {name: 'SKU'});
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toContain('sku-error');
    expect(screen.getByRole('alert').textContent).toBe('SKU is required');
  });
  it('does not mark valid fields as invalid', () => {
    render(<Input id="sku" label="SKU" />);
    expect(screen.getByRole('textbox', {name: 'SKU'}).getAttribute('aria-invalid')).toBe('false');
  });
});
