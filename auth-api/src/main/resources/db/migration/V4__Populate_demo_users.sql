DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tb_user
        WHERE id BETWEEN 101 AND 120
           OR email IN (
               'ana.bertoldi@demo.example.com',
               'gustavo.rech@demo.example.com',
               'fernanda.zanella@demo.example.com',
               'rafael.machado@demo.example.com',
               'luciana.kessler@demo.example.com',
               'bruno.schmitt@demo.example.com',
               'camila.bernardi@demo.example.com',
               'diego.kowalski@demo.example.com',
               'juliana.ferreira@demo.example.com',
               'marcelo.oliveira@demo.example.com',
               'patricia.souza@demo.example.com',
               'thiago.nascimento@demo.example.com',
               'carolina.ribeiro@demo.example.com',
               'leonardo.araujo@demo.example.com',
               'vanessa.duarte@demo.example.com',
               'ricardo.moura@demo.example.com',
               'tatiane.santos@demo.example.com',
               'felipe.cavalcanti@demo.example.com',
               'aline.barbosa@demo.example.com',
               'rodrigo.pinheiro@demo.example.com'
           )
        )
    THEN
        RAISE EXCEPTION 'Demo user IDs or emails already exist; refusing to overwrite business data';
    END IF;
END;
$$;

INSERT INTO tb_user (id, name, email, password, type) VALUES
    (101, 'Ana Paula Bertoldi', 'ana.bertoldi@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (102, 'Gustavo Rech', 'gustavo.rech@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (103, 'Fernanda Zanella', 'fernanda.zanella@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (104, 'Rafael Machado', 'rafael.machado@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (105, 'Luciana Kessler', 'luciana.kessler@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (106, 'Bruno Schmitt', 'bruno.schmitt@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (107, 'Camila Bernardi', 'camila.bernardi@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (108, 'Diego Kowalski', 'diego.kowalski@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (109, 'Juliana Ferreira', 'juliana.ferreira@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (110, 'Marcelo Oliveira', 'marcelo.oliveira@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (111, 'Patrícia Souza', 'patricia.souza@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (112, 'Thiago Nascimento', 'thiago.nascimento@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (113, 'Carolina Ribeiro', 'carolina.ribeiro@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (114, 'Leonardo Araújo', 'leonardo.araujo@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (115, 'Vanessa Duarte', 'vanessa.duarte@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (116, 'Ricardo Moura', 'ricardo.moura@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (117, 'Tatiane Santos', 'tatiane.santos@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (118, 'Felipe Cavalcanti', 'felipe.cavalcanti@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (119, 'Aline Barbosa', 'aline.barbosa@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1),
    (120, 'Rodrigo Pinheiro', 'rodrigo.pinheiro@demo.example.com', '$2b$10$y4tgisKXHz7B1ZAlKMDI8.FOXU5EbingP931O/2z7Tq1ROCnOD1vi', 1);

SELECT setval(pg_get_serial_sequence('tb_user', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM tb_user), 1), 120), true);
