import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Home from './pages/Home';
import CanonicalDocs from './pages/CanonicalDocs';
import { ThemeProvider } from './components/ThemeProvider';

export default function App() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/documentation" element={<CanonicalDocs />} />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}
