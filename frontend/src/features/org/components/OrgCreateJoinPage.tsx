import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { useCreateOrganization, useJoinOrganization } from '../hooks/useOrgMutations';

export function OrgCreatePage() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [type, setType] = useState('BOOTCAMP');
  const createOrg = useCreateOrganization();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    createOrg.mutate(
      { name, type },
      {
        onSuccess: (res) => {
          navigate(`/org/${res.id}`);
        },
      },
    );
  }

  return (
    <div className="p-6 max-w-lg">
      <h1 className="text-xl font-semibold text-neutral-100 mb-4">Create Organization</h1>
      <Card>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm text-neutral-300 mb-1">Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full bg-neutral-800 border border-neutral-700 rounded-md px-3 py-2 text-sm text-neutral-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
              placeholder="My Trading Bootcamp"
              required
            />
          </div>
          <div>
            <label className="block text-sm text-neutral-300 mb-1">Type</label>
            <select
              value={type}
              onChange={(e) => setType(e.target.value)}
              className="w-full bg-neutral-800 border border-neutral-700 rounded-md px-3 py-2 text-sm text-neutral-100 focus:outline-none focus:ring-1 focus:ring-brand-500"
            >
              <option value="BOOTCAMP">Bootcamp</option>
              <option value="UNIVERSITY">University</option>
              <option value="INDIVIDUAL">Individual</option>
            </select>
          </div>
          {createOrg.isError && (
            <p className="text-xs text-loss">
              {createOrg.error instanceof Error ? createOrg.error.message : 'Failed to create organization'}
            </p>
          )}
          <Button type="submit" isLoading={createOrg.isPending} className="w-full">
            Create Organization
          </Button>
        </form>
      </Card>
    </div>
  );
}

export function OrgJoinPage() {
  const navigate = useNavigate();
  const [orgId, setOrgId] = useState('');
  const joinOrg = useJoinOrganization();

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    joinOrg.mutate(
      { organizationId: orgId },
      {
        onSuccess: () => {
          navigate(`/org/${orgId}`);
        },
      },
    );
  }

  return (
    <div className="p-6 max-w-lg">
      <h1 className="text-xl font-semibold text-neutral-100 mb-4">Join Organization</h1>
      <Card>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm text-neutral-300 mb-1">Organization ID</label>
            <input
              type="text"
              value={orgId}
              onChange={(e) => setOrgId(e.target.value)}
              className="w-full bg-neutral-800 border border-neutral-700 rounded-md px-3 py-2 text-sm text-neutral-100 focus:outline-none focus:ring-1 focus:ring-brand-500 font-mono"
              placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
              required
            />
            <p className="text-xs text-neutral-500 mt-1">
              Enter the UUID of the organization you want to join.
            </p>
          </div>
          {joinOrg.isError && (
            <p className="text-xs text-loss">
              {joinOrg.error instanceof Error ? joinOrg.error.message : 'Failed to join organization'}
            </p>
          )}
          <Button type="submit" isLoading={joinOrg.isPending} className="w-full">
            Join Organization
          </Button>
        </form>
      </Card>
    </div>
  );
}
