import {useState, type FormEvent} from 'react';
import {Navigate, useLocation} from 'react-router-dom';
import {Warehouse} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {canAccess} from '../context/permissions';
import Input from '../components/ui/Input';
import Button from '../components/ui/Button';
import Alert from '../components/ui/Alert';
export default function LoginPage() {
  const {user,login} = useAuth(); const location = useLocation();
  const [name,setName] = useState(''); const [password,setPassword] = useState('');
  const [pending,setPending] = useState(false); const [error,setError] = useState('');
  if (user) {const from = location.state?.from; return <Navigate to={typeof from === 'string' && canAccess(user.role,from) ? from : '/'} replace/>;}
  async function submit(event: FormEvent) {
    event.preventDefault(); if (pending) return; setError('');
    if (!name.trim() || !password || new TextEncoder().encode(password).length > 72) {setError('Enter a login and a password of at most 72 bytes.'); return;}
    setPending(true);
    try {await login({login:name.trim(),password}); setPassword('');}
    catch (e) {setError(e instanceof Error ? e.message : 'Sign in failed.');}
    finally {setPending(false);}
  }
  return <main className="auth-screen"><section className="panel auth-panel" aria-labelledby="login-title"><div className="eyebrow"><Warehouse size={24} aria-hidden="true"/> WAREHOUSE SLOTTING</div><h1 id="login-title">Sign in</h1><p>Use your warehouse account to continue.</p><form onSubmit={submit}><Input name="login" label="Login" autoComplete="username" required maxLength={64} value={name} onChange={e=>setName(e.target.value)}/><Input name="password" label="Password" type="password" autoComplete="current-password" required maxLength={72} value={password} onChange={e=>setPassword(e.target.value)}/>{error && <Alert tone="error">{error}</Alert>}<Button type="submit" loading={pending}>Sign in</Button></form></section></main>;
}
