import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/guards';
import { Layout } from './components/Layout';
import { LoginPage } from './pages/Login';
import { DashboardPage } from './pages/Dashboard';
import { CustomersPage } from './pages/Customers';
import { VendorsPage } from './pages/Vendors';
import { VendorVerificationPage } from './pages/VendorVerification';
import { OrdersPage } from './pages/Orders';
import { OrderDetailPage } from './pages/OrderDetail';
import { SubscriptionsPage } from './pages/Subscriptions';
import { DeliveriesPage } from './pages/Deliveries';
import { ServiceZonesPage } from './pages/ServiceZones';
import { PaymentsPage } from './pages/Payments';
import { PayoutsPage } from './pages/Payouts';
import { SupportPage } from './pages/Support';
import { AuditLogsPage } from './pages/AuditLogs';
import { SettingsPage } from './pages/Settings';

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            element={
              <RequireAuth>
                <Layout />
              </RequireAuth>
            }
          >
            <Route index element={<DashboardPage />} />
            <Route path="customers" element={<CustomersPage />} />
            <Route path="vendors" element={<VendorsPage />} />
            <Route path="verifications" element={<VendorVerificationPage />} />
            <Route path="orders" element={<OrdersPage />} />
            <Route path="orders/:id" element={<OrderDetailPage />} />
            <Route path="subscriptions" element={<SubscriptionsPage />} />
            <Route path="deliveries" element={<DeliveriesPage />} />
            <Route path="zones" element={<ServiceZonesPage />} />
            <Route path="payments" element={<PaymentsPage />} />
            <Route path="payouts" element={<PayoutsPage />} />
            <Route path="support" element={<SupportPage />} />
            <Route path="audit" element={<AuditLogsPage />} />
            <Route path="settings" element={<SettingsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
