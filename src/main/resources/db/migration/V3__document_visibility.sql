-- Phase 5: who may retrieve a document. PUBLIC documents are support policies any signed-in user may be
-- answered from. AGENT_ONLY documents (internal procedures) are retrieved only for support agents. The filter is
-- applied in the retrieval query, before any text can reach a prompt.

ALTER TABLE documents
    ADD COLUMN visibility VARCHAR(12) NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'AGENT_ONLY'));
