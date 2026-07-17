ALTER TABLE `sys_user`
    ADD COLUMN `responsible_area` VARCHAR(500) NULL,
    ADD COLUMN `specialties` VARCHAR(500) NULL;

UPDATE `sys_user`
SET `responsible_area` = NULL,
    `specialties` = NULL
WHERE `role` <> 'STAFF';
