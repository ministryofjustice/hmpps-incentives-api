insert into iep_level (iep_code, iep_description, sequence, active) values ('ENT', 'Entry', 99, false);
insert into iep_level (iep_code, iep_description, sequence, active) values ('EN2', 'Enhanced 2', 4, true);

delete from iep_level where iep_code = 'ENH2';