import {LoaderCircle} from 'lucide-react';
export default function Spinner({label='Loading…'}:{label?:string}){return <span className="ui-spinner" role="status"><LoaderCircle className="ui-spin" size={19} aria-hidden="true"/>{label}</span>}
