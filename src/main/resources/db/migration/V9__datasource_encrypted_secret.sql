-- Room for AES-GCM encrypted passwords (enc:v1:<base64>) next to env:VAR references.
alter table data_source alter column secret_ref type varchar(1024);
