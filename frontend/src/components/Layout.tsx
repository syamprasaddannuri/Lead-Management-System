import React, { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth, hasAnyRole } from '../auth'
import { api } from '../api'
import LmsLogo from './LmsLogo'

export default function Layout() {
  const { user, logout } = useAuth()
  const canManage = hasAnyRole(user, ['SUPER_ADMIN', 'ADMIN'])
  const canApprove = hasAnyRole(user, ['SUPER_ADMIN', 'ADMIN', 'SALES_MANAGER'])
  const [pending, setPending] = useState(0)
  const initials = ((user?.firstName?.[0] || user?.email?.[0] || 'U') + (user?.lastName?.[0] || '')).toUpperCase()

  useEffect(() => {
    if (!canApprove) return
    api.pendingChangeRequestCount().then(r => setPending(r.pending)).catch(() => {})
  }, [canApprove])

  return (
    <>
      <nav className="nav">
        <div className="wrap">
          <NavLink to="/"><LmsLogo height={26} light /></NavLink>
          <div className="links">
            <NavLink to="/" end className={({ isActive }) => isActive ? 'active' : ''}>Dashboard</NavLink>
            <NavLink to="/leads" className={({ isActive }) => isActive ? 'active' : ''}>Leads</NavLink>
            {canApprove && <NavLink to="/requests" className={({ isActive }) => isActive ? 'active' : ''}>
              Requests{pending > 0 && <span className="navbadge">{pending}</span>}
            </NavLink>}
            {canApprove && <NavLink to="/templates" className={({ isActive }) => isActive ? 'active' : ''}>Templates</NavLink>}
            {canManage && <NavLink to="/reports" className={({ isActive }) => isActive ? 'active' : ''}>Reports</NavLink>}
            {canManage && <NavLink to="/team" className={({ isActive }) => isActive ? 'active' : ''}>Team</NavLink>}
          </div>
          <div className="right">
            <NavLink to="/account" className="muted" title="Account settings">{user?.email}</NavLink>
            <NavLink to="/account" title="Account settings"><div className="avatar">{initials}</div></NavLink>
            <button className="btn ghost" onClick={logout}>Logout</button>
          </div>
        </div>
      </nav>
      <Outlet />
    </>
  )
}
