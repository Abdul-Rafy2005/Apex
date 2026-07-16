interface EmptyStateProps {
  title: string;
  description?: string;
  icon?: React.ReactNode;
}

export function EmptyState({ title, description, icon }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      {icon && <div className="text-neutral-600 mb-3">{icon}</div>}
      <h3 className="text-sm font-medium text-neutral-300">{title}</h3>
      {description && (
        <p className="text-xs text-neutral-500 mt-1 max-w-xs">{description}</p>
      )}
    </div>
  );
}
