import { Routes } from '@angular/router';
import { AccountsPage } from './accounts/accounts-page';
import { ImportsPage } from './imports/imports-page';
import { TransactionsPage } from './transactions/transactions-page';
import { ImportReviewPage } from './imports/import-review-page';
import { DashboardPage } from './dashboard/dashboard-page';
import { AccountHistoryPage } from './accounts/account-history-page';
import { NotificationsPage } from './notifications/notifications-page';
import { AssistantPage } from './assistant/assistant-page';

export const routes: Routes = [
  { path: 'dashboard', component: DashboardPage, title: 'Dashboard | Finance Tracker' },
  { path: 'accounts/:accountId/history', component: AccountHistoryPage, title: 'Account History | Finance Tracker' },
  { path: 'accounts', component: AccountsPage, title: 'Accounts | Finance Tracker' },
  { path: 'imports', component: ImportsPage, title: 'Import statement | Finance Tracker' },
  { path: 'imports/:importId/review', component: ImportReviewPage, title: 'Review import | Finance Tracker' },
  { path: 'transactions', component: TransactionsPage, title: 'Transactions | Finance Tracker' },
  { path: 'notifications', component: NotificationsPage, title: 'Notifications | Finance Tracker' },
  { path: 'assistant', component: AssistantPage, title: 'Finance Assistant | Finance Tracker' },
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: '**', redirectTo: 'accounts' },
];
