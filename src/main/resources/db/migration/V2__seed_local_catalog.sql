INSERT INTO catalog_providers(id,code,name,category) VALUES
(gen_random_uuid(),'VINA','VinaPhone','TELECOM'),
(gen_random_uuid(),'MOBI','MobiFone','TELECOM'),
(gen_random_uuid(),'VIETTEL','Viettel','TELECOM')
ON CONFLICT DO NOTHING;

DO $$
DECLARE p record; i int;
BEGIN
  FOR p IN SELECT id,code,name FROM catalog_providers LOOP
    FOR i IN 1..20 LOOP
      INSERT INTO catalog_products(id,code,provider_id,category,name,description,price,discount,active,metadata)
      VALUES(gen_random_uuid(), p.code || '_PKG_' || i, p.id, 'DATA', p.name || ' Gói ' || i,
             'Gói dữ liệu local seed phục vụ phát triển', 10000*i, 0, true, jsonb_build_object('localSeed',true))
      ON CONFLICT DO NOTHING;
    END LOOP;
  END LOOP;
END $$;

INSERT INTO promotions(id,code,title,description,category,active,valid_from,valid_until)
VALUES
(gen_random_uuid(),'WELCOME10','Ưu đãi chào mừng','Dữ liệu seed local, không phải chương trình thật','WELCOME',true,now(),now()+interval '365 day'),
(gen_random_uuid(),'DATA5','Ưu đãi data','Dữ liệu seed local, không phải chương trình thật','TELECOM',true,now(),now()+interval '365 day')
ON CONFLICT DO NOTHING;

INSERT INTO partner_links(id,name,web_url,android_deep_link,ios_deep_link,fallback_url,active) VALUES
(gen_random_uuid(),'FPT','https://fpt.vn',NULL,NULL,'https://fpt.vn',true),
(gen_random_uuid(),'CGV','https://www.cgv.vn',NULL,NULL,'https://www.cgv.vn',true)
ON CONFLICT DO NOTHING;
