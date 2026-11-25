DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM tb_product
        WHERE id BETWEEN 1001 AND 1030
    ) THEN
        RAISE EXCEPTION 'Demo product IDs already exist; refusing to overwrite business data';
    END IF;
END;
$$;

INSERT INTO tb_product (
    id, name, brand, model, description, currency, price, subcategory_id,
    product_type, active
)
SELECT seeds.id, seeds.name, seeds.brand, seeds.model, seeds.description, 'BRL', seeds.price,
       subcategories.id, seeds.product_type, seeds.active
FROM (
    VALUES
        (1001, 'Erva-mate Barão Tradicional 1 kg', 'Barão de Cotegipe', 'Tradicional 1 kg', 'Erva-mate tradicional para chimarrão, moagem média e sabor equilibrado. Pacote de 1 kg.', 24.90, 'erva-mate', 'tradicional', 'MULTI_UNIT', TRUE),
        (1002, 'Erva-mate Ximango Moída Grossa 1 kg', 'Ximango', 'Moída Grossa 1 kg', 'Erva-mate de moagem grossa, com mais palito e mate mais duradouro na cuia. Pacote de 1 kg.', 27.90, 'erva-mate', 'moida-grossa', 'MULTI_UNIT', TRUE),
        (1003, 'Erva-mate Vier Pura Folha 1 kg', 'Vier', 'Pura Folha 1 kg', 'Erva-mate pura folha, sem palito, de cor verde intensa e sabor suave. Pacote de 1 kg.', 29.90, 'erva-mate', 'pura-folha', 'MULTI_UNIT', TRUE),
        (1004, 'Erva-mate Rei Verde Nativa 1 kg', 'Rei Verde', 'Nativa 1 kg', 'Erva-mate nativa, colhida de ervais sombreados, com amargor marcante. Pacote de 1 kg.', 26.50, 'erva-mate', 'nativa', 'MULTI_UNIT', TRUE),
        (1005, 'Erva-mate Madrugada Tradicional 500 g', 'Madrugada', 'Tradicional 500 g', 'Erva-mate tradicional em embalagem de 500 g, ideal para quem toma chimarrão sozinho.', 13.90, 'erva-mate', 'tradicional', 'MULTI_UNIT', TRUE),
        (1006, 'Erva-mate com Menta e Boldo 500 g', 'Barão de Cotegipe', 'Menta e Boldo 500 g', 'Erva-mate composta com menta e boldo para um chimarrão refrescante. Pacote de 500 g. Linha descontinuada.', 15.90, 'erva-mate', 'com-sabores', 'MULTI_UNIT', FALSE),
        (1007, 'Erva-mate para Tereré Limão 500 g', 'Mate Laranjeira', 'Tereré Limão 500 g', 'Erva-mate de moagem grossa com toque de limão, própria para tereré com água gelada. Pacote de 500 g.', 17.90, 'erva-mate', 'para-terere', 'MULTI_UNIT', TRUE),
        (1008, 'Erva-mate Envelhecida Premium 1 kg', 'Seiva Pura', 'Envelhecida 1 kg', 'Erva-mate envelhecida por 12 meses em barricas, sabor encorpado e menos amargo. Pacote de 1 kg.', 39.90, 'erva-mate', 'nativa', 'MULTI_UNIT', TRUE),
        (1009, 'Cuia de Porongo com Bocal de Alpaca', 'Artesanato Missioneiro', 'Porongo Bocal Alpaca', 'Cuia de porongo curada, peça única, com bocal de alpaca cinzelado à mão.', 189.90, 'cuias', 'porongo', 'SINGLE_UNIT', TRUE),
        (1010, 'Cuia de Porongo Lisa Tradicional', 'Artesanato Missioneiro', 'Porongo Lisa', 'Cuia de porongo lisa e curada, formato tradicional gaúcho. Peça única.', 69.90, 'cuias', 'porongo', 'SINGLE_UNIT', TRUE),
        (1011, 'Cuia de Porongo Revestida em Couro', 'Gaúcha Artesanal', 'Porongo Couro', 'Cuia de porongo revestida em couro legítimo com costura aparente. Peça única.', 129.90, 'cuias', 'porongo', 'SINGLE_UNIT', TRUE),
        (1012, 'Cuia de Porongo com Bocal de Prata 950', 'Prataria Gaúcha', 'Porongo Bocal Prata 950', 'Cuia de porongo selecionado com bocal e base em prata 950 trabalhada. Peça única de coleção.', 749.90, 'cuias', 'porongo', 'SINGLE_UNIT', TRUE),
        (1013, 'Cuia de Madeira de Angico', 'Madeiras do Pampa', 'Angico Torneada', 'Cuia torneada em madeira de angico, impermeabilizada com cera natural. Peça única.', 89.90, 'cuias', 'madeira', 'SINGLE_UNIT', TRUE),
        (1014, 'Guampa para Tereré', 'Pantanal Artesanal', 'Guampa Chifre', 'Guampa de chifre bovino polido, tradicional para tereré. Peça única.', 59.90, 'cuias', 'terere', 'SINGLE_UNIT', TRUE),
        (1015, 'Bomba Inox com Mola', 'Bombas Campeiras', 'Inox 304 Mola', 'Bomba em aço inox 304 com filtro de mola removível, fácil de limpar.', 39.90, 'bombas', 'aco-inoxidavel', 'MULTI_UNIT', TRUE),
        (1016, 'Bomba Inox Desmontável', 'Bombas Campeiras', 'Inox Desmontável', 'Bomba de inox com bojo desmontável por rosca, para higienização completa.', 49.90, 'bombas', 'aco-inoxidavel', 'MULTI_UNIT', TRUE),
        (1017, 'Bomba de Alpaca Bico de Pato', 'Ourivesaria do Sul', 'Alpaca Bico de Pato', 'Bomba de alpaca com bico de pato e bojo tipo colher, acabamento polido.', 119.90, 'bombas', 'de-alpaca', 'MULTI_UNIT', TRUE),
        (1018, 'Bomba Banhada a Prata com Detalhe em Ouro', 'Ourivesaria do Sul', 'Prata e Ouro', 'Bomba banhada a prata com aplicação de detalhe em ouro, acompanha estojo.', 289.90, 'bombas', 'prata', 'MULTI_UNIT', TRUE),
        (1019, 'Bomba de Aço Comum', 'Bombas Campeiras', 'Aço Comum', 'Bomba simples de aço com bojo fixo. Modelo descontinuado.', 19.90, 'bombas', 'aco-comum', 'MULTI_UNIT', FALSE),
        (1020, 'Garrafa Térmica Termolar Lumina 1 L', 'Termolar', 'Lumina 1 L', 'Garrafa térmica de 1 litro com bico corta-gotas próprio para chimarrão.', 99.90, 'garrafa-termica', 'termolar', 'MULTI_UNIT', TRUE),
        (1021, 'Garrafa Térmica Termolar Magic Pump 1,8 L', 'Termolar', 'Magic Pump 1,8 L', 'Garrafa térmica de 1,8 litro com sistema de pressão, mantém a água quente por até 12 horas.', 139.90, 'garrafa-termica', 'termolar', 'MULTI_UNIT', TRUE),
        (1022, 'Garrafa Térmica Mokha Inox 1 L', 'Mokha', 'Inox 1 L', 'Garrafa térmica em aço inox de parede dupla, 1 litro, com alça e bico dosador.', 159.90, 'garrafa-termica', 'mokha', 'MULTI_UNIT', TRUE),
        (1023, 'Garrafa Térmica Mokha Bico Chimarrão 1,2 L', 'Mokha', 'Bico Chimarrão 1,2 L', 'Garrafa térmica de 1,2 litro com bico fino para servir o mate sem lavar a erva.', 119.90, 'garrafa-termica', 'mokha', 'MULTI_UNIT', TRUE),
        (1024, 'Mateira de Couro Legítimo', 'Selaria Campeira', 'Couro Legítimo', 'Mateira fechada em couro legítimo com espaço para térmica, cuia, bomba e erva.', 349.90, 'mateiras', 'fechadas', 'MULTI_UNIT', TRUE),
        (1025, 'Mateira de Lona Reforçada', 'Selaria Campeira', 'Lona Reforçada', 'Mateira aberta em lona reforçada, leve e resistente para levar o chimarrão ao trabalho.', 129.90, 'mateiras', 'abertas', 'MULTI_UNIT', TRUE),
        (1026, 'Mateira Térmica com Alça', 'Selaria Campeira', 'Térmica com Alça', 'Mateira fechada com forro térmico e alça transversal ajustável.', 179.90, 'mateiras', 'fechadas', 'MULTI_UNIT', TRUE),
        (1027, 'Porta-Erva de Couro com Tampa', 'Selaria Campeira', 'Couro com Tampa', 'Porta-erva revestido em couro, com tampa de rosca, capacidade para 500 g.', 79.90, 'porta-erva-mate', 'couro', 'MULTI_UNIT', TRUE),
        (1028, 'Porta-Erva em Lata Decorada', 'Casa do Mate', 'Lata Decorada', 'Lata decorada com motivos gaúchos e tampa hermética para conservar a erva-mate.', 34.90, 'porta-erva-mate', 'lata', 'MULTI_UNIT', TRUE),
        (1029, 'Suporte para Cuia em Madeira Maciça', 'Madeiras do Pampa', 'Suporte Maciço', 'Suporte em madeira maciça para apoiar a cuia e a bomba durante a roda de mate.', 29.90, 'acessorios-para-chimarrao', 'suportes-para-cuia', 'MULTI_UNIT', TRUE),
        (1030, 'Tapa-Erva em Inox', 'Casa do Mate', 'Tapa-Erva Inox', 'Tapa-erva em aço inox para montar o chimarrão sem desmanchar o topete da erva.', 16.90, 'acessorios-para-chimarrao', 'tapa-erva', 'MULTI_UNIT', TRUE)
) AS seeds(id, name, brand, model, description, price, category_slug, subcategory_slug, product_type, active)
JOIN categories ON categories.slug = seeds.category_slug
JOIN subcategories ON subcategories.category_id = categories.id AND subcategories.slug = seeds.subcategory_slug;

UPDATE tb_product
SET active = FALSE
WHERE name IN (
    'Erva-mate Nativa 1 kg',
    'Erva-mate para tereré 500 g',
    'Erva-mate com menta 500 g',
    'Erva-mate moída grossa 1 kg',
    'Erva-mate pura folha 1 kg',
    'Erva-mate tradicional 1 kg',
    'Cuia de porongo artesanal',
    'Cuia de madeira torneada',
    'Cuia para tereré',
    'Bomba de chimarrão em aço',
    'Bomba de chimarrão inox',
    'Bomba de chimarrão acabamento prata',
    'Bomba de chimarrão em alpaca',
    'Garrafa térmica Termolar 1 L',
    'Garrafa térmica Mokha 1 L',
    'Mateira aberta em lona',
    'Mateira fechada em couro sintético',
    'Porta-erva-mate de couro',
    'Porta-erva-mate em lata',
    'Porta-erva-mate de porongo',
    'Porta-erva-mate de madeira',
    'Suporte para cuia em madeira',
    'Tapa-erva ajustável',
    'Vira-mate para cuia'
);

SELECT setval(pg_get_serial_sequence('tb_product', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM tb_product), 1), 1030), true);
