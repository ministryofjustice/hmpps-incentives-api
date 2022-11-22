-- Add when_updated column to next_review_date
ALTER TABLE next_review_date
ADD COLUMN when_updated timestamp with time zone not null default now();
