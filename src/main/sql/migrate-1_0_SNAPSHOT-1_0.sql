CREATE INDEX IF NOT EXISTS `ix_team_region_battlenet_id` ON `team` (`region`, `battlenet_id`, `id`);

DELETE `a`
FROM
`team` as `a`,
`team` as `b`
WHERE
`a`.`region` = `b`.`region`
AND `a`.`battlenet_id` = `b`.`battlenet_id`
AND `a`.`id` < `b`.`id`;

DROP INDEX `ix_team_region_battlenet_id` ON `team`;

CREATE INDEX `ix_team_division_id_fk` on `team` (`division_id`);
ALTER TABLE `team`
DROP CONSTRAINT `uq_team_division_id_battlenet_id`,
ADD CONSTRAINT `uq_team_region_battlenet_id` UNIQUE (`region`, `battlenet_id`);
