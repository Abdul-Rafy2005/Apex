import type { TableHTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react';

export function Table({ className = '', children, ...props }: TableHTMLAttributes<HTMLTableElement>) {
  return (
    <div className="w-full overflow-x-auto">
      <table
        className={['w-full text-sm', className].join(' ')}
        {...props}
      >
        {children}
      </table>
    </div>
  );
}

export function Thead({ className = '', ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <thead className={['border-b border-neutral-800', className].join(' ')} {...props} />;
}

export function Tbody({ className = '', ...props }: React.HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={className} {...props} />;
}

export function Tr({ className = '', ...props }: React.HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr
      className={['border-b border-neutral-800/50 hover:bg-neutral-800/30 transition-colors', className].join(' ')}
      {...props}
    />
  );
}

export function Th({ className = '', ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={[
        'h-9 px-3 text-left text-xs font-medium text-neutral-400 uppercase tracking-wider',
        'first:pl-4 last:pr-4',
        className,
      ].join(' ')}
      {...props}
    />
  );
}

export function Td({ className = '', ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td
      className={[
        'h-10 px-3 text-sm text-neutral-200',
        'first:pl-4 last:pr-4',
        className,
      ].join(' ')}
      {...props}
    />
  );
}
