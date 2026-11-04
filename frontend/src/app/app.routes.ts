import { Routes } from '@angular/router';
import { agentGuard } from './auth/agent.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'chat' },
  { path: 'chat', title: 'Ask support', loadComponent: () => import('./pages/chat/chat').then((m) => m.Chat) },
  { path: 'orders', title: 'Orders', loadComponent: () => import('./pages/orders/orders').then((m) => m.Orders) },
  { path: 'usage', title: 'Usage', loadComponent: () => import('./pages/usage/usage').then((m) => m.Usage) },
  {
    path: 'documents',
    title: 'Documents',
    canMatch: [agentGuard],
    loadComponent: () => import('./pages/documents/documents').then((m) => m.Documents),
  },
  { path: '**', redirectTo: 'chat' },
];
