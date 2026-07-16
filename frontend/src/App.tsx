import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/lib/queryClient';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { ToastProvider } from '@/components/ui/Toast';
import { ProtectedRoute } from '@/app/ProtectedRoute';
import { AppShell } from '@/app/AppShell';
import { AuthPage } from '@/features/auth/components/AuthPage';
import { DashboardPage } from '@/features/dashboard/components/DashboardPage';
import { Skeleton } from '@/components/ui/Skeleton';

const MarketPage = lazy(() => import('@/features/market/components/MarketPage').then(m => ({ default: m.MarketPage })));
const TradePage = lazy(() => import('@/features/trading/components/TradePage').then(m => ({ default: m.TradePage })));
const PortfolioPage = lazy(() => import('@/features/portfolio/components/PortfolioPage').then(m => ({ default: m.PortfolioPage })));
const AnalyticsPage = lazy(() => import('@/features/analytics/components/AnalyticsPage').then(m => ({ default: m.AnalyticsPage })));
const JournalPage = lazy(() => import('@/features/journal/components/JournalPage').then(m => ({ default: m.JournalPage })));
const OrgDashboardPage = lazy(() => import('@/features/org/components/OrgDashboardPage').then(m => ({ default: m.OrgDashboardPage })));
const OrgCreateJoinPage = lazy(() => import('@/features/org/components/OrgCreateJoinPage').then(m => ({ default: m.OrgCreatePage })));
const OrgJoinPage = lazy(() => import('@/features/org/components/OrgCreateJoinPage').then(m => ({ default: m.OrgJoinPage })));
const UserSettingsPage = lazy(() => import('@/features/org/components/UserSettingsPage').then(m => ({ default: m.UserSettingsPage })));

function RouteSpinner() {
  return (
    <div className="p-6 space-y-4">
      <Skeleton className="h-8 w-48" />
      <Skeleton className="h-64 w-full" />
    </div>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ErrorBoundary>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/auth" element={<AuthPage />} />
              <Route element={<ProtectedRoute />}>
                <Route element={<AppShell />}>
                  <Route path="/" element={<DashboardPage />} />
                  <Route path="/market" element={<Suspense fallback={<RouteSpinner />}><MarketPage /></Suspense>} />
                  <Route path="/trade" element={<Suspense fallback={<RouteSpinner />}><TradePage /></Suspense>} />
                  <Route path="/portfolio" element={<Suspense fallback={<RouteSpinner />}><PortfolioPage /></Suspense>} />
                  <Route path="/analytics" element={<Suspense fallback={<RouteSpinner />}><AnalyticsPage /></Suspense>} />
                  <Route path="/journal" element={<Suspense fallback={<RouteSpinner />}><JournalPage /></Suspense>} />
                  <Route path="/org/create" element={<Suspense fallback={<RouteSpinner />}><OrgCreateJoinPage /></Suspense>} />
                  <Route path="/org/join" element={<Suspense fallback={<RouteSpinner />}><OrgJoinPage /></Suspense>} />
                  <Route path="/org/:orgId" element={<Suspense fallback={<RouteSpinner />}><OrgDashboardPage /></Suspense>} />
                  <Route path="/settings" element={<Suspense fallback={<RouteSpinner />}><UserSettingsPage /></Suspense>} />
                </Route>
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </ErrorBoundary>
    </QueryClientProvider>
  );
}
