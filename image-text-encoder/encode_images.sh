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

# Create a directory for storing vector representations if it doesn't exist
output_dir="${directory_path}/vectors"
mkdir -p "$output_dir"

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
    
    filename=$(basename "$file")
    echo "Processing image: $filename"
    
    # Convert image to Base64
    base64_data=$(base64 -i "$file" | tr -d '\n')
    
    # Send the Base64-encoded image to the API endpoint
    response=$(curl -s -X POST http://localhost:5555/encode_image \
        -H "Content-Type: application/json" \
        -d "{\"image_b64\": \"$base64_data\"}")
    
    # Save the vector representation to a file
    echo "$response" > "${output_dir}/${filename%.*}.json"
    
    echo "Vector representation saved to: ${output_dir}/${filename%.*}.json"
done

echo "All images processed successfully."