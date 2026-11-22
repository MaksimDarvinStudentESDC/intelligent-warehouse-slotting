import type {ReactNode} from 'react';
import {AlertCircle,CheckCircle2,Info,AlertTriangle} from 'lucide-react';
type Tone='error'|'success'|'info'|'warning';
const icons={error:AlertCircle,success:CheckCircle2,info:Info,warning:AlertTriangle};
export default function Alert({tone='info',title,children}:{tone?:Tone;title?:string;children:ReactNode}){const Icon=icons[tone];return <div className={`ui-alert ui-alert--${tone}`} role={tone==='error'?'alert':'status'}><Icon size={19} aria-hidden="true"/><div>{title&&<strong>{title}</strong>}<div>{children}</div></div></div>}
