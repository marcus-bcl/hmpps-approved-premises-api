ALTER TABLE lost_beds RENAME TO cas3_void_bedspaces;

ALTER TABLE lost_bed_cancellations RENAME TO cas3_void_bedspace_cancellations;

DELETE FROM lost_bed_reasons WHERE service_scope='approved-premises';
ALTER TABLE lost_bed_reasons DROP COLUMN service_scope;
ALTER TABLE lost_bed_reasons RENAME TO cas3_void_bedspace_reasons;