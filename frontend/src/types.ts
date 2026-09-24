export type Kind = 'INCOME' | 'EXPENSE';
export interface Category { categoryId: number; name: string; type: Kind }
export interface Transaction { transactionId: number; categoryId: number; amount: number; transactionType: Kind; transactionAt: string; description: string | null; vendorName: string | null; balanceAfter: number }
export interface Analysis {
  periodStart: string; periodEnd: string; currentBalance: number; totalIncome: number; totalExpense: number;
  previousPeriodExpense: number; expenseChangeRate: number | null; averageExpense: number; transactionCount: number;
  topCategory: string | null; topVendor: string | null;
  categoryExpenses: {category: string; amount: number; count: number}[];
  monthlyExpenses: {label: string; amount: number}[];
}
export interface Report { reportId: number; reportType: string; reportMonth: string | null; reportContent: string; generatedAt: string }
export interface Session { accessToken: string; email: string; expiresAt: string }
export type Mapping = Record<'date' | 'description' | 'category' | 'amount' | 'type', string>;
export interface Preview { headers: string[]; rows: Record<string, string>[]; suggestedMapping: Partial<Mapping>; totalRows: number }
export interface CsvResult { validation: { validRows: number; errorRows: number; suspectedDuplicates: number; errors: {rowNumber: number; field: string; message: string}[] }; analysis: Analysis | null }
