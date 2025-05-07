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

# Perform classic search in Elasticsearch (matching file name)
classic_search_response=$(curl -k -s -X POST \
    -H "Content-Type: application/json" \
    -H "Authorization: ApiKey $ES_API_KEY" \
    "${ES_URL}/${index_name}/_search" \
    -d '{
        "size": 5,
        "query": {
            "match": {
                "full-path": "'"$search_text"'"
            }
        }
    }')

# Check if curl command was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to perform classic search in Elasticsearch. Elasticsearch might not be running."
    exit 1
fi

# Perform vector search in Elasticsearch
vector_search_response=$(curl -k -s -X POST \
    -H "Content-Type: application/json" \
    -H "Authorization: ApiKey $ES_API_KEY" \
    "${ES_URL}/${index_name}/_search" \
    -d '{
        "size": 5,
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
    echo "Error: Failed to perform vector search in Elasticsearch. Elasticsearch might not be running."
    exit 1
fi

# Extract results from both searches
if command -v jq &> /dev/null; then
    # Extract classic search results
    classic_hits=$(echo "$classic_search_response" | jq -c '.hits.hits')
    classic_count=$(echo "$classic_hits" | jq 'length')

    # Extract vector search results
    vector_hits=$(echo "$vector_search_response" | jq -c '.hits.hits')
    vector_count=$(echo "$vector_hits" | jq 'length')

    # Combine results using RRF (Reciprocal Rank Fusion)
    # Create temporary files to store document IDs, scores, and paths
    scores_file=$(mktemp)
    paths_file=$(mktemp)

    # Process classic search results
    for i in $(seq 0 $(($classic_count - 1))); do
        doc_id=$(echo "$classic_hits" | jq -r ".[$i]._id")
        full_path=$(echo "$classic_hits" | jq -r ".[$i]._source.\"full-path\"")
        rank=$(($i + 1))
        # RRF formula: 1/(k + rank) where k is a constant (typically 60)
        score=$(echo "scale=6; 1/(60 + $rank)" | bc)
        echo "$doc_id $score" >> "$scores_file"
        echo "$doc_id $full_path" >> "$paths_file"
    done

    # Process vector search results
    for i in $(seq 0 $(($vector_count - 1))); do
        doc_id=$(echo "$vector_hits" | jq -r ".[$i]._id")
        full_path=$(echo "$vector_hits" | jq -r ".[$i]._source.\"full-path\"")
        rank=$(($i + 1))
        # RRF formula: 1/(k + rank) where k is a constant (typically 60)
        score=$(echo "scale=6; 1/(60 + $rank)" | bc)

        # Check if document already exists in scores_file
        existing_score=$(grep "^$doc_id " "$scores_file" | awk '{print $2}')
        if [ -n "$existing_score" ]; then
            # Add the new score to the existing score
            new_score=$(echo "scale=6; $existing_score + $score" | bc)
            # Update the score in the scores_file
            sed -i.bak "s/^$doc_id $existing_score/$doc_id $new_score/" "$scores_file"
            rm -f "$scores_file.bak"
        else
            # Add new document to scores_file and paths_file
            echo "$doc_id $score" >> "$scores_file"
            echo "$doc_id $full_path" >> "$paths_file"
        fi
    done

    # Sort documents by combined score (descending) and take top 5
    sorted_docs=$(sort -k2,2 -nr "$scores_file" | head -5)

    # Display the results
    echo "Top matching images (hybrid search):"
    echo "=================================="

    rank=1
    while read -r doc_id score; do
        # Look up the document path from paths_file
        full_path=$(grep "^$doc_id " "$paths_file" | awk '{print $2}')
        echo "$rank. $full_path (RRF Score: $score)"
        rank=$((rank + 1))
    done <<< "$sorted_docs"

    if [ $rank -eq 1 ]; then
        echo "No matching images found."
    fi

    # Clean up temporary files
    rm -f "$scores_file" "$paths_file"
else
    # Fallback if jq is not available
    echo "Error: jq is required for hybrid search. Please install jq."
    exit 1
fi

echo "=================================="
echo "Search completed."
