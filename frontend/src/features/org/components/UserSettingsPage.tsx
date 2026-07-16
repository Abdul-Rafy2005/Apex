import { useState, useEffect } from 'react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { useAuthStore } from '@/store/auth.store';
import { useToggleLeaderboardVisibility } from '@/features/org/hooks/useOrgMutations';
import { apiFetch } from '@/lib/api';

interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  role: string;
  leaderboardVisible: boolean;
  createdAt: string;
}

export function UserSettingsPage() {
  const user = useAuthStore((s) => s.user);
  const toggleLeaderboard = useToggleLeaderboardVisibility();
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiFetch<UserProfile>('/api/v1/users/me')
      .then(setProfile)
      .catch(() => setProfile(null))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="p-6 space-y-4">
        <div className="h-8 w-48 bg-neutral-800 rounded animate-pulse" />
        <Card><div className="h-32 bg-neutral-800 rounded animate-pulse" /></Card>
      </div>
    );
  }

  return (
    <div className="p-6 max-w-lg space-y-6">
      <h1 className="text-xl font-semibold text-neutral-100">Settings</h1>

      <Card>
        <h2 className="text-sm font-medium text-neutral-300 mb-4">Profile</h2>
        <div className="space-y-3">
          <div>
            <p className="text-xs text-neutral-500">Display Name</p>
            <p className="text-sm text-neutral-100">{profile?.displayName ?? user?.displayName}</p>
          </div>
          <div>
            <p className="text-xs text-neutral-500">Email</p>
            <p className="text-sm text-neutral-100">{profile?.email ?? user?.email}</p>
          </div>
          <div>
            <p className="text-xs text-neutral-500">Role</p>
            <p className="text-sm text-neutral-100">{profile?.role ?? user?.role}</p>
          </div>
        </div>
      </Card>

      <Card>
        <h2 className="text-sm font-medium text-neutral-300 mb-4">Leaderboard</h2>
        <p className="text-xs text-neutral-500 mb-4">
          Control whether your profile appears on organization leaderboards.
        </p>
        <div className="flex items-center justify-between">
          <div>
            <p className="text-sm text-neutral-200">Visible on Leaderboard</p>
            <p className="text-xs text-neutral-500">
              When off, instructors and admins won't see you in org leaderboards.
            </p>
          </div>
          <Button
            variant={profile?.leaderboardVisible !== false ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => {
              const newValue = profile?.leaderboardVisible === false;
              toggleLeaderboard.mutate(newValue, {
                onSuccess: () => {
                  setProfile((prev) =>
                    prev ? { ...prev, leaderboardVisible: newValue } : prev,
                  );
                },
              });
            }}
            isLoading={toggleLeaderboard.isPending}
          >
            {profile?.leaderboardVisible !== false ? 'Visible' : 'Hidden'}
          </Button>
        </div>
      </Card>
    </div>
  );
}
