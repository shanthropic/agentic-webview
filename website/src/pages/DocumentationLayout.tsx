import React, { useState, useEffect } from 'react';
import { NavLink, Outlet, Navigate, useLocation } from 'react-router-dom';
import { ChevronRight, ArrowLeft, PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { Link } from 'react-router-dom';
import { ThemeToggle } from '../components/ThemeToggle';
import { Footer } from '../components/Footer';
import { Logo } from '../components/Logo';

const DOCS_NAV = [
  { path: '/documentation/integration-guide', label: 'Integration Guide' },
  { path: '/documentation/agent-integration', label: 'Agent Integration Guide' },
  { path: '/documentation/best-practices', label: 'Best Practices' },
];

export default function DocumentationLayout() {
  const location = useLocation();
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);

  useEffect(() => {
    const handleResize = () => {
      if (window.innerWidth >= 768) {
        setIsSidebarOpen(true);
      } else {
        setIsSidebarOpen(false);
      }
    };
    
    // Set initial state based on window size
    handleResize();

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Close menu when route changes on mobile
  useEffect(() => {
    if (window.innerWidth < 768) {
      setIsSidebarOpen(false);
    }
  }, [location.pathname]);

  if (location.pathname === '/documentation' || location.pathname === '/documentation/') {
    return <Navigate to="/documentation/integration-guide" replace />;
  }

  const currentIndex = DOCS_NAV.findIndex(item => item.path === location.pathname);
  const prevPage = currentIndex > 0 ? DOCS_NAV[currentIndex - 1] : null;
  const nextPage = currentIndex < DOCS_NAV.length - 1 ? DOCS_NAV[currentIndex + 1] : null;

  const scrollToTop = () => {
    document.getElementById('main-scroll-area')?.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="h-screen overflow-hidden bg-vp-bg text-vp-fg font-sans antialiased flex flex-col items-center">
      <header className="shrink-0 w-full max-w-[1200px] px-4 sm:px-6 h-16 sm:h-20 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button 
            className="p-1.5 -ml-1.5 text-vp-subtle hover:text-vp-fg transition-colors rounded-sm"
            onClick={() => setIsSidebarOpen(!isSidebarOpen)}
            aria-label="Toggle navigation menu"
          >
            {isSidebarOpen ? <PanelLeftClose size={20} strokeWidth={1.5} /> : <PanelLeftOpen size={20} strokeWidth={1.5} />}
          </button>
          <Link to="/" className="flex items-center gap-2 sm:gap-3">
            <Logo className="w-7 h-7 sm:w-8 sm:h-8 rounded-lg shadow-sm" />
            <span className="font-medium text-[14px] sm:text-[15px] tracking-tight text-vp-fg">Agentic WebView</span>
          </Link>
        </div>
        <ThemeToggle />
      </header>
      
      <div className="flex w-full max-w-[1200px] px-4 sm:px-6 flex-1 overflow-hidden min-h-0">
        {/* Sidebar Navigation */}
        <div 
          className={`shrink-0 overflow-y-auto h-full transition-[width,opacity] duration-300 ease-in-out ${
            isSidebarOpen ? 'w-full md:w-56 opacity-100' : 'w-0 opacity-0'
          }`}
        >
          <aside className="w-full md:w-56 py-6 md:py-16 md:pr-8">
            <Link to="/" onClick={scrollToTop} className="inline-flex items-center text-vp-subtle hover:text-vp-fg transition-colors mb-6 md:mb-10 text-sm">
              <ArrowLeft size={16} className="mr-2" />
              Home
            </Link>
            
            <h3 className="text-xs font-medium tracking-wider text-vp-subtle mb-3 md:mb-4 uppercase">Documentation</h3>
            <nav className="flex flex-col gap-2 md:gap-1 pb-4 md:pb-0">
              {DOCS_NAV.map((navItem) => (
                <NavLink
                  key={navItem.path}
                  to={navItem.path}
                  onClick={scrollToTop}
                  className={({ isActive }) =>
                    `block py-1.5 md:py-1 text-[14px] transition-colors ${
                      isActive
                        ? 'text-vp-fg font-medium'
                        : 'text-vp-subtle hover:text-vp-fg'
                    }`
                  }
                >
                  {navItem.label}
                </NavLink>
              ))}
            </nav>
          </aside>
        </div>

        {/* Main Content Area */}
        <main 
          id="main-scroll-area"
          className={`flex-1 overflow-y-auto h-full py-4 md:py-16 md:pl-12 max-w-[800px] w-full min-w-0 transition-opacity duration-300 ${isSidebarOpen ? 'max-md:opacity-0 max-md:pointer-events-none' : 'opacity-100'}`}
        >
          <Outlet />

          {/* Prev / Next Pagination */}
          <div className="mt-20 pt-8 border-t border-vp-border flex items-center justify-between">
            <div className="flex-1">
              {prevPage && (
                <Link 
                  to={prevPage.path}
                  onClick={scrollToTop}
                  className="group flex flex-col items-start text-vp-text hover:text-vp-highlight transition-colors w-max"
                >
                  <span className="text-xs text-vp-subtle uppercase tracking-wider mb-1 group-hover:text-vp-highlight/70">Previous</span>
                  <span className="font-medium">{prevPage.label}</span>
                </Link>
              )}
            </div>
            <div className="flex-1 flex justify-end">
              {nextPage && (
                <Link 
                  to={nextPage.path}
                  onClick={scrollToTop}
                  className="group flex flex-col items-end text-vp-text hover:text-vp-highlight transition-colors w-max"
                >
                  <span className="text-xs text-vp-subtle uppercase tracking-wider mb-1 group-hover:text-vp-highlight/70">Next</span>
                  <span className="font-medium text-right">{nextPage.label}</span>
                </Link>
              )}
            </div>
          </div>

          <Footer className="mt-12 mb-12" />
        </main>
      </div>
    </div>
  );
}
