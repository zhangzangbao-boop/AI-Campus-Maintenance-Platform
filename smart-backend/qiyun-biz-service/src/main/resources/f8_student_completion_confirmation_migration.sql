ALTER TABLE `repair_order`
    ADD COLUMN `student_confirmed_at` DATETIME NULL,
    ADD COLUMN `student_rejection_reason` TEXT NULL;

UPDATE `repair_order`
SET `student_confirmed_at` = COALESCE(`student_confirmed_at`, `completed_at`)
WHERE `status` IN ('WAITING_FEEDBACK', 'FEEDBACKED', 'CLOSED')
  AND `completed_at` IS NOT NULL
  AND `student_confirmed_at` IS NULL;
