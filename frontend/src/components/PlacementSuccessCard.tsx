import Alert from './ui/Alert';
import Button from './ui/Button';
import type {PlacementResponse} from '../types/api';
import './PlacementSuccessCard.css';

type Props = {
  placement: PlacementResponse;
  onReceiveAnotherBatch: () => void;
};

export default function PlacementSuccessCard({placement, onReceiveAnotherBatch}: Props) {
  return <section className="placement-success-card" aria-label="Placement result">
    <Alert tone="success" title="Placement successful">The batch has been successfully placed.</Alert>
    <dl className="placement-success-details">
      <div><dt>Batch ID</dt><dd>#{placement.batchId}</dd></div>
      <div><dt>Storage location</dt><dd>{placement.locationCode}</dd></div>
      <div><dt>Movement ID</dt><dd>#{placement.movementId}</dd></div>
      <div><dt>Status</dt><dd>{placement.status === 'placed' ? 'Placed' : placement.status}</dd></div>
    </dl>
    <h3>Location occupancy after placement</h3>
    <dl className="placement-success-details">
      <div><dt>Occupied volume</dt><dd>{placement.occupiedVolumeDm3} dm³</dd></div>
      <div><dt>Occupied weight</dt><dd>{placement.occupiedWeightKg} kg</dd></div>
    </dl>
    <p className="hint">Placed at: <time dateTime={placement.placedAt}>{new Date(placement.placedAt).toLocaleString()}</time></p>
    <Button type="button" onClick={onReceiveAnotherBatch}>Receive another batch</Button>
  </section>;
}
