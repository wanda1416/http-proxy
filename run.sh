#!/bin/sh
# Forward every request arriving on :8080 to the target, capturing each transaction into ./capture.
java -jar target/http-proxy.jar \
  --server.port=8080 \
  --forward.url=http://127.0.0.1:7080 \
  --capture.dir=./capture

# Enable Basic auth on forwarded requests:
# java -jar target/http-proxy.jar --forward.url=http://127.0.0.1:7080 \
#   --authorize=true --username=ADMIN --password=secret
