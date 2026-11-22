import {afterEach, describe, expect, it, vi} from 'vitest';
import {cleanup, fireEvent, render, screen} from '@testing-library/react';
import Button from '../components/ui/Button';

afterEach(cleanup);
describe('Button', () => {
  it('calls onClick when enabled', () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Save</Button>);
    fireEvent.click(screen.getByRole('button', {name: 'Save'}));
    expect(onClick).toHaveBeenCalledOnce();
  });
  it('does not call onClick when disabled', () => {
    const onClick = vi.fn();
    render(<Button disabled onClick={onClick}>Save</Button>);
    const button = screen.getByRole('button', {name: 'Save'}) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    fireEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
  it('blocks interaction and signals busy state while loading', () => {
    render(<Button loading>Submit</Button>);
    const button = screen.getByRole('button', {name: 'Submit'}) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.getAttribute('aria-busy')).toBe('true');
  });
});
