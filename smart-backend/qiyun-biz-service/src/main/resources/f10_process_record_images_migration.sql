CREATE TABLE IF NOT EXISTS `repair_process_record_image` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `process_record_id` BIGINT NOT NULL,
    `image_url` VARCHAR(500) NOT NULL,
    `created_at` DATETIME NOT NULL,
    CONSTRAINT `fk_process_record_image_record`
        FOREIGN KEY (`process_record_id`) REFERENCES `repair_process_record` (`id`) ON DELETE CASCADE,
    INDEX `idx_process_record_image_record` (`process_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `repair_process_record_image` (`process_record_id`, `image_url`, `created_at`)
SELECT `id`, `image_url`, COALESCE(`created_at`, NOW())
FROM `repair_process_record`
WHERE `image_url` IS NOT NULL
  AND `image_url` <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM `repair_process_record_image` existing
      WHERE existing.`process_record_id` = `repair_process_record`.`id`
        AND existing.`image_url` = `repair_process_record`.`image_url`
  );
