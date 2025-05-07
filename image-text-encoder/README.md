# Image-Text Encoder

The Image-Text Encoder is a service that generates vector embeddings for both images and text, enabling multimodal AI applications. It transforms visual and textual content into a shared vector space, allowing for cross-modal similarity comparisons, search, and retrieval operations.

## General Purpose

This service enables various AI applications such as:
- Semantic image search using text queries
- Finding similar images based on content
- Cross-modal retrieval (finding images that match text descriptions and vice versa)
- Building multimodal AI systems that understand both visual and textual information

## Models Used

The service leverages state-of-the-art CLIP (Contrastive Language-Image Pre-training) models:

- **Image Encoding**: Uses the `clip-ViT-B-32` model, which is based on a Vision Transformer architecture with 32x32 patch size
- **Text Encoding**: Employs the `clip-ViT-B-32-multilingual-v1` model from sentence-transformers, which extends CLIP's capabilities to support 50+ languages while maintaining alignment with the image vector space

Both models produce 512-dimensional embeddings that exist in the same vector space, allowing for direct comparison between image and text embeddings.

## Building the Docker Image

The Dockerfile uses a multi-stage build process to ensure the container works completely offline after being built:

1. **Build Stage**: Downloads and caches all required models during the build process
2. **Runtime Stage**: Uses the pre-downloaded models without requiring internet access

To build the Docker image, navigate to the directory containing the Dockerfile and run:

```bash
docker build -t image-text-encoder .
```

This will create a Docker image named `image-text-encoder` with all the necessary dependencies and pre-downloaded models. The build process may take some time as it downloads the models, but once built, the container will work entirely offline.

## Running the Container

To run the container, use the following command:

```bash
docker run -d -p 5555:5555 --name image-text-encoder-container image-text-encoder
```

This will:
- Run the container in detached mode (`-d`)
- Map port 5555 from the container to port 5555 on your host (`-p 5555:5555`)
- Name the container `image-text-encoder-container`

You can verify the container is running with:

```bash
docker ps
```

And check the logs with:

```bash
docker logs image-text-encoder-container
```

## Making Requests

### Encoding Text

To encode text, send a POST request to the `/encode_text` endpoint with a JSON payload containing the text:

```bash
curl -X POST http://localhost:5555/encode_text \
  -H "Content-Type: application/json" \
  -d '{"text": "This is a sample text to encode"}'
```

### Encoding Images

There are two ways to encode images: using a URL or using Base64 encoding.

#### Using a URL

To encode an image from a URL, send a POST request to the `/encode_image` endpoint with a JSON payload containing the image URL:

```bash
curl -X POST http://localhost:5555/encode_image \
  -H "Content-Type: application/json" \
  -d '{"image_url": "https://example.com/path/to/image.jpg"}'
```

#### Using Base64

To encode an image using Base64:

1. First, convert your image to Base64:

```bash
base64 -i path/to/your/image.jpg | tr -d '\n' > image.b64
```

2. Then, send a POST request with the Base64-encoded image:

```bash
curl -X POST http://localhost:5555/encode_image \
  -H "Content-Type: application/json" \
  -d "{\"image_b64\": \"$(cat image.b64)\"}"
```

Alternatively, you can do it in one command:

```bash
curl -X POST http://localhost:5555/encode_image \
  -H "Content-Type: application/json" \
  -d "{\"image_b64\": \"$(base64 -i path/to/your/image.jpg | tr -d '\n')\"}"
```

## Health Check

To check if the service is running properly:

```bash
curl http://localhost:5555/health
```

This should return `{"status": "healthy"}` if the service is running correctly.
