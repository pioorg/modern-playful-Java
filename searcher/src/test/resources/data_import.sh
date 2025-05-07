# Check if Elasticsearch environment variables are set
if [ -z "$ES_URL" ] || [ -z "$ES_API_KEY" ]; then
    echo "Error: Elasticsearch environment variables ES_URL and ES_API_KEY must be set."
    echo "Example:"
    echo "export ES_URL=http://localhost:9200"
    echo "export ES_API_KEY=TFot....=="
    exit 1
fi

# Import mapping
echo "Importing mapping..."
curl -X PUT "$ES_URL/catalogue" \
  -H "Authorization: ApiKey $ES_API_KEY" \
  -H "Content-Type: application/json" \
  -d @mapping.json

# Import data
echo "Importing data..."
curl -X POST "$ES_URL/catalogue/_bulk" \
  -H "Authorization: ApiKey $ES_API_KEY" \
  -H "Content-Type: application/x-ndjson" \
  --data-binary @data.ndjson

# Import prices
echo "Importing prices..."
curl -X POST "$ES_URL/catalogue/_bulk" \
  -H "Authorization: ApiKey $ES_API_KEY" \
  -H "Content-Type: application/x-ndjson" \
  --data-binary @prices.ndjson