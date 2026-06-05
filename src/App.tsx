import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";
import Dashboard from "./pages/Dashboard";
import LBSRadar from "./pages/LBSRadar";
import GEOSearchOptimization from "./pages/GEOSearchOptimization";
import Customers from "./pages/Customers";
import Marketing from "./pages/Marketing";
import Analytics from "./pages/Analytics";
import Settings from "./pages/Settings";
import GroundCombat from "./pages/GroundCombat";
import BrandDataPlatform from "./pages/BrandDataPlatform";

export default function App() {
  return (
    <Router>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/lbs" element={<LBSRadar />} />
          <Route path="/geo-search" element={<GEOSearchOptimization />} />
          <Route path="/geo-optimization" element={<GEOSearchOptimization />} />
          <Route path="/customers" element={<Customers />} />
          <Route path="/marketing" element={<Marketing />} />
          <Route path="/ground-combat" element={<GroundCombat />} />
          <Route path="/brand-data" element={<BrandDataPlatform />} />
          <Route path="/analytics" element={<Analytics />} />
          <Route path="/settings" element={<Settings />} />
        </Routes>
      </Layout>
    </Router>
  );
}
