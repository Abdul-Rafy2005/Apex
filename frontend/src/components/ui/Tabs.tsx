import { createContext, useContext, useState, type ReactNode } from 'react';

interface TabsContextValue {
  activeTab: string;
  setActiveTab: (id: string) => void;
}

const TabsContext = createContext<TabsContextValue | null>(null);

function useTabsContext() {
  const ctx = useContext(TabsContext);
  if (!ctx) throw new Error('Tabs components must be used within <Tabs>');
  return ctx;
}

interface TabsProps {
  defaultTab: string;
  children: ReactNode;
  className?: string;
}

export function Tabs({ defaultTab, children, className = '' }: TabsProps) {
  const [activeTab, setActiveTab] = useState(defaultTab);
  return (
    <TabsContext.Provider value={{ activeTab, setActiveTab }}>
      <div className={className}>{children}</div>
    </TabsContext.Provider>
  );
}

interface TabListProps {
  children: ReactNode;
  className?: string;
}

export function TabList({ children, className = '' }: TabListProps) {
  return (
    <div
      className={[
        'flex border-b border-neutral-800 gap-0',
        className,
      ].join(' ')}
      role="tablist"
    >
      {children}
    </div>
  );
}

interface TabTriggerProps {
  id: string;
  children: ReactNode;
  className?: string;
}

export function TabTrigger({ id, children, className = '' }: TabTriggerProps) {
  const { activeTab, setActiveTab } = useTabsContext();
  const isActive = activeTab === id;

  return (
    <button
      role="tab"
      aria-selected={isActive}
      onClick={() => setActiveTab(id)}
      className={[
        'px-4 py-2 text-sm font-medium transition-colors -mb-px',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 focus-visible:ring-offset-neutral-950 rounded-t-md',
        isActive
          ? 'text-neutral-100 border-b-2 border-brand-500'
          : 'text-neutral-400 hover:text-neutral-200 border-b-2 border-transparent',
        className,
      ].join(' ')}
    >
      {children}
    </button>
  );
}

interface TabContentProps {
  id: string;
  children: ReactNode;
  className?: string;
}

export function TabContent({ id, children, className = '' }: TabContentProps) {
  const { activeTab } = useTabsContext();
  if (activeTab !== id) return null;
  return (
    <div role="tabpanel" className={['pt-4', className].join(' ')}>
      {children}
    </div>
  );
}
