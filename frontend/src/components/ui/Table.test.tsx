import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Table, Thead, Tbody, Tr, Th, Td } from './Table';

describe('Table', () => {
  it('renders a table element', () => {
    render(
      <Table>
        <Thead>
          <Tr>
            <Th>Name</Th>
            <Th>Price</Th>
          </Tr>
        </Thead>
        <Tbody>
          <Tr>
            <Td>Bitcoin</Td>
            <Td className="tabular-nums">$42,000</Td>
          </Tr>
        </Tbody>
      </Table>,
    );

    expect(screen.getByRole('table')).toBeInTheDocument();
    expect(screen.getByText('Bitcoin')).toBeInTheDocument();
    expect(screen.getByText('$42,000')).toBeInTheDocument();
  });

  it('renders column headers', () => {
    render(
      <Table>
        <Thead>
          <Tr>
            <Th>Symbol</Th>
            <Th>Price</Th>
            <Th>24h Change</Th>
          </Tr>
        </Thead>
        <Tbody />
      </Table>,
    );

    expect(screen.getByText('Symbol')).toBeInTheDocument();
    expect(screen.getByText('Price')).toBeInTheDocument();
    expect(screen.getByText('24h Change')).toBeInTheDocument();
  });

  it('renders multiple rows', () => {
    render(
      <Table>
        <Thead>
          <Tr><Th>Asset</Th></Tr>
        </Thead>
        <Tbody>
          <Tr><Td>BTC</Td></Tr>
          <Tr><Td>ETH</Td></Tr>
          <Tr><Td>SOL</Td></Tr>
        </Tbody>
      </Table>,
    );

    expect(screen.getByText('BTC')).toBeInTheDocument();
    expect(screen.getByText('ETH')).toBeInTheDocument();
    expect(screen.getByText('SOL')).toBeInTheDocument();
  });
});
