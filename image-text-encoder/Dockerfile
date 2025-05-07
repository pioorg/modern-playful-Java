# Use Python 3.9 as the base image
FROM python:3.9-slim

# Set metadata
LABEL maintainer="Image Encoder Service"
LABEL description="Docker container running CLIP model for image embeddings"

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
# First install PyTorch from CPU-only index
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

# Then install other dependencies from default PyPI index
RUN pip install --no-cache-dir \
    flask \
    sentence-transformers \
    requests \
    Pillow \
    numpy

# Copy the application code
COPY app.py .

# Create a non-root user and switch to it for security
RUN adduser --disabled-password --gecos '' appuser
RUN chown -R appuser:appuser /app
USER appuser

# Expose the port the app runs on
EXPOSE 5555

# Add healthcheck using the health endpoint
HEALTHCHECK --interval=30s --timeout=30s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:5555/health || exit 1

# Command to run the application
CMD ["python", "app.py"]
