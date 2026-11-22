import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RotateCcw } from 'lucide-react';

type Props = {
  children: ReactNode;
};

type State = {
  hasError: boolean;
};

export default class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Log technical details for developers; never display stack traces to users.
    console.error('Unexpected React rendering error', error, info.componentStack);
  }

  private handleRetry = (): void => {
    this.setState({ hasError: false });
  };

  private handleGoHome = (): void => {
    window.location.assign('/');
  };

  render(): ReactNode {
    if (!this.state.hasError) return this.props.children;

    return (
      <main className="error-boundary-page" id="main-content">
        <section className="error-boundary-card" role="alert" aria-labelledby="unexpected-error-title">
          <AlertTriangle size={32} aria-hidden="true" className="error-boundary-icon" />
          <h1 id="unexpected-error-title">Something went wrong</h1>
          <p>An unexpected interface error occurred. Your warehouse data has not been changed by this screen.</p>
          <div className="error-boundary-actions">
            <button type="button" className="error-boundary-primary" onClick={this.handleRetry}>
              <RotateCcw size={18} aria-hidden="true" /> Try again
            </button>
            <button type="button" className="error-boundary-secondary" onClick={this.handleGoHome}>
              Go to Home
            </button>
          </div>
        </section>
      </main>
    );
  }
}
