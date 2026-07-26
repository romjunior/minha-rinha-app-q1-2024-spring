CREATE TABLE clientes (
	id SERIAL PRIMARY KEY,
	nome VARCHAR(50) NOT NULL,
	limite INTEGER NOT NULL
);

CREATE TABLE transacoes (
	id SERIAL PRIMARY KEY,
	cliente_id INTEGER NOT NULL,
	valor INTEGER NOT NULL,
	tipo CHAR(1) NOT NULL,
	descricao VARCHAR(10) NOT NULL,
	realizada_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_clientes_transacoes_id
		FOREIGN KEY (cliente_id) REFERENCES clientes(id)
);

CREATE TABLE saldos (
	id SERIAL PRIMARY KEY,
	cliente_id INTEGER NOT NULL,
	valor INTEGER NOT NULL,
	CONSTRAINT fk_clientes_saldos_id
		FOREIGN KEY (cliente_id) REFERENCES clientes(id),
	CONSTRAINT uk_saldos_cliente UNIQUE (cliente_id)
);

CREATE INDEX idx_transacoes_cliente_data
	ON transacoes (cliente_id, realizada_em DESC, id DESC);

-- IDs do histórico não precisam ser sequenciais. Um cache maior evita uma
-- atualização da sequência para cada transação durante picos de escrita.
ALTER SEQUENCE transacoes_id_seq CACHE 1000;

DO $$
BEGIN
	INSERT INTO clientes (nome, limite)
	VALUES
		('o barato sai caro', 1000 * 100),
		('zan corp ltda', 800 * 100),
		('les cruders', 10000 * 100),
		('padaria joia de cocaia', 100000 * 100),
		('kid mais', 5000 * 100);

	INSERT INTO saldos (cliente_id, valor)
		SELECT id, 0 FROM clientes;
END;
$$;
