# Instala dependencias, corre las pruebas (si fallan, no hay imagen) y ejecuta con uvicorn.
FROM python:3.12.7-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 TZ=America/Bogota
WORKDIR /srv
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt

FROM base AS pruebas
COPY requirements-dev.txt pyproject.toml ./
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements-dev.txt
COPY app ./app
COPY tests ./tests
RUN python -m pytest -q && touch /tmp/pruebas-ok

FROM base
COPY --from=pruebas /tmp/pruebas-ok /tmp/pruebas-ok
COPY app ./app
RUN useradd --system --no-create-home app
USER app
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers"]
