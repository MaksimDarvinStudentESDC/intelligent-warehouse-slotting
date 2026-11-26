import type {StockMovementResponse} from '../types/api';

export default function MovementHistoryTable({movements}: {movements: StockMovementResponse[]}) {
  return (
    <div className="tablewrap movement-history-table" tabIndex={0} role="region" aria-label="Warehouse movement history">
      <table>
        <thead>
          <tr>
            {['Movement ID', 'Batch ID', 'Batch code', 'Product ID', 'Type', 'From', 'To', 'Performed by', 'Performed at'].map(label => (
              <th key={label} scope="col">{label}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {movements.map(m => (
            <tr key={m.id}>
              <td>#{m.id}</td>
              <td>#{m.batchId}</td>
              <td>{m.batchCode}</td>
              <td>{m.productId}</td>
              <td>{m.movementType.charAt(0).toUpperCase() + m.movementType.slice(1)}</td>
              <td>{m.fromLocationCode ?? '—'}</td>
              <td><strong>{m.toLocationCode}</strong></td>
              <td>{m.performedBy}</td>
              <td><time dateTime={m.performedAt}>{new Date(m.performedAt).toLocaleString()}</time></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
