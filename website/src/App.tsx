import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Home from './pages/Home';
import DocumentationLayout from './pages/DocumentationLayout';
import IntegrationGuide from './pages/docs/IntegrationGuide';
import AgentIntegration from './pages/docs/AgentIntegration';
import BestPractices from './pages/docs/BestPractices';
import { ThemeProvider } from './components/ThemeProvider';

export default function App() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/documentation" element={<DocumentationLayout />}>
            <Route path="integration-guide" element={<IntegrationGuide />} />
            <Route path="agent-integration" element={<AgentIntegration />} />
            <Route path="best-practices" element={<BestPractices />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}
