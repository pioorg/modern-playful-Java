#!/bin/bash

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

# Ask the user what image they are looking for
echo "What image are you looking for?"
read search_text

echo "Searching for: $search_text"

# Send the text to the encoder service to get a vector representation
response=$(curl -s -X POST http://localhost:5555/encode_text \
    -H "Content-Type: application/json" \
    -d "{\"text\": \"$search_text\"}")

# Check if curl command was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to get vector representation. The service might not be running."
    exit 1
fi

# Extract the embedding array from the JSON response
# Check if jq is available
if command -v jq &> /dev/null; then
    # Use jq to extract the embedding array
    vector=$(echo "$response" | jq -c '.embedding')
    if [ -z "$vector" ] || [ "$vector" = "null" ]; then
        echo "Error: Failed to extract embedding from response."
        exit 1
    fi
else
    # Fallback to grep and sed if jq is not available
    # This is a simple extraction and might not work for all JSON structures
    vector=$(echo "$response" | grep -o '"embedding":\[[^]]*\]' | sed 's/"embedding"://')
    if [ -z "$vector" ]; then
        echo "Error: Failed to extract embedding from response. Please install jq for better JSON parsing."
        exit 1
    fi
fi

# Perform vector search in Elasticsearch
search_response=$(curl -k -s -X POST \
    -H "Content-Type: application/json" \
    -H "Authorization: ApiKey $ES_API_KEY" \
    "${ES_URL}/${index_name}/_search" \
    -d '{
        "size": 3,
        "query": {
            "knn": {
                "field": "embedding",
                "query_vector": '"${vector}"',
                "k": 5,
                "num_candidates": 100
            }
        }
    }')

# Check if curl command was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to search in Elasticsearch. Elasticsearch might not be running."
    exit 1
fi

# Extract and display the results
echo "Top 3 matching images:"
echo "======================"

if command -v jq &> /dev/null; then
    # Use jq to extract the results
    hits=$(echo "$search_response" | jq -c '.hits.hits')
    count=$(echo "$hits" | jq 'length')

    if [ "$count" -eq 0 ]; then
        echo "No matching images found."
        exit 0
    fi

    for i in $(seq 0 $(($count - 1))); do
        full_path=$(echo "$hits" | jq -r ".[$i]._source.\"full-path\"")
        score=$(echo "$hits" | jq -r ".[$i]._score")
        echo "$(($i + 1)). $full_path (Score: $score)"
    done
else
    # Fallback to grep and sed if jq is not available
    echo "For better results, please install jq."
    echo "$search_response" | grep -o '"full-path":"[^"]*"' | sed 's/"full-path":"//;s/"//'
fi

echo "======================"
echo "Search completed."
