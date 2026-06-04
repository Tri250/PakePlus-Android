import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { useEffect } from 'react';
import Layout from '@/components/Layout';
import Login from '@/pages/Login';
import Cockpit from '@/pages/Cockpit';
import MapWorkspace from '@/pages/MapWorkspace';
import Persona from '@/pages/Persona';
import Campaign from '@/pages/Campaign';
import Leads from '@/pages/Leads';
import Dashboard from '@/pages/Dashboard';
import Settings from '@/pages/Settings';
import { useGlobal } from '@/store/useGlobal';

const Protected = ({ children }: { children: React.ReactNode }) => {
  const { isAuthed, loading } = useGlobal();
  if (loading) {
    return (
      <div className="h-screen grid place-items-center text-ink-400 text-sm font-mono">
        邻客 AI · 正在加载工作台…
      </div>
    );
  }
  if (!isAuthed) return <Navigate to="/login" replace />;
  return <>{children}</>;
};

export default function App() {
  const bootstrap = useGlobal((s) => s.bootstrap);

  useEffect(() => {
    bootstrap();
  }, [bootstrap]);

  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          element={
            <Protected>
              <Layout />
            </Protected>
          }
        >
          <Route path="/" element={<Cockpit />} />
          <Route path="/cockpit" element={<Cockpit />} />
          <Route path="/map" element={<MapWorkspace />} />
          <Route path="/persona" element={<Persona />} />
          <Route path="/campaign" element={<Campaign />} />
          <Route path="/leads" element={<Leads />} />
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/settings" element={<Settings />} />
        </Route>
      </Routes>
    </Router>
  );
}
