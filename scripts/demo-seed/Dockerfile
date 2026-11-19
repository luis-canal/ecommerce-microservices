# Imagem do seed de demonstração: Python (somente biblioteca padrão) + cliente psql.
# O código e os dados são montados em /seed pelo serviço demo-seed do docker-compose,
# então alterar data/*.json ou seed.py não exige rebuild.
FROM python:3.12-alpine3.21

RUN apk add --no-cache postgresql17-client

WORKDIR /seed
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

ENTRYPOINT ["python", "-I", "/seed/seed.py"]
CMD ["help"]
