FROM python:3.11-slim

WORKDIR /app

# System deps for MediaPipe + OpenCV headless
# libgles2 + libglfw3 required by MediaPipe's C bindings (libGLESv2.so.2)
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libgles2 \
    libglfw3 \
    libegl1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

# Use uvicorn directly — proper SIGTERM handling in containers
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
