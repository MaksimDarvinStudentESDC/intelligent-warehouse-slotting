import type {ButtonHTMLAttributes, ReactNode} from 'react';
import {LoaderCircle} from 'lucide-react';
type Props=ButtonHTMLAttributes<HTMLButtonElement>&{variant?:'primary'|'secondary'|'ghost';loading?:boolean;children:ReactNode};
export default function Button({variant='primary',loading=false,disabled,children,className='',...props}:Props){return <button {...props} disabled={disabled||loading} aria-busy={loading||undefined} className={`ui-button ui-button--${variant} ${className}`.trim()}>{loading&&<LoaderCircle size={17} className="ui-spin" aria-hidden="true"/>}{children}</button>}
