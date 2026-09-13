import { AccountType } from './account';

export interface FinanceTransaction {
  id: string;
  accountId: string | null;
  accountName: string | null;
  accountType: AccountType | null;
  date: string;
  description: string;
  merchantName: string;
  amount: number;
  balance: number | null;
  category: string;
  categorizationConfidence: 'HIGH' | 'MEDIUM' | 'NEEDS_REVIEW';
  transferGroupId: string | null;
  status: string;
}
