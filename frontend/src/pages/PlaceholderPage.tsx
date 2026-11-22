import {Link} from 'react-router-dom';
export default function PlaceholderPage({title,detail}:{title:string;detail:string}){return <><div className="eyebrow">WORKSPACE / PLANNED</div><h1>{title}</h1><section className="panel"><h2>Next development stage</h2><p>{detail}</p><Link to="/receive">Go to Receive batch →</Link></section></>}
