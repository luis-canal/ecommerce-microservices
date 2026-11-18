package br.edu.atitus.productapi.enums;

/**
 * Filtro de status da listagem administrativa de produtos.
 * ACTIVE: produto, subcategoria e categoria ativos (mesmo critério do catálogo público).
 * INACTIVE: produto com active = false, independentemente da subcategoria/categoria.
 * ALL: sem filtro de ativo.
 */
public enum ProductStatusFilter {
    ACTIVE,
    INACTIVE,
    ALL
}
