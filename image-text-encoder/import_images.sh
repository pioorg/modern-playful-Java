#!/bin/bash

# Check if a directory path is provided
if [ $# -ne 1 ]; then
    echo "Usage: $0 <directory_path>"
    exit 1
fi

# Get the directory path from the command line argument
directory_path="$1"

# Check if the directory exists
if [ ! -d "$directory_path" ]; then
    echo "Error: Directory '$directory_path' does not exist."
    exit 1
fi

# Check if Elasticsearch environment variables are set
if [ -z "$ES_URL" ] || [ -z "$ES_API_KEY" ]; then
    echo "Error: Elasticsearch environment variables ES_URL and ES_API_KEY must be set."
    echo "Example:"
    echo "export ES_URL=http://localhost:9200"
    echo "export ES_API_KEY=TFot....=="
    exit 1
fi

# Define the index name
index_name="catalogue"

# Create the index if it doesn't exist
index_exists=$(curl -k -s --connect-timeout 5 --max-time 10 -o /dev/null -w "%{http_code}" -X GET \
    -H "Authorization: ApiKey $ES_API_KEY" \
    "${ES_URL}/${index_name}")

# Check if curl command was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to check if index exists. Elasticsearch might not be running."
    exit 1
fi

if [ "$index_exists" != "200" ]; then
    echo "Creating index: $index_name"

    # Define the index mapping with the required fields
    response=$(curl -k -s --connect-timeout 5 --max-time 10 -X PUT \
        -H "Content-Type: application/json" \
        -H "Authorization: ApiKey $ES_API_KEY" \
        "${ES_URL}/${index_name}" \
        -d '{
            "mappings": {
                "properties": {
                    "filename": {
                        "type": "keyword"
                    },
                    "full-path": {
                        "type": "keyword"
                    },
                    "embedding": {
                        "type": "dense_vector",
                        "dims": 512
                    },
                    "@timestamp": {
                        "type": "date"
                    }
                }
            }
        }')

    if [ $? -ne 0 ]; then
        echo "Error: Failed to create index. Elasticsearch might not be running."
        exit 1
    fi

    echo "Index created successfully."
else
    echo "Index already exists: $index_name"
fi

echo "Processing images in directory: $directory_path"

# Common image file extensions
image_extensions=("jpg" "jpeg" "png" "gif" "bmp" "tiff" "webp")

# Function to check if a file has an image extension
is_image_file() {
    local file="$1"
    local ext="${file##*.}"
    ext=$(echo "$ext" | tr '[:upper:]' '[:lower:]')

    for valid_ext in "${image_extensions[@]}"; do
        if [ "$ext" = "$valid_ext" ]; then
            return 0
        fi
    done
    return 1
}

# Process each file in the directory
for file in "$directory_path"/*; do
    # Skip if not a file or not an image file
    if [ ! -f "$file" ] || ! is_image_file "$file"; then
        continue
    fi

    # Get the full path of the image
    full_path=$(realpath "$file")
    filename=$(basename "$file")

    echo "Processing image: $filename"

    # Convert image to Base64
    base64_data=$(base64 -i "$file" | tr -d '\n')

    # Send the Base64-encoded image to the API endpoint with a timeout
    response=$(curl -s --connect-timeout 5 --max-time 10 -X POST http://localhost:5555/encode_image \
        -H "Content-Type: application/json" \
        -d "{\"image_b64\": \"$base64_data\"}")

    # Check if curl command was successful
    if [ $? -ne 0 ]; then
        echo "Error: Failed to get vector representation for $filename. The service might not be running."
        continue
    fi

    # Extract the embedding array from the JSON response
    # Check if jq is available
    if command -v jq &> /dev/null; then
        # Use jq to extract the embedding array
        vector=$(echo "$response" | jq -c '.embedding')
        if [ -z "$vector" ] || [ "$vector" = "null" ]; then
            echo "Error: Failed to extract embedding from response for $filename."
            continue
        fi
    else
        # Fallback to grep and sed if jq is not available
        # This is a simple extraction and might not work for all JSON structures
        vector=$(echo "$response" | grep -o '"embedding":\[[^]]*\]' | sed 's/"embedding"://')
        if [ -z "$vector" ]; then
            echo "Error: Failed to extract embedding from response for $filename. Please install jq for better JSON parsing."
            continue
        fi
    fi

    # Get current datetime in UTC
    current_utc_datetime=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

    # Index the data into Elasticsearch with a timeout
    response=$(curl -k -s --connect-timeout 5 --max-time 10 -X POST \
        -H "Content-Type: application/json" \
        -H "Authorization: ApiKey $ES_API_KEY" \
        "${ES_URL}/${index_name}/_doc" \
-d '{
    "filename": "'"${filename}"'",
    "full-path": "'"${full_path}"'",
    "embedding": '"${vector}"',
    "@timestamp": "'"${current_utc_datetime}"'"
}')

    # Check if curl command was successful
    if [ $? -ne 0 ]; then
        echo "Error: Failed to index vector for $filename. Elasticsearch might not be running."
        continue
    fi

    echo "Vector indexed for: $filename"
    echo "===="
done

echo "All images processed and vectors imported successfully."
