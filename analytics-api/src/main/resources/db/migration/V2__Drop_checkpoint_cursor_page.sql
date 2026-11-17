-- A coleta de pedidos não persiste mais a página entre execuções: cada execução
-- percorre todas as páginas a partir do cursor e só então grava o novo cursor.
ALTER TABLE collection_checkpoint DROP COLUMN cursor_page;
