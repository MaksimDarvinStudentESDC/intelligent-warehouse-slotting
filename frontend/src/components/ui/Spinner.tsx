import { LoaderCircle } from 'lucide-react';

interface SpinnerProps {
    label?: string;
}

export default function Spinner({
                                    label = 'Loading...',
                                }: SpinnerProps) {
    return (
        <span className="spinner" role="status">
      <LoaderCircle size={18} aria-hidden="true" />
            {label}
    </span>
    );
}