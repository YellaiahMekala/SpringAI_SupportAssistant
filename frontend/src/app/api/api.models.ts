export interface Me {
  subject: string;
  roles: string[];
  displayName: string | null;
}

export type OrderStatus = 'PLACED' | 'SHIPPED' | 'DELAYED' | 'DELIVERED' | 'CANCELLED';

export interface OrderSummary {
  orderRef: string;
  status: OrderStatus;
  promisedDate: string | null;
}

export interface OrderEvent {
  eventType: string;
  occurredAt: string;
}

export interface OrderDetail extends OrderSummary {
  events: OrderEvent[];
}

export interface UsageToday {
  day: string;
  tokensUsed: number;
  dailyLimit: number;
  remainingTokens: number;
  note: string;
}

export interface UsageSummary {
  requestId: string;
  inputTokens: number;
  outputTokens: number;
  /** True when the counts are local estimates rather than numbers reported by the model provider. */
  estimated: boolean;
  costStatus: string;
  /** The decision: ANSWER, or ABSTAIN / CLARIFY when no model call was made. */
  status?: AnswerStatus;
  /** The final answer after the server checked its citations. Replaces the text streamed so far. */
  answer?: string;
  /** The sources the answer relied on, each checked against what was retrieved. */
  citations?: Citation[];
}

export type AnswerStatus = 'ANSWER' | 'ABSTAIN' | 'CLARIFY' | 'DENY';

export type StreamEvent =
  | { type: 'token'; text: string }
  | { type: 'done'; usage: UsageSummary };

export interface Citation {
  id: string;
  documentId: number;
  title: string;
  version: number;
  section: string | null;
  page: number | null;
  chunkIndex: number;
  score: number;
  excerpt: string;
}

export type Visibility = 'PUBLIC' | 'AGENT_ONLY';

/** One stored document, as listed for support agents. */
export interface DocumentInfo {
  id: number;
  title: string;
  version: number;
  visibility: Visibility;
  chunks: number;
  /** How many of its chunks are embedded for the model in use. Fewer than `chunks` means it needs re-embedding. */
  embeddedForProvider: number;
}

export interface IngestionResult {
  documentId: number;
  title: string;
  version: number;
  chunks: number;
  /** CREATED: new. DUPLICATE: same content already stored. REINDEXED: stored, but embedded again for this model. */
  status: 'CREATED' | 'DUPLICATE' | 'REINDEXED';
}
