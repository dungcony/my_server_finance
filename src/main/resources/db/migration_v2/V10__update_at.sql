CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- gắn cho mọi bảng có cột updated_at
DO $$
DECLARE t TEXT;
BEGIN
   FOR t IN
       SELECT c.table_name
       FROM information_schema.columns c
       JOIN information_schema.tables tb
         ON tb.table_schema = c.table_schema AND tb.table_name = c.table_name
       WHERE c.table_schema = 'public'
         AND c.column_name = 'updated_at'
         AND tb.table_type = 'BASE TABLE'
   LOOP
       EXECUTE format(
           'CREATE TRIGGER %I BEFORE UPDATE ON %I
            FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at()',
           'trg_' || t || '_updated_at', t);
   END LOOP;

END $$;
