from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer, util
from PIL import Image, ImageFile
import requests
import torch
import io
import base64
import numpy as np
import json
import logging
import sys

app = Flask(__name__)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)

# Enable loading truncated images
ImageFile.LOAD_TRUNCATED_IMAGES = True

# Initialize models at startup
logger.info("Loading models...")
# We use the original clip-ViT-B-32 for encoding images
img_model = SentenceTransformer('clip-ViT-B-32')
# Our text embedding model is aligned to the img_model and maps 50+
# languages to the same vector space
text_model = SentenceTransformer('sentence-transformers/clip-ViT-B-32-multilingual-v1')
logger.info("Models loaded successfully")

def load_image(url_or_path=None, image_b64=None):
    """Load an image from a URL, file path, or base64 string"""
    try:
        logger.info("Loading image...")

        # Handle base64 encoded image directly
        if image_b64:
            logger.info("Processing base64 encoded image")
            try:
                image_data = base64.b64decode(image_b64)
                return Image.open(io.BytesIO(image_data))
            except Exception as e:
                logger.error(f"Error decoding base64 image: {e}")
                return None

        # Handle URL, file path, or data URL
        if isinstance(url_or_path, str):
            if url_or_path.startswith("http://") or url_or_path.startswith("https://"):
                logger.info(f"Loading image from URL: {url_or_path[:50]}...")
                return Image.open(requests.get(url_or_path, stream=True).raw)
            elif url_or_path.startswith("data:image"):
                logger.info("Processing data URL image")
                # Handle base64 encoded images in data URL format
                base64_data = url_or_path.split(",")[1]
                image_data = base64.b64decode(base64_data)
                return Image.open(io.BytesIO(image_data))
            else:
                logger.info(f"Loading image from file path: {url_or_path}")
                return Image.open(url_or_path)
        elif isinstance(url_or_path, bytes):
            logger.info("Loading image from bytes")
            return Image.open(io.BytesIO(url_or_path))
        else:
            logger.error("No valid image source provided")
            return None
    except Exception as e:
        logger.error(f"Error loading image: {e}")
        return None

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint for Docker healthcheck"""
    return jsonify({"status": "healthy"}), 200

@app.route('/encode_image', methods=['POST'])
def encode_image():
    """Encode an image and return its embedding"""
    try:
        logger.info("Received request to encode image")

        # Get the request data
        if request.is_json:
            data = request.json
            logger.info("Received JSON data")
        else:
            logger.error("Request is not JSON")
            return jsonify({"error": "Request must be JSON"}), 400

        # Check if we have data
        if not data:
            logger.error("No JSON data in request")
            return jsonify({"error": "Missing request data"}), 400

        # Log the received data for debugging
        logger.info(f"Received data keys: {list(data.keys())}")

        image = None

        # Try to load image from base64 first (if it exists)
        if 'image_b64' in data and data['image_b64']:
            logger.info("Found image_b64 in request")
            image = load_image(image_b64=data['image_b64'])
            if image is None:
                logger.error("Failed to load image from base64")

        # Then try to load from URL (if it exists and we don't have an image yet)
        if image is None and 'image_url' in data and data['image_url']:
            logger.info("Found image_url in request")
            image = load_image(url_or_path=data['image_url'])
            if image is None:
                logger.error("Failed to load image from URL")

        # If we still don't have an image, return an error
        if image is None:
            if 'image_url' not in data and 'image_b64' not in data:
                logger.error("Request missing both image_url and image_b64")
                return jsonify({"error": "Missing image_url in request"}), 400
            else:
                logger.error("Failed to load image from provided sources")
                return jsonify({"error": "Failed to load image"}), 400

        # Encode the image
        logger.info("Encoding image...")
        embedding = img_model.encode(image)

        # Convert to list for JSON serialization
        embedding_list = embedding.tolist()
        logger.info(f"Successfully encoded image to {len(embedding_list)} dimensions")

        return jsonify({
            "success": True,
            "embedding": embedding_list,
            "dimensions": len(embedding_list)
        })
    except Exception as e:
        logger.error(f"Error encoding image: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/encode_text', methods=['POST'])
def encode_text():
    """Encode text and return its embedding"""
    try:
        logger.info("Received request to encode text")
        data = request.json
        if not data or 'text' not in data:
            logger.error("Missing text in request")
            return jsonify({"error": "Missing text in request"}), 400

        text = data['text']
        logger.info(f"Encoding text: '{text[:50]}{'...' if len(text) > 50 else ''}'")

        # Encode the text
        logger.info("Encoding text...")
        embedding = text_model.encode(text)

        # Convert to list for JSON serialization
        embedding_list = embedding.tolist()
        logger.info(f"Successfully encoded text to {len(embedding_list)} dimensions")

        return jsonify({
            "success": True,
            "embedding": embedding_list,
            "dimensions": len(embedding_list)
        })
    except Exception as e:
        logger.error(f"Error encoding text: {e}")
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5555)
