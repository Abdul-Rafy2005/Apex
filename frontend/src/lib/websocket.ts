import { Client as StompClient, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const DEFAULT_WS_URL = 'http://localhost:8080/ws';

type ConnectionCallback = () => void;
type ErrorCallback = (error: string) => void;

let stompClient: StompClient | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
const pendingSubscriptions = new Map<string, (body: string) => void>();

function getWsUrl(): string {
  return import.meta.env.VITE_WS_URL ?? DEFAULT_WS_URL;
}

export function connectWebSocket(
  token: string,
  opts?: {
    onConnect?: ConnectionCallback;
    onDisconnect?: ConnectionCallback;
    onError?: ErrorCallback;
  },
): void {
  if (stompClient?.active) {
    stompClient.deactivate();
  }

  stompClient = new StompClient({
    webSocketFactory: () => new SockJS(`${getWsUrl()}?token=${token}`) as unknown as WebSocket,
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
    onConnect: () => {
      opts?.onConnect?.();
      resubscribePending();
    },
    onDisconnect: () => {
      opts?.onDisconnect?.();
    },
    onStompError: (frame) => {
      const msg = frame.headers['message'] ?? 'Unknown STOMP error';
      console.error('[WS] STOMP error:', msg, frame.body);
      opts?.onError?.(String(msg));
    },
  });

  stompClient.activate();
}

function resubscribePending(): void {
  if (!stompClient?.connected) return;
  for (const [dest, cb] of pendingSubscriptions) {
    subscribe(dest, cb);
  }
}

export function subscribe(
  destination: string,
  callback: (body: string) => void,
): StompSubscription | null {
  if (!stompClient?.connected) {
    pendingSubscriptions.set(destination, callback);
    return null;
  }

  const sub = stompClient.subscribe(destination, (message: IMessage) => {
    callback(message.body);
  });

  pendingSubscriptions.delete(destination);
  return sub;
}

export function unsubscribe(destination: string): void {
  pendingSubscriptions.delete(destination);
}

export function disconnectWebSocket(): void {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  pendingSubscriptions.clear();

  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
}

export function isConnected(): boolean {
  return stompClient?.connected ?? false;
}
