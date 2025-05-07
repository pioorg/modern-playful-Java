# Multi-stage build for image-text-encoder

# Build stage for downloading models
FROM python:3.9-slim AS builder

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
# Install typing-extensions first to avoid dependency issues
RUN pip install --no-cache-dir typing-extensions>=4.10.0

# Then install PyTorch from CPU-only index (version 2.6+ required for security)
RUN pip install --no-cache-dir torch>=2.6.0 --index-url https://download.pytorch.org/whl/cpu

# Verify PyTorch version
RUN python -c "import torch; print(f'PyTorch version: {torch.__version__}'); assert torch.__version__.startswith('2.'), f'PyTorch version {torch.__version__} is not 2.x'"

# Then install other dependencies including huggingface_hub with hf_xet
# Install transformers explicitly with a compatible version
RUN pip install --no-cache-dir \
    flask \
    transformers==4.38.0 \
    sentence-transformers \
    requests \
    Pillow \
    numpy \
    "huggingface_hub[hf_xet]"

# Copy the application code
COPY app.py .

# Pin NumPy to version below 2.0 to avoid compatibility issues
RUN pip install --no-cache-dir "numpy<2.0" --upgrade

# Create a script to download models
RUN echo '#!/usr/bin/env python \n\
import os \n\
from sentence_transformers import SentenceTransformer \n\
import torch \n\
\n\
# Ensure we are NOT in offline mode during download \n\
os.environ.pop("HF_DATASETS_OFFLINE", None) \n\
os.environ.pop("TRANSFORMERS_OFFLINE", None) \n\
\n\
print("Downloading clip-ViT-B-32...") \n\
img_model = SentenceTransformer("clip-ViT-B-32") \n\
print("Downloading clip-ViT-B-32-multilingual-v1...") \n\
text_model = SentenceTransformer("sentence-transformers/clip-ViT-B-32-multilingual-v1") \n\
\n\
# Verify models work by encoding sample data \n\
print("Testing models...") \n\
img_embedding = img_model.encode("Test image") \n\
text_embedding = text_model.encode("Test text") \n\
print("Models downloaded and tested successfully") \n\
' > download_models.py

# Download models during build
RUN python download_models.py

# List cache directories to verify their locations
RUN find / -name ".cache" -type d | grep -v proc

# Runtime stage
FROM python:3.9-slim

# Set metadata
LABEL maintainer="Image Encoder Service"
LABEL description="Docker container running CLIP model for image embeddings"

# We'll set offline mode after downloading the models

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
# Install typing-extensions first to avoid dependency issues
RUN pip install --no-cache-dir typing-extensions>=4.10.0

# Then install PyTorch from CPU-only index (version 2.6+ required for security)
RUN pip install --no-cache-dir torch>=2.6.0 --index-url https://download.pytorch.org/whl/cpu

# Verify PyTorch version
RUN python -c "import torch; print(f'PyTorch version: {torch.__version__}'); assert torch.__version__.startswith('2.'), f'PyTorch version {torch.__version__} is not 2.x'"

# Then install other dependencies including huggingface_hub with hf_xet
# Install transformers explicitly with a compatible version
RUN pip install --no-cache-dir \
    flask \
    transformers==4.38.0 \
    sentence-transformers \
    requests \
    Pillow \
    numpy \
    "huggingface_hub[hf_xet]"

# Copy the application code
COPY app.py .

# Pin NumPy to version below 2.0 to avoid compatibility issues
RUN pip install --no-cache-dir "numpy<2.0" --upgrade

# Create a non-root user and switch to it for security
RUN adduser --disabled-password --gecos '' appuser
RUN chown -R appuser:appuser /app

# Create a script to download models in the runtime stage
COPY --from=builder /app/download_models.py /app/download_models.py
RUN chmod +x /app/download_models.py

# Run as root to download models
RUN python download_models.py

# Set proper permissions for the app user
RUN chown -R appuser:appuser /app
RUN mkdir -p /home/appuser/.cache
RUN if [ -d "/root/.cache" ]; then cp -r /root/.cache/* /home/appuser/.cache/ || true; fi
RUN chown -R appuser:appuser /home/appuser/.cache

# Now set offline mode for runtime
ENV HF_DATASETS_OFFLINE=1
ENV TRANSFORMERS_OFFLINE=1

USER appuser

# Expose the port the app runs on
EXPOSE 5555

# Add healthcheck using the health endpoint
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:5555/health || exit 1

# Command to run the application
CMD ["python", "app.py"]
