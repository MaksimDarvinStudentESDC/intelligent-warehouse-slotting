import {Link} from 'react-router-dom';
import {PackagePlus, MapPinned, ChartNoAxesCombined, Info} from 'lucide-react';

import {useAuth} from '../context/AuthContext';
import {canAccess} from '../context/permissions';
export default function HomePage() {
    const {user} = useAuth();
    if (!user) return null;
    return <>
        <div className="eyebrow">OPERATIONS OVERVIEW</div>
        <h1>Warehouse workspace</h1><p className="lead">Manage incoming batches, review suitable storage locations and
        compare placement strategies.</p>
        <div className="tiles">{canAccess(user.role,"/receive") && <Link className="tile" to="/receive"><PackagePlus/><h2>Receive a batch</h2><p>Register
            incoming goods and request ranked locations.</p><strong>Start receiving →</strong></Link>}{canAccess(user.role,"/locations") && <Link
            className="tile" to="/locations"><MapPinned/><h2>Storage locations</h2><p>Inspect location capacities and
            occupancy.</p><strong>View locations →</strong></Link>}{canAccess(user.role,"/compare") && <Link className="tile"
                                                                        to="/compare"><ChartNoAxesCombined/><h2>Strategy
            comparison</h2><p>Evaluate the optimized strategy against two baselines.</p><strong>View experiments
            →</strong></Link>}</div>
        <div className="notice"><Info size={18}/> Live metrics will appear after backend integration. No demonstration
            values are presented as real data.
        </div>
    </>
}
