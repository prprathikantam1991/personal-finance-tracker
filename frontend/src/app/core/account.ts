export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'OTHER';

export interface Account {
  id: string;
  name: string;
  institution: string;
  accountType: AccountType;
  lastFour: string | null;
  currency: string;
  createdAt: string;
  currentApr: number | null;
  promotionalApr: number | null;
  promotionalAprExpiresOn: string | null;
  creditLimit: number | null;
  penaltyApr: number | null;
  statementBalance?: number | null;
  availableCredit?: number | null;
  creditUtilizationPercent?: number | null;
  previousCreditUtilizationPercent?: number | null;
  previousStatementBalance?: number | null;
  previousCreditLimit?: number | null;
  feesCharged?: number | null;
  interestCharged?: number | null;
  minimumPayment?: number | null;
  paymentDueDate?: string | null;
  cycleStartDate?: string | null;
  cycleEndDate?: string | null;
  nextExpectedStatementDate?: string | null;
  beginningBalance?: number | null;
  totalCredits?: number | null;
  totalDebits?: number | null;
  interestEarned?: number | null;
  annualInterestRate?: number | null;
  annualPercentageYield?: number | null;
  statementImportedAt?: string | null;
}
