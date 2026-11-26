import {useState, type FormEvent} from 'react';
import {useQuery} from '@tanstack/react-query';
import Input from '../components/ui/Input';
import Card from '../components/ui/Card';
import Alert from '../components/ui/Alert';
import Spinner from '../components/ui/Spinner';
import Button from '../components/ui/Button';
import MovementHistoryTable from '../components/MovementHistoryTable';
import {getMovements, type MovementFilters} from '../services/movementService';
import './MovementHistoryPage.css';

const defaults = {warehouse: '1', product: '', location: '', from: '', to: '', size: '20'};
const positiveId = (value: string) => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) > 0;

// Interpret calendar dates in the browser's local timezone. The API's upper bound is exclusive.
function dateBoundary(value: string, nextDay = false): string | undefined {
  if (!value) return undefined;
  const date = new Date(`${value}T00:00:00`);
  if (nextDay) date.setDate(date.getDate() + 1);
  return date.toISOString();
}

export default function MovementHistoryPage() {
  const [draft, setDraft] = useState(defaults);
  const [applied, setApplied] = useState<{warehouseId: number; filters: MovementFilters; size: number}>({warehouseId: 1, filters: {}, size: 20});
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const query = useQuery({
    queryKey: ['movements', applied.warehouseId, applied.filters, page, applied.size],
    queryFn: () => getMovements(applied.warehouseId, applied.filters, page, applied.size),
    staleTime: 0,
    refetchOnMount: 'always',
    retry: false,
  });
  const update = (key: keyof typeof defaults, value: string) => setDraft(old => ({...old, [key]: value}));
  const warehouseValid = positiveId(draft.warehouse);
  const productValid = !draft.product || positiveId(draft.product);
  const locationValid = !draft.location || positiveId(draft.location);
  const datesValid = !draft.from || !draft.to || draft.from <= draft.to;

  function apply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!warehouseValid || !productValid || !locationValid || !datesValid) {
      setError('Correct the invalid filters before applying.');
      return;
    }
    try {
      const filters: MovementFilters = {
        productId: draft.product ? Number(draft.product) : undefined,
        locationId: draft.location ? Number(draft.location) : undefined,
        from: dateBoundary(draft.from),
        to: dateBoundary(draft.to, true),
      };
      setApplied({warehouseId: Number(draft.warehouse), filters, size: Number(draft.size)});
      setPage(0);
      setError('');
    } catch {
      setError('Enter valid calendar dates.');
    }
  }

  function reset() {
    setDraft(defaults);
    setApplied({warehouseId: 1, filters: {}, size: 20});
    setPage(0);
    setError('');
  }

  return (
    <>
      <div className="eyebrow">WAREHOUSE / MOVEMENTS</div>
      <h1>Movement history</h1>
      <p className="lead">Track stock movements and placement operations within the warehouse.</p>
      <Card className="panel">
        <form onSubmit={apply} noValidate className="movement-filters">
          <div className="movement-filter-grid">
            <Input id="movement-warehouse" label="Warehouse ID" type="number" min="1" step="1" value={draft.warehouse} onChange={e => update('warehouse', e.target.value)} error={warehouseValid ? undefined : 'Enter a positive whole warehouse ID.'}/>
            <Input id="movement-product" label="Product ID" type="number" min="1" step="1" placeholder="All products" value={draft.product} onChange={e => update('product', e.target.value)} error={productValid ? undefined : 'Enter a positive whole product ID.'}/>
            <Input id="movement-location" label="Location ID" type="number" min="1" step="1" placeholder="All locations" value={draft.location} onChange={e => update('location', e.target.value)} error={locationValid ? undefined : 'Enter a positive whole location ID.'}/>
            <div className="ui-field"><label className="ui-label" htmlFor="movement-size">Page size</label><select className="ui-input" id="movement-size" value={draft.size} onChange={e => update('size', e.target.value)}>{[2, 5, 10, 20, 50, 100].map(size => <option key={size} value={size}>{size}</option>)}</select></div>
            <Input id="movement-from" label="Date from" type="date" value={draft.from} onChange={e => update('from', e.target.value)}/>
            <Input id="movement-to" label="Date to" type="date" value={draft.to} onChange={e => update('to', e.target.value)} error={datesValid ? undefined : 'End date must not precede start date.'}/>
          </div>
          <p className="hint">Dates are inclusive and use your local timezone. Location ID matches either source or destination.</p>
          {error && <Alert tone="error">{error}</Alert>}
          <div className="movement-actions"><Button type="submit">Apply filters</Button><Button type="button" variant="secondary" onClick={reset}>Reset filters</Button></div>
        </form>
        <p className="hint">Results for warehouse #{applied.warehouseId}{applied.filters.productId !== undefined ? ` · Product #${applied.filters.productId}` : ''}{applied.filters.locationId !== undefined ? ` · Location #${applied.filters.locationId}` : ''}{applied.filters.from ? ` · From ${new Date(applied.filters.from).toLocaleDateString()}` : ''}{applied.filters.to ? ` · Through ${new Date(new Date(applied.filters.to).getTime() - 1).toLocaleDateString()}` : ''}.</p>
        {query.isPending ? <Spinner label="Loading movements…"/> : query.isError ? (
          <Alert tone="error" title="Unable to load movement history">{query.error.message}</Alert>
        ) : (
          <>
            <p role="status">{query.data.content.length ? `Showing ${query.data.page * query.data.size + 1}–${query.data.page * query.data.size + query.data.content.length} of ${query.data.totalElements} movements.` : 'No stock movements found on this page.'}</p>
            {query.data.content.length > 0 && <MovementHistoryTable movements={query.data.content}/>}
            <nav className="movement-pagination" aria-label="Movement history pagination">
              <Button type="button" variant="secondary" disabled={page === 0 || query.isFetching} onClick={() => setPage(p => Math.max(0, p - 1))}>Previous</Button>
              <span>{query.data.totalPages === 0 ? 'Page 0 of 0' : `Page ${query.data.page + 1} of ${query.data.totalPages}`}</span>
              <Button type="button" variant="secondary" disabled={page + 1 >= query.data.totalPages || query.isFetching} onClick={() => setPage(p => p + 1)}>Next</Button>
            </nav>
          </>
        )}
      </Card>
    </>
  );
}
