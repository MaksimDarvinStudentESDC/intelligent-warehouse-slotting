import Input from '../components/ui/Input';
import Card from '../components/ui/Card';
import Alert from '../components/ui/Alert';
import Spinner from '../components/ui/Spinner';
import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {getLocations} from '../services/locationService';
export default function LocationsPage(){const[id,setId]=useState('1');const q=useQuery({queryKey:['locations',id],queryFn:()=>getLocations(Number(id)),enabled:Number(id)>0});return <><div className="eyebrow">WAREHOUSE / STORAGE</div><h1>Storage locations</h1><p className="lead">Live capacity data from the warehouse API.</p><Card className="panel"><Input id="warehouse" label="Warehouse ID" type="number" min="1" value={id} onChange={e=>setId(e.target.value)}/>{q.isPending?<Spinner label="Loading locations…"/>:q.isError?<Alert tone="error">{q.error.message}</Alert>:q.data?.length===0?<p>No active locations found.</p>:<div className="tablewrap"><table><thead><tr><th>Location</th><th>Zone</th><th>Occupied</th><th>Capacity</th><th>Free space</th></tr></thead><tbody>{q.data?.map(l=><tr key={l.id}><td><strong>{l.code}</strong></td><td>{l.zoneId}</td><td>{l.occupiedDm3} dm³</td><td>{l.capacityDm3} dm³</td><td>{Math.max(0,l.capacityDm3-l.occupiedDm3).toFixed(1)} dm³</td></tr>)}</tbody></table></div>}</Card></>}
