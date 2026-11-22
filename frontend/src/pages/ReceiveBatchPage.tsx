import {useState} from 'react';
import {useMutation} from '@tanstack/react-query';
import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {MapPinned} from 'lucide-react';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Card from '../components/ui/Card';
import Alert from '../components/ui/Alert';
import {createBatch, recommend} from '../services/batchService';
import type {RecommendationResult} from '../types/api';

const schema = z.object({
  productId: z.string().trim().min(1, 'Product ID is required.').refine(
    value => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) > 0,
    'Product ID must be a positive whole number.'
  ),
  batchCode: z.string().trim().min(1, 'Batch code is required.'),
  quantity: z.string().refine(
    value => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) >= 1 && Number(value) <= 9999,
    'Quantity must be a whole number between 1 and 9999.'
  ),
  expiry: z.string(),
});

type FormValues = z.infer<typeof schema>;

function localToday(): string {
  const date = new Date();
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export default function ReceiveBatchPage() {
  const [error, setError] = useState('');
  const [result, setResult] = useState<RecommendationResult | null>(null);
  const [selection, setSelection] = useState<number | null>(null);
  const [createdBatchId, setCreatedBatchId] = useState<number | null>(null);
  const batchMutation = useMutation({mutationFn: createBatch});
  const recommendationMutation = useMutation({mutationFn: recommend});
  const today = localToday();

  const {register, handleSubmit, formState: {errors}} = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {productId: '', batchCode: '', quantity: '1', expiry: ''},
  });

  async function requestRecommendations(batchId: number) {
    try {
      const recommendations = await recommendationMutation.mutateAsync(batchId);
      setResult(recommendations);
      setSelection(recommendations.recommendations[0]?.locationId ?? null);
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to request recommendations.');
    }
  }

  async function submit(values: FormValues) {
    setError('');
    setResult(null);
    setSelection(null);
    // Never reuse a previously created batch for a newly submitted form.
    setCreatedBatchId(null);
    if (values.expiry && values.expiry < localToday()) {
      setError('The expiry date has passed. Check the date on the packaging.');
      return;
    }
    try {
      const batch = await batchMutation.mutateAsync({
        productId: Number(values.productId),
        batchCode: values.batchCode.trim(),
        quantity: Number(values.quantity),
        receivingDate: localToday(),
        expiryDate: values.expiry || null,
      });
      setCreatedBatchId(batch.id);
      await requestRecommendations(batch.id);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to register the batch.');
    }
  }

  const pending = batchMutation.isPending || recommendationMutation.isPending;

  return <>
    <div className="eyebrow">OPERATIONS / RECEIVE</div>
    <h1>Receive batch</h1>
    <p className="lead">Register an incoming batch and find a suitable location. Recommendations come from the backend.</p>
    <div className="columns">
      <Card className="panel">
        <h2>1. Batch details</h2>
        <p className="hint">Fields marked * are required.</p>
        {error && <Alert tone="error" title="Check the request">{error}</Alert>}
        <form noValidate onSubmit={handleSubmit(submit)}>
          <Input id="product" label="Product ID *" type="number" min="1" step="1"
            placeholder="Existing product ID" hint="Temporary ID input until GET /api/products is implemented."
            error={errors.productId?.message} required {...register('productId')}/>
          <Input id="code" label="Batch code *" placeholder="e.g. BATCH-2026-001"
            error={errors.batchCode?.message} required {...register('batchCode')}/>
          <Input id="quantity" label="Quantity *" type="number" min="1" max="9999" step="1"
            error={errors.quantity?.message} required {...register('quantity')}/>
          <Input id="expiry" label="Expiry date (if applicable)" type="date" min={today}
            error={errors.expiry?.message} {...register('expiry')}/>
          <Button loading={pending} type="submit">Get recommendation</Button>
        </form>
        {createdBatchId !== null && !result && !recommendationMutation.isPending && <div>
          <p className="hint">Batch #{createdBatchId} was registered. Recommendations could not be loaded.</p>
          <Button type="button" onClick={() => void requestRecommendations(createdBatchId)}>
            Retry recommendations for batch #{createdBatchId}
          </Button>
        </div>}
      </Card>
      <Card className="panel">
        <h2>2. Ranked locations</h2>
        {!result ? <div className="empty"><MapPinned size={36}/><h3>No recommendation yet</h3>
          <p>Enter batch details and request a recommendation to see feasible storage locations.</p></div> : <>
          <div className="success">Batch #{result.batchId} registered · {result.feasibleCount} feasible locations</div>
          {result.recommendations.length === 0 ?
            <Alert tone="warning">No suitable location is available. Review the batch and location constraints.</Alert> : <>
              <p className="hint">Select a feasible location. Placement confirmation requires a backend endpoint and is not enabled yet.</p>
              {result.recommendations.map(item => <label
                className={'recommendation ' + (selection === item.locationId ? 'selected' : '')}
                key={item.locationId}>
                <input type="radio" name="location" checked={selection === item.locationId}
                  onChange={() => setSelection(item.locationId)}/>
                <span><strong>#{item.rank} · {item.locationCode}</strong>
                  {item.rank === 1 && <span className="pill">Best match</span>}
                  <small>{item.distanceMeters} m · {item.explanation}</small>
                </span>
              </label>)}
              <button className="primary" disabled title="Awaiting backend placement confirmation endpoint">
                Confirm placement (coming soon)
              </button>
            </>}
        </>}
      </Card>
    </div>
  </>;
}
