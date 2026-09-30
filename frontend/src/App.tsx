import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, RequireAuth } from './auth'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Leads from './pages/Leads'
import LeadDetail from './pages/LeadDetail'
import BulkImport from './pages/BulkImport'
import NewLead from './pages/NewLead'
import Team from './pages/Team'
import Reports from './pages/Reports'
import Requests from './pages/Requests'
import Templates from './pages/Templates'
import Account from './pages/Account'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<RequireAuth><Layout /></RequireAuth>}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/account" element={<Account />} />
            <Route path="/leads" element={<Leads />} />
            <Route path="/leads/new" element={<NewLead />} />
            <Route path="/leads/import" element={<BulkImport />} />
            <Route path="/leads/:id" element={<LeadDetail />} />
            <Route path="/requests" element={<RequireAuth roles={['SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER']}><Requests /></RequireAuth>} />
            <Route path="/templates" element={<RequireAuth roles={['SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER']}><Templates /></RequireAuth>} />
            <Route path="/reports" element={<RequireAuth roles={['SUPER_ADMIN', 'ADMIN']}><Reports /></RequireAuth>} />
            <Route path="/team" element={<RequireAuth roles={['SUPER_ADMIN', 'ADMIN']}><Team /></RequireAuth>} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
