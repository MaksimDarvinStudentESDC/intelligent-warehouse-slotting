import { useEffect, useRef, useState } from 'react';
import { NavLink, Routes, Route, useLocation } from 'react-router-dom';
import { Warehouse, PackagePlus, Upload, ChartNoAxesCombined, MapPinned, House, Menu, X } from 'lucide-react';
import HomePage from '../pages/HomePage';
import ReceiveBatchPage from '../pages/ReceiveBatchPage';
import LocationsPage from '../pages/LocationsPage';
import PlaceholderPage from '../pages/PlaceholderPage';

const navigation = [
  { to: '/', label: 'Home', icon: House },
  { to: '/receive', label: 'Receive batch', icon: PackagePlus },
  { to: '/import', label: 'Data import', icon: Upload },
  { to: '/compare', label: 'Compare strategies', icon: ChartNoAxesCombined },
  { to: '/locations', label: 'Locations', icon: MapPinned },
];

export default function AppLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  const location = useLocation();

  useEffect(() => { setMobileOpen(false); }, [location.pathname]);
  useEffect(() => {
    if (!mobileOpen) return;
    const onEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMobileOpen(false);
        menuButton.current?.focus();
      }
    };
    window.addEventListener('keydown', onEscape);
    return () => window.removeEventListener('keydown', onEscape);
  }, [mobileOpen]);

  const links = navigation.map(({ to, label, icon: Icon }) => (
    <NavLink
      key={to}
      end={to === '/'}
      to={to}
      onClick={() => setMobileOpen(false)}
      className={({ isActive }) => isActive ? 'nav active' : 'nav'}
    >
      <Icon size={19} aria-hidden="true" />{label}
    </NavLink>
  ));

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand"><Warehouse size={27} aria-hidden="true" /><span>Warehouse<br /><small>Slotting System</small></span></div>
        <nav aria-label="Main navigation">{links}</nav>
        <div className="sidefoot">Intelligent placement · Prototype</div>
      </aside>
      <div className="mobile-header">
        <div className="mobile-brand"><Warehouse size={24} aria-hidden="true" /><span>Warehouse Slotting</span></div>
        <button ref={menuButton} className="menu-toggle" type="button"
          aria-expanded={mobileOpen} aria-controls="mobile-nav" aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'}
          onClick={() => setMobileOpen(open => !open)}>
          {mobileOpen ? <X size={23} aria-hidden="true" /> : <Menu size={23} aria-hidden="true" />}
        </button>
      </div>
      <nav id="mobile-nav" className={`mobile-nav${mobileOpen ? ' is-open' : ''}`} aria-label="Mobile navigation" inert={!mobileOpen}>
        {links}
      </nav>
      <main className="main" id="main-content">
        <header className="topbar"><span>Intelligent Warehouse Slotting</span><span className="demo">Development build</span></header>
        <div className="content">
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/receive" element={<ReceiveBatchPage />} />
            <Route path="/import" element={<PlaceholderPage title="Data import" detail="CSV validation and import will be connected when backend endpoints are available." />} />
            <Route path="/compare" element={<PlaceholderPage title="Compare strategies" detail="B1 / B2 / Optimized experiment runner is planned for the next integration stage." />} />
            <Route path="/locations" element={<LocationsPage />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}
