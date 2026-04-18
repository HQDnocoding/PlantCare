FROM python:3.11-slim AS builder

WORKDIR /build

# Install build dependencies for packages that need compilation
# (e.g. tokenizers, numpy, Pillow)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
# Install to a fixed prefix instead of --user so path is predictable
RUN pip install --no-cache-dir --prefix=/install -r requirements.txt

#=======================================

FROM python:3.11-slim AS runtime

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender1 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Copy to /usr/local so uvicorn and all packages are on PATH for any user
COPY --from=builder /install /usr/local

COPY . .

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
ENV HOME=/app
USER appuser

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1"]