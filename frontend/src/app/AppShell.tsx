import type { ReactNode } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/store/auth.store';
import { useOrganizations } from '@/features/org/hooks/useOrg';

const navItems = [
  { to: '/', label: 'Dashboard', icon: 'grid' },
  { to: '/market', label: 'Market', icon: 'chart' },
  { to: '/trade', label: 'Trade', icon: 'swap' },
  { to: '/portfolio', label: 'Portfolio', icon: 'briefcase' },
  { to: '/analytics', label: 'Analytics', icon: 'analytics' },
  { to: '/journal', label: 'Journal', icon: 'book' },
] as const;

const iconMap: Record<string, ReactNode> = {
  grid: (
    <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M4.25 2A2.25 2.25 0 002 4.25v2.5A2.25 2.25 0 004.25 9h2.5A2.25 2.25 0 009 6.75v-2.5A2.25 2.25 0 006.75 2h-2.5zm0 9A2.25 2.25 0 002 13.25v2.5A2.25 2.25 0 004.25 18h2.5A2.25 2.25 0 009 15.75v-2.5A2.25 2.25 0 006.75 11h-2.5zm9-9A2.25 2.25 0 0011 4.25v2.5A2.25 2.25 0 0013.25 9h2.5A2.25 2.25 0 0018 6.75v-2.5A2.25 2.25 0 0015.75 2h-2.5zm0 9A2.25 2.25 0 0011 13.25v2.5A2.25 2.25 0 0013.25 18h2.5A2.25 2.25 0 0018 15.75v-2.5A2.25 2.25 0 0015.75 11h-2.5z" clipRule="evenodd" />
    </svg>
  ),
  chart: (
    <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 7.758A.75.75 0 014.68 7.18l5-3.06a.75.75 0 01.84 0l5 3.06a.75.75 0 11-.6 1.28L10.75 5.612v10.638A.75.75 0 0110 17z" clipRule="evenodd" />
    </svg>
  ),
  swap: (
    <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M15.312 11.424a5.5 5.5 0 01-9.201 2.466l-.312-.311h2.433a.75.75 0 000-1.5H4.598a.75.75 0 00-.75.75v3.634a.75.75 0 001.5 0v-2.033l.312.311a7 7 0 0011.712-3.138.75.75 0 00-1.449-.39zm-11.23-3.424a.75.75 0 01.564-.89 7 7 0 0111.712 3.138.75.75 0 01-1.449.39 5.5 5.5 0 00-9.201-2.466l-.312.311V5.018a.75.75 0 00-1.5 0v3.634a.75.75 0 00.75.75h3.634a.75.75 0 000-1.5H5.117l.312-.311a.75.75 0 00-.32-1.234z" clipRule="evenodd" />
    </svg>
  ),
  briefcase: (
    <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
      <path fillRule="evenodd" d="M6 3.75A2.75 2.75 0 018.75 1h2.5A2.75 2.75 0 0114 3.75v.443c.572.055 1.14.122 1.706.2C17.053 4.582 18 5.75 18 7.07v3.469c0 1.126-.694 2.191-1.83 2.54-1.952.599-4.024.921-6.17.921s-4.219-.322-6.17-.921C2.694 12.73 2 11.665 2 10.539V7.07c0-1.321.947-2.489 2.294-2.676A41.047 41.047 0 016 4.193V3.75zm6.5 0v.325a41.622 41.622 0 00-5 0V3.75c0-.69.56-1.25 1.25-1.25h2.5c.69 0 1.25.56 1.25 1.25zM10 10a1 1 0 00-1 1v.01a1 1 0 001 1h.01a1 1 0 001-1V11a1 1 0 00-1-1H10z" clipRule="evenodd" />
      <path d="M3 15.055v-.684c.126.053.255.1.39.142 2.092.642 4.313.987 6.61.987 2.297 0 4.518-.345 6.61-.987.135-.041.264-.089.39-.142v.684c0 1.347-.985 2.53-2.363 2.686a41.454 41.454 0 01-9.274 0C3.985 17.585 3 16.402 3 15.055z" />
    </svg>
  ),
  analytics: (
    <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
      <path d="M15.5 2A1.5 1.5 0 0014 3.5v13a1.5 1.5 0 001.5 1.5h1a1.5 1.5 0 001.5-1.5v-13A1.5 1.5 0 0016.5 2h-1zM9.5 6A1.5 1.5 0 008 7.5v9A1.5 1.5 0 009.5 18h1a1.5 1.5 0 001.5-1.5v-9A1.5 1.5 0 0010.5 6h-1zM3.5 10A1.5 1.5 0 002 11.5v5A1.5 1.5 0 003.5 18h1A1.5 1.5 0 006 16.5v-5A1.5 1.5 0 004.5 10h-1z" />
    </svg>
  ),
  book: (
    <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
      <path d="M10.75 16.82A7.462 7.462 0 0115 15.5c.71 0 1.396.098 2.046.282A.75.75 0 0018 15.06V11.19a.75.75 0 00-.517-.704 5.741 5.741 0 00-1.724-.248 4.51 4.51 0 00-5.146 0 5.735 5.735 0 00-1.724.248.75.75 0 00-.517.704v3.87a.75.75 0 00.325.624zM9.75 7.5a.75.75 0 00-1.5 0v2.25H6a.75.75 0 000 1.5h2.25v2.25a.75.75 0 001.5 0v-2.25H12a.75.75 0 000-1.5H9.75V7.5z" />
    </svg>
  ),
  org: (
    <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
      <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
    </svg>
  ),
};

export function AppShell() {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const navigate = useNavigate();
  const orgsQuery = useOrganizations();

  function handleLogout() {
    logout();
    navigate('/auth');
  }

  const organizations = orgsQuery.data ?? [];

  return (
    <div className="min-h-screen bg-neutral-950 flex">
      {/* Sidebar */}
      <aside className="w-56 border-r border-neutral-800 flex flex-col shrink-0">
        <div className="h-12 px-4 flex items-center border-b border-neutral-800">
          <span className="text-lg font-bold text-neutral-100 tracking-tight">Apex</span>
        </div>

        <nav className="flex-1 py-2 px-2 space-y-0.5">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                [
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-neutral-800 text-neutral-100'
                    : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800/50',
                ].join(' ')
              }
            >
              {iconMap[item.icon]}
              {item.label}
            </NavLink>
          ))}

          {/* Organization section */}
          {organizations.length > 0 && (
            <div className="pt-3 mt-3 border-t border-neutral-800">
              <p className="px-3 py-1 text-xs font-medium text-neutral-500 uppercase tracking-wider">
                Organizations
              </p>
              {organizations.map((org) => (
                <NavLink
                  key={org.id}
                  to={`/org/${org.id}`}
                  className={({ isActive }) =>
                    [
                      'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-neutral-800 text-neutral-100'
                        : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800/50',
                    ].join(' ')
                  }
                >
                  {iconMap.org}
                  {org.name}
                </NavLink>
              ))}
            </div>
          )}

          {/* Org action links */}
          <div className="pt-3 mt-3 border-t border-neutral-800">
            <NavLink
              to="/org/create"
              className={({ isActive }) =>
                [
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-neutral-800 text-neutral-100'
                    : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800/50',
                ].join(' ')
              }
            >
              <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
                <path d="M10.75 4.75a.75.75 0 00-1.5 0v4.5h-4.5a.75.75 0 000 1.5h4.5v4.5a.75.75 0 001.5 0v-4.5h4.5a.75.75 0 000-1.5h-4.5v-4.5z" />
              </svg>
              Create Org
            </NavLink>
            <NavLink
              to="/org/join"
              className={({ isActive }) =>
                [
                  'flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-neutral-800 text-neutral-100'
                    : 'text-neutral-400 hover:text-neutral-200 hover:bg-neutral-800/50',
                ].join(' ')
              }
            >
              <svg className="w-5 h-5" viewBox="0 0 20 20" fill="currentColor">
                <path d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" />
              </svg>
              Join Org
            </NavLink>
          </div>
        </nav>

        {/* User section */}
        <div className="p-3 border-t border-neutral-800">
          <NavLink
            to="/settings"
            className={({ isActive }) =>
              [
                'flex items-center gap-2 px-2 py-1.5 rounded-md text-xs font-medium transition-colors mb-2',
                isActive
                  ? 'bg-neutral-800 text-neutral-100'
                  : 'text-neutral-500 hover:text-neutral-300 hover:bg-neutral-800/50',
              ].join(' ')
            }
          >
            <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M7.84 1.804A1 1 0 018.82 1h2.36a1 1 0 01.98.804l.331 1.652a6.993 6.993 0 011.929 1.115l1.598-.54a1 1 0 011.186.447l1.18 2.044a1 1 0 01-.205 1.251l-1.267 1.113a7.047 7.047 0 010 2.228l1.267 1.113a1 1 0 01.206 1.25l-1.18 2.045a1 1 0 01-1.187.447l-1.598-.54a6.993 6.993 0 01-1.929 1.115l-.33 1.652a1 1 0 01-.98.804H8.82a1 1 0 01-.98-.804l-.331-1.652a6.993 6.993 0 01-1.929-1.115l-1.598.54a1 1 0 01-1.186-.447l-1.18-2.044a1 1 0 01.205-1.251l1.267-1.114a7.05 7.05 0 010-2.227L1.821 7.773a1 1 0 01-.206-1.25l1.18-2.045a1 1 0 011.187-.447l1.598.54A6.993 6.993 0 017.51 3.456l.33-1.652zM10 13a3 3 0 100-6 3 3 0 000 6z" clipRule="evenodd" />
            </svg>
            Settings
          </NavLink>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full bg-neutral-800 flex items-center justify-center text-sm font-medium text-neutral-300">
              {user?.displayName?.charAt(0)?.toUpperCase() ?? '?'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-neutral-200 truncate">{user?.displayName}</p>
              <p className="text-xs text-neutral-500 truncate">{user?.email}</p>
            </div>
            <button
              onClick={handleLogout}
              className="text-neutral-500 hover:text-neutral-300 transition-colors p-1 -m-1"
              title="Sign out"
            >
              <svg className="w-4 h-4" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M3 4.25A2.25 2.25 0 015.25 2h5.5A2.25 2.25 0 0113 4.25v2a.75.75 0 01-1.5 0v-2a.75.75 0 00-.75-.75h-5.5a.75.75 0 00-.75.75v11.5c0 .414.336.75.75.75h5.5a.75.75 0 00.75-.75v-2a.75.75 0 011.5 0v2A2.25 2.25 0 0110.75 18h-5.5A2.25 2.25 0 013 15.75V4.25z" clipRule="evenodd" />
                <path fillRule="evenodd" d="M6 10a.75.75 0 01.75-.75h9.546l-1.048-.943a.75.75 0 111.004-1.114l2.5 2.25a.75.75 0 010 1.114l-2.5 2.25a.75.75 0 11-1.004-1.114l1.048-.943H6.75A.75.75 0 016 10z" clipRule="evenodd" />
              </svg>
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 min-w-0 flex flex-col">
        <Outlet />
      </main>
    </div>
  );
}
